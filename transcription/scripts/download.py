import os, time, urllib.request, concurrent.futures as cf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, "models")
DATA = os.path.join(ROOT, "data")
os.makedirs(MODELS, exist_ok=True)
os.makedirs(DATA, exist_ok=True)

GH = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
HF = "https://huggingface.co/datasets/google/fleurs/resolve/main/data/"

jobs = [
    (GH + "sherpa-onnx-whisper-turbo.tar.bz2", os.path.join(MODELS, "whisper-turbo.tar.bz2")),
    (GH + "sherpa-onnx-whisper-small.tar.bz2", os.path.join(MODELS, "whisper-small.tar.bz2")),
    (GH + "sherpa-onnx-whisper-base.tar.bz2", os.path.join(MODELS, "whisper-base.tar.bz2")),
    (GH + "sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16.tar.bz2", os.path.join(MODELS, "gigaam-v3.tar.bz2")),
    (GH + "sherpa-onnx-zipformer-ru-int8-2025-04-20.tar.bz2", os.path.join(MODELS, "zipformer-ru.tar.bz2")),
    (HF + "ru_ru/audio/test.tar.gz", os.path.join(DATA, "ru_ru_test.tar.gz")),
]

def get(job):
    url, dst = job
    for attempt in range(6):
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

with cf.ThreadPoolExecutor(max_workers=3) as ex:
    for res in ex.map(get, jobs):
        print(res, flush=True)

print("downloads finished", flush=True)
