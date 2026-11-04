"""Диаризация: аудио -> список (start, end, speaker).

    from diarization import diarize
    segs = diarize("dialog.wav", "models/wespeaker_en_voxceleb_resnet34.onnx", num_speakers=2)
"""
from __future__ import annotations

import functools
from dataclasses import dataclass

import librosa
import numpy as np
import sherpa_onnx
from sklearn.cluster import AgglomerativeClustering, SpectralClustering

SR = 16000
DEFAULT_VAD = "models/silero_vad.onnx"


@dataclass
class SpeakerSeg:
    start: float
    end: float
    speaker: int


# ------------------------------------------------------------------
# вспомогательное
# ------------------------------------------------------------------
@functools.lru_cache(maxsize=4)
def _get_extractor(model_path: str, num_threads: int):
    return sherpa_onnx.SpeakerEmbeddingExtractor(
        sherpa_onnx.SpeakerEmbeddingExtractorConfig(
            model=model_path, num_threads=num_threads
        )
    )


def warmup(emb_model: str, num_threads: int = 2):
    """Заранее загрузить модель (чтобы загрузка не попала в замер скорости)."""
    _get_extractor(emb_model, num_threads)


def load_audio(audio) -> np.ndarray:
    """путь к файлу или массив -> float32, 16 кГц, mono"""
    if isinstance(audio, (str, bytes)) or hasattr(audio, "__fspath__"):
        audio, _ = librosa.load(audio, sr=SR, mono=True)
    return np.asarray(audio, dtype=np.float32)


# ------------------------------------------------------------------
# 1. VAD
# ------------------------------------------------------------------
def vad_segments(audio: np.ndarray, vad_model: str = DEFAULT_VAD,
                 max_speech: float = 8.0) -> list[tuple[float, float]]:
    cfg = sherpa_onnx.VadModelConfig()
    cfg.silero_vad.model = vad_model
    cfg.silero_vad.threshold = 0.5
    cfg.silero_vad.min_silence_duration = 0.2
    cfg.silero_vad.min_speech_duration = 0.2
    cfg.silero_vad.max_speech_duration = max_speech
    cfg.sample_rate = SR
    vad = sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=60)
    ws = cfg.silero_vad.window_size

    out: list[tuple[float, float]] = []

    def drain():
        while not vad.empty():
            s = vad.front
            out.append((s.start / SR, s.start / SR + len(s.samples) / SR))
            vad.pop()

    for i in range(0, len(audio) - ws + 1, ws):
        vad.accept_waveform(audio[i:i + ws])
        drain()
    vad.flush()
    drain()
    return out


# ------------------------------------------------------------------
# 2. сектора + эмбеддинги
# ------------------------------------------------------------------
def get_sector_embeddings(audio, emb_model: str, vad_model: str = DEFAULT_VAD,
                          win: float = 1.5, step: float = 0.5,
                          min_len: float = 0.6, num_threads: int = 2):
    """-> (windows: list[(start, end)], embeddings: ndarray [N, D])"""
    audio = load_audio(audio)
    extractor = _get_extractor(emb_model, num_threads)

    def embed(chunk):
        s = extractor.create_stream()
        s.accept_waveform(sample_rate=SR, waveform=chunk)
        s.input_finished()
        return np.array(extractor.compute(s), dtype=np.float32)

    wins, embs = [], []
    for vs, ve in vad_segments(audio, vad_model):
        if ve - vs < min_len:
            continue
        if ve - vs <= win:
            starts, w = [vs], ve - vs
        else:
            starts, w = np.arange(vs, ve - win + 1e-6, step), win
        for t in starts:
            chunk = audio[int(t * SR): int((t + w) * SR)]
            wins.append((float(t), float(t + w)))
            embs.append(embed(chunk))
    return wins, (np.array(embs) if embs else np.zeros((0, 0), np.float32))


# ------------------------------------------------------------------
# 3. кластеризация
# ------------------------------------------------------------------
def _normalize(X):
    return X / (np.linalg.norm(X, axis=1, keepdims=True) + 1e-9)


def cluster_embeddings(embs: np.ndarray, num_speakers: int | None = 2,
                       threshold: float = 0.5) -> np.ndarray:
    n = len(embs)
    if n < 2 or (num_speakers is not None and num_speakers < 2):
        return np.zeros(n, dtype=int)

    X = _normalize(embs)
    X = _normalize(X - X.mean(axis=0))      # центрирование

    if num_speakers is not None:
        k = min(num_speakers, n)
        try:
            A = np.clip(X @ X.T, 0, 1)
            return SpectralClustering(
                n_clusters=k, affinity="precomputed", random_state=0
            ).fit_predict(A)
        except Exception:
            return AgglomerativeClustering(
                n_clusters=k, metric="cosine", linkage="average"
            ).fit_predict(X)

    return AgglomerativeClustering(
        n_clusters=None, distance_threshold=threshold,
        metric="cosine", linkage="average",
    ).fit_predict(X)


# ------------------------------------------------------------------
# 4. окна -> сегменты (голосование)
# ------------------------------------------------------------------
def windows_to_segments(wins, labels, res: float = 0.05,
                        min_dur: float = 0.3, max_gap: float = 0.3) -> list[SpeakerSeg]:
    if not wins:
        return []
    n_spk = int(labels.max()) + 1
    T = int(max(e for _, e in wins) / res) + 1
    votes = np.zeros((T, n_spk))
    for (s, e), l in zip(wins, labels):
        votes[int(s / res): int(e / res), l] += 1

    speech = votes.sum(1) > 0
    lab = votes.argmax(1)

    segs: list[SpeakerSeg] = []
    i = 0
    while i < T:
        if not speech[i]:
            i += 1
            continue
        j = i
        while j < T and speech[j] and lab[j] == lab[i]:
            j += 1
        segs.append(SpeakerSeg(i * res, j * res, int(lab[i])))
        i = j

    merged: list[SpeakerSeg] = []
    for s in segs:
        if merged and merged[-1].speaker == s.speaker and s.start - merged[-1].end <= max_gap:
            merged[-1].end = s.end
        else:
            merged.append(s)
    return [s for s in merged if s.end - s.start >= min_dur]


# ------------------------------------------------------------------
# главная функция
# ------------------------------------------------------------------
def diarize(audio, emb_model: str, num_speakers: int | None = 2,
            threshold: float = 0.5, vad_model: str = DEFAULT_VAD,
            win: float = 1.5, step: float = 0.5, min_len: float = 0.6,
            num_threads: int = 2) -> list[SpeakerSeg]:
    """
    audio        путь к файлу или np.ndarray (float32, 16 кГц, mono)
    emb_model    путь к .onnx speaker embedding модели
    num_speakers число спикеров; None = определить автоматически по threshold
    """
    wins, embs = get_sector_embeddings(
        audio, emb_model, vad_model, win, step, min_len, num_threads
    )
    if len(wins) == 0:
        return []
    labels = cluster_embeddings(embs, num_speakers, threshold)
    return windows_to_segments(wins, labels)