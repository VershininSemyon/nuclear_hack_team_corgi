import os, time, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, "models")
os.makedirs(MODELS, exist_ok=True)

GH = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
url = GH + "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2"
dst = os.path.join(MODELS, "parakeet-v3.tar.bz2")

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
        print("done", os.path.getsize(dst) / 1e6, "MB", flush=True)
        break
    except Exception as e:
        print(f"retry {attempt+1}: {type(e).__name__} {e}", flush=True)
        time.sleep(3 * (attempt + 1))
