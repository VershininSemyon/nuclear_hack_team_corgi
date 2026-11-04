"""Скачивает silero_vad.onnx и embedding-модели в ./models.
Запуск:  python download.py            (всё)
         python download.py resnet34   (только модели, в имени которых есть подстрока)
"""
import os
import sys
import urllib.request

MODELS_DIR = "models"
HF = "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/main/"
SILERO_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

EMBEDDING_MODELS = [
    "wespeaker_en_voxceleb_resnet34.onnx",
    "wespeaker_en_voxceleb_resnet34_LM.onnx",
    "wespeaker_en_voxceleb_CAM++_LM.onnx",
    "wespeaker_en_voxceleb_resnet221_LM.onnx",
    "wespeaker_en_voxceleb_resnet293_LM.onnx",
    "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
    "3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx",
    "3dspeaker_speech_eres2netv2_sv_zh-cn_16k-common.onnx",
    "nemo_en_titanet_small.onnx",
    "nemo_en_titanet_large.onnx",
]


def download(url: str, dst: str):
    if os.path.exists(dst) and os.path.getsize(dst) > 0:
        print(f"[skip] {os.path.basename(dst)} уже есть")
        return

    def hook(blocks, block_size, total):
        if total > 0:
            done = min(blocks * block_size, total)
            print(f"\r  {os.path.basename(dst)}: {done / 1e6:6.1f}/{total / 1e6:.1f} МБ",
                  end="", flush=True)

    tmp = dst + ".part"
    try:
        urllib.request.urlretrieve(url, tmp, hook)
        os.replace(tmp, dst)
        print(f"\r[ok]   {os.path.basename(dst)}" + " " * 30)
    except Exception as e:
        if os.path.exists(tmp):
            os.remove(tmp)
        print(f"\n[ERR]  {os.path.basename(dst)}: {e}")


def main():
    os.makedirs(MODELS_DIR, exist_ok=True)
    filters = sys.argv[1:]

    download(SILERO_URL, os.path.join(MODELS_DIR, "silero_vad.onnx"))
    for name in EMBEDDING_MODELS:
        if filters and not any(f in name for f in filters):
            continue
        download(HF + name, os.path.join(MODELS_DIR, name))


if __name__ == "__main__":
    main()