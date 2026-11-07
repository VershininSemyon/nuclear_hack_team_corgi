"""Diarization benchmark: our pipeline vs the sherpa-onnx threshold baseline.

Every run appends one JSON line, so the benchmark can be interrupted and
resumed. Existing records with the same key are skipped.

Examples
--------
    # screening: 13 embedding models on a dev sample, 2 s / 1 s windows
    python scripts/benchmark.py --stage screen --models all

    # final: best models on full dev + test sets
    python scripts/benchmark.py --stage final --models campplus_zh_en,eres2netv2_zh

    # one model, all modes, full test sets
    python scripts/benchmark.py --stage test --models campplus_zh_en
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from dataclasses import dataclass

import numpy as np
import soundfile as sf

from evaluate import aggregate, evaluate_file, format_metrics, load_marking
from models import EMBEDDING, SEGMENTATION, existing_embeddings, get_embedding
from pipeline import (
    PipelineConfig,
    diarize,
    read_audio,
    sherpa_diarize,
)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RESULTS_DIR = os.path.join(ROOT, "results", "raw")
RAW_PATH = os.path.join(RESULTS_DIR, "runs.jsonl")

# name -> (relative dir, trim_reference_energy)
DATASETS = {
    "ami_validation": ("data/ami/validation", False),
    "ami_test": ("data/ami/test", False),
    "vox_dev": ("data/vox/dev", False),
    "vox_test": ("data/vox/test", False),
    "ru_dev": ("data/ru/dev", True),
    "ru_test": ("data/ru/test", True),
}

DEV_DATASETS = ["ami_validation", "vox_dev", "ru_dev"]
TEST_DATASETS = ["ami_test", "vox_test", "ru_test"]


@dataclass
class RunKey:
    dataset: str
    file: str
    model: str
    mode: str
    scales: str
    auto_k_scales: str
    clustering: str
    seg_model: str
    cache: bool = True

    def text(self) -> str:
        return "|".join(
            [
                self.dataset,
                self.file,
                self.model,
                self.mode,
                self.scales,
                self.auto_k_scales,
                self.clustering,
                self.seg_model,
                "cold" if not self.cache else "warm",
            ]
        )


def parse_scales(text: str) -> tuple[tuple[float, float], ...]:
    return tuple(tuple(float(x) for x in p.split(":")) for p in text.split(","))


def scales_text(scales) -> str:
    return ",".join(f"{a:g}:{b:g}" for a, b in scales)


def load_done(path: str) -> set[str]:
    done: set[str] = set()
    if not os.path.exists(path):
        return done
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                r = json.loads(line)
            except json.JSONDecodeError:
                continue
            if "key" in r:
                key = r["key"]
                done.add(key)
                parts = key.split("|")
                if parts and parts[-1] in ("warm", "cold"):
                    done.add("|".join(parts[:-1]))
    return done


def append_result(path: str, record: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n")


def ru_sample(marking: dict, per_k: int) -> list[str]:
    """Deterministic sample: `per_k` files for each true speaker count."""
    by_k: dict[int, list[str]] = {}
    for name, entry in marking.items():
        k = int(entry.get("num_speakers", len({s["speaker"] for s in entry["segments"]})))
        by_k.setdefault(k, []).append(name)
    picked: list[str] = []
    for k in sorted(by_k):
        picked.extend(sorted(by_k[k])[:per_k])
    return picked


def sample_files(dataset: str, marking: dict, args) -> list[str]:
    names = sorted(marking)
    if dataset in ("ru_dev", "ru_test"):
        names = ru_sample(marking, args.ru_per_k)
    elif args.per_dataset > 0:
        names = names[: args.per_dataset]
    return names


def speaker_count(entry: dict) -> int:
    if "num_speakers" in entry:
        return int(entry["num_speakers"])
    return len({s["speaker"] for s in entry["segments"]})


# Same thresholds as the Kotlin `SpeakerCountSweep` wrapper.
SWEEP_THRESHOLDS = tuple(round(0.35 + 0.05 * i, 2) for i in range(11))


def choose_sweep(counts: list[int]) -> int:
    """Pick the run index by the same plateau rule as Kotlin SpeakerCountSweep."""
    if len(counts) == 1:
        return 0
    frequency: dict[int, int] = {}
    for c in counts:
        frequency[c] = frequency.get(c, 0) + 1
    best_frequency = max(frequency.values())
    candidates = [c for c, n in frequency.items() if n == best_frequency]
    ordered = sorted(counts)
    n = len(ordered)
    median = (
        float(ordered[n // 2])
        if n % 2 == 1
        else (ordered[n // 2 - 1] + ordered[n // 2]) / 2.0
    )
    chosen = min(candidates, key=lambda c: abs(c - median))
    middle = (len(counts) - 1) / 2.0
    indices = [i for i, c in enumerate(counts) if c == chosen]
    if not indices:
        return 0
    best_center = indices[0]
    best_distance = float("inf")
    i = 0
    while i < len(indices):
        j = i
        while j + 1 < len(indices) and indices[j + 1] == indices[j] + 1:
            j += 1
        center = (indices[i] + indices[j]) / 2.0
        distance = abs(center - middle)
        if distance < best_distance:
            best_distance = distance
            best_center = int(round(center))
        i = j + 1
    return best_center


_sherpa_cache: dict[tuple, object] = {}


def run_sherpa(
    wav: np.ndarray,
    seg_model: str,
    emb_model: str,
    num_speakers: int,
    threshold: float,
    min_on: float,
    min_off: float,
    shift: float,
):
    import sherpa_onnx as so

    key = (seg_model, emb_model, num_speakers, threshold, min_on, min_off, shift)
    if key not in _sherpa_cache:
        cfg = so.OfflineSpeakerDiarizationConfig(
            segmentation=so.OfflineSpeakerSegmentationModelConfig(
                pyannote=so.OfflineSpeakerSegmentationPyannoteModelConfig(
                    model=seg_model, window_shift_ratio=shift
                )
            ),
            embedding=so.SpeakerEmbeddingExtractorConfig(model=emb_model),
            clustering=so.FastClusteringConfig(
                num_clusters=num_speakers, threshold=threshold, compute_confidence=True
            ),
            min_duration_on=min_on,
            min_duration_off=min_off,
        )
        _sherpa_cache[key] = so.OfflineSpeakerDiarization(cfg)
    d = _sherpa_cache[key]
    t0 = time.perf_counter()
    result = d.process(np.ascontiguousarray(wav, dtype=np.float32))
    elapsed = time.perf_counter() - t0
    segs = [
        {"start": float(s.start), "end": float(s.end), "speaker": int(s.speaker)}
        for s in result.sort_by_start_time()
    ]
    return segs, elapsed


def main() -> None:
    ap = argparse.ArgumentParser(description="Run the diarization benchmark")
    ap.add_argument(
        "--stage",
        choices=["screen", "dev", "test", "final"],
        default="screen",
        help="screen=dev sample, dev=full dev, test=full test, final=dev+test",
    )
    ap.add_argument("--models", default="all", help="comma-separated ids or 'all'")
    ap.add_argument(
        "--modes",
        default="auto,oracle,sherpa_auto,sherpa_oracle",
        help="auto, oracle, sherpa_auto, sherpa_oracle, sherpa_sweep",
    )
    ap.add_argument("--scales", default="2.0:1.0")
    ap.add_argument("--auto-k-scales", default="")
    ap.add_argument("--clustering", default="complete")
    ap.add_argument("--seg-model", default="pyannote-3.0")
    ap.add_argument("--threshold", type=float, default=0.5)
    ap.add_argument("--per-dataset", type=int, default=3, help="0 = all files")
    ap.add_argument("--ru-per-k", type=int, default=2)
    ap.add_argument("--no-cache", action="store_true", help="cold run for honest RTF")
    ap.add_argument("--out", default=RAW_PATH)
    ap.add_argument("--datasets", default="", help="override dataset list")
    args = ap.parse_args()

    if args.models == "all":
        model_ids = [m.id for m in existing_embeddings()]
    else:
        model_ids = [m.strip() for m in args.models.split(",") if m.strip()]
    for mid in model_ids:
        get_embedding(mid)  # validate

    modes = [m.strip() for m in args.modes.split(",") if m.strip()]
    scales = parse_scales(args.scales)
    auto_k_scales = parse_scales(args.auto_k_scales) if args.auto_k_scales else None

    if args.datasets:
        datasets = [d.strip() for d in args.datasets.split(",") if d.strip()]
    elif args.stage == "screen":
        datasets = DEV_DATASETS
    elif args.stage == "dev":
        datasets = DEV_DATASETS
    elif args.stage == "test":
        datasets = TEST_DATASETS
    else:
        datasets = DEV_DATASETS + TEST_DATASETS

    seg_model = SEGMENTATION[args.seg_model].path
    done = load_done(args.out)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)

    total = 0
    for dataset in datasets:
        rel_dir, trim_ref = DATASETS[dataset]
        data_dir = os.path.join(ROOT, rel_dir)
        marking = load_marking(os.path.join(data_dir, "marking.json"))
        names = sample_files(dataset, marking, args)
        print(f"\n=== {dataset}: {len(names)} files ===")
        for name in names:
            audio_path = os.path.join(data_dir, name)
            wav = read_audio(audio_path)
            ref_k = speaker_count(marking[name])
            duration = len(wav) / 16000.0
            for mid in model_ids:
                emb_path = get_embedding(mid).path
                for mode in modes:
                    if mode in ("auto", "oracle"):
                        key = RunKey(
                            dataset, name, mid, mode,
                            scales_text(scales),
                            scales_text(auto_k_scales) if auto_k_scales else "",
                            args.clustering, args.seg_model,
                            cache=not args.no_cache,
                        )
                    else:
                        clustering = "sherpa_sweep" if mode == "sherpa_sweep" else "sherpa"
                        key = RunKey(
                            dataset, name, mid, mode,
                            scales_text(scales), "", clustering, args.seg_model,
                            cache=not args.no_cache,
                        )
                    if key.text() in done:
                        continue
                    total += 1
                    t0 = time.perf_counter()
                    extra: dict = {}
                    try:
                        if mode in ("auto", "oracle"):
                            cfg = PipelineConfig(
                                segmentation_model=seg_model,
                                embedding_model=emb_path,
                                scales=scales,
                                clustering=args.clustering,
                                auto_k_scales=auto_k_scales,
                                cache=not args.no_cache,
                            )
                            out = diarize(
                                wav,
                                cfg,
                                audio_path=audio_path,
                                num_speakers=ref_k if mode == "oracle" else None,
                            )
                            segs = out.segments
                            extra["timings"] = out.timings
                            if out.auto_k:
                                extra["auto_k"] = out.auto_k
                        elif mode == "sherpa_auto":
                            segs, elapsed = run_sherpa(
                                wav, seg_model, emb_path, -1, args.threshold,
                                0.3, 0.5, 0.5,
                            )
                            extra["timings"] = {"total_sec": elapsed}
                        elif mode == "sherpa_oracle":
                            segs, elapsed = run_sherpa(
                                wav, seg_model, emb_path, ref_k, args.threshold,
                                0.3, 0.5, 0.5,
                            )
                            extra["timings"] = {"total_sec": elapsed}
                        elif mode == "sherpa_sweep":
                            sweep_counts: list[int] = []
                            sweep_segs: list[list[dict]] = []
                            sweep_elapsed = 0.0
                            for thr in SWEEP_THRESHOLDS:
                                s, e = run_sherpa(
                                    wav, seg_model, emb_path, -1, thr,
                                    0.3, 0.5, 0.5,
                                )
                                sweep_segs.append(s)
                                sweep_counts.append(
                                    len({x["speaker"] for x in s})
                                )
                                sweep_elapsed += e
                            idx = choose_sweep(sweep_counts)
                            segs = sweep_segs[idx]
                            extra["timings"] = {"total_sec": sweep_elapsed}
                            extra["sweep"] = {
                                "thresholds": list(SWEEP_THRESHOLDS),
                                "speaker_counts": sweep_counts,
                                "chosen_index": idx,
                                "chosen_threshold": SWEEP_THRESHOLDS[idx],
                            }
                            # 11 threshold configs hold 11 copies of the ONNX
                            # sessions; drop them before the next file.
                            _sherpa_cache.clear()
                        else:
                            raise SystemExit(f"unknown mode {mode}")

                        m = evaluate_file(
                            audio_path, marking[name], segs, trim_ref=trim_ref
                        )
                        rec = {
                            "key": key.text(),
                            "dataset": dataset,
                            "split": dataset.rsplit("_", 1)[-1],
                            "file": name,
                            "duration": duration,
                            "ref_speakers": ref_k,
                            "hyp_speakers": m["hyp_speakers"],
                            "speaker_count_error": m["speaker_count_error"],
                            "model": mid,
                            "model_file": os.path.basename(emb_path),
                            "model_size_mb": round(get_embedding(mid).size_mb, 1),
                            "mode": mode,
                            "scales": scales_text(scales),
                            "auto_k_scales": scales_text(auto_k_scales) if auto_k_scales else "",
                            "clustering": args.clustering if mode in ("auto", "oracle") else clustering,
                            "seg_model": args.seg_model,
                            "cache": not args.no_cache,
                            "threshold": (
                                extra["sweep"]["chosen_threshold"]
                                if mode == "sherpa_sweep"
                                else args.threshold
                            ) if mode.startswith("sherpa") else None,
                            "trim_ref": trim_ref,
                            "der": m["der"],
                            "confusion": m["confusion"],
                            "miss": m["miss"],
                            "false_alarm": m["false_alarm"],
                            "ref_duration": m["ref_duration"],
                            "hyp_duration": m["hyp_duration"],
                            "elapsed_sec": time.perf_counter() - t0,
                            **extra,
                        }
                        append_result(args.out, rec)
                        done.add(key.text())
                        print(
                            f"{dataset}/{name} {mid} {mode:13s} "
                            f"k={m['hyp_speakers']}/{ref_k} DER={m['der']*100:5.1f}%"
                        )
                    except Exception as exc:  # keep the benchmark going
                        print(f"FAIL {dataset}/{name} {mid} {mode}: {exc}", file=sys.stderr)
                        append_result(
                            args.out,
                            {
                                "key": key.text(),
                                "dataset": dataset,
                                "file": name,
                                "model": mid,
                                "mode": mode,
                                "error": repr(exc),
                            },
                        )
                        done.add(key.text())

    print(f"\nfinished: {total} new runs -> {args.out}")


if __name__ == "__main__":
    main()
