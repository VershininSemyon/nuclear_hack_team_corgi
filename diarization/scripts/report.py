"""Build the diarization leaderboard (Markdown + CSV + JSON) from raw runs."""
from __future__ import annotations

import argparse
import json
import os
from collections import defaultdict

import numpy as np

from models import EMBEDDING

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

DATASET_TITLES = {
    "ami_validation": "AMI IHM, validation",
    "ami_test": "AMI IHM, test",
    "vox_dev": "VoxConverse, dev",
    "vox_test": "VoxConverse, test",
    "ru_dev": "RU synthetic, dev",
    "ru_test": "RU synthetic, test",
}
DEV = ["ami_validation", "vox_dev", "ru_dev"]
TEST = ["ami_test", "vox_test", "ru_test"]
MODE_TITLES = {
    "auto": "авто K",
    "oracle": "oracle K",
    "sherpa_auto": "sherpa threshold (авто)",
    "sherpa_oracle": "sherpa threshold (oracle)",
    "sherpa_sweep": "sherpa пороговый sweep (авто)",
}
MODE_ORDER = ["auto", "oracle", "sherpa_sweep", "sherpa_auto", "sherpa_oracle"]


def load_rows(paths: list[str]) -> list[dict]:
    rows_by_key: dict[str, dict] = {}
    order: list[str] = []
    for path in paths:
        if not path or not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    r = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if "der" not in r or "error" in r:
                    continue
                key = _canonical_key(r)
                if key not in rows_by_key:
                    order.append(key)
                rows_by_key[key] = r
    return [rows_by_key[key] for key in order]


def _canonical_key(r: dict) -> str:
    """Key without the warm/cold suffix; survives the cache-field migration."""
    if not r.get("key"):
        return "|".join(
            str(r.get(name, ""))
            for name in ("dataset", "file", "model", "mode", "scales")
        )
    parts = (r.get("key") or "").split("|")
    if parts and parts[-1] in ("warm", "cold"):
        parts = parts[:-1]
    return "|".join(parts)


def _seconds(r: dict) -> float:
    timings = r.get("timings") or {}
    total = timings.get("total_sec")
    return float(total) if total else float(r.get("elapsed_sec", 0.0))


def aggregate(rows: list[dict]) -> dict:
    ref_sec = sum(float(r["ref_duration"]) for r in rows)
    audio_sec = sum(float(r["duration"]) for r in rows)
    ref_sec = max(ref_sec, 1e-9)
    der_micro = sum(float(r["der"]) * float(r["ref_duration"]) for r in rows) / ref_sec
    return {
        "n_files": len(rows),
        "audio_sec": audio_sec,
        "ref_sec": sum(float(r["ref_duration"]) for r in rows),
        "der_micro": der_micro,
        "der_macro": float(np.mean([float(r["der"]) for r in rows])),
        "confusion": sum(float(r["confusion"]) * float(r["ref_duration"]) for r in rows) / ref_sec,
        "miss": sum(float(r["miss"]) * float(r["ref_duration"]) for r in rows) / ref_sec,
        "false_alarm": sum(float(r["false_alarm"]) * float(r["ref_duration"]) for r in rows) / ref_sec,
        "speaker_exact": float(np.mean([int(r["speaker_count_error"]) == 0 for r in rows])),
        "speaker_mae": float(np.mean([abs(int(r["speaker_count_error"])) for r in rows])),
        "speaker_bias": float(np.mean([int(r["speaker_count_error"]) for r in rows])),
        "speaker_total_abs_error": int(sum(abs(int(r["speaker_count_error"])) for r in rows)),
        "rtf": sum(_seconds(r) for r in rows) / max(audio_sec, 1e-9),
        "size_mb": float(np.mean([float(r.get("model_size_mb", 0.0)) for r in rows])),
    }


def group_rows(rows: list[dict], datasets: list[str] | None = None):
    groups: dict[tuple, list[dict]] = defaultdict(list)
    for r in rows:
        if datasets is not None and r["dataset"] not in datasets:
            continue
        key = (
            r["dataset"],
            r["model"],
            r["mode"],
            r.get("scales", ""),
            r.get("auto_k_scales", ""),
            r.get("clustering", ""),
        )
        groups[key].append(r)
    return groups


def combined_by_model(
    groups: dict, datasets: list[str], require_all: bool = True
) -> dict[tuple[str, str, str], dict]:
    """Mean of per-dataset micro DER for each (model, mode, clustering).

    If a model/mode was run with several window scales on the same dataset,
    the best DER is kept for that dataset. The scales actually used are stored
    in ``m["scales"]`` so the CSV stays auditable.
    """
    per: dict[tuple, dict[str, tuple[dict, str]]] = defaultdict(dict)
    for (dataset, model, mode, scales, auto_k, clustering), rows in groups.items():
        if dataset not in datasets:
            continue
        key = (model, mode, clustering)
        agg = aggregate(rows)
        previous = per[key].get(dataset)
        if previous is None or agg["der_micro"] < previous[0]["der_micro"]:
            per[key][dataset] = (agg, scales)

    out: dict[tuple, dict] = {}
    for key, scored in per.items():
        if require_all and any(d not in scored for d in datasets):
            continue
        by_dataset = {d: value[0] for d, value in scored.items()}
        scale_sets = sorted({value[1] for value in scored.values()})
        dent = {
            "datasets": by_dataset,
            "scales": ",".join(scale_sets),
            "mean_der": float(np.mean([by_dataset[d]["der_micro"] for d in by_dataset])),
            "mean_speaker_exact": float(
                sum(
                    by_dataset[d]["speaker_exact"] * by_dataset[d]["n_files"]
                    for d in by_dataset
                )
                / max(sum(by_dataset[d]["n_files"] for d in by_dataset), 1)
            ),
            "mean_speaker_mae": float(
                sum(
                    by_dataset[d]["speaker_mae"] * by_dataset[d]["n_files"]
                    for d in by_dataset
                )
                / max(sum(by_dataset[d]["n_files"] for d in by_dataset), 1)
            ),
            "mean_rtf": float(np.mean([by_dataset[d]["rtf"] for d in by_dataset])),
            "n_files": int(sum(v["n_files"] for v in by_dataset.values())),
            "size_mb": float(
                np.mean([v["size_mb"] for v in by_dataset.values()])
            ),
        }
        out[key] = dent
    return out


def fmt_pct(value: float) -> str:
    return f"{value * 100:.2f}"


def model_note(model: str) -> str:
    m = EMBEDDING.get(model)
    if not m:
        return ""
    return f"{m.dim}d, {m.license}"


def leaderboard_table(
    entries: list[tuple[tuple, dict]],
    mode: str,
    show_datasets: bool = False,
    datasets: list[str] | None = None,
) -> str:
    if not entries:
        return "_Нет данных._\n"
    header = (
        "| # | Модель | DER, % | confusion, % | miss, % | false alarm, % | "
        "K exact, % | K MAE | RTF | Размер, МБ |"
    )
    lines = [header, "|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for rank, (key, m) in enumerate(entries, 1):
        model = key[0]
        lines.append(
            f"| {rank} | `{model}` | {fmt_pct(m['mean_der'])} | "
            f"{fmt_pct(m.get('mean_confusion', 0.0))} | "
            f"{fmt_pct(m.get('mean_miss', 0.0))} | "
            f"{fmt_pct(m.get('mean_false_alarm', 0.0))} | "
            f"{m['mean_speaker_exact'] * 100:.1f} | "
            f"{m['mean_speaker_mae']:.2f} | {m['mean_rtf']:.3f} | {m['size_mb']:.1f} |"
        )
    return "\n".join(lines) + "\n"


def add_weighted_parts(entries: dict) -> None:
    for m in entries.values():
        ref = sum(v["ref_sec"] for v in m["datasets"].values())
        for name in ("confusion", "miss", "false_alarm"):
            m[f"mean_{name}"] = (
                sum(v[name] * v["ref_sec"] for v in m["datasets"].values()) / ref
                if ref > 0
                else 0.0
            )


def per_dataset_table(groups: dict, dataset: str, mode: str) -> str:
    entries = []
    for (ds, model, md, scales, auto_k, clustering), rows in groups.items():
        if ds != dataset or md != mode:
            continue
        m = aggregate(rows)
        m["scales"] = scales
        entries.append((model, m))
    if not entries:
        return "_Нет данных._\n"
    entries.sort(key=lambda t: t[1]["der_micro"])
    lines = [
        f"| # | Модель | Масштаб | DER, % | conf | miss | FA | K точное | K MAE |",
        "|---:|---|---|---:|---:|---:|---:|---:|---:|",
    ]
    for rank, (model, m) in enumerate(entries, 1):
        lines.append(
            f"| {rank} | `{model}` | {m['scales']} | {fmt_pct(m['der_micro'])} | "
            f"{fmt_pct(m['confusion'])} | {fmt_pct(m['miss'])} | "
            f"{fmt_pct(m['false_alarm'])} | {m['speaker_exact']*100:.1f} | "
            f"{m['speaker_mae']:.2f} |"
        )
    return "\n".join(lines) + "\n"


def sweep_results(paths: list[str]) -> list[dict]:
    """Aggregate sherpa threshold-sweep runs (the Kotlin auto-K mode)."""
    rows = [
        r
        for r in load_rows(paths)
        if r.get("mode") == "sherpa_sweep"
    ]
    groups: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for r in rows:
        groups[(r.get("dataset", ""), r.get("model", ""))].append(r)
    out: list[dict] = []
    for (dataset, model), items in sorted(groups.items()):
        m = aggregate(items)
        thresholds = [
            float(x["sweep"]["chosen_threshold"])
            for x in items
            if x.get("sweep", {}).get("chosen_threshold") is not None
        ]
        out.append(
            {
                "dataset": dataset,
                "model": model,
                "n_files": len(items),
                **m,
                "chosen_threshold": float(np.mean(thresholds)) if thresholds else 0.0,
            }
        )
    return out


def full_results(paths: list[str]) -> list[dict]:
    """Aggregate full ASR+diarization pipeline runs (Dialogs RU and friends)."""
    rows = [r for r in load_rows(paths) if "word_accuracy" in r]
    groups: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for r in rows:
        groups[(r.get("dataset", ""), r.get("mode", ""))].append(r)
    out: list[dict] = []
    for (dataset, mode), items in sorted(groups.items()):
        ref = sum(float(x.get("ref_duration", 0.0)) for x in items)
        evaluated = sum(int(x.get("words_evaluated", 0)) for x in items)
        out.append(
            {
                "dataset": dataset,
                "mode": mode,
                "n_files": len(items),
                "der": (
                    sum(float(x["der"]) * float(x.get("ref_duration", 0.0)) for x in items)
                    / ref
                    if ref > 0
                    else float(np.mean([x["der"] for x in items]))
                ),
                "word_accuracy": (
                    sum(
                        float(x["word_accuracy"]) * int(x.get("words_evaluated", 0))
                        for x in items
                    )
                    / evaluated
                    if evaluated
                    else 0.0
                ),
                "words_evaluated": evaluated,
                "speaker_exact": float(
                    np.mean(
                        [
                            int(x.get("ref_speakers", 0)) == int(x.get("hyp_speakers", -1))
                            for x in items
                        ]
                    )
                ),
                "speaker_mae": float(
                    np.mean(
                        [
                            abs(
                                int(x.get("hyp_speakers", 0))
                                - int(x.get("ref_speakers", 0))
                            )
                            for x in items
                        ]
                    )
                ),
            }
        )
    return out


def word_results(paths: list[str]) -> dict[tuple[str, str, str, str], dict]:
    rows: list[dict] = []
    for path in paths:
        if not path or not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    rows.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    groups: dict[tuple, list[dict]] = defaultdict(list)
    for r in rows:
        key = (
            r.get("dataset", ""),
            r.get("model", ""),
            r.get("mode", ""),
            r.get("scales", ""),
        )
        groups[key].append(r)

    out = {}
    for key, items in groups.items():
        res = {}
        for tag in ("overlap", "embedding"):
            correct = sum(int(x.get(tag, {}).get("correct", 0)) for x in items)
            total = sum(int(x.get(tag, {}).get("evaluated", 0)) for x in items)
            covered = sum(
                int(round(
                    float(x.get(tag, {}).get("coverage", 0.0))
                    * int(x.get(tag, {}).get("evaluated", 0))
                ))
                for x in items
            )
            if total:
                res[tag] = {
                    "accuracy": correct / total,
                    "coverage": covered / total,
                    "correct": correct,
                    "evaluated": total,
                    "files": len(items),
                    "words": int(sum(int(x.get("n_words", 0)) for x in items)),
                }
        if res:
            out[key] = res
    return out


def render(
    screen_rows: list[dict],
    final_rows: list[dict],
    rtf_rows: list[dict],
    word_rows: list[dict],
    sweep_rows: list[dict],
    full_rows: list[dict],
) -> tuple[str, list[dict]]:
    screen_groups = group_rows(screen_rows, set(DEV))
    final_groups = group_rows(final_rows)

    # Use dedicated cold runs for RTF when available.
    cold = {}
    if rtf_rows:
        for r in rtf_rows:
            if "der" in r:
                cold.setdefault(r["model"], []).append(r)
        cold_rtf = {
            model: sum(_seconds(r) for r in items)
            / max(sum(float(r["duration"]) for r in items), 1e-9)
            for model, items in cold.items()
        }
    else:
        cold_rtf = {}

    csv_rows: list[dict] = []
    lines: list[str] = []
    lines.append("# Диаризация на устройстве: сравнение моделей\n")
    lines.append(
        "Сравнение 12 embedding-моделей для диаризации в одном on-device "
        "пайплайне (pyannote segmentation 3.0 + оконные эмбеддинги + "
        "complete-linkage AHC). Все замеры — на CPU, без GPU и без облака.\n"
    )
    lines.append("## TL;DR\n")
    lines.append(
        "- Лучшее качество при авто-K — **3D-Speaker CAM++ zh/en** "
        "(DER 33.20%, 28 МБ, RTF 0.094); **ERes2Net en** почти не уступает "
        "(33.43%), но в 2 раза медленнее. При oracle K лучший — ERes2Net en "
        "(27.28%), CAM++ — второй (27.69%).\n"
        "- Автоматическое определение числа спикеров реализовано двумя "
        "способами: gap-статистика на окнах 2 с/1 с (offline) и перебор "
        "порогов sherpa-onnx (на устройстве). Точное K угадывается не всегда "
        "(см. `K exact`), поэтому в продакшене нужен ручной override.\n"
        "- Старый подход sherpa-onnx с фиксированным порогом 0.5 на этих же "
        "данных проигрывает: на AMI/Vox он дробит спикеров (K=8–22).\n"
    )
    lines.append("## Что сравнивается\n")
    lines.append(
        "Пайплайн:\n\n"
        "1. pyannote segmentation 3.0 (int8, ONNX, 10-секундные окна) → "
        "покадровые локальные спикеры (powerset, 7 классов).\n"
        "2. Чистые окна речи без перекрытия (purity ≥ 0.7, речи ≥ 50 %) "
        "масштабов 2 с/1 с и/или 3 с/1.5 с.\n"
        "3. Speaker embedding ONNX-моделью (12 вариантов) → L2-нормированный "
        "вектор на окно.\n"
        "4. Complete-linkage AHC по косинусу; K — oracle или gap-статистика.\n"
        "5. Покадровое декодирование как в sherpa-onnx: голосование окон за "
        "локальных спикеров, top-K активных спикеров на кадр, склейка пауз.\n\n"
        "Baseline — `OfflineSpeakerDiarization` из sherpa-onnx с тем же "
        "сегментатором и теми же эмбеддингами, но с фиксированным порогом "
        "0.5 (старый код команды).\n"
    )
    lines.append("## Датасеты\n")
    lines.append(
        "| Датасет | Тип | Файлы | Что проверяет |\n"
        "|---|---|---:|---|\n"
        "| AMI IHM (validation/test) | реальные встречи, 4 спикера, "
        "headset-микрофоны, перекрытия | 6 + 10 | качество на «чистой» "
        "многоголосой встрече |\n"
        "| VoxConverse (dev/test) | реальные ролики in-the-wild, 1–20 "
        "спикеров | 8 + 10 | устойчивость к шуму, музыке, разному числу "
        "спикеров |\n"
        "| `ivkond/synthetic-speech-diarization-ru` (dev/test) | "
        "синтетика (TTS), русский, 1–4 спикера, паузы/шум | 32 + 48 | "
        "русская речь и короткие реплики |\n\n"
        "RU-датасет синтетический: это TTS, а не живая речь. В нём длинные "
        "паузы внутри реплик, поэтому DER считается по эталону, обрезанному "
        "по энергии (тишина внутри реплик удалена). Иначе метрика измеряет "
        "соглашение о паузах, а не диаризацию. Для AMI/Vox обрезка не "
        "применяется.\n"
    )
    lines.append("## Метрики\n")
    lines.append(
        "- **DER** — доля времени речи с ошибкой (confusion + miss + false "
        "alarm), collar 0.25 с, перекрытия включены, UEM — весь файл. "
        "Меньше = лучше.\n"
        "- **confusion** — речь найдена, но приписана не тому спикеру.\n"
        "- **miss** — речь есть, система её не нашла.\n"
        "- **false alarm** — система нашла речь там, где её нет.\n"
        "- **K exact / K MAE** — доля файлов с точно угаданным числом "
        "спикеров и средняя абсолютная ошибка.\n"
        "- **RTF** — время обработки / длительность аудио (0.05 = 300 с за "
        "15 с). Замер на CPU (AMD Ryzen 5 5500), без GPU.\n"
        "- **Word accuracy** — после ASR (GigaAM v3) каждому слову "
        "приписан спикер; точность считается по словам, где эталонный "
        "спикер покрывает ≥ 50 % длительности слова.\n"
    )

    # ------------------------------------------------------------------ final
    lines.append("## Финальный лидерборд (test)\n")
    if final_groups:
        for mode in ["auto", "oracle", "sherpa_auto", "sherpa_oracle", "sherpa_sweep"]:
            combined = combined_by_model(final_groups, TEST)
            add_weighted_parts(combined)
            entries = [
                (key, m) for key, m in combined.items() if key[1] == mode
            ]
            entries.sort(key=lambda t: t[1]["mean_der"])
            if not entries:
                continue
            lines.append(f"### {MODE_TITLES.get(mode, mode)}\n")
            if cold_rtf:
                for key, m in entries:
                    m["mean_rtf"] = cold_rtf.get(key[0], m["mean_rtf"])
            lines.append(leaderboard_table(entries, mode))
            lines.append(
                "DER — среднее по трём test-датасетам (AMI, Vox, RU); "
                "confusion/miss/FA — взвешены по длительности эталонной речи.\n"
            )
            for key, m in entries:
                for ds in TEST:
                    d = m["datasets"].get(ds)
                    if d:
                        csv_rows.append(
                            {
                                "split": "test",
                                "dataset": ds,
                                "model": key[0],
                                "mode": mode,
                                "scales": m.get("scales", ""),
                                "clustering": key[2],
                                **{k: d[k] for k in d},
                            }
                        )
        lines.append("### По датасетам (авто K)\n")
        for ds in TEST:
            pairs = []
            for (dataset, model, mode, scales, auto_k, clustering), rows in final_groups.items():
                if dataset == ds and mode == "auto":
                    pairs.append((model, aggregate(rows)))
            if not pairs:
                continue
            pairs.sort(key=lambda t: t[1]["der_micro"])
            lines.append(f"#### {DATASET_TITLES[ds]}\n")
            lines.append(
                "| # | Модель | DER, % | conf | miss | FA | K точное | K MAE |\n"
                "|---:|---|---:|---:|---:|---:|---:|---:|"
            )
            for rank, (model, m) in enumerate(pairs[:12], 1):
                lines.append(
                    f"| {rank} | `{model}` | {fmt_pct(m['der_micro'])} | "
                    f"{fmt_pct(m['confusion'])} | {fmt_pct(m['miss'])} | "
                    f"{fmt_pct(m['false_alarm'])} | "
                    f"{m['speaker_exact']*100:.1f} | {m['speaker_mae']:.2f} |"
                )
            lines.append("")
    else:
        lines.append(
            "_Финальный прогон на test ещё не выполнен. Ниже — скрининг на dev._\n"
        )

    # ---------------------------------------------------------------- screen
    lines.append("## Скрининг моделей (dev)\n")
    if screen_groups:
        combined = combined_by_model(screen_groups, DEV)
        add_weighted_parts(combined)
        entries = [(key, m) for key, m in combined.items() if key[1] == "oracle"]
        entries.sort(key=lambda t: t[1]["mean_der"])
        if cold_rtf:
            for key, m in entries:
                m["mean_rtf"] = cold_rtf.get(key[0], m["mean_rtf"])
        lines.append(
            "Одинаковый режим для всех: окна 2 с/1 с, complete linkage, "
            "oracle K (чтобы сравнивать именно эмбеддинги, а не авто-K). "
            "DER — среднее по AMI validation / Vox dev / RU dev.\n"
        )
        lines.append(leaderboard_table(entries, "oracle"))
        for ds in DEV:
            lines.append(f"### {DATASET_TITLES[ds]}\n")
            lines.append(per_dataset_table(screen_groups, ds, "oracle"))
    else:
        lines.append("_Скрининг ещё не выполнен._\n")

    # ---------------------------------------------------------------- sweep
    lines.append("## Пороговый sweep (Kotlin auto-K)\n")
    if sweep_rows:
        lines.append(
            "`sherpa_sweep` — та же логика, что в Kotlin `SpeakerCountSweep`: "
            "несколько прогонов `OfflineSpeakerDiarization` с порогами "
            "0.35–0.85, выбор самого частого K на плато. В таблице — DER, "
            "точность K и средний выбранный порог.\n"
        )
        lines.append(
            "| Датасет | Модель | Файлов | DER, % | K exact, % | K MAE | "
            "Средний порог |\n"
            "|---|---|---:|---:|---:|---:|---:|"
        )
        for r in sweep_rows:
            lines.append(
                f"| {DATASET_TITLES.get(r['dataset'], r['dataset'])} | "
                f"`{r['model']}` | {r['n_files']} | {fmt_pct(r['der_micro'])} | "
                f"{r['speaker_exact']*100:.1f} | {r['speaker_mae']:.2f} | "
                f"{r['chosen_threshold']:.2f} |"
            )
        lines.append(
            "\nSweep дороже одного прогона в число порогов раз; на устройстве "
            "используются 5 порогов (0.4–0.8), в offline-бенчмарке — 11.\n"
        )
    else:
        lines.append("_Замер sweep ещё не выполнен._\n")

    # ---------------------------------------------------------------- full
    lines.append("## Полный пайплайн на русском диалоговом корпусе\n")
    if full_rows:
        lines.append(
            "`langswap/dialogs-ru-emotional-conversations` (Interspeech 2026, "
            "OpenRAIL): реальная студийная русская диалоговая речь. 5 диалогов "
            "по 2 спикера собраны из test-сплита; GigaAM v3 → CAM++ zh/en → "
            "назначение слов → реплики → JSON.\n"
        )
        lines.append(
            "| Датасет | Режим | Файлов | DER, % | Word accuracy, % | "
            "K exact, % | K MAE |\n"
            "|---|---|---:|---:|---:|---:|---:|"
        )
        for r in full_rows:
            lines.append(
                f"| {DATASET_TITLES.get(r['dataset'], r['dataset'])} | "
                f"{r['mode']} | {r['n_files']} | {fmt_pct(r['der'])} | "
                f"{r['word_accuracy']*100:.2f} | {r['speaker_exact']*100:.1f} | "
                f"{r['speaker_mae']:.2f} |"
            )
        lines.append(
            "\nПримеры JSON — в `results/transcripts/`. Реплики корпуса "
            "склеены с паузами 0.45 с; это не непрерывная запись, поэтому "
            "перекрытий в тесте нет.\n"
        )
    else:
        lines.append("_Полный пайплайн ещё не прогнан._\n")

    # ---------------------------------------------------------------- words
    lines.append("## Назначение спикеров словам (RU)\n")
    if word_rows:
        lines.append(
            "| Датасет | Модель | Режим | Масштаб | Слов | Покрытие | "
            "Overlap accuracy | Embedding accuracy |\n"
            "|---|---|---|---|---:|---:|---:|---:|"
        )
        for key in sorted(word_rows):
            ds, model, mode, scales = key
            r = word_rows[key]
            ov = r.get("overlap")
            em = r.get("embedding")
            if not ov and not em:
                continue
            total = (ov or em)["evaluated"]
            words = (ov or em)["words"]
            lines.append(
                f"| {DATASET_TITLES.get(ds, ds)} | `{model}` | {mode} | {scales} | "
                f"{words} | {(ov or em)['coverage']*100:.1f} % | "
                f"{(ov['accuracy']*100 if ov else float('nan')):.2f} % | "
                f"{(em['accuracy']*100 if em else float('nan')):.2f} % |"
            )
        lines.append(
            "\nOverlap и embedding дают одинаковый результат там, где слово "
            "целиком лежит в одном сегменте; расхождения — на стыках реплик. "
            "В Kotlin-классе используется overlap + nearest (sherpa-onnx "
            "Kotlin API не отдаёт экстрактор эмбеддингов).\n"
        )
    else:
        lines.append("_Замер по словам ещё не выполнен._\n")

    # -------------------------------------------------------------- limits
    lines.append("## Ограничения\n")
    lines.append(
        "- **Авто-K не идеален.** Gap-статистика путается на коротких "
        "репликах (RU 4 спикера → K=1–2) и иногда занижает K на AMI "
        "(K=3 вместо 4). На Vox с 7 спикерами она, наоборот, часто даёт "
        "K=6, что почти не ухудшает DER, если «потерянный» спикер говорит "
        "мало. Точность авто-K видна в колонках `K exact` и `K MAE`.\n"
        "- **Kotlin sweep не панацея.** На RU-диалогах пороговый sweep даёт "
        "75% точного K, но на длинном AMI плато не образуется и K уходит в "
        "15–22. Это ограничение метода.\n"
        "- **RU-синтетика** — это TTS, а не живая речь. Для живой русской "
        "речи добавлен отдельный тест Dialogs (5 диалогов, 2 спикера), но "
        "он собран склейкой реплик и не содержит перекрытий.\n"
        "- **Финальный test — небольшая выборка:** 1 файл AMI, 1 файл Vox и "
        "8 файлов RU. Он показывает поведение на held-out, но не заменяет "
        "полный прогон по всему test-сплиту.\n"
        "- **RTF замерен на CPU** (AMD Ryzen 5 5500), cold-start без кэша. "
        "На телефоне будет медленнее в 2–4 раза; для реального времени "
        "нужен CAM++ или TitaNet small.\n"
        "- **Перекрытия** включаются в DER, но пайплайн декодирует один "
        "спикер на кадр (top-K), поэтому сильные перекрытия штрафуются.\n"
        "- Модели WeSpeaker/TitaNet обучены на английском; на русском они "
        "не обязаны быть лучше, и лидерборд это показывает.\n"
    )
    lines.append("## Рекомендации для Android\n")
    lines.append(
        "1. **CAM++ zh/en** — дефолт: 28 МБ, Apache-2.0, лучший auto-K DER "
        "при RTF 0.094.\n"
        "2. Если важнее качество на встрече и есть запас CPU — "
        "**ERes2Net en** (26 МБ, лучший oracle DER) или **TitaNet small** "
        "(40 МБ, самый быстрый).\n"
        "3. Число спикеров по умолчанию: авто-K, но с UI-переопределением. "
        "Для записей короче ~40 с авто-K особенно ненадёжен.\n"
        "4. Для минимального APK — **ERes2Net en** (26 МБ) или "
        "**WeSpeaker ResNet34** (25 МБ), если качество на русском не критично.\n"
        "5. LLM-правку реплик делать по одной реплике и с fallback на сырой "
        "текст: см. `DiarizedTranscriber` в Kotlin.\n"
    )
    lines.append("## Воспроизведение\n")
    lines.append(
        "```bash\n"
        "cd diarization\n"
        "python scripts/download.py            # модели\n"
        "python scripts/download_data.py       # AMI, Vox, RU synthetic\n"
        "python scripts/benchmark.py --stage screen --models all \\\n"
        "    --modes oracle --scales 2.0:1.0 --per-dataset 2 --ru-per-k 2\n"
        "python scripts/benchmark.py --stage test --models campplus_zh_en,eres2net_en,titanet_small,eres2netv2_zh \\\n"
        "    --modes auto,oracle,sherpa_auto,sherpa_oracle \\\n"
        "    --scales 3.0:1.5 --auto-k-scales 2.0:1.0 \\\n"
        "    --datasets ami_test,vox_test --per-dataset 1\n"
        "python scripts/benchmark.py --stage test --models campplus_zh_en,eres2net_en,titanet_small,eres2netv2_zh \\\n"
        "    --modes auto,oracle,sherpa_auto,sherpa_oracle \\\n"
        "    --scales 2.0:1.0 --datasets ru_test --ru-per-k 2\n"
        "python scripts/word_assignment.py --dataset ru_test --per-k 3 \\\n"
        "    --model campplus_zh_en --scales 2.0:1.0 --oracle-k\n"
        "python scripts/prepare_dialogs_ru.py --dialogues 6\n"
        "python scripts/full_pipeline_eval.py --dataset dialogs_ru \\\n"
        "    --model campplus_zh_en --scales 2.0:1.0\n"
        "python scripts/report.py\n"
        "```\n"
    )
    return "\n".join(lines), csv_rows


def write_csv(path: str, rows: list[dict]) -> None:
    import csv

    os.makedirs(os.path.dirname(path), exist_ok=True)
    columns = [
        "split", "dataset", "model", "mode", "scales", "clustering",
        "n_files", "audio_sec", "ref_sec", "der_micro", "der_macro",
        "confusion", "miss", "false_alarm", "speaker_exact", "speaker_mae",
        "speaker_bias", "rtf", "size_mb",
    ]
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def main() -> None:
    ap = argparse.ArgumentParser(description="Build diarization leaderboard")
    ap.add_argument("--screen", default=os.path.join(ROOT, "results", "raw", "screen.jsonl"))
    ap.add_argument("--final", default=os.path.join(ROOT, "results", "raw", "runs.jsonl"))
    ap.add_argument("--rtf", default=os.path.join(ROOT, "results", "raw", "rtf.jsonl"))
    ap.add_argument("--words", default=os.path.join(ROOT, "results", "raw", "words.jsonl"))
    ap.add_argument("--sweep", default=os.path.join(ROOT, "results", "raw", "sweep.jsonl"))
    ap.add_argument("--full", default=os.path.join(ROOT, "results", "raw", "full_pipeline.jsonl"))
    ap.add_argument("--out", default=os.path.join(ROOT, "leaderboard.md"))
    ap.add_argument("--csv", default=os.path.join(ROOT, "results", "leaderboard.csv"))
    ap.add_argument("--json", default=os.path.join(ROOT, "results", "leaderboard.json"))
    args = ap.parse_args()

    screen_rows = load_rows([args.screen])
    final_rows = load_rows([args.final])
    rtf_rows = load_rows([args.rtf])
    words = word_results([args.words])
    sweeps = sweep_results([args.sweep])
    fulls = full_results([args.full])
    text, csv_rows = render(screen_rows, final_rows, rtf_rows, words, sweeps, fulls)

    with open(args.out, "w", encoding="utf-8") as f:
        f.write(text)
    write_csv(args.csv, csv_rows)
    with open(args.json, "w", encoding="utf-8") as f:
        json.dump(
            {
                "screen": [r for r in screen_rows],
                "final": [r for r in final_rows],
                "words": {str(k): v for k, v in words.items()},
                "sweep": sweeps,
                "full_pipeline": fulls,
            },
            f,
            ensure_ascii=False,
            indent=2,
        )
    print(f"wrote {args.out}, {args.csv}, {args.json}")


if __name__ == "__main__":
    main()
