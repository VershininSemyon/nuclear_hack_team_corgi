"""Build the diarization quality-vs-speed Pareto chart.

Reads the final test runs and cold RTF measurements, groups by model and writes
``results/pareto_diarization.png``. Lower-left is better.
"""
from __future__ import annotations

import json
import os
from collections import defaultdict

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RAW = os.path.join(ROOT, "results", "raw")

DISPLAY = {
    "campplus_zh_en": "CAM++ zh/en",
    "eres2net_en": "ERes2Net en",
    "titanet_small": "TitaNet small",
    "eres2netv2_zh": "ERes2NetV2 zh",
}
COLORS = {
    "campplus_zh_en": "#4285F4",
    "eres2net_en": "#34A853",
    "titanet_small": "#FBBC04",
    "eres2netv2_zh": "#EA4335",
}


def read_jsonl(name: str) -> list[dict]:
    path = os.path.join(RAW, name)
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def main() -> None:
    runs = read_jsonl("runs.jsonl")
    best: dict[str, dict] = {}
    for row in runs:
        if "der" not in row or row.get("mode") != "auto":
            continue
        key = "|".join((row.get("key") or "").split("|")[:8])
        best[key] = row

    by_dataset: dict[str, dict[str, list[dict]]] = defaultdict(lambda: defaultdict(list))
    size: dict[str, float] = {}
    for row in best.values():
        by_dataset[row["model"]][row["dataset"]].append(row)
        size[row["model"]] = float(row.get("model_size_mb", 0.0))
    der: dict[str, list[float]] = {}
    for model, datasets in by_dataset.items():
        der[model] = [
            sum(float(r["der"]) * float(r["ref_duration"]) for r in items)
            / max(sum(float(r["ref_duration"]) for r in items), 1e-9)
            for items in datasets.values()
        ]

    rtf_num: dict[str, float] = defaultdict(float)
    rtf_den: dict[str, float] = defaultdict(float)
    for row in read_jsonl("rtf.jsonl"):
        if "der" not in row:
            continue
        model = row["model"]
        rtf_num[model] += float(row.get("timings", {}).get("total_sec", 0.0))
        rtf_den[model] += float(row["duration"])
    rtf = {m: rtf_num[m] / max(rtf_den[m], 1e-9) for m in rtf_num}

    models = [m for m in DISPLAY if m in der and m in rtf]
    models.sort(key=lambda m: np.mean(der[m]))

    fig, ax = plt.subplots(figsize=(10.4, 6.4), dpi=160)
    fig.patch.set_facecolor("white")
    ax.set_facecolor("#fbfcfe")
    for model in models:
        x = rtf[model]
        y = float(np.mean(der[model])) * 100.0
        ax.scatter(
            x,
            y,
            s=max(90.0, size.get(model, 30.0) * 3.4),
            color=COLORS.get(model, "#7f8c8d"),
            edgecolor="white",
            linewidth=1.4,
            alpha=0.92,
            zorder=3,
        )
        ax.annotate(
            DISPLAY[model],
            (x, y),
            xytext=(8, 9),
            textcoords="offset points",
            fontsize=10.5,
            color="#1f2937",
        )
        ax.annotate(
            f"{y:.1f}% / {x:.3f}",
            (x, y),
            xytext=(8, -13),
            textcoords="offset points",
            fontsize=8.5,
            color="#5f6b7a",
        )

    ax.set_xscale("log")
    ax.set_xlabel("RTF cold (меньше = быстрее)", fontsize=12)
    ax.set_ylabel("DER, % при auto K (меньше = лучше)", fontsize=12)
    ax.set_title("Диаризация на устройстве: качество vs скорость", fontsize=15, pad=12)
    ax.grid(True, which="both", color="#dfe5ee", linewidth=0.8)
    ax.set_axisbelow(True)
    for spine in ("top", "right"):
        ax.spines[spine].set_visible(False)
    ax.margins(x=0.18, y=0.20)
    ax.text(
        0.99,
        0.02,
        "размер круга ∝ размеру модели; ниже и левее — лучше",
        transform=ax.transAxes,
        ha="right",
        va="bottom",
        fontsize=8.5,
        color="#6b7280",
    )
    fig.tight_layout()
    out = os.path.join(ROOT, "results", "pareto_diarization.png")
    fig.savefig(out, bbox_inches="tight")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
