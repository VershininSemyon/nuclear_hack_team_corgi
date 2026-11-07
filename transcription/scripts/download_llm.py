"""Скачать GGUF-модель Qwen2.5 для локальной пунктуации.

Модели не входят в репозиторий: 0.5B весит ~0.5 ГБ, 1.5B — ~1.1 ГБ,
3B — ~2.1 ГБ. Скрипт качает файл с Hugging Face, проверяет SHA-256 и
кладёт в transcription/llm/.

Примеры:
    python transcription/scripts/download_llm.py --list
    python transcription/scripts/download_llm.py --model qwen2.5-1.5b
    python transcription/scripts/download_llm.py --model qwen2.5-3b --output-dir D:/models

Дальше в Kotlin:

    val llm = LlamaCppLocalLlm.load(
        File(filesDir, "models/qwen2.5-1.5b-instruct-q4_k_m.gguf")
    )
    val restorer = PunctuationRestorer(llm)
"""
import argparse
import hashlib
import os
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DIR = os.path.join(ROOT, "llm")

MODELS = {
    "qwen2.5-0.5b": {
        "repo": "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
        "file": "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        "size": 491_400_032,
        "sha256": "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        "ram": "~0.7 ГБ",
        "note": "самая быстрая; базовая пунктуация, слабее на сложных фразах",
    },
    "qwen2.5-1.5b": {
        "repo": "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
        "file": "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        "size": 1_117_320_736,
        "sha256": "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
        "ram": "~1.4–1.6 ГБ",
        "note": "рекомендуемый баланс качества и скорости для 6–8 ГБ RAM",
    },
    "qwen2.5-3b": {
        "repo": "Qwen/Qwen2.5-3B-Instruct-GGUF",
        "file": "qwen2.5-3b-instruct-q4_k_m.gguf",
        "size": 2_104_932_768,
        "sha256": "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
        "ram": "~2.5–2.8 ГБ",
        "note": "лучшее качество; нужен флагман с 8+ ГБ RAM",
    },
}


def human_size(size):
    if size >= 1e9:
        return f"{size / 1e9:.2f} ГБ"
    return f"{size / 1e6:.0f} МБ"


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            block = f.read(8 << 20)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def verify(path, model):
    size = os.path.getsize(path)
    if size != model["size"]:
        return False, f"размер {human_size(size)}, ожидали {human_size(model['size'])}"
    actual = sha256_of(path)
    if actual != model["sha256"]:
        return False, f"SHA-256 {actual[:16]}…, ожидали {model['sha256'][:16]}…"
    return True, "ok"


def download(url, dst, expected_size):
    part = dst + ".part"
    os.makedirs(os.path.dirname(dst), exist_ok=True)

    for attempt in range(8):
        try:
            have = os.path.getsize(part) if os.path.exists(part) else 0
            if have > expected_size:
                os.remove(part)
                have = 0

            request = urllib.request.Request(url)
            request.add_header("User-Agent", "android-transcriber-llm-downloader/1.0")
            if have:
                request.add_header("Range", f"bytes={have}-")

            try:
                response = urllib.request.urlopen(request, timeout=60)
            except urllib.error.HTTPError as error:
                if error.code == 416 and have:
                    os.replace(part, dst)
                    return
                raise

            append = have > 0 and response.status == 206
            mode = "ab" if append else "wb"
            done = have if append else 0
            total = expected_size
            next_report = 0.0

            with open(part, mode) as f:
                while True:
                    block = response.read(1 << 20)
                    if not block:
                        break
                    f.write(block)
                    done += len(block)
                    percent = done * 100.0 / total if total else 0
                    if percent >= next_report:
                        print(
                            f"\r  {percent:5.1f}%  {human_size(done)} / {human_size(total)}",
                            end="",
                            flush=True,
                        )
                        next_report = percent + 2
            print()

            if done != expected_size:
                raise IOError(f"короткое чтение: {done} из {expected_size}")

            os.replace(part, dst)
            return
        except Exception as error:
            print(
                f"\n  попытка {attempt + 1}/8: {type(error).__name__}: {error}",
                flush=True,
            )
            time.sleep(3 * (attempt + 1))

    raise RuntimeError(f"не удалось скачать {os.path.basename(dst)}")


def fetch(name, output_dir, force=False):
    model = MODELS[name]
    dst = os.path.join(output_dir, model["file"])

    print(f"\n{name}: {model['file']}")
    print(f"  RAM: {model['ram']}   {model['note']}")

    if os.path.exists(dst) and not force:
        ok, reason = verify(dst, model)
        if ok:
            print(f"  уже скачано: {dst}")
            return True
        print(f"  файл повреждён ({reason}), качаю заново")
        os.remove(dst)

    url = f"https://huggingface.co/{model['repo']}/resolve/main/{model['file']}?download=true"
    print(f"  {url}")

    try:
        download(url, dst, model["size"])
    except Exception as error:
        print(f"  ОШИБКА: {error}")
        return False

    print("  проверяю SHA-256…")
    ok, reason = verify(dst, model)
    if not ok:
        print(f"  ОШИБКА проверки: {reason}")
        os.remove(dst)
        return False

    print(f"  готово: {dst}")
    return True


def main():
    parser = argparse.ArgumentParser(
        description="Скачать Qwen2.5 GGUF для локальной пунктуации",
    )
    parser.add_argument(
        "--model",
        "-m",
        choices=sorted(MODELS),
        default="qwen2.5-1.5b",
        help="какая модель нужна (по умолчанию qwen2.5-1.5b)",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="скачать все три модели",
    )
    parser.add_argument(
        "--output-dir",
        "-o",
        default=DEFAULT_DIR,
        help=f"куда сохранять (по умолчанию {DEFAULT_DIR})",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="перекачать, даже если файл уже есть",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="показать доступные модели и выйти",
    )
    args = parser.parse_args()

    if args.list:
        width = max(len(name) for name in MODELS)
        for name, model in MODELS.items():
            print(
                f"{name:<{width}}  {human_size(model['size']):>9}  "
                f"RAM {model['ram']:<12}  {model['note']}"
            )
        return 0

    output_dir = os.path.abspath(os.path.expanduser(args.output_dir))
    names = sorted(MODELS) if args.all else [args.model]

    results = [fetch(name, output_dir, args.force) for name in names]
    if not all(results):
        return 1

    print("\nГотово. В Kotlin передайте путь к .gguf в LlamaCppLocalLlm.load().")
    return 0


if __name__ == "__main__":
    sys.exit(main())
