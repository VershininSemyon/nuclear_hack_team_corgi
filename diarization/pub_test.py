"""Публичный тест: скачивает размеченные записи (AMI) из интернета и гоняет по ним
все embedding-модели из ./models. Результат: pub_leaderboard.csv

python pub_test.py                         # 4 записи по 5 минут, число спикеров из разметки
python pub_test.py --n-files 8 --max-seconds 600
python pub_test.py --auto --threshold 0.5  # число спикеров определяется автоматически
python pub_test.py --refresh               # перекачать данные заново
python pub_test.py --skip-overlap          # не считать перекрытия речи ошибками
"""
import argparse
import glob
import io
import json
import os
import time

import librosa
import numpy as np
import pandas as pd
import soundfile as sf
from pyannote.core import Annotation, Segment
from pyannote.metrics.diarization import DiarizationErrorRate

from diarization import SR, diarize, load_audio, warmup  # <- поменяйте, если файл назван иначе

DATASET = "diarizers-community/ami"
DATA_DIR = "pub_audio_test"

p = argparse.ArgumentParser()
p.add_argument("--models-dir", default="models")
p.add_argument("--vad", default=None, help="путь к silero_vad.onnx (по умолчанию models/silero_vad.onnx)")
p.add_argument("--config", default="ihm", help="конфиг AMI: ihm (гарнитуры) или sdm (дальний микрофон)")
p.add_argument("--split", default="test", choices=["train", "validation", "test"])
p.add_argument("--n-files", type=int, default=4, help="сколько записей взять")
p.add_argument("--max-seconds", type=float, default=300, help="обрезать каждую запись до N секунд")
p.add_argument("--refresh", action="store_true", help="скачать данные заново")
p.add_argument("--auto", action="store_true", help="не подсказывать число спикеров")
p.add_argument("--threshold", type=float, default=0.5)
p.add_argument("--collar", type=float, default=0.25)
p.add_argument("--skip-overlap", action="store_true")
p.add_argument("--out", default="pub_leaderboard.csv")
args = p.parse_args()

VAD = args.vad or os.path.join(args.models_dir, "silero_vad.onnx")
MARKING = os.path.join(DATA_DIR, "marking.json")


# ------------------------------------------------------------------
# 1. скачивание и подготовка данных
# ------------------------------------------------------------------
def prepare_data():
    os.makedirs(DATA_DIR, exist_ok=True)
    if os.path.exists(MARKING) and not args.refresh:
        print(f"[data] {DATA_DIR}/ уже подготовлен, пропускаю загрузку (--refresh чтобы перекачать)")
        return
    for f in glob.glob(os.path.join(DATA_DIR, "ami_*.wav")):
        os.remove(f)

    try:
        from datasets import Audio, load_dataset
    except ImportError:
        raise SystemExit("Нужен пакет datasets:  pip install datasets")

    print(f"[data] стримлю {DATASET} ({args.config}, {args.split}), "
          f"беру {args.n_files} записей по {args.max_seconds:.0f} с ...")
    ds = load_dataset(DATASET, args.config, split=args.split, streaming=True)
    ds = ds.cast_column("audio", Audio(decode=False))   # байты, без torchcodec

    marking = {}
    for i, ex in enumerate(ds):
        if i >= args.n_files:
            break
        raw = ex["audio"].get("bytes")
        if not raw:
            raise SystemExit("В примере нет байтов аудио; проверьте версию datasets.")

        wav, sr = sf.read(io.BytesIO(raw), dtype="float32", always_2d=True)
        wav = wav.mean(axis=1)
        if sr != SR:
            wav = librosa.resample(wav, orig_sr=sr, target_sr=SR)
        wav = wav[: int(args.max_seconds * SR)]
        dur = len(wav) / SR

        segs = []
        for s, e, spk in zip(ex["timestamps_start"], ex["timestamps_end"], ex["speakers"]):
            if s >= dur:
                continue
            e = min(e, dur)
            if e - s >= 0.05:
                segs.append({"speaker": str(spk), "start": round(float(s), 3), "end": round(float(e), 3)})
        segs.sort(key=lambda x: x["start"])

        fname = f"ami_{args.split}_{i + 1}.wav"
        sf.write(os.path.join(DATA_DIR, fname), wav, SR)
        marking[fname] = {"segments": segs}
        n_spk = len({s["speaker"] for s in segs})
        print(f"[data] {fname}: {dur:.0f} с, дикторов {n_spk}, реплик {len(segs)}")

    if not marking:
        raise SystemExit("Не удалось получить ни одной записи. Если датасет просит вход: huggingface-cli login")
    with open(MARKING, "w", encoding="utf-8") as f:
        json.dump(marking, f, ensure_ascii=False, indent=2)
    print(f"[data] готово: {DATA_DIR}/")


# ------------------------------------------------------------------
# 2. загрузка разметки
# ------------------------------------------------------------------
def load_marking():
    with open(MARKING, encoding="utf-8") as f:
        raw = json.load(f)
    refs = {}
    for fname, item in raw.items():
        ann = Annotation()
        for i, s in enumerate(item["segments"]):
            ann[Segment(s["start"], s["end"]), i] = str(s["speaker"])
        refs[fname] = ann
    return refs


# ------------------------------------------------------------------
# 3. тест одной модели
# ------------------------------------------------------------------
def run_model(emb_model, refs, audios):
    warmup(emb_model)                       # загрузка модели не входит в замер
    metric = DiarizationErrorRate(collar=args.collar, skip_overlap=args.skip_overlap)
    per_file, total_time, total_audio, spk_err = [], 0.0, 0.0, []

    for fname, ref in refs.items():
        audio = audios[fname]
        n_ref = len(ref.labels())
        n = None if args.auto else n_ref

        t0 = time.perf_counter()
        segs = diarize(audio, emb_model, num_speakers=n,
                       threshold=args.threshold, vad_model=VAD)
        elapsed = time.perf_counter() - t0

        hyp = Annotation()
        for i, s in enumerate(segs):
            hyp[Segment(s.start, s.end), i] = f"spk{s.speaker}"

        d = metric(ref, hyp, detailed=True)
        dur = len(audio) / SR
        total_time += elapsed
        total_audio += dur
        spk_err.append(abs(len(hyp.labels()) - n_ref))
        per_file.append({
            "model": os.path.basename(emb_model),
            "file": fname,
            "DER_%": round(100 * d["diarization error rate"], 2),
            "confusion_%": round(100 * d["confusion"] / d["total"], 2),
            "miss_%": round(100 * d["missed detection"] / d["total"], 2),
            "false_alarm_%": round(100 * d["false alarm"] / d["total"], 2),
            "RTF": round(elapsed / dur, 3),
        })

    comp = metric[:]
    total = comp["total"]
    summary = {
        "model": os.path.basename(emb_model),
        "DER_%": round(100 * abs(metric), 2),
        "confusion_%": round(100 * comp["confusion"] / total, 2),
        "miss_%": round(100 * comp["missed detection"] / total, 2),
        "false_alarm_%": round(100 * comp["false alarm"] / total, 2),
        "RTF": round(total_time / total_audio, 3),
        "size_MB": round(os.path.getsize(emb_model) / 1e6, 1),
        "spk_count_err": round(float(np.mean(spk_err)), 2),
    }
    return summary, per_file


# ------------------------------------------------------------------
def main():
    prepare_data()
    refs = load_marking()

    audios = {}
    for fname in list(refs):
        path = os.path.join(DATA_DIR, fname)
        if not os.path.exists(path):
            print(f"[warn] нет файла {path}, пропускаю")
            del refs[fname]
            continue
        audios[fname] = load_audio(path)
    if not refs:
        raise SystemExit("Нет данных для теста")

    if not os.path.exists(VAD):
        raise SystemExit(f"Не найден {VAD}. Запустите download.py")
    models = sorted(m for m in glob.glob(os.path.join(args.models_dir, "*.onnx"))
                    if "silero" not in os.path.basename(m).lower())
    if not models:
        raise SystemExit("В models/ нет embedding-моделей. Запустите download.py")

    mode = f"auto (threshold={args.threshold})" if args.auto else "oracle (число спикеров из разметки)"
    print(f"\nРежим: {mode}; файлов: {len(refs)}; моделей: {len(models)}; "
          f"перекрытия {'не ' if args.skip_overlap else ''}учитываются\n")

    rows, details = [], []
    for m in models:
        print(f"== {os.path.basename(m)}")
        try:
            summary, per_file = run_model(m, refs, audios)
        except Exception as e:
            print(f"   [ERR] {e}")
            continue
        print(f"   DER={summary['DER_%']}%  RTF={summary['RTF']}")
        rows.append(summary)
        details += per_file

    if not rows:
        raise SystemExit("Ни одна модель не отработала")
    board = pd.DataFrame(rows).sort_values("DER_%").reset_index(drop=True)
    board.index += 1
    board.index.name = "rank"
    board.to_csv(args.out, encoding="utf-8-sig")
    per_file_path = args.out.replace(".csv", "_per_file.csv")
    pd.DataFrame(details).to_csv(per_file_path, index=False, encoding="utf-8-sig")
    print("\n" + board.to_string())
    print(f"\nСохранено: {args.out} и {per_file_path}")


if __name__ == "__main__":
    main()