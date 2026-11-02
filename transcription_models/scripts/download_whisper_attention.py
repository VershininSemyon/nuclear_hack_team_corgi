"""Скачать Whisper-декодеры с cross-attention (для точных таймингов слов).

Обычные декодеры sherpa-onnx не отдают attention, поэтому WordTimestamps
вынужден раскладывать время пропорционально длине слов. Эти файлы кладутся
рядом с обычными под именем *-decoder-attention.int8.onnx, и классы
WhisperTurboTranscriber / WhisperSmallTranscriber выбирают их первыми.

Энкодер и tokens.txt от обычной модели подходят — качать их не нужно.
"""
import os
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, "models")

TURBO = "https://huggingface.co/cohearo/sherpa-onnx-whisper-large-v3-turbo-attention-int8/resolve/main/turbo-decoder.int8.onnx"
SMALL = "https://huggingface.co/clairemcw/sherpa-onnx-whisper-small-attention/resolve/main/small-decoder.int8.onnx"

JOBS = [
    (TURBO, os.path.join(MODELS, "sherpa-onnx-whisper-turbo", "turbo-decoder-attention.int8.onnx")),
    (SMALL, os.path.join(MODELS, "sherpa-onnx-whisper-small", "small-decoder-attention.int8.onnx")),
]


def get(url, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    for attempt in range(8):
        try:
            have = os.path.getsize(dst) if os.path.exists(dst) else 0
            req = urllib.request.Request(url)
            if have:
                req.add_header("Range", f"bytes={have}-")
            with urllib.request.urlopen(req, timeout=60) as r:
                mode = "ab" if (have and r.status == 206) else "wb"
                total = int(r.headers.get("Content-Length", 0)) + (have if mode == "ab" else 0)
                with open(dst, mode) as f:
                    done = have
                    while True:
                        chunk = r.read(1 << 20)
                        if not chunk:
                            break
                        f.write(chunk)
                        done += len(chunk)
            if total and os.path.getsize(dst) < total:
                raise IOError(f"short read {os.path.getsize(dst)}/{total}")
            return f"done {os.path.basename(dst)} {os.path.getsize(dst)/1e6:.0f}MB"
        except Exception as e:
            print(f"retry {attempt+1} {os.path.basename(dst)}: {type(e).__name__} {e}", flush=True)
            time.sleep(3 * (attempt + 1))
    return f"FAILED {os.path.basename(dst)}"


for url, dst in JOBS:
    print(get(url, dst), flush=True)

print("attention decoders ready", flush=True)
