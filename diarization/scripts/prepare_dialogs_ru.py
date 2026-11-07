"""Build Russian dialogue test files from the public Dialogs corpus.

Source: https://huggingface.co/datasets/langswap/dialogs-ru-emotional-conversations
License: OpenRAIL. Paper: Shigabeev & Latyshev, Interspeech 2026.

The corpus stores one WAV per utterance. We group utterances by dialogue part,
sort them by the original utterance index, and concatenate them with short
pauses. This reconstructs a multi-speaker conversation suitable for diarization
and full-pipeline evaluation. The script is deterministic and idempotent.

Output:
    data/dialogs_ru/test/dialogs_ru_*.wav   16 kHz mono
    data/dialogs_ru/test/marking.json       speaker segments
    data/dialogs_ru/test/sources.csv        source utterances
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import os
import re
import sys
import urllib.parse
import urllib.request
import urllib.error
from collections import defaultdict

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DATA = os.path.join(ROOT, "data", "dialogs_ru")
TEST = os.path.join(DATA, "test")
BASE = "https://huggingface.co/datasets/langswap/dialogs-ru-emotional-conversations/resolve/main"
SR = 16_000
PAUSE = 0.45


def download(url: str, dst: str) -> bool:
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if os.path.exists(dst) and os.path.getsize(dst) > 0:
        return True
    tmp = dst + ".part"
    try:
        with urllib.request.urlopen(url, timeout=60) as r, open(tmp, "wb") as f:
            while True:
                chunk = r.read(1 << 20)
                if not chunk:
                    break
                f.write(chunk)
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return False
        raise
    os.replace(tmp, dst)
    return True


def read_csv() -> list[dict]:
    path = os.path.join(DATA, "test.csv")
    download(f"{BASE}/test.csv", path)
    with open(path, encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="|"))


def dialogue_key(audio_path: str) -> str:
    base = os.path.basename(audio_path)
    return re.sub(r"_\d+\.wav$", "", base)


def utterance_index(audio_path: str) -> int:
    m = re.search(r"_(\d+)\.wav$", audio_path)
    return int(m.group(1)) if m else 0


def load_16k(path: str) -> np.ndarray:
    wav, sr = sf.read(path, dtype="float32", always_2d=True)
    mono = wav.mean(axis=1)
    if sr != SR:
        g = np.gcd(int(sr), SR)
        mono = resample_poly(mono, SR // g, int(sr) // g).astype(np.float32)
    return mono


def pick_groups(rows: list[dict], max_dialogues: int) -> list[tuple[str, list[dict]]]:
    groups: dict[str, list[dict]] = defaultdict(list)
    for r in rows:
        groups[dialogue_key(r["audio_path"])].append(r)
    candidates = []
    for key, items in groups.items():
        items.sort(key=lambda r: utterance_index(r["audio_path"]))
        duration = sum(float(r["duration"]) for r in items)
        speakers = {r["speaker_id"] for r in items}
        if len(items) >= 8 and len(speakers) == 2 and 55.0 <= duration <= 150.0:
            candidates.append((duration, key, items))
    # Prefer shorter, balanced dialogues and alternate M-D / M-S parts.
    candidates.sort(key=lambda t: (abs(t[0] - 90.0), t[1]))
    picked: list[tuple[str, list[dict]]] = []
    used_pairs: dict[tuple[str, str], int] = defaultdict(int)
    for duration, key, items in candidates:
        pair = tuple(sorted({r["speaker_id"] for r in items}))
        if used_pairs[pair] >= max(1, max_dialogues // 2):
            continue
        used_pairs[pair] += 1
        picked.append((key, items))
        if len(picked) >= max_dialogues:
            break
    if len(picked) < max_dialogues:
        for duration, key, items in candidates:
            if key not in {k for k, _ in picked}:
                picked.append((key, items))
            if len(picked) >= max_dialogues:
                break
    return picked


def main() -> int:
    ap = argparse.ArgumentParser(description="Build Russian Dialogs test set")
    ap.add_argument("--dialogues", type=int, default=6)
    ap.add_argument("--max-seconds", type=float, default=0.0, help="0 = no limit")
    args = ap.parse_args()

    os.makedirs(TEST, exist_ok=True)
    rows = read_csv()
    picked = pick_groups(rows, args.dialogues)
    if not picked:
        print("No suitable dialogue groups found", file=sys.stderr)
        return 1

    marking: dict[str, dict] = {}
    sources: list[dict] = []
    for i, (key, items) in enumerate(picked, 1):
        name = f"dialogs_ru_{i:03d}.wav"
        pieces: list[np.ndarray] = []
        segments: list[dict] = []
        cursor = 0.0
        for item in items:
            src = os.path.join(DATA, "raw", item["audio_path"].replace("/", os.sep))
            url = BASE + "/" + urllib.parse.quote(item["audio_path"])
            if not download(url, src):
                print(f"  skip missing {item['audio_path']}")
                continue
            wav = load_16k(src)
            if args.max_seconds and cursor >= args.max_seconds:
                break
            start = cursor
            end = start + len(wav) / SR
            segments.append(
                {
                    "start": round(start, 3),
                    "end": round(end, 3),
                    "speaker": item["speaker_id"],
                }
            )
            pieces.append(wav)
            pieces.append(np.zeros(int(PAUSE * SR), dtype=np.float32))
            cursor = end + PAUSE
            sources.append(
                {
                    "dialogue": name,
                    "source": item["audio_path"],
                    "speaker": item["speaker_id"],
                    "start": round(start, 3),
                    "end": round(end, 3),
                    "text": item["text"],
                }
            )
        audio = np.concatenate(pieces) if pieces else np.zeros(0, dtype=np.float32)
        sf.write(os.path.join(TEST, name), audio, SR, subtype="PCM_16")
        marking[name] = {
            "num_speakers": len({s["speaker"] for s in segments}),
            "segments": segments,
        }
        print(
            f"{name}: {len(items)} turns, {len({s['speaker'] for s in segments})} speakers, "
            f"{len(audio)/SR:.1f} s"
        )

    with open(os.path.join(TEST, "marking.json"), "w", encoding="utf-8") as f:
        json.dump(marking, f, ensure_ascii=False, indent=2)
    with open(os.path.join(TEST, "sources.csv"), "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["dialogue", "source", "speaker", "start", "end", "text"],
        )
        writer.writeheader()
        writer.writerows(sources)
    print(f"\nwrote {len(marking)} dialogues -> {TEST}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
