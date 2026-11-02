import json, os
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
res = json.load(open(os.path.join(ROOT, "results", "results.json"), encoding="utf-8"))
rows = [json.loads(l) for l in open(os.path.join(ROOT, "results", "transcripts.jsonl"), encoding="utf-8")]

for m in res["models"]:
    w = np.array(m["utt_wer"])
    print(f"{m['model']:26s} mean-utt {w.mean()*100:5.1f}%  median {np.median(w)*100:4.1f}%  "
          f">50%: {(w>0.5).sum():3d}  >80%: {(w>0.8).sum():3d}  empty {m['empty']}")
    idx = np.argsort(-w)[:5]
    for i in idx:
        print(f"   [{w[i]*100:5.1f}%] REF: {rows[i]['ref'][:110]}")
        print(f"            HYP: {rows[i][m['model']][:110]}")
    print()
