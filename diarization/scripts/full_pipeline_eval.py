"""Run the full on-device pipeline on a labeled dialogue dataset.

ASR (GigaAM) -> diarization -> word-to-speaker assignment -> utterance merge ->
``{"transcript": [...]}`` JSON. Also reports DER and word-level speaker
accuracy. Intended for the Russian Dialogs corpus prepared by
``prepare_dialogs_ru.py``; other datasets from ``word_assignment.DATASETS`` are
supported too.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time

from asr import load_recognizer, recognize_words
from evaluate import evaluate_file, load_marking
from models import SEGMENTATION, get_embedding
from pipeline import PipelineConfig, diarize, read_audio
from word_assignment import DATASETS as WORD_DATASETS
from word_assignment import assign_by_overlap, evaluate_words

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

DATASETS = dict(WORD_DATASETS)
DATASETS["dialogs_ru"] = ("data/dialogs_ru/test", False)


def speaker_labels(segments) -> dict[int, int]:
    """speaker_1 is the first cluster to speak, as in the Kotlin API."""
    order = [
        int(s["speaker"])
        for s in sorted(segments, key=lambda x: (x["start"], x["end"]))
    ]
    labels: dict[int, int] = {}
    for spk in order:
        if spk not in labels:
            labels[spk] = len(labels) + 1
    return labels


def build_utterances(
    words: list[dict],
    speakers: list[int | None],
    labels: dict[int, int],
    max_gap: float = 1.2,
    max_duration: float = 15.0,
) -> list[dict]:
    items = [
        (w, spk)
        for w, spk in zip(words, speakers)
        if spk is not None and str(w.get("word", "")).strip()
    ]
    items.sort(key=lambda x: (x[0]["start"], x[0]["end"]))
    out: list[dict] = []
    current: list[tuple[dict, int]] = []

    def flush() -> None:
        if not current:
            return
        start = float(current[0][0]["start"])
        end = float(current[-1][0]["end"])
        spk = current[0][1]
        text = " ".join(str(w["word"]).strip() for w, _ in current)
        out.append(
            {
                "speaker_id": labels.get(spk, spk + 1),
                "start_ms": int(round(start * 1000)),
                "end_ms": int(round(end * 1000)),
                "text": text,
            }
        )

    for word, spk in items:
        if current:
            prev_word, prev_spk = current[-1]
            gap = float(word["start"]) - float(prev_word["end"])
            duration = float(word["end"]) - float(current[0][0]["start"])
            if spk != prev_spk or gap > max_gap or duration > max_duration:
                flush()
                current = []
        current.append((word, spk))
    flush()
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="Full ASR + diarization pipeline evaluation")
    ap.add_argument("--dataset", default="dialogs_ru", choices=sorted(DATASETS))
    ap.add_argument("--files", default="", help="comma-separated file names")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--model", default="campplus_zh_en")
    ap.add_argument("--seg-model", default="pyannote-3.0")
    ap.add_argument("--scales", default="2.0:1.0")
    ap.add_argument("--auto-k-scales", default="2.0:1.0")
    ap.add_argument("--oracle-k", action="store_true", help="use reference K")
    ap.add_argument("--max-gap", type=float, default=1.2)
    ap.add_argument("--max-utterance", type=float, default=15.0)
    ap.add_argument("--out", default=os.path.join(ROOT, "results", "raw", "full_pipeline.jsonl"))
    ap.add_argument("--json-dir", default=os.path.join(ROOT, "results", "transcripts"))
    args = ap.parse_args()

    if args.dataset not in DATASETS:
        print(f"unknown dataset {args.dataset}", file=sys.stderr)
        return 1
    rel_dir, trim_ref = DATASETS[args.dataset]
    data_dir = os.path.join(ROOT, rel_dir)
    marking = load_marking(os.path.join(data_dir, "marking.json"))
    if args.files:
        names = [n.strip() for n in args.files.split(",") if n.strip()]
    else:
        names = sorted(marking)
        if args.limit > 0:
            names = names[: args.limit]

    rec = load_recognizer()
    emb = get_embedding(args.model)
    seg_model = SEGMENTATION[args.seg_model].path
    scales = tuple(tuple(float(x) for x in p.split(":")) for p in args.scales.split(","))
    auto_k_scales = (
        tuple(tuple(float(x) for x in p.split(":")) for p in args.auto_k_scales.split(","))
        if args.auto_k_scales
        else None
    )
    cfg = PipelineConfig(
        segmentation_model=seg_model,
        embedding_model=emb.path,
        scales=scales,
        auto_k_scales=auto_k_scales,
    )
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    os.makedirs(args.json_dir, exist_ok=True)

    totals = {"correct": 0, "evaluated": 0, "ref": 0.0, "der": 0.0}
    for name in names:
        audio_path = os.path.join(data_dir, name)
        wav = read_audio(audio_path)
        ref = marking[name]
        ref_segments = ref["segments"]
        ref_k = int(
            ref.get("num_speakers", len({s["speaker"] for s in ref_segments}))
        )

        t0 = time.perf_counter()
        text, words = recognize_words(wav, rec)
        asr_sec = time.perf_counter() - t0

        out = diarize(
            wav,
            cfg,
            audio_path=audio_path,
            num_speakers=ref_k if args.oracle_k else None,
        )
        segs = [
            {"start": s.start, "end": s.end, "speaker": s.speaker}
            for s in out.segments
        ]
        labels = speaker_labels(segs)
        pred = assign_by_overlap(words, segs)
        word_metrics = evaluate_words(words, pred, ref_segments)
        utterances = build_utterances(
            words,
            pred,
            labels,
            max_gap=args.max_gap,
            max_duration=args.max_utterance,
        )
        transcript = {"transcript": utterances}
        stem = os.path.splitext(name)[0]
        json_path = os.path.join(args.json_dir, f"{args.dataset}_{stem}.json")
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(transcript, f, ensure_ascii=False, indent=2)

        der = evaluate_file(audio_path, ref, segs, trim_ref=trim_ref)
        row = {
            "dataset": args.dataset,
            "file": name,
            "model": args.model,
            "mode": "oracle" if args.oracle_k else "auto",
            "scales": args.scales,
            "ref_speakers": ref_k,
            "hyp_speakers": out.speaker_count,
            "der": der["der"],
            "confusion": der["confusion"],
            "miss": der["miss"],
            "false_alarm": der["false_alarm"],
            "ref_duration": der["ref_duration"],
            "hyp_duration": der["hyp_duration"],
            "duration": len(wav) / 16000.0,
            "word_accuracy": word_metrics["accuracy"],
            "word_coverage": word_metrics["coverage"],
            "words_evaluated": word_metrics["evaluated"],
            "n_words": len(words),
            "n_utterances": len(utterances),
            "asr_sec": round(asr_sec, 2),
            "diar_sec": round(out.timings.get("total_sec", 0.0), 2),
            "json": os.path.relpath(json_path, ROOT),
            "text_sample": text[:200],
        }
        with open(args.out, "a", encoding="utf-8") as f:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
        print(
            f"{name}: K={out.speaker_count}/{ref_k} DER={der['der']*100:5.1f}% "
            f"words={word_metrics['accuracy']*100:5.1f}% "
            f"utt={len(utterances)} json={row['json']}"
        )
        totals["correct"] += word_metrics["correct"]
        totals["evaluated"] += word_metrics["evaluated"]
        totals["ref"] += der["ref_duration"]
        totals["der"] += der["der"] * der["ref_duration"]

    if totals["ref"] > 0:
        print("\nsummary")
        print(f"  DER: {totals['der']/totals['ref']*100:.2f}%")
        if totals["evaluated"]:
            print(
                f"  word accuracy: {totals['correct']}/{totals['evaluated']} = "
                f"{totals['correct']/totals['evaluated']*100:.2f}%"
            )
    print(f"\nresults -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
