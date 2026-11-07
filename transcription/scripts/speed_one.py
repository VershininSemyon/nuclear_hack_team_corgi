"""Один прогон одного модели в отдельном процессе (чистый замер RAM)."""
import json, os, sys, time
import numpy as np
import psutil
import soundfile as sf

import eval as E

name = sys.argv[1]
E.THREADS = int(os.environ.get("THREADS", "4"))
N_CHUNKS, CHUNK_SEC = 10, 26.0

# собираем чанки < 30 c (Whisper не умеет длиннее)
rows = [l.rstrip("\n").split("\t") for l in open(os.path.join(E.DATA, "ru_ru.tsv"), encoding="utf-8")]
rows = [r for r in rows if len(r) >= 3][:400]
chunks, cur, cur_dur = [], [], 0.0
for r in rows:
    p = os.path.join(E.DATA, "test", r[1])
    if not os.path.exists(p):
        continue
    x, sr = sf.read(p, dtype="float32")
    if x.ndim > 1:
        x = x.mean(axis=1)
    d = len(x) / 16000
    if cur and cur_dur + d > CHUNK_SEC:
        chunks.append(np.concatenate(cur)); cur, cur_dur = [], 0.0
        if len(chunks) >= N_CHUNKS:
            break
    cur.append(x); cur.append(np.zeros(1600, dtype=np.float32)); cur_dur += d + 0.1
if len(chunks) < N_CHUNKS and cur:
    chunks.append(np.concatenate(cur))
audio_sec = sum(len(c) / 16000 for c in chunks)

proc = psutil.Process()
rss0 = proc.memory_info().rss
kind, prefix = [(k, p) for n, k, p in E.REGISTRY if n == name][0]
d = E.find_model_dir(prefix)
rec = E.build(kind, d)
rss1 = proc.memory_info().rss

st = rec.create_stream(); st.accept_waveform(16000, chunks[0][: 16000 * 3]); rec.decode_stream(st)
t0 = time.perf_counter()
for c in chunks:
    st = rec.create_stream(); st.accept_waveform(16000, c); rec.decode_stream(st)
dt = time.perf_counter() - t0
rss2 = proc.memory_info().rss

print(json.dumps({"model": name, "rtf": dt / audio_sec, "audio_sec": audio_sec,
                  "decode_sec": dt, "ram_weights_mb": (rss1 - rss0) / 1e6,
                  "ram_total_mb": (rss2 - rss0) / 1e6}, ensure_ascii=False))
