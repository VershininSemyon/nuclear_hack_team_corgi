"""Build leaderboard.csv / table.md / pareto.png from results, golos, speed."""
import json, os
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "results")
res = json.load(open(os.path.join(OUT, "results.json"), encoding="utf-8"))
golos = json.load(open(os.path.join(OUT, "golos.json"), encoding="utf-8")) \
    if os.path.exists(os.path.join(OUT, "golos.json")) else {"models": []}
speed_p = os.path.join(OUT, "speed.json")
speed = {}
ram = {}
if os.path.exists(speed_p):
    spd = json.load(open(speed_p, encoding="utf-8"))
    speed = {m["model"]: m["rtf"] for m in spd["models"]}
    ram = {m["model"]: m.get("ram_total_mb", m.get("ram_mb", float("nan"))) for m in spd["models"]}

fleurs = {m["model"]: m for m in res["models"]}
gol = {m["model"]: m for m in golos["models"]}

names = [n for n in fleurs if n in gol] or list(fleurs)
rows = []
for n in names:
    f = fleurs[n]
    g = gol.get(n)
    w_f = f["wer"]
    w_g = g["wer"] if g else float("nan")
    w_avg = np.nanmean([w_f, w_g])
    rows.append({"model": n, "wer_fleurs": w_f, "wer_golos": w_g, "wer_avg": w_avg,
                 "cer_fleurs": f["cer"], "size_mb": f["size_mb"],
                 "rtf_short": f["rtf"], "rtf_long": speed.get(n, float("nan")),
                 "ram_mb": ram.get(n, float("nan")),
                 "median_utt_wer": float(np.median(f["utt_wer"])),
                 "loop_rate": float(np.mean(np.array(f["utt_wer"]) > 0.8)),
                 "ci": f.get("wer_ci")})
rows.sort(key=lambda r: r["wer_avg"])

hdr = ("| # | Модель | Размер | WER FLEURS | WER Golos | WER avg | CER FLEURS | "
       "RTF (26-с сегм.) | RAM | Медиана WER | Фраз с WER>80% |")
sep = "|---|---|---|---|---|---|---|---|---|---|---|"
lines = [hdr, sep]
for i, r in enumerate(rows, 1):
    lines.append(
        f"| {i} | **{r['model']}** | {r['size_mb']:.0f} МБ | {r['wer_fleurs']*100:.2f}% | "
        f"{r['wer_golos']*100:.2f}% | **{r['wer_avg']*100:.2f}%** | {r['cer_fleurs']*100:.2f}% | "
        f"{r['rtf_long']:.3f} | {r['ram_mb']:.0f} МБ | {r['median_utt_wer']*100:.1f}% | "
        f"{r['loop_rate']*100:.0f}% |")
table = "\n".join(lines)
open(os.path.join(OUT, "table.md"), "w", encoding="utf-8").write(table + "\n")
print(table)

# csv
with open(os.path.join(OUT, "leaderboard.csv"), "w", encoding="utf-8") as f:
    f.write("rank,model,size_mb,wer_fleurs,wer_golos,wer_avg,cer_fleurs,rtf_short,rtf_long,ram_mb,median_utt_wer,loop_rate\n")
    for i, r in enumerate(rows, 1):
        f.write(f"{i},{r['model']},{r['size_mb']:.0f},{r['wer_fleurs']:.4f},{r['wer_golos']:.4f},"
                f"{r['wer_avg']:.4f},{r['cer_fleurs']:.4f},{r['rtf_short']:.4f},{r['rtf_long']:.4f},"
                f"{r['ram_mb']:.0f},{r['median_utt_wer']:.4f},{r['loop_rate']:.4f}\n")

# plot
fig, ax = plt.subplots(figsize=(7.6, 4.8), dpi=170)
for r in rows:
    x = r["rtf_long"] if not np.isnan(r["rtf_long"]) else r["rtf_short"]
    y = r["wer_avg"] * 100
    ax.scatter(x, y, s=max(r["size_mb"] / 5, 60), alpha=0.75, edgecolor="k", linewidth=0.7)
    ax.annotate(r["model"], (x, y), textcoords="offset points", xytext=(9, 7), fontsize=8)
ax.set_xlabel("RTF (5-мин аудио, 4 потока CPU) — меньше = быстрее")
ax.set_ylabel("WER, % (среднее по FLEURS + Golos) — меньше = лучше")
ax.set_xscale("log")
ax.grid(alpha=0.3)
ax.set_title("Русские on-device ASR: качество vs скорость", fontsize=10)
fig.tight_layout()
fig.savefig(os.path.join(OUT, "pareto.png"))
print("saved table.md, leaderboard.csv, pareto.png")
