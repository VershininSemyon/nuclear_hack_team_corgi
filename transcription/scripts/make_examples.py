import json, os
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "results")


def load_jsonl(path):
    return [json.loads(l) for l in open(path, encoding="utf-8")]


def block(title, ref, items):
    lines = [f"### {title}", "", f"**Эталон:** {ref}", ""]
    for name, hyp, w in items:
        lines.append(f"- **{name}** (WER {w*100:.0f}%): {hyp if hyp.strip() else '—'}")
    lines.append("")
    return "\n".join(lines)


fleurs = load_jsonl(os.path.join(OUT, "transcripts.jsonl"))
gres = json.load(open(os.path.join(OUT, "results.json"), encoding="utf-8"))
names = [m["model"] for m in gres["models"]]
utt_wer = {m["model"]: m["utt_wer"] for m in gres["models"]}

# 1. типичная короткая фраза, все справились
idx_short = min(range(len(fleurs)), key=lambda i: (abs(len(fleurs[i]["ref"]) - 70), i))
# 2. длинная фраза с числом/именем
cands = [i for i, r in enumerate(fleurs) if any(ch.isdigit() for ch in r["ref"]) and len(r["ref"]) > 120]
idx_num = cands[0] if cands else 0
# 3. худший кейс Whisper (циклы)
w = utt_wer["Whisper large-v3-turbo"]
idx_loop = int(np.argmax(w))

parts = ["## Примеры транскрибирования (FLEURS ru, сырой вывод моделей)", ""]
for tag, i in [("Короткая чистая фраза", idx_short), ("Фраза с числами и именем", idx_num),
               ("Худший кейс Whisper: зацикливание", idx_loop)]:
    items = [(n, fleurs[i].get(n, ""), utt_wer[n][i]) for n in names]
    parts.append(block(tag, fleurs[i]["ref"], items))

gpath = os.path.join(OUT, "golos.json")
if os.path.exists(gpath):
    g = json.load(open(gpath, encoding="utf-8"))
    gnames = [m["model"] for m in g["models"]]
    gwer = {m["model"]: m["utt_wer"] for m in g["models"]}
    refs = g["models"][0]["refs"]
    parts.append("## Примеры транскрибирования (Golos crowd, спонтанные команды)")
    parts.append("")
    tw = np.array(gwer["Whisper large-v3-turbo"])
    gw = np.array(gwer["GigaAM v3 (RU)"])
    typical = int(np.argmin(np.abs(tw - np.median(tw))))
    picks = list(dict.fromkeys([typical, int(np.argmax(tw)), int(np.argmax(gw))]))
    for i in picks:
        items = [(n, g["models"][j]["hyps"][i], gwer[n][i]) for j, n in enumerate(gnames)]
        parts.append(block(f"Фраза (Golos #{i + 1})", refs[i], items))

open(os.path.join(OUT, "examples.md"), "w", encoding="utf-8").write("\n".join(parts))
print("saved examples.md")
