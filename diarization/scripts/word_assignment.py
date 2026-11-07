"""Assign a speaker id to every ASR word and measure word-level accuracy.

Two assigners are provided:

* ``assign_by_overlap`` — max time overlap with diarization segments (fast,
  robust when word boundaries are accurate);
* ``assign_by_embedding`` — for words that straddle a speaker change or fall
  into a gap, compare the word embedding with the speaker centroids produced
  by the diarization step; overlap is used as a prior.

The benchmark keeps only words whose reference speaker covers at least half
of the word interval, so boundary artefacts do not distort the accuracy.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time

import numpy as np
import soundfile as sf

from asr import load_recognizer, recognize_words
from evaluate import load_marking
from models import get_embedding
from pipeline import PipelineConfig, diarize, embed_units, read_audio

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

DATASETS = {
    "ru_dev": ("data/ru/dev", True),
    "ru_test": ("data/ru/test", True),
    "ami_validation": ("data/ami/validation", False),
    "ami_test": ("data/ami/test", False),
    "vox_dev": ("data/vox/dev", False),
}


def speaker_overlaps(word: dict, segments) -> dict[int, float]:
    a, b = float(word["start"]), float(word["end"])
    out: dict[int, float] = {}
    for s in segments:
        spk = int(s["speaker"] if isinstance(s, dict) else s.speaker)
        st = float(s["start"] if isinstance(s, dict) else s.start)
        en = float(s["end"] if isinstance(s, dict) else s.end)
        ov = min(b, en) - max(a, st)
        if ov > 0:
            out[spk] = out.get(spk, 0.0) + ov
    return out


def _nearest_speaker(word: dict, segments) -> int | None:
    a, b = float(word["start"]), float(word["end"])
    best = None
    for s in segments:
        spk = int(s["speaker"] if isinstance(s, dict) else s.speaker)
        st = float(s["start"] if isinstance(s, dict) else s.start)
        en = float(s["end"] if isinstance(s, dict) else s.end)
        d = max(st - b, a - en, 0.0)
        if best is None or d < best[0]:
            best = (d, spk)
    return None if best is None else best[1]


def assign_by_overlap(words: list[dict], segments) -> list[int | None]:
    result: list[int | None] = []
    for w in words:
        ov = speaker_overlaps(w, segments)
        if ov:
            result.append(max(ov, key=ov.get))
        else:
            result.append(_nearest_speaker(w, segments))
    return result


def _word_embeddings(
    wav: np.ndarray, words: list[dict], model_path: str, min_seconds: float = 0.35
) -> tuple[np.ndarray, np.ndarray]:
    units = [
        {
            "chunk": 0,
            "speaker": 0,
            "intervals": [
                [
                    max(0, int(round(w["start"] * 16000))),
                    min(len(wav), int(round(w["end"] * 16000))),
                ]
            ],
            "duration": max(0.0, w["end"] - w["start"]),
            "purity": 1.0,
        }
        for w in words
    ]
    # drop empty intervals; pad a little so very short words still work
    for u in units:
        a, b = u["intervals"][0]
        if b <= a:
            u["intervals"][0] = [a, min(len(wav), a + int(min_seconds * 16000))]
    return embed_units(wav, units, model_path, min_seconds=min_seconds)


def assign_by_embedding(
    words: list[dict],
    segments,
    wav: np.ndarray,
    centroids: np.ndarray,
    model_path: str,
    overlap_weight: float = 0.35,
    trust_overlap_share: float = 0.6,
) -> tuple[list[int | None], dict]:
    """Hybrid: trust clear time overlap, use embeddings at boundaries."""
    base = assign_by_overlap(words, segments)
    if centroids is None or len(centroids) == 0 or not words:
        return base, {"used_embedding": 0}

    emb, ok = _word_embeddings(wav, words, model_path)
    used = 0
    out: list[int | None] = []
    for i, w in enumerate(words):
        ov = speaker_overlaps(w, segments)
        total = sum(ov.values())
        share = max(ov.values()) / total if total > 0 else 0.0
        if total > 0 and share >= trust_overlap_share:
            out.append(max(ov, key=ov.get))
            continue
        if not ok[i]:
            out.append(base[i])
            continue
        sims = centroids @ emb[i]
        cand = list(ov) if ov else list(range(len(centroids)))
        if not cand:
            out.append(base[i])
            continue
        scores = []
        for c in cand:
            prior = ov.get(c, 0.0) / total if total > 0 else 1.0 / len(cand)
            scores.append(float(sims[c]) + overlap_weight * prior)
        out.append(cand[int(np.argmax(scores))])
        used += 1
    return out, {"used_embedding": used}


def reference_speaker(
    word: dict, ref_segments: list[dict], min_share: float = 0.5
) -> tuple[str | None, float]:
    a, b = float(word["start"]), float(word["end"])
    dur = max(b - a, 1e-9)
    votes: dict[str, float] = {}
    for s in ref_segments:
        ov = min(b, float(s["end"])) - max(a, float(s["start"]))
        if ov > 0:
            votes[str(s["speaker"])] = votes.get(str(s["speaker"]), 0.0) + ov
    if not votes:
        return None, 0.0
    spk, sec = max(votes.items(), key=lambda t: t[1])
    share = sec / dur
    return (spk if share >= min_share else None), share


def evaluate_words(
    words: list[dict],
    pred: list[int | None],
    ref_segments: list[dict],
    min_share: float = 0.5,
) -> dict:
    pairs: list[tuple[int | None, str]] = []
    items: list[tuple[dict, int | None, str]] = []
    covered = 0
    for w, p in zip(words, pred):
        ref, share = reference_speaker(w, ref_segments, min_share)
        if ref is None:
            continue
        pairs.append((p, ref))
        items.append((w, p, ref))
        if p is not None:
            covered += 1

    # Cluster ids have no intrinsic meaning: map them to reference speakers by
    # the Hungarian assignment that maximises the number of correct words.
    from scipy.optimize import linear_sum_assignment

    pred_ids = sorted({p for p, _ in pairs if p is not None})
    ref_ids = sorted({r for _, r in pairs})
    mapping: dict[int, str] = {}
    correct = 0
    if pred_ids and ref_ids:
        conf = np.zeros((len(pred_ids), len(ref_ids)), dtype=np.int64)
        for p, r in pairs:
            if p is not None:
                conf[pred_ids.index(p), ref_ids.index(r)] += 1
        rows, cols = linear_sum_assignment(-conf)
        for r, c in zip(rows, cols):
            mapping[pred_ids[r]] = ref_ids[c]
            correct += int(conf[r, c])
    errors: list[dict] = []
    for w, p, ref in items:
        mapped = mapping.get(p) if p is not None else None
        if mapped != ref and len(errors) < 20:
            errors.append(
                {
                    "word": w["word"],
                    "start": w["start"],
                    "ref": ref,
                    "pred": p,
                    "pred_mapped": mapped,
                }
            )
    total = len(pairs)
    return {
        "words": len(words),
        "evaluated": total,
        "covered": covered,
        "correct": correct,
        "accuracy": correct / total if total else 0.0,
        "coverage": covered / total if total else 0.0,
        "mapping": {str(k): v for k, v in mapping.items()},
        "errors": errors,
    }


def main() -> None:
    ap = argparse.ArgumentParser(description="Word-level speaker assignment benchmark")
    ap.add_argument("--dataset", default="ru_dev")
    ap.add_argument("--files", default="", help="comma-separated file names")
    ap.add_argument("--limit", type=int, default=8)
    ap.add_argument("--per-k", type=int, default=0, help="RU: files per true speaker count")
    ap.add_argument("--model", default="campplus_zh_en")
    ap.add_argument("--seg-model", default="pyannote-3.0")
    ap.add_argument("--scales", default="3.0:1.5")
    ap.add_argument("--auto-k-scales", default="2.0:1.0")
    ap.add_argument("--num-speakers", type=int, default=0)
    ap.add_argument(
        "--oracle-k",
        action="store_true",
        help="use the reference speaker count per file (upper bound for assignment)",
    )
    ap.add_argument("--assigner", default="both", choices=["overlap", "embedding", "both"])
    ap.add_argument("--out", default=os.path.join(ROOT, "results", "raw", "words.jsonl"))
    args = ap.parse_args()

    from models import SEGMENTATION

    rel_dir, trim = DATASETS[args.dataset]
    data_dir = os.path.join(ROOT, rel_dir)
    marking = load_marking(os.path.join(data_dir, "marking.json"))
    if args.files:
        names = [n.strip() for n in args.files.split(",") if n.strip()]
    elif args.per_k > 0:
        by_k: dict[int, list[str]] = {}
        for name, entry in marking.items():
            k = int(entry.get("num_speakers", len({s["speaker"] for s in entry["segments"]})))
            by_k.setdefault(k, []).append(name)
        names = []
        for k in sorted(by_k):
            names.extend(sorted(by_k[k])[: args.per_k])
    else:
        names = sorted(marking)[: args.limit]

    rec = load_recognizer()
    emb_model = get_embedding(args.model)
    seg_model = SEGMENTATION[args.seg_model].path
    scales = tuple(tuple(float(x) for x in p.split(":")) for p in args.scales.split(","))
    auto_k_scales = (
        tuple(tuple(float(x) for x in p.split(":")) for p in args.auto_k_scales.split(","))
        if args.auto_k_scales
        else None
    )

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    sums = {"overlap_correct": 0, "overlap_total": 0, "emb_correct": 0, "emb_total": 0}
    for name in names:
        audio_path = os.path.join(data_dir, name)
        wav = read_audio(audio_path)
        t0 = time.perf_counter()
        text, words = recognize_words(wav, rec)
        asr_sec = time.perf_counter() - t0
        ref_segments = marking[name]["segments"]
        ref_k = int(
            marking[name].get(
                "num_speakers", len({s["speaker"] for s in ref_segments})
            )
        )
        cfg = PipelineConfig(
            segmentation_model=seg_model,
            embedding_model=emb_model.path,
            scales=scales,
            auto_k_scales=auto_k_scales,
        )
        out = diarize(
            wav,
            cfg,
            audio_path=audio_path,
            num_speakers=ref_k if args.oracle_k else (args.num_speakers or None),
            keep_embeddings=True,
        )
        segs = [
            {"start": s.start, "end": s.end, "speaker": s.speaker}
            for s in out.segments
        ]

        row = {
            "dataset": args.dataset,
            "file": name,
            "model": args.model,
            "mode": "oracle" if (args.num_speakers or args.oracle_k) else "auto",
            "scales": args.scales,
            "ref_speakers": ref_k,
            "hyp_speakers": out.speaker_count,
            "asr_sec": round(asr_sec, 2),
            "diar_sec": round(out.timings.get("total_sec", 0.0), 2),
            "n_words": len(words),
            "text_sample": text[:160],
        }
        if args.assigner in ("overlap", "both"):
            pred = assign_by_overlap(words, segs)
            m = evaluate_words(words, pred, ref_segments)
            row["overlap"] = {k: m[k] for k in ("evaluated", "correct", "accuracy", "coverage")}
            sums["overlap_correct"] += m["correct"]
            sums["overlap_total"] += m["evaluated"]
        if args.assigner in ("embedding", "both"):
            pred, diag = assign_by_embedding(
                words, segs, wav, out.centroids, emb_model.path
            )
            m = evaluate_words(words, pred, ref_segments)
            row["embedding"] = {
                **{k: m[k] for k in ("evaluated", "correct", "accuracy", "coverage")},
                **diag,
            }
            sums["emb_correct"] += m["correct"]
            sums["emb_total"] += m["evaluated"]
            row["errors"] = m["errors"]

        with open(args.out, "a", encoding="utf-8") as f:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
        parts = [f"{name} k={out.speaker_count}/{ref_k} words={len(words)}"]
        if "overlap" in row:
            parts.append(f"overlap={row['overlap']['accuracy']*100:.1f}%")
        if "embedding" in row:
            parts.append(f"emb={row['embedding']['accuracy']*100:.1f}%")
        print(" ".join(parts))

    print("\nsummary")
    for tag in ("overlap", "emb"):
        tot = sums[f"{tag}_total"]
        if tot:
            print(
                f"  {tag}: {sums[f'{tag}_correct']}/{tot} = "
                f"{sums[f'{tag}_correct']/tot*100:.2f}%"
            )
    print(f"results -> {args.out}")


if __name__ == "__main__":
    main()
