"""Скачивание моделей диаризации в ./models.

Все модели — ONNX, запускаются в sherpa-onnx на CPU и в Android/Kotlin.
Запуск:
    python scripts/download.py              # всё
    python scripts/download.py pyannote     # только файлы с подстрокой
    python scripts/download.py --list       # показать список
"""
from __future__ import annotations

import argparse
import os
import sys
import urllib.request
from dataclasses import dataclass

MODELS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "models")

HF_SPEAKER = "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/main/"
GITHUB_SEG = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/"


@dataclass(frozen=True)
class Model:
    name: str
    url: str
    archive: bool = False
    note: str = ""


MODELS: list[Model] = [
    # Сегментация: находит «кто когда говорит» на уровне кадров.
    Model(
        "sherpa-onnx-pyannote-segmentation-3-0/model.onnx",
        GITHUB_SEG + "sherpa-onnx-pyannote-segmentation-3-0.tar.bz2",
        archive=True,
        note="pyannote/segmentation-3.0, MIT",
    ),
    Model(
        "sherpa-onnx-pyannote-segmentation-3-0/model.int8.onnx",
        GITHUB_SEG + "sherpa-onnx-pyannote-segmentation-3-0.tar.bz2",
        archive=True,
        note="pyannote/segmentation-3.0, int8",
    ),
    Model(
        "sherpa-onnx-reverb-diarization-v1/model.onnx",
        GITHUB_SEG + "sherpa-onnx-reverb-diarization-v1.tar.bz2",
        archive=True,
        note="Revai/reverb-diarization-v1, non-commercial",
    ),
    Model(
        "sherpa-onnx-reverb-diarization-v1/model.int8.onnx",
        GITHUB_SEG + "sherpa-onnx-reverb-diarization-v1.tar.bz2",
        archive=True,
        note="Revai/reverb-diarization-v1, int8, non-commercial",
    ),
    # Эмбеддинги спикера: превращают фрагмент речи в вектор.
    Model("3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
          HF_SPEAKER + "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx"),
    Model("3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx",
          HF_SPEAKER + "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"),
    Model("3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx",
          HF_SPEAKER + "3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx"),
    Model("3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx",
          HF_SPEAKER + "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"),
    Model("3dspeaker_speech_eres2netv2_sv_zh-cn_16k-common.onnx",
          HF_SPEAKER + "3dspeaker_speech_eres2netv2_sv_zh-cn_16k-common.onnx"),
    Model("nemo_en_titanet_small.onnx", HF_SPEAKER + "nemo_en_titanet_small.onnx"),
    Model("nemo_en_titanet_large.onnx", HF_SPEAKER + "nemo_en_titanet_large.onnx"),
    Model("wespeaker_en_voxceleb_CAM++_LM.onnx", HF_SPEAKER + "wespeaker_en_voxceleb_CAM++_LM.onnx"),
    Model("wespeaker_en_voxceleb_resnet34.onnx", HF_SPEAKER + "wespeaker_en_voxceleb_resnet34.onnx"),
    Model("wespeaker_en_voxceleb_resnet34_LM.onnx", HF_SPEAKER + "wespeaker_en_voxceleb_resnet34_LM.onnx"),
    Model("wespeaker_en_voxceleb_resnet152_LM.onnx", HF_SPEAKER + "wespeaker_en_voxceleb_resnet152_LM.onnx"),
    Model("wespeaker_en_voxceleb_resnet221_LM.onnx", HF_SPEAKER + "wespeaker_en_voxceleb_resnet221_LM.onnx"),
    Model("wespeaker_en_voxceleb_resnet293_LM.onnx", HF_SPEAKER + "wespeaker_en_voxceleb_resnet293_LM.onnx"),
]


def human(n: float) -> str:
    return f"{n / 1e6:.1f} МБ"


def download(url: str, dst: str) -> None:
    if os.path.exists(dst) and os.path.getsize(dst) > 0:
        print(f"[skip] {os.path.basename(dst)} ({human(os.path.getsize(dst))})")
        return

    os.makedirs(os.path.dirname(dst) or ".", exist_ok=True)
    tmp = dst + ".part"

    def hook(blocks: int, block_size: int, total: int) -> None:
        if total > 0:
            done = min(blocks * block_size, total)
            print(f"\r  {os.path.basename(dst)}: {human(done)} / {human(total)}", end="", flush=True)

    print(f"[get ] {url}")
    try:
        urllib.request.urlretrieve(url, tmp, hook)
        os.replace(tmp, dst)
        print(f"\r[ok  ] {os.path.basename(dst)} ({human(os.path.getsize(dst))})" + " " * 20)
    except Exception as exc:  # noqa: BLE001
        if os.path.exists(tmp):
            os.remove(tmp)
        print(f"\n[ERR ] {os.path.basename(dst)}: {exc}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("filters", nargs="*", help="подстроки имён файлов")
    parser.add_argument("--list", action="store_true", help="только показать список")
    args = parser.parse_args()

    selected = []
    for model in MODELS:
        if args.filters and not any(f.lower() in model.name.lower() for f in args.filters):
            continue
        selected.append(model)

    if args.list:
        for model in selected:
            print(f"{model.name:70s} {model.note}")
        return 0

    os.makedirs(MODELS_DIR, exist_ok=True)
    print(f"Каталог моделей: {MODELS_DIR}")
    archives: dict[str, str] = {}
    for model in selected:
        target = os.path.join(MODELS_DIR, model.name)
        if model.archive:
            archive = os.path.join(MODELS_DIR, os.path.basename(model.url))
            if archive not in archives:
                download(model.url, archive)
                archives[archive] = model.url
            # распаковываем только нужный файл
            import tarfile

            print(f"[tar ] {os.path.basename(model.name)}")
            with tarfile.open(archive, "r:bz2") as tar:
                member = next((m for m in tar.getmembers() if m.name.endswith(model.name)), None)
                if member is None:
                    print(f"[ERR ] в архиве нет {model.name}")
                    continue
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with tar.extractfile(member) as src, open(target, "wb") as dst:  # type: ignore[arg-type]
                    dst.write(src.read())
            print(f"[ok  ] {model.name}")
        else:
            download(model.url, target)
    print("\nГотово. Модели лежат в", MODELS_DIR)
    return 0


if __name__ == "__main__":
    sys.exit(main())
