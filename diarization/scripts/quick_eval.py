"""Quick single-file evaluation used while tuning the pipeline."""
import argparse
import json
import os
import sys

import numpy as np
import soundfile as sf

from pipeline import PipelineConfig, diarize, read_audio, sherpa_diarize
from evaluate import evaluate_file, format_metrics, aggregate
from models import SEGMENTATION, get_embedding

DATASETS = {
    "ami_test": ("data/ami/test", False),
    "ami_validation": ("data/ami/validation", False),
    "vox_dev": ("data/vox/dev", False),
    "vox_test": ("data/vox/test", False),
    "ru_dev": ("data/ru/dev", True),
    "ru_test": ("data/ru/test", True),
}

ap = argparse.ArgumentParser()
ap.add_argument("dataset")
ap.add_argument("name")
ap.add_argument("--model", default="campplus_zh_en")
ap.add_argument("--seg-model", default="pyannote-3.0")
ap.add_argument("--scales", default="2.0:1.0")
ap.add_argument("--auto-k-scales", default="")
ap.add_argument("--clustering", default="complete")
ap.add_argument("--num-speakers", type=int, default=0)
ap.add_argument("--chunk-units", action="store_true")
ap.add_argument("--sherpa", action="store_true")
ap.add_argument("--threshold", type=float, default=0.5)
args = ap.parse_args()

root, trim = DATASETS[args.dataset]
audio = os.path.join(root, args.name)
marking = json.load(open(os.path.join(root, "marking.json"), encoding="utf-8"))[args.name]
ref_k = len({s["speaker"] for s in marking["segments"]})
wav = read_audio(audio)

if args.sherpa:
    segs = sherpa_diarize(
        wav,
        SEGMENTATION[args.seg_model].path,
        get_embedding(args.model).path,
        num_speakers=args.num_speakers or ref_k,
        threshold=args.threshold,
    )
    out = None
else:
    scales = tuple(tuple(float(x) for x in p.split(":")) for p in args.scales.split(","))
    auto_k_scales = None
    if args.auto_k_scales:
        auto_k_scales = tuple(
            tuple(float(x) for x in p.split(":")) for p in args.auto_k_scales.split(",")
        )
    cfg = PipelineConfig(
        segmentation_model=SEGMENTATION[args.seg_model].path,
        embedding_model=get_embedding(args.model).path,
        scales=scales,
        clustering=args.clustering,
        include_chunk_units=args.chunk_units,
        auto_k_scales=auto_k_scales,
    )
    out = diarize(
        wav,
        cfg,
        audio_path=audio,
        num_speakers=args.num_speakers or None,
    )
    segs = out.segments

m = evaluate_file(audio, marking, segs, trim_ref=trim)
print(f"{args.dataset}/{args.name} ref_k={ref_k} hyp_k={m['hyp_speakers']}")
print(format_metrics(aggregate([m])))
print({k: round(v, 4) for k, v in m.items() if isinstance(v, float)})
if out and out.auto_k:
    print("auto_k", out.auto_k["k"], "units", out.auto_k["n_units"])
