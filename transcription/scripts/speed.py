"""Оркестратор замера скорости: каждый модель в своём процессе."""
import json, os, subprocess, sys

import eval as E

out = []
for name, _, _ in E.REGISTRY:
    r = subprocess.run([sys.executable, os.path.join(E.ROOT, "speed_one.py"), name],
                       capture_output=True, text=True, encoding="utf-8")
    line = [l for l in r.stdout.strip().splitlines() if l.startswith("{")]
    if not line:
        print("FAIL", name, r.stderr[-500:], flush=True)
        continue
    d = json.loads(line[-1])
    out.append(d)
    print(f"{name:28s} RTF {d['rtf']:.3f}  RAM {d['ram_total_mb']:.0f}MB "
          f"({d['decode_sec']:.1f}s / {d['audio_sec']:.0f}s)", flush=True)

with open(os.path.join(E.OUT, "speed.json"), "w", encoding="utf-8") as f:
    json.dump({"chunk_sec": 26, "models": out}, f, ensure_ascii=False, indent=2)
print("saved speed.json", flush=True)
