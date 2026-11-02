"""Benchmark runner: local Russian ASR models via sherpa-onnx (same runtime as Android)."""
import json, os, re, sys, time, glob
import numpy as np
import soundfile as sf
import sherpa_onnx
from jiwer import wer as jiwer_wer, cer as jiwer_cer
from num2words import num2words

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, "models")
DATA = os.path.join(ROOT, "data")
OUT = os.path.join(ROOT, "results")
os.makedirs(OUT, exist_ok=True)

N_UTTS = int(os.environ.get("N_UTTS", "200"))
THREADS = int(os.environ.get("THREADS", "4"))
SPLIT = os.environ.get("SPLIT", "ru")

# ---------------------------------------------------------------- text norm
_num_re = re.compile(r"\d[\d\s.,]*\d|\d")
def _num_to_words(m):
    s = m.group(0).replace(" ", "").replace("\u00a0", "")
    s = s.replace(",", ".")
    try:
        if "." in s:
            return num2words(float(s), lang="ru")
        return num2words(int(s), lang="ru")
    except Exception:
        return " "

def norm(s: str) -> str:
    s = s.lower().replace("ё", "е").replace("’", "'")
    s = s.replace("-", " ")
    s = _num_re.sub(_num_to_words, s)
    s = re.sub(r"[^\w\s']", " ", s, flags=re.UNICODE)
    s = re.sub(r"\s+", " ", s).strip()
    return s

# ---------------------------------------------------------------- dataset
def load_fleurs(lang_dir, tsv, audio_root, n):
    rows = []
    with open(tsv, encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) < 3:
                continue
            rows.append((p[1], p[2]))
    rows = rows[:n]
    out = []
    for fn, ref in rows:
        path = os.path.join(audio_root, fn)
        if not os.path.exists(path):
            continue
        samples, sr = sf.read(path, dtype="float32")
        if samples.ndim > 1:
            samples = samples.mean(axis=1)
        assert sr == 16000, sr
        out.append({"file": fn, "ref": ref, "samples": samples, "dur": len(samples) / sr})
    return out

# ---------------------------------------------------------------- models
def pick(dirpath, *names):
    for n in names:
        p = os.path.join(dirpath, n)
        if os.path.exists(p):
            return p
    g = glob.glob(os.path.join(dirpath, names[0]))
    return g[0] if g else None

def find_model_dir(prefix):
    cands = [d for d in glob.glob(os.path.join(MODELS, prefix + "*")) if os.path.isdir(d)]
    if not cands:
        raise FileNotFoundError(prefix)
    # prefer the shallowest / shortest name
    return sorted(cands, key=len)[0]

def build(kind, d):
    if kind == "whisper":
        enc = pick(d, "turbo-encoder.int8.onnx", "small-encoder.int8.onnx", "base-encoder.int8.onnx",
                   "*-encoder.int8.onnx", "*-encoder.onnx")
        dec = pick(d, "turbo-decoder.int8.onnx", "small-decoder.int8.onnx", "base-decoder.int8.onnx",
                   "*-decoder.int8.onnx", "*-decoder.onnx")
        tok = pick(d, "turbo-tokens.txt", "small-tokens.txt", "base-tokens.txt", "*-tokens.txt", "tokens.txt")
        return sherpa_onnx.OfflineRecognizer.from_whisper(
            encoder=enc, decoder=dec, tokens=tok, language="ru", task="transcribe",
            num_threads=THREADS, tail_paddings=-1)
    enc = pick(d, "encoder.int8.onnx", "encoder.onnx")
    dec = pick(d, "decoder.int8.onnx", "decoder.onnx")
    joi = pick(d, "joiner.int8.onnx", "joiner.onnx")
    tok = pick(d, "tokens.txt")
    return sherpa_onnx.OfflineRecognizer.from_transducer(
        encoder=enc, decoder=dec, joiner=joi, tokens=tok,
        num_threads=THREADS, model_type="nemo_transducer" if kind == "nemo" else "transducer")

def model_size_mb(d):
    tot = 0
    for p in glob.glob(os.path.join(d, "*.onnx")):
        if ".int8." in os.path.basename(p) or not glob.glob(p.replace(".onnx", ".int8.onnx")):
            tot += os.path.getsize(p)
    tok = glob.glob(os.path.join(d, "*tokens.txt"))
    if tok:
        tot += os.path.getsize(tok[0])
    return tot / 1e6

def run(name, kind, prefix, utts):
    d = find_model_dir(prefix)
    rec = build(kind, d)
    # warmup
    s = rec.create_stream(); s.accept_waveform(16000, utts[0]["samples"]); rec.decode_stream(s)

    hyps, times = [], []
    t_all = time.perf_counter()
    for u in utts:
        t0 = time.perf_counter()
        st = rec.create_stream()
        st.accept_waveform(16000, u["samples"])
        rec.decode_stream(st)
        times.append(time.perf_counter() - t0)
        hyps.append(st.result.text)
    total = time.perf_counter() - t_all
    audio = sum(u["dur"] for u in utts)
    return {
        "model": name, "dir": d, "size_mb": model_size_mb(d),
        "hyps": hyps, "times": times,
        "audio_sec": audio, "decode_sec": sum(times), "wall_sec": total,
        "rtf": sum(times) / audio,
        "median_latency": float(np.median(times)),
    }

REGISTRY = [
    ("GigaAM v3 (RU)",            "nemo",      "sherpa-onnx-nemo-transducer-giga-am-v3"),
    ("Parakeet TDT 0.6B v3",      "nemo",      "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3"),
    ("Whisper large-v3-turbo",    "whisper",   "sherpa-onnx-whisper-turbo"),
    ("Whisper small",             "whisper",   "sherpa-onnx-whisper-small"),
    ("Zipformer-RU",              "transducer","sherpa-onnx-zipformer-ru"),
]

def main():
    if SPLIT == "ru":
        utts = load_fleurs("ru_ru", os.path.join(DATA, "ru_ru.tsv"), os.path.join(DATA, "test"), N_UTTS)
    else:
        utts = load_fleurs("en_us", os.path.join(DATA, "en_us.tsv"), os.path.join(DATA, "en_us", "test"), N_UTTS)
    print(f"{len(utts)} utterances, {sum(u['dur'] for u in utts):.0f}s audio", flush=True)

    refs = [norm(u["ref"]) for u in utts]
    ref_words = [len(r.split()) for r in refs]
    results = []
    only = os.environ.get("ONLY", "")
    for name, kind, prefix in REGISTRY:
        if only and only.lower() not in name.lower():
            continue
        try:
            r = run(name, kind, prefix, utts)
        except Exception as e:
            print(f"SKIP {name}: {type(e).__name__}: {e}", flush=True)
            continue
        hyps = [norm(h) for h in r["hyps"]]
        r["wer"] = jiwer_wer(refs, hyps)
        r["cer"] = jiwer_cer(refs, hyps)
        r["empty"] = sum(1 for h in hyps if not h)
        r["refs"] = refs
        r["hyps_norm"] = hyps
        r["utt_wer"] = [jiwer_wer(a, b) if a else 0.0 for a, b in zip(refs, hyps)]
        results.append(r)
        print(f"{name:28s} WER {r['wer']*100:5.2f}%  CER {r['cer']*100:5.2f}%  "
              f"RTF {r['rtf']:.3f}  size {r['size_mb']:.0f}MB", flush=True)

    # bootstrap CI over utterances
    rng = np.random.default_rng(0)
    for r in results:
        errs = np.array([jiwer_wer(refs[i], r["hyps_norm"][i]) * ref_words[i] for i in range(len(refs))])
        w = np.array(ref_words, dtype=float)
        boots = []
        for _ in range(2000):
            idx = rng.integers(0, len(refs), len(refs))
            boots.append(errs[idx].sum() / max(w[idx].sum(), 1))
        r["wer_ci"] = [float(np.percentile(boots, 2.5)), float(np.percentile(boots, 97.5))]

    for r in results:
        r.pop("refs", None)

    with open(os.path.join(OUT, "results.json"), "w", encoding="utf-8") as f:
        json.dump({"n_utts": len(utts), "split": SPLIT, "threads": THREADS, "models": results}, f, ensure_ascii=False, indent=2)
    with open(os.path.join(OUT, "transcripts.jsonl"), "w", encoding="utf-8") as f:
        for i, u in enumerate(utts):
            row = {"file": u["file"], "ref": u["ref"], "norm_ref": refs[i]}
            for r in results:
                row[r["model"]] = r["hyps_norm"][i]
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print("saved", OUT, flush=True)

if __name__ == "__main__":
    main()
