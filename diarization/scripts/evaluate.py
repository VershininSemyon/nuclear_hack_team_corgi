"""Diarization metrics: DER with collar, speaker-count accuracy, optional RU trim."""
from __future__ import annotations

import json
import os
from typing import Iterable, Sequence

import numpy as np
import soundfile as sf
from pyannote.core import Annotation, Segment as PySegment
from pyannote.metrics.diarization import DiarizationErrorRate

from pipeline import SR, Segment

COLLAR = 0.25


def to_annotation(segments: Sequence[Segment] | Sequence[dict]) -> Annotation:
    ann = Annotation()
    for s in segments:
        if isinstance(s, dict):
            start, end, spk = s["start"], s["end"], s["speaker"]
        else:
            start, end, spk = s.start, s.end, s.speaker
        if end > start:
            ann[PySegment(float(start), float(end))] = str(spk)
    return ann


def load_marking(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def trim_reference_energy(
    wav: np.ndarray,
    segments: Sequence[dict],
    frame: float = 0.025,
    hop: float = 0.01,
    rel_db: float = 35.0,
    abs_db: float = -50.0,
    merge_gap: float = 0.3,
) -> list[dict]:
    """Drop reference frames that contain no audible speech.

    The Russian synthetic set marks a whole speaker turn as one segment even
    when the TTS audio has long pauses inside. Raw DER then measures the pause
    convention, not diarization quality. This energy trim keeps the comparison
    honest; it is applied only to the RU set and is documented in the report.
    """
    n = len(wav)
    fl = int(frame * SR)
    hl = int(hop * SR)
    if n < fl or not segments:
        return [dict(s) for s in segments]
    nf = (n - fl) // hl + 1
    idx = np.arange(nf)[:, None] * hl + np.arange(fl)[None, :]
    frames = wav[idx]
    rms = np.sqrt((frames ** 2).mean(axis=1) + 1e-12)
    db = 20.0 * np.log10(np.maximum(rms, 1e-9))
    th = max(float(db.max()) - rel_db, abs_db)
    voiced = db >= th

    out: list[list] = []
    for s in segments:
        a, b = float(s["start"]), float(s["end"])
        f0 = max(0, int(a / hop))
        f1 = min(len(voiced), int(np.ceil(b / hop)) + 1)
        v = voiced[f0:f1]
        on = False
        st = 0
        for i, x in enumerate(v):
            if x and not on:
                on = True
                st = i
            elif not x and on:
                on = False
                out.append([max(a, (f0 + st) * hop), min(b, (f0 + i) * hop), s["speaker"]])
        if on:
            out.append([max(a, (f0 + st) * hop), b, s["speaker"]])

    merged: list[list] = []
    for seg in sorted(out):
        if (
            merged
            and seg[2] == merged[-1][2]
            and seg[0] - merged[-1][1] < merge_gap
        ):
            merged[-1][1] = max(merged[-1][1], seg[1])
        else:
            merged.append(seg)
    return [
        {"start": float(a), "end": float(b), "speaker": str(c)}
        for a, b, c in merged
        if b - a >= 0.1
    ]


def speaker_durations(segments) -> dict[int, float]:
    d: dict[int, float] = {}
    for s in segments:
        if isinstance(s, dict):
            start, end, spk = s["start"], s["end"], s["speaker"]
        else:
            start, end, spk = s.start, s.end, s.speaker
        d[int(spk)] = d.get(int(spk), 0.0) + max(0.0, float(end) - float(start))
    return d


def evaluate_segments(
    ref_segments: Sequence[dict],
    hyp_segments: Sequence[Segment],
    duration: float,
    min_speaker_sec: float = 0.5,
) -> dict:
    ref = to_annotation(ref_segments)
    hyp = to_annotation(hyp_segments)
    metric = DiarizationErrorRate(collar=COLLAR, skip_overlap=False)
    uem = PySegment(0.0, float(duration))
    detail = metric(ref, hyp, uem=uem, detailed=True)
    total = max(float(detail.get("total", 0.0)), 1e-9)
    der = float(detail.get("diarization error rate", 1.0))
    confusion = float(detail.get("confusion", 0.0)) / total
    miss = float(detail.get("missed detection", 0.0)) / total
    false_alarm = float(detail.get("false alarm", 0.0)) / total

    ref_spk = {str(s["speaker"]) for s in ref_segments}
    hyp_dur = speaker_durations(hyp_segments)
    hyp_spk = {c for c, d in hyp_dur.items() if d >= min_speaker_sec}
    if not hyp_spk and not hyp_segments:
        hyp_spk = set()
    elif not hyp_spk:
        hyp_spk = {max(hyp_dur, key=hyp_dur.get)} if hyp_dur else set()

    return {
        "der": der,
        "confusion": confusion,
        "miss": miss,
        "false_alarm": false_alarm,
        "ref_duration": total,
        "ref_speakers": len(ref_spk),
        "hyp_speakers": len(hyp_spk),
        "speaker_count_error": len(hyp_spk) - len(ref_spk),
        "audio_duration": float(duration),
        "hyp_duration": float(sum(hyp_dur.values())),
        "error_sec": der * total,
    }


def evaluate_file(
    audio_path: str,
    marking_entry: dict,
    hyp_segments: Sequence[Segment],
    trim_ref: bool = False,
) -> dict:
    wav, sr = sf.read(audio_path, dtype="float32")
    if wav.ndim > 1:
        wav = wav.mean(axis=1)
    if sr != SR:
        raise ValueError(f"Expected {SR} Hz, got {sr}")
    duration = len(wav) / SR
    ref = list(marking_entry["segments"])
    if trim_ref:
        ref = trim_reference_energy(wav, ref)
    return evaluate_segments(ref, hyp_segments, duration)


def aggregate(file_results: Sequence[dict]) -> dict:
    if not file_results:
        return {"n_files": 0}
    ref_sec = sum(r["ref_duration"] for r in file_results)
    audio_sec = sum(r["audio_duration"] for r in file_results)
    err_sec = sum(r["error_sec"] for r in file_results)
    out = {
        "n_files": len(file_results),
        "audio_sec": audio_sec,
        "ref_sec": ref_sec,
        "der_macro": float(np.mean([r["der"] for r in file_results])),
        "der_micro": err_sec / max(ref_sec, 1e-9),
        "confusion": sum(r["confusion"] * r["ref_duration"] for r in file_results) / max(ref_sec, 1e-9),
        "miss": sum(r["miss"] * r["ref_duration"] for r in file_results) / max(ref_sec, 1e-9),
        "false_alarm": sum(r["false_alarm"] * r["ref_duration"] for r in file_results) / max(ref_sec, 1e-9),
        "speaker_exact": float(np.mean([r["speaker_count_error"] == 0 for r in file_results])),
        "speaker_mae": float(np.mean([abs(r["speaker_count_error"]) for r in file_results])),
        "speaker_bias": float(np.mean([r["speaker_count_error"] for r in file_results])),
        "speaker_total_abs_error": int(sum(abs(r["speaker_count_error"]) for r in file_results)),
    }
    return out


def format_metrics(m: dict) -> str:
    return (
        f"DER {m['der_micro']*100:.2f}% (macro {m['der_macro']*100:.2f}%) | "
        f"conf {m['confusion']*100:.2f}% miss {m['miss']*100:.2f}% "
        f"fa {m['false_alarm']*100:.2f}% | "
        f"spk exact {m['speaker_exact']*100:.1f}% MAE {m['speaker_mae']:.2f} "
        f"bias {m['speaker_bias']:+.2f}"
    )
