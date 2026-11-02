# Ветка `transcription` — русский ASR на устройстве

Сравнение пяти моделей распознавания русской речи, которые работают **локально**
(без облака) и запускаются в Android/Kotlin через один рантайм —
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). Плюс готовые классы,
которые выдают текст и тайминги каждого слова.

## Навигация

| Что | Где |
|---|---|
| **Главный документ:** лидерборд, методика, разбор метрик, примеры, рекомендации | [`../transcription_leaderboard.md`](../transcription_leaderboard.md) |
| **Kotlin-классы** для Android (`transcribe()` → текст + `start`/`end` слов) | [`kotlin/transcribers/`](kotlin/transcribers/) |
| Инструкция по подключению sherpa-onnx и грабли | [`kotlin/README.md`](kotlin/README.md) |
| Сырые результаты: JSON, CSV, график, примеры | [`results/`](results/) |
| Скрипты бенчмарка | [`scripts/`](scripts/) |

## Результат коротко

| # | Модель | Размер | WER чтение | WER команды | WER avg | RTF | RAM |
|---|---|---|---|---|---|---|---|
| 1 | **GigaAM v3** | 229 МБ | 3.77% | 1.10% | **2.44%** | 0.043 | 337 МБ |
| 2 | **Zipformer-RU** | 73 МБ | 5.59% | 1.50% | 3.55% | **0.019** | 401 МБ |
| 3 | **Parakeet TDT 0.6B v3** | 670 МБ | 7.69% | 7.09% | 7.39% | 0.064 | 988 МБ |
| 4 | **Whisper large-v3-turbo** | 1037 МБ | 13.89% | 22.48% | 18.18% | 0.382 | 2288 МБ |
| 5 | **Whisper small** | 375 МБ | 11.29% | 32.57% | 21.93% | 0.343 | 1234 МБ |

WER — доля ошибок в словах (0% = идеально, может быть >100% из-за вставок).
Подробный разбор всех метрик — в
[разделе 3.1 лидерборда](../transcription_leaderboard.md#31-метрики-что-означают-цифры).

**Вывод:** для русского — **GigaAM v3**; для слабых устройств и мгновенной
диктовки — **Zipformer-RU**. Whisper через sherpa-onnx не рекомендуется:
greedy-декодер без fallback зацикливается на ~4% фраз.

## Быстрый старт в Kotlin

```kotlin
val asr: Transcriber = GigaAmTranscriber(
    context = applicationContext,
    modelDir = File(filesDir, "models/gigaam-v3"),
    numThreads = 4,
)

val result = asr.transcribe(File("/sdcard/record.mp4")) // WAV / MP3 / MP4
println(result.text)
result.words.forEach { w ->
    println("${w.word}: ${w.start} .. ${w.end} с")
}
asr.close()
```

Классы: `GigaAmTranscriber`, `ZipformerRuTranscriber`, `ParakeetTranscriber`,
`WhisperTurboTranscriber`, `WhisperSmallTranscriber`; общий интерфейс —
`Transcriber`. Подробности и требования — в
[`kotlin/transcribers/README.md`](kotlin/transcribers/README.md).

## Что внутри

| Файл | Назначение |
|---|---|
| `scripts/download.py` | скачивание моделей и датасета FLEURS ru |
| `scripts/download_parakeet.py` | Parakeet TDT 0.6B v3 (докачка) |
| `scripts/download_whisper_attention.py` | Whisper-декодеры с attention (точные тайминги слов) |
| `scripts/eval.py` | WER/CER + bootstrap CI на FLEURS ru test (200 фраз) |
| `scripts/eval_golos.py` | WER/CER на Golos 10h crowd (200 фраз) |
| `scripts/speed.py` | RTF и RAM на 10 сегментах по ~20 с (каждый в своём процессе) |
| `scripts/make_report.py` | таблица + график `results/pareto.png` |
| `scripts/analyze.py` | разбор худших/лучших примеров |
| `kotlin/transcribers/` | Kotlin-классы с таймингами слов |
| `results/*.json` | сырые метрики (WER, CER, CI, тайминги, гипотезы) |

## Воспроизведение

```bash
pip install sherpa-onnx jiwer soundfile numpy num2words pyarrow psutil matplotlib
python scripts/download.py
python scripts/download_parakeet.py
python scripts/download_whisper_attention.py   # опционально: точные тайминги Whisper
python scripts/eval.py            # N_UTTS=200 THREADS=4
python scripts/eval_golos.py
python scripts/speed.py
python scripts/make_report.py
```

Модели и аудио не коммитятся (см. `.gitignore`) — они скачиваются скриптами.

## Условия замера

- sherpa-onnx 1.13.8 (Python), CPU, int8-веса, `num_threads=4`.
- AMD Ryzen 5 5500 (6C/12T), Windows 11; машина была частично загружена,
  поэтому абсолютные RTF завышены — сравнение моделей корректно, но на
  телефоне цифры будут другими.
- Аудио: 16 кГц моно.
- Нормализация текста: lower-case, `ё→е`, пунктуация/дефисы удалены,
  числа переведены в слова (`num2words`), лишние пробелы схлопнуты.
- WER/CER — [jiwer](https://github.com/jitsi/jiwer), 95% CI — bootstrap по
  фразам (2000 ресэмплов).

### Датасеты

- **FLEURS ru_ru test** — 200 фраз (2317 с), чтение вслух, чистый звук,
  88 уникальных предложений в выборке (FLEURS даёт несколько дикторов на фразу).
- **Golos 10h crowd** — 200 фраз (~13 мин), короткие спонтанные команды,
  шум/телефонный звук, разметка без пунктуации.

## Ограничения

- FLEURS — чтение, Golos — короткие команды. Совещания/лекции с длинными
  сегментами и спонтанной речью будут сложнее для всех моделей.
- GigaAM и Zipformer-RU, вероятно, видели Golos/OpenSTT в обучении — их WER
  на Golos оптимистичен.
- Whisper в sherpa-onnx обрабатывает только первые 30 с; в классах это
  учтено нарезкой по 28 с.
