"""On-device diarization pipeline: pyannote segmentation + window embeddings + auto-K.

The pipeline replaces a fixed cosine-similarity threshold with:
  * multi-scale pure-speech windows (no overlap frames),
  * complete-linkage agglomerative clustering,
  * gap-statistic model selection for the number of speakers.

It reproduces the frame decoding of sherpa-onnx, so the output can be compared
with `OfflineSpeakerDiarization` one-to-one.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import time
from dataclasses import dataclass, field, asdict
from typing import Iterable, Sequence

import numpy as np
import onnxruntime as ort
import soundfile as sf

SR = 16000
WINDOW_SIZE = 160000
NUM_FRAMES = 589
RF_SHIFT = 270
RF_SIZE = 991

POWERSET = np.array(
    [
        [0, 0, 0],
        [1, 0, 0],
        [0, 1, 0],
        [0, 0, 1],
        [1, 1, 0],
        [1, 0, 1],
        [0, 1, 1],
    ],
    dtype=np.int8,
)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CACHE_DIR = os.path.join(ROOT, "cache", "pipeline")

_SESSIONS: dict[str, ort.InferenceSession] = {}


def _session(path: str) -> ort.InferenceSession:
    path = os.path.abspath(path)
    if path not in _SESSIONS:
        _SESSIONS[path] = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    return _SESSIONS[path]


def _file_key(path: str) -> str:
    st = os.stat(path)
    return f"{os.path.abspath(path)}|{st.st_size}|{int(st.st_mtime)}"


def _hash(obj) -> str:
    return hashlib.sha1(json.dumps(obj, sort_keys=True).encode("utf-8")).hexdigest()[:20]


@dataclass
class PipelineConfig:
    segmentation_model: str = os.path.join(
        ROOT, "models", "sherpa-onnx-pyannote-segmentation-3-0", "model.int8.onnx"
    )
    embedding_model: str = ""  # set by CLI / benchmark
    window_shift_ratio: float = 0.5
    scales: tuple[tuple[float, float], ...] = ((2.0, 1.0), (3.0, 1.5))
    purity: float = 0.7
    min_speech: float = 0.5
    min_duration_on: float = 0.3
    min_duration_off: float = 0.5
    clustering: str = "complete"  # complete | spectral
    k_max: int = 12
    gap_bootstrap: int = 10
    seed: int = 0
    cache: bool = True
    include_chunk_units: bool = False
    # If set, the speaker count is estimated on these windows, while the
    # clustering itself runs on `scales`. Short windows give the gap statistic
    # more units and a more reliable K; longer windows give better embeddings.
    auto_k_scales: tuple[tuple[float, float], ...] | None = None


@dataclass
class Segment:
    start: float
    end: float
    speaker: int
    confidence: float = 0.0


@dataclass
class DiarizationOutput:
    segments: list[Segment]
    speaker_count: int
    embedding_model: str
    timings: dict = field(default_factory=dict)
    auto_k: dict | None = None
    config: dict = field(default_factory=dict)
    centroids: np.ndarray | None = None

    def to_json(self) -> dict:
        return {
            "speaker_count": self.speaker_count,
            "embedding_model": self.embedding_model,
            "timings": self.timings,
            "auto_k": self.auto_k,
            "config": self.config,
            "segments": [asdict(s) for s in self.segments],
        }


# --------------------------------------------------------------------------- segmentation
def segment_waveform(
    wav: np.ndarray,
    model_path: str,
    window_shift_ratio: float = 0.5,
) -> tuple[np.ndarray, np.ndarray, int]:
    """Return (labels[N,589,3], starts[N], hop_samples)."""
    sess = _session(model_path)
    n = len(wav)
    hop = int(WINDOW_SIZE * window_shift_ratio)
    if n <= WINDOW_SIZE:
        starts = np.array([0], dtype=np.int64)
        chunks = [np.pad(wav, (0, WINDOW_SIZE - n))]
    else:
        starts = list(range(0, n - WINDOW_SIZE + 1, hop))
        if (n - WINDOW_SIZE) % hop:
            starts.append(n - WINDOW_SIZE)
        starts = np.array(starts, dtype=np.int64)
        chunks = [
            np.pad(wav[s : s + WINDOW_SIZE], (0, max(0, WINDOW_SIZE - (n - s))))
            for s in starts
        ]

    labels = np.empty((len(chunks), NUM_FRAMES, 3), dtype=np.int8)
    for i, chunk in enumerate(chunks):
        logits = sess.run(None, {"x": chunk.reshape(1, 1, -1).astype(np.float32)})[0][0]
        labels[i] = POWERSET[logits.argmax(-1)]
    return labels, starts, hop


# --------------------------------------------------------------------------- units
def build_window_units(
    labels: np.ndarray,
    starts: np.ndarray,
    n_samples: int,
    scales: Sequence[tuple[float, float]] = ((2.0, 1.0), (3.0, 1.5)),
    purity: float = 0.7,
    min_speech: float = 0.5,
) -> list[dict]:
    """Pure-speech windows with a single dominant local speaker."""
    units: list[dict] = []
    n_chunks = len(labels)
    total = min(int(starts[-1]) + WINDOW_SIZE, n_samples)
    chunk_ends = starts + WINDOW_SIZE

    for scale_idx, (win_s, hop_s) in enumerate(scales):
        win = int(win_s * SR)
        hop = int(hop_s * SR)
        for s in range(0, max(0, total - win + 1), hop):
            e = s + win
            # chunk with the largest overlap with [s, e)
            best = None
            for ci in range(n_chunks):
                a = max(s, int(starts[ci]))
                b = min(e, int(chunk_ends[ci]))
                if b > a:
                    ov = b - a
                    if best is None or ov > best[0]:
                        best = (ov, ci, a, b)
            if best is None:
                continue
            _, ci, a, b = best
            cs = int(starts[ci])
            f0 = int(round((a - cs) / WINDOW_SIZE * NUM_FRAMES))
            f1 = int(round((b - cs) / WINDOW_SIZE * NUM_FRAMES))
            f0 = max(0, min(NUM_FRAMES - 1, f0))
            f1 = max(f0 + 1, min(NUM_FRAMES, f1))
            lab = labels[ci, f0:f1]
            nf = f1 - f0
            counts = lab.sum(axis=0)
            speech_frames = int((lab.sum(axis=1) > 0).sum())
            if speech_frames < min_speech * nf:
                continue
            spk = int(np.argmax(counts))
            if counts[spk] <= 0:
                continue
            pur = float(counts[spk] / max(speech_frames, 1))
            if pur < purity:
                continue
            units.append(
                {
                    "chunk": int(ci),
                    "speaker": spk,
                    "intervals": [[int(a), int(b)]],
                    "frames": speech_frames,
                    "purity": pur,
                    "scale": scale_idx,
                    "duration": (b - a) / SR,
                }
            )
    return units


def build_chunk_units(labels: np.ndarray, starts: np.ndarray) -> list[dict]:
    """Long units: contiguous non-overlap frames of one local speaker in a chunk."""
    units: list[dict] = []
    for ci, lab in enumerate(labels):
        active = lab.sum(axis=1) == 1
        for spk in range(3):
            mask = active & (lab[:, spk] == 1)
            if mask.sum() < 10:
                continue
            runs = []
            on = False
            st = 0
            for i, v in enumerate(mask):
                if v and not on:
                    on = True
                    st = i
                elif not v and on:
                    on = False
                    if i - st >= 5:
                        runs.append((st, i))
            if on and NUM_FRAMES - st >= 5:
                runs.append((st, NUM_FRAMES))
            intervals = []
            for a, b in runs:
                sa = int(round(a / NUM_FRAMES * WINDOW_SIZE)) + int(starts[ci])
                sb = int(round(b / NUM_FRAMES * WINDOW_SIZE)) + int(starts[ci])
                if sb > sa:
                    intervals.append([sa, sb])
            if intervals:
                dur = sum(b - a for a, b in intervals) / SR
                units.append(
                    {
                        "chunk": int(ci),
                        "speaker": spk,
                        "intervals": intervals,
                        "frames": int(mask.sum()),
                        "purity": 1.0,
                        "scale": -1,
                        "duration": dur,
                    }
                )
    return units


def embed_units(
    wav: np.ndarray,
    units: Sequence[dict],
    model_path: str,
    min_seconds: float = 0.35,
) -> tuple[np.ndarray, np.ndarray]:
    """Extract one L2-normalized embedding per unit."""
    import sherpa_onnx as so

    ex = so.SpeakerEmbeddingExtractor(so.SpeakerEmbeddingExtractorConfig(model=model_path))
    rows: list[np.ndarray | None] = []
    ok = np.zeros(len(units), dtype=bool)
    dim = None
    for i, u in enumerate(units):
        st = ex.create_stream()
        total = 0
        for a, b in u["intervals"]:
            x = np.ascontiguousarray(wav[a:b])
            if len(x):
                st.accept_waveform(SR, x)
                total += len(x)
        if total < int(min_seconds * SR):
            pad = np.zeros(int(min_seconds * SR) - total, dtype=np.float32)
            st.accept_waveform(SR, pad)
        st.input_finished()
        if not ex.is_ready(st):
            rows.append(None)
            continue
        e = np.asarray(ex.compute(st), dtype=np.float32)
        if e.size == 0 or np.isnan(e).any():
            rows.append(None)
            continue
        if dim is None:
            dim = e.shape[0]
        rows.append(e)
        ok[i] = True
    if dim is None:
        raise RuntimeError("No valid embeddings extracted")
    emb = np.zeros((len(rows), dim), dtype=np.float32)
    for i, e in enumerate(rows):
        if e is not None:
            emb[i] = e
    emb /= np.maximum(np.linalg.norm(emb, axis=1, keepdims=True), 1e-9)
    return emb, ok


# --------------------------------------------------------------------------- auto-K
def _dispersion(X: np.ndarray, labels: np.ndarray) -> float:
    w = 0.0
    for c in np.unique(labels):
        Z = X[labels == c]
        n = len(Z)
        if n < 2:
            continue
        S = 1.0 - Z @ Z.T
        np.fill_diagonal(S, 0.0)
        w += S.sum() / (2.0 * n)
    return max(w, 1e-12)


def _ahc(X: np.ndarray, k: int) -> np.ndarray:
    from sklearn.cluster import AgglomerativeClustering

    return AgglomerativeClustering(
        n_clusters=k, metric="cosine", linkage="complete"
    ).fit_predict(X)


def _reference(X: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    from sklearn.decomposition import PCA

    n = len(X)
    d = min(16, n - 1, X.shape[1])
    p = PCA(n_components=d, random_state=0).fit(X)
    Z = p.transform(X)
    lo, hi = Z.min(axis=0), Z.max(axis=0)
    R = rng.uniform(lo, hi, size=(n, d))
    Y = p.inverse_transform(R)
    return Y / np.maximum(np.linalg.norm(Y, axis=1, keepdims=True), 1e-9)


def auto_speaker_count(
    X: np.ndarray,
    k_max: int = 12,
    bootstrap: int = 10,
    seed: int = 0,
) -> tuple[int, dict]:
    """Gap statistic with a PCA-bounding-box reference distribution.

    Returns (k, diagnostics). K=1 is a valid answer for monologue audio.
    """
    n = len(X)
    if n < 4:
        return 1, {"method": "gap", "n_units": n, "k_max": 1, "gap": [], "s": []}
    kmax = min(k_max, max(2, n // 2))
    rng = np.random.default_rng(seed)
    logw = np.empty(kmax)
    for k in range(1, kmax + 1):
        lab = np.zeros(n, dtype=int) if k == 1 else _ahc(X, k)
        logw[k - 1] = np.log(_dispersion(X, lab))

    ref = np.empty((bootstrap, kmax))
    for b in range(bootstrap):
        R = _reference(X, rng)
        for k in range(1, kmax + 1):
            lab = np.zeros(n, dtype=int) if k == 1 else _ahc(R, k)
            ref[b, k - 1] = np.log(_dispersion(R, lab))
    gap = ref.mean(axis=0) - logw
    s = ref.std(axis=0) * np.sqrt(1.0 + 1.0 / bootstrap)

    k = kmax
    for j in range(1, kmax):
        if gap[j - 1] >= gap[j] - s[j]:
            k = j
            break
    diag = {
        "method": "gap",
        "n_units": n,
        "k_max": kmax,
        "bootstrap": bootstrap,
        "gap": [round(float(x), 4) for x in gap],
        "s": [round(float(x), 4) for x in s],
        "k": int(k),
    }
    return int(k), diag


# --------------------------------------------------------------------------- clustering
def cluster_embeddings(
    X: np.ndarray,
    k: int,
    method: str = "complete",
    seed: int = 0,
) -> np.ndarray:
    n = len(X)
    if k <= 1:
        return np.zeros(n, dtype=int)
    if k >= n:
        return np.arange(n, dtype=int)
    if method == "complete":
        return _ahc(X, k)
    if method == "spectral":
        from sklearn.cluster import SpectralClustering

        S = X @ X.T
        np.fill_diagonal(S, 0.0)
        W = np.maximum(S, 0.0)
        return SpectralClustering(
            n_clusters=k, affinity="precomputed", assign_labels="kmeans", random_state=seed
        ).fit_predict(W)
    raise ValueError(f"Unknown clustering method '{method}'")


# --------------------------------------------------------------------------- decode
def _majority_map(units: Sequence[dict], unit_labels: np.ndarray) -> dict[tuple[int, int], int]:
    votes: dict[tuple[int, int], dict[int, float]] = {}
    for u, lab in zip(units, unit_labels):
        if lab < 0:
            continue
        key = (u["chunk"], u["speaker"])
        weight = max(float(u.get("duration", 1.0)), 1e-3)
        weight *= float(u.get("purity", 1.0))
        votes.setdefault(key, {})
        votes[key][int(lab)] = votes[key].get(int(lab), 0.0) + weight
    return {key: max(v.items(), key=lambda t: t[1])[0] for key, v in votes.items()}


def decode_segments(
    labels: np.ndarray,
    starts: np.ndarray,
    units: Sequence[dict],
    unit_labels: np.ndarray,
    n_samples: int,
    k: int,
    min_duration_on: float = 0.3,
    min_duration_off: float = 0.5,
) -> list[Segment]:
    mapping = _majority_map(units, unit_labels)
    n_chunks = len(labels)
    window_shift = int(starts[1] - starts[0]) if n_chunks > 1 else WINDOW_SIZE
    global_frames = (WINDOW_SIZE + (n_chunks - 1) * window_shift) // RF_SHIFT + 1

    count = np.zeros((global_frames, k), dtype=np.float32)
    speakers_per_frame = np.zeros(global_frames, dtype=np.float32)
    weight = np.zeros(global_frames, dtype=np.float32)

    for ci, lab in enumerate(labels):
        start = int(round(ci * window_shift / RF_SHIFT))
        end = min(start + lab.shape[0], global_frames)
        m = end - start
        if m <= 0:
            continue
        for spk in range(3):
            cid = mapping.get((ci, spk), -1)
            if cid >= 0:
                count[start:end, cid] += lab[:m, spk]
        speakers_per_frame[start:end] += lab[:m].sum(axis=1)
        weight[start:end] += 1.0

    speakers_per_frame = np.floor(
        speakers_per_frame / np.maximum(weight, 1e-12) + 0.5
    ).astype(int)

    final = np.zeros_like(count)
    for f in range(global_frames):
        kf = int(speakers_per_frame[f])
        if kf <= 0:
            continue
        top = np.argsort(count[f])[-kf:]
        final[f, top] = 1.0

    scale = RF_SHIFT / SR
    offset = 0.5 * RF_SIZE / SR
    duration = n_samples / SR
    raw: list[Segment] = []
    for c in range(k):
        active = final[:, c] > 0
        on = False
        st = 0
        for f, v in enumerate(active):
            if v and not on:
                on = True
                st = f
            elif not v and on:
                on = False
                raw.append(Segment(st * scale + offset, f * scale + offset, c))
        if on:
            raw.append(Segment(st * scale + offset, global_frames * scale + offset, c))

    merged: list[Segment] = []
    for c in range(k):
        cs = sorted((s for s in raw if s.speaker == c), key=lambda s: s.start)
        cur: Segment | None = None
        for s in cs:
            if cur is not None and s.start - cur.end < min_duration_off:
                cur.end = max(cur.end, s.end)
            else:
                if cur is not None:
                    merged.append(cur)
                cur = Segment(s.start, s.end, c)
        if cur is not None:
            merged.append(cur)

    out = []
    for s in merged:
        s.start = max(0.0, min(duration, s.start))
        s.end = max(0.0, min(duration, s.end))
        if s.end - s.start >= min_duration_on:
            out.append(s)
    out.sort(key=lambda s: (s.start, s.end, s.speaker))
    return out


# --------------------------------------------------------------------------- orchestration
def _seg_cache_path(audio_key: str, cfg: PipelineConfig) -> str:
    key = _hash(
        {
            "audio": audio_key,
            "seg": _file_key(cfg.segmentation_model),
            "shift": cfg.window_shift_ratio,
        }
    )
    return os.path.join(CACHE_DIR, "seg", key + ".npz")


def _emb_cache_path(
    audio_key: str,
    cfg: PipelineConfig,
    scales: tuple[tuple[float, float], ...] | None = None,
    suffix: str = "",
) -> str:
    key = _hash(
        {
            "audio": audio_key,
            "seg": _file_key(cfg.segmentation_model),
            "emb": _file_key(cfg.embedding_model),
            "shift": cfg.window_shift_ratio,
            "scales": cfg.scales if scales is None else scales,
            "purity": cfg.purity,
            "min_speech": cfg.min_speech,
            "chunk_units": cfg.include_chunk_units,
            "suffix": suffix,
        }
    )
    return os.path.join(CACHE_DIR, "emb", key + ".npz")


def _units_cache_path(
    audio_key: str,
    cfg: PipelineConfig,
    scales: tuple[tuple[float, float], ...] | None = None,
    suffix: str = "",
) -> str:
    return _emb_cache_path(audio_key, cfg, scales, suffix).replace(".npz", "_units.json")


def _load_units(
    wav: np.ndarray,
    labels: np.ndarray,
    starts: np.ndarray,
    cfg: PipelineConfig,
    audio_key: str,
    scales: tuple[tuple[float, float], ...] | None = None,
    suffix: str = "",
) -> tuple[list[dict], np.ndarray, np.ndarray]:
    """Build (or load from cache) units and their embeddings for one scale set."""
    emb_path = _emb_cache_path(audio_key, cfg, scales, suffix)
    units_path = _units_cache_path(audio_key, cfg, scales, suffix)
    if cfg.cache and os.path.exists(emb_path) and os.path.exists(units_path):
        z = np.load(emb_path)
        emb, ok = z["emb"], z["ok"]
        with open(units_path, encoding="utf-8") as f:
            units = json.load(f)
        return units, emb, ok

    units = build_window_units(
        labels,
        starts,
        len(wav),
        scales=cfg.scales if scales is None else scales,
        purity=cfg.purity,
        min_speech=cfg.min_speech,
    )
    if cfg.include_chunk_units and suffix != "auto_k":
        units += build_chunk_units(labels, starts)
    emb, ok = embed_units(wav, units, cfg.embedding_model)
    if cfg.cache:
        os.makedirs(os.path.dirname(emb_path), exist_ok=True)
        np.savez_compressed(emb_path, emb=emb, ok=ok)
        with open(units_path, "w", encoding="utf-8") as f:
            json.dump(units, f)
    return units, emb, ok


def diarize(
    wav: np.ndarray,
    cfg: PipelineConfig,
    audio_path: str = "",
    num_speakers: int | None = None,
    keep_embeddings: bool = False,
) -> DiarizationOutput:
    t0 = time.perf_counter()
    timings: dict[str, float] = {}

    audio_key = _file_key(audio_path) if audio_path else _hash(
        {"n": len(wav), "sum": float(np.sum(np.abs(wav[: min(len(wav), 16000)])))}
    )

    seg_path = _seg_cache_path(audio_key, cfg)
    if cfg.cache and os.path.exists(seg_path):
        z = np.load(seg_path)
        labels, starts, hop = z["labels"], z["starts"], int(z["hop"])
    else:
        labels, starts, hop = segment_waveform(
            wav, cfg.segmentation_model, cfg.window_shift_ratio
        )
        if cfg.cache:
            os.makedirs(os.path.dirname(seg_path), exist_ok=True)
            np.savez_compressed(seg_path, labels=labels, starts=starts, hop=hop)
    timings["segmentation_sec"] = time.perf_counter() - t0

    t1 = time.perf_counter()
    units, emb, ok = _load_units(wav, labels, starts, cfg, audio_key)
    timings["embedding_sec"] = time.perf_counter() - t1

    t2 = time.perf_counter()
    X = emb[ok]
    if len(X) == 0:
        raise RuntimeError("No valid units: cannot diarize")
    if num_speakers is not None and num_speakers > 0:
        k = int(num_speakers)
        diag = None
    else:
        Xk = X
        if cfg.auto_k_scales is not None:
            t_k = time.perf_counter()
            _, emb_k, ok_k = _load_units(
                wav,
                labels,
                starts,
                cfg,
                audio_key,
                scales=cfg.auto_k_scales,
                suffix="auto_k",
            )
            Xk = emb_k[ok_k]
            timings["auto_k_embedding_sec"] = time.perf_counter() - t_k
        k, diag = auto_speaker_count(
            Xk,
            k_max=cfg.k_max,
            bootstrap=cfg.gap_bootstrap,
            seed=cfg.seed,
        )
    labels_unit = np.full(len(units), -1, dtype=int)
    labels_unit[np.where(ok)[0]] = cluster_embeddings(
        X, k, method=cfg.clustering, seed=cfg.seed
    )
    timings["clustering_sec"] = time.perf_counter() - t2

    t3 = time.perf_counter()
    segments = decode_segments(
        labels,
        starts,
        units,
        labels_unit,
        len(wav),
        k,
        min_duration_on=cfg.min_duration_on,
        min_duration_off=cfg.min_duration_off,
    )
    timings["decoding_sec"] = time.perf_counter() - t3
    timings["total_sec"] = time.perf_counter() - t0
    timings["audio_sec"] = len(wav) / SR
    timings["rtf"] = timings["total_sec"] / max(timings["audio_sec"], 1e-9)

    centroids = None
    if keep_embeddings:
        colors = labels_unit[ok]
        cents = []
        for c in range(k):
            Z = X[colors == c]
            if len(Z):
                v = Z.mean(axis=0)
                cents.append(v / max(np.linalg.norm(v), 1e-9))
            else:
                cents.append(np.zeros(X.shape[1], dtype=np.float32))
        centroids = np.stack(cents)

    return DiarizationOutput(
        segments=segments,
        speaker_count=k,
        embedding_model=os.path.basename(cfg.embedding_model),
        timings=timings,
        auto_k=diag,
        config={
            "segmentation_model": os.path.basename(cfg.segmentation_model),
            "embedding_model": os.path.basename(cfg.embedding_model),
            "window_shift_ratio": cfg.window_shift_ratio,
            "scales": list(cfg.scales),
            "purity": cfg.purity,
            "min_speech": cfg.min_speech,
            "clustering": cfg.clustering,
            "include_chunk_units": cfg.include_chunk_units,
            "auto_k_scales": cfg.auto_k_scales,
        },
        centroids=centroids,
    )


# --------------------------------------------------------------------------- sherpa baseline
def sherpa_diarize(
    wav: np.ndarray,
    segmentation_model: str,
    embedding_model: str,
    num_speakers: int = -1,
    threshold: float = 0.5,
    window_shift_ratio: float = 0.5,
    min_duration_on: float = 0.3,
    min_duration_off: float = 0.5,
) -> list[Segment]:
    import sherpa_onnx as so

    cfg = so.OfflineSpeakerDiarizationConfig(
        segmentation=so.OfflineSpeakerSegmentationModelConfig(
            pyannote=so.OfflineSpeakerSegmentationPyannoteModelConfig(
                model=segmentation_model, window_shift_ratio=window_shift_ratio
            )
        ),
        embedding=so.SpeakerEmbeddingExtractorConfig(model=embedding_model),
        clustering=so.FastClusteringConfig(
            num_clusters=num_speakers, threshold=threshold, compute_confidence=True
        ),
        min_duration_on=min_duration_on,
        min_duration_off=min_duration_off,
    )
    d = so.OfflineSpeakerDiarization(cfg)
    result = d.process(np.ascontiguousarray(wav, dtype=np.float32))
    return [
        Segment(float(s.start), float(s.end), int(s.speaker), float(s.confidence))
        for s in result.sort_by_start_time()
    ]


def read_audio(path: str) -> np.ndarray:
    wav, sr = sf.read(path, dtype="float32")
    if wav.ndim > 1:
        wav = wav.mean(axis=1)
    if sr != SR:
        raise ValueError(f"Expected {SR} Hz, got {sr}: resample first")
    return np.ascontiguousarray(wav, dtype=np.float32)


def main() -> None:
    ap = argparse.ArgumentParser(description="Diarize one audio file")
    ap.add_argument("--audio", required=True)
    ap.add_argument("--model", default="campplus_zh_en")
    ap.add_argument("--seg-model", default="pyannote-3.0")
    ap.add_argument("--num-speakers", type=int, default=0)
    ap.add_argument("--scales", default="2.0:1.0,3.0:1.5")
    ap.add_argument(
        "--auto-k-scales",
        default="",
        help="windows used only for speaker-count estimation, e.g. 2.0:1.0",
    )
    ap.add_argument("--clustering", default="complete", choices=["complete", "spectral"])
    ap.add_argument("--sherpa", action="store_true", help="run sherpa-onnx baseline instead")
    ap.add_argument("--threshold", type=float, default=0.5)
    ap.add_argument("--out", default="")
    args = ap.parse_args()

    from models import SEGMENTATION, get_embedding

    wav = read_audio(args.audio)
    emb_model = get_embedding(args.model).path
    seg_model = SEGMENTATION[args.seg_model].path

    if args.sherpa:
        t0 = time.perf_counter()
        segs = sherpa_diarize(
            wav,
            seg_model,
            emb_model,
            num_speakers=args.num_speakers if args.num_speakers > 0 else -1,
            threshold=args.threshold,
        )
        out = DiarizationOutput(
            segments=segs,
            speaker_count=len(set(s.speaker for s in segs)),
            embedding_model=os.path.basename(emb_model),
            timings={"total_sec": time.perf_counter() - t0, "audio_sec": len(wav) / SR},
            config={"pipeline": "sherpa", "threshold": args.threshold},
        )
    else:
        scales = tuple(
            tuple(float(x) for x in pair.split(":")) for pair in args.scales.split(",")
        )
        auto_k_scales = None
        if args.auto_k_scales:
            auto_k_scales = tuple(
                tuple(float(x) for x in pair.split(":"))
                for pair in args.auto_k_scales.split(",")
            )
        cfg = PipelineConfig(
            segmentation_model=seg_model,
            embedding_model=emb_model,
            scales=scales,
            clustering=args.clustering,
            auto_k_scales=auto_k_scales,
        )
        out = diarize(
            wav,
            cfg,
            audio_path=args.audio,
            num_speakers=args.num_speakers or None,
        )

    text = json.dumps(out.to_json(), ensure_ascii=False, indent=2)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(text)
    else:
        print(text)


if __name__ == "__main__":
    main()
