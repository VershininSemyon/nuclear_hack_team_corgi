"""Second domain: Golos 10h crowd (spontaneous short commands, noisy)."""
import io, json, os, time
import numpy as np
import pyarrow.parquet as pq
import soundfile as sf

import eval as E

N = int(os.environ.get("N_UTTS", "200"))
E.THREADS = int(os.environ.get("THREADS", "4"))
DATA = E.DATA

tbl = pq.read_table(os.path.join(DATA, "golos_test0.parquet"), columns=["audio", "transcription"])
rows = tbl.slice(0, N * 2).to_pylist()
utts = []
for r in rows:
    if not r["transcription"]:
        continue
    x, sr = sf.read(io.BytesIO(r["audio"]["bytes"]), dtype="float32")
    assert sr == 16000
    if x.ndim > 1:
        x = x.mean(axis=1)
    utts.append({"file": str(len(utts)), "ref": r["transcription"], "samples": x, "dur": len(x) / sr})
    if len(utts) >= N:
        break
print(f"{len(utts)} utts, {sum(u['dur'] for u in utts):.0f}s", flush=True)

refs = [E.norm(u["ref"]) for u in utts]
ref_words = np.array([len(r.split()) for r in refs], dtype=float)
results = []
for name, kind, prefix in E.REGISTRY:
    try:
        r = E.run(name, kind, prefix, utts)
    except Exception as e:
        print("SKIP", name, e); continue
    hyps = [E.norm(h) for h in r["hyps"]]
    r["wer"] = E.jiwer_wer(refs, hyps)
    r["cer"] = E.jiwer_cer(refs, hyps)
    r["hyps_norm"] = hyps
    r["refs"] = refs
    r["utt_wer"] = [E.jiwer_wer(a, b) if a else 0.0 for a, b in zip(refs, hyps)]
    results.append(r)
    print(f"{name:28s} WER {r['wer']*100:5.2f}%  CER {r['cer']*100:5.2f}%  RTF {r['rtf']:.3f}", flush=True)

rng = np.random.default_rng(1)
for r in results:
    errs = np.array([E.jiwer_wer(refs[i], r["hyps_norm"][i]) * ref_words[i] for i in range(len(refs))])
    boots = []
    for _ in range(2000):
        idx = rng.integers(0, len(refs), len(refs))
        boots.append(errs[idx].sum() / max(ref_words[idx].sum(), 1))
    r["wer_ci"] = [float(np.percentile(boots, 2.5)), float(np.percentile(boots, 97.5))]

with open(os.path.join(E.OUT, "golos.json"), "w", encoding="utf-8") as f:
    json.dump({"n_utts": len(utts), "dataset": "golos_10h_crowd", "models": results}, f, ensure_ascii=False, indent=2)
print("saved golos.json", flush=True)
