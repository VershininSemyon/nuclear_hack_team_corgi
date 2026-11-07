"""GigaAM v3 ASR with word-level timestamps (same sherpa-onnx runtime as Android)."""
from __future__ import annotations

import os

import numpy as np

MODEL_DIR = os.environ.get(
    "GIGAAM_MODEL_DIR",
    os.path.join(
        os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
        "transcription",
        "models",
        "sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16",
    ),
)


def load_recognizer(model_dir: str = MODEL_DIR, num_threads: int = 4):
    import sherpa_onnx

    return sherpa_onnx.OfflineRecognizer.from_transducer(
        encoder=os.path.join(model_dir, "encoder.int8.onnx"),
        decoder=os.path.join(model_dir, "decoder.onnx"),
        joiner=os.path.join(model_dir, "joiner.onnx"),
        tokens=os.path.join(model_dir, "tokens.txt"),
        num_threads=num_threads,
        model_type="nemo_transducer",
    )


def words_from_tokens(
    tokens: list[str], timestamps: list[float], durations: list[float] | None = None
) -> list[dict]:
    """Merge character tokens into words. GigaAM uses a plain space token."""
    words: list[dict] = []
    cur = ""
    start = 0.0
    end = 0.0
    for i, tok in enumerate(tokens):
        if tok in ("<blk>", "<s>", "</s>", "<pad>"):
            continue
        t0 = float(timestamps[i]) if i < len(timestamps) else end
        t1 = t0 + float(durations[i]) if durations and i < len(durations) else t0 + 0.06
        if tok == " " or tok.startswith("▁") or tok == "|":
            if cur:
                words.append({"word": cur, "start": start, "end": end})
                cur = ""
            if tok.startswith("▁") and len(tok) > 1:
                cur = tok[1:]
                start, end = t0, t1
            continue
        if not cur:
            start = t0
        cur += tok
        end = t1
    if cur:
        words.append({"word": cur, "start": start, "end": end})
    return words


def recognize_words(wav: np.ndarray, recognizer=None) -> tuple[str, list[dict]]:
    import sherpa_onnx

    if recognizer is None:
        recognizer = load_recognizer()
    stream = recognizer.create_stream()
    stream.accept_waveform(16000, np.ascontiguousarray(wav, dtype=np.float32))
    recognizer.decode_stream(stream)
    r = stream.result
    return r.text, words_from_tokens(list(r.tokens), list(r.timestamps))
