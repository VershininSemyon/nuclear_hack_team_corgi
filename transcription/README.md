# Ветка `transcription` — русский ASR на устройстве

Сравнение пяти моделей распознавания русской речи, которые работают **локально**
(без облака) и запускаются в Android/Kotlin через один рантайм —
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). Плюс готовые классы,
которые выдают текст и тайминги каждого слова.

## Навигация

| Что | Где |
|---|---|
| **Главный документ:** лидерборд, методика, разбор метрик, примеры, рекомендации | [`leaderboard.md`](leaderboard.md) |
| **Kotlin-классы** для Android (`transcribe()` → текст + `start`/`end` слов, `transcribeWithAudio()` → ещё и куски аудио) | [`kotlin/transcribers/`](kotlin/transcribers/) |
| **Пунктуация локальной LLM** (Qwen2.5, GGUF, без облака) | [`kotlin/transcribers/README.md#пунктуация-и-форматирование-локальной-llm`](kotlin/transcribers/README.md#пунктуация-и-форматирование-локальной-llm) |
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
[разделе 3.1 лидерборда](leaderboard.md#31-метрики-что-означают-цифры).

**Вывод:** для русского — **GigaAM v3**; для слабых устройств и мгновенной
диктовки — **Zipformer-RU**. Whisper через sherpa-onnx не рекомендуется:
greedy-декодер без fallback зацикливается на ~4% фраз.

## Архитектура ASR-пайплайна

```
file / Uri / FloatArray
        │
        ▼
AudioDecoder ── MediaExtractor + MediaCodec ──▶ PCM 16 кГц, моно, [-1, 1]
        │
        ▼
AudioChunker ── режет на сегменты ≤ 25 с (Whisper ≤ 28 с)
        │
        ▼
OfflineRecognizer (sherpa-onnx, greedy_search)
        │  tokens + timestamps + durations
        ▼
WordTimestamps.fromTokens ── char / BPE / SentencePiece → WordTimestamp
        │
        ▼
TranscriptionResult { text, words, approximateTimestamps }
        │
        ├──▶ AudioSlicer.sliceWords ──▶ TranscriptionWithAudio.chunks
        │
        ▼
PunctuationRestorer (опционально)
        ├── LocalLlm (Qwen2.5 GGUF) → пунктуация/регистр/абзацы
        └── fallback: RuleBasedFormatter по паузам
```

| Компонент | Что делает | Файл |
|---|---|---|
| `AudioDecoder` | WAV/MP3/MP4/M4A → PCM 16 кГц моно; ресемплинг | `AudioDecoder.kt` |
| `AudioChunker` | режет длинное аудио по тишине на сегменты ≤ 25 с | `AudioChunker.kt` |
| `BaseSherpaTranscriber` | создаёт stream, декодирует, собирает слова и текст | `BaseSherpaTranscriber.kt` |
| `WordTimestamps` | приводит токены разных моделей к словам с секундами | `WordTimestamps.kt` |
| `AudioSlicer` | вырезает кусок исходного аудио для каждого слова | `AudioSlicer.kt` |
| `PunctuationRestorer` | пунктуация/правка локальной LLM + fallback | `PunctuationRestorer.kt` |

### Что делает каждая модель

| Класс | Семейство | `modelType` | Особенность |
|---|---|---|---|
| `GigaAmTranscriber` | NeMo RNNT | `nemo_transducer` | посимвольные токены, русский, лучшее качество |
| `ZipformerRuTranscriber` | Zipformer transducer | `transducer` | самый лёгкий и быстрый |
| `ParakeetTranscriber` | NeMo TDT | `nemo_transducer` | 25 языков, пунктуация из коробки |
| `WhisperTurboTranscriber` | Whisper | `whisper` | 30-секундное окно, `language = "ru"` |
| `WhisperSmallTranscriber` | Whisper | `whisper` | то же, меньше и быстрее |

Все модели используют один `OfflineRecognizer`, 16 кГц, 80 mel-фильтров и
`greedy_search`. Различие — только в `OfflineModelConfig` и именах файлов.

### Тайминги слов

`WordTimestamps.fromTokens` поддерживает три формата токенов:

- **GigaAM** — один символ на токен, пробел тоже токен;
- **Zipformer-RU / Parakeet** — SentencePiece/BPE с `▁` (U+2581);
- **Whisper** — BPE с обычным пробелом и `Ġ` (U+0120).

Если модель не вернула реальные `timestamps`, время раскладывается
пропорционально длине токенов, а `TranscriptionResult.approximateTimestamps`
становится `true`. Для Whisper точные тайминги включаются только с
attention-декодером (`*-decoder-attention.int8.onnx`).

### Постобработка текста

`PunctuationRestorer` режет слова на куски по ~160 слов (по самой длинной
паузе), отправляет каждый кусок в локальную LLM и проверяет ответ через
`TextAligner`:

- `EditingMode.STRICT` — менять можно только пунктуацию и регистр;
- `EditingMode.CORRECT` — разрешены очевидные исправления ASR, удаление
  слов-паразитов; максимум правок ограничен `maxEditsRatio = 0.4`;
- если LLM недоступна или ответ не прошёл проверку — `RuleBasedFormatter`
  делит текст на предложения по паузам.

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

Пунктуация и абзацы — отдельный необязательный слой: `PunctuationRestorer` +
`LlamaCppLocalLlm` (Qwen2.5 0.5B/1.5B/3B GGUF). Если LLM нет или она ошиблась,
текст всё равно делится на предложения по паузам. См.
[раздел про LLM](kotlin/transcribers/README.md#пунктуация-и-форматирование-локальной-llm).

## Что внутри

| Файл | Назначение |
|---|---|
| `scripts/download.py` | скачивание моделей и датасета FLEURS ru |
| `scripts/download_parakeet.py` | Parakeet TDT 0.6B v3 (докачка) |
| `scripts/download_whisper_attention.py` | Whisper-декодеры с attention (точные тайминги слов) |
| `scripts/download_llm.py` | Qwen2.5 GGUF для локальной пунктуации (0.5B/1.5B/3B, SHA-256) |
| `scripts/eval.py` | WER/CER + bootstrap CI на FLEURS ru test (200 фраз) |
| `scripts/eval_golos.py` | WER/CER на Golos 10h crowd (200 фраз) |
| `scripts/speed.py` | RTF и RAM на 10 сегментах по ~20 с (каждый в своём процессе) |
| `scripts/make_report.py` | таблица + график `results/pareto.png` |
| `scripts/analyze.py` | разбор худших/лучших примеров |
| `kotlin/transcribers/` | Kotlin-классы с таймингами слов |
| `kotlin/transcribers/src/test/` | 71 JVM unit-тест (JUnit 4) |
| `kotlin/transcribers/src/androidTest/` | instrumentation-тест `AudioDecoder` (WAV/MP3/MP4) |
| `kotlin/transcribers/run_unit_tests.ps1` | запуск unit-тестов без Gradle |
| `llm/*.gguf` | скачанные Qwen2.5 для пунктуации (в git не попадают) |
| `results/*.json` | сырые метрики (WER, CER, CI, тайминги, гипотезы) |

## Метрики: что именно считается

| Метрика | Формула / правило | Что показывает | Как читать |
|---|---|---|---|
| **WER** | `(S + D + I) / N` | Доля ошибок в словах: замены + удаления + вставки, делённые на число слов эталона | 0% — идеально; 10% — одна ошибка на 10 слов; может быть >100% из-за вставок |
| **S (substitutions)** | слово эталона заменено другим | Неверно распознанное слово | Основная ошибка на шумной речи |
| **D (deletions)** | слово эталона пропущено | Потерянное слово | Растёт на тихой/быстрой речи |
| **I (insertions)** | добавлено слово, которого нет в эталоне | Лишнее слово | Растёт на галлюцинациях/повторах Whisper |
| **CER** | то же на уровне символов | Устойчивее к морфологии русского | Полезен, когда важно «похожесть», а не точное слово |
| **95% CI** | bootstrap по фразам, 2000 ресэмплов | Доверительный интервал WER | Если интервалы моделей пересекаются, разница может быть случайной |
| **RTF** | `processing_seconds / audio_seconds` | Скорость относительно реального времени | `0.043` = 1 минута аудио за 2.6 с; `<1` — быстрее realtime |
| **RAM** | пиковое RSS процесса | Память на устройстве | 337 МБ — комфортно, 2.3 ГБ — риск OOM на бюджете |
| **Размер** | сумма `.onnx` + токенов | Место в APK/на диске | Влияет на скачивание и Play Store |

### Пример чтения WER

Если в эталоне 100 слов, 5 заменены, 2 пропущены и 1 добавлено:
`WER = (5 + 2 + 1) / 100 = 8%`. При этом `CER` может быть заметно ниже,
если ошибки — в окончаниях («масква» вместо «москва»).

Нормализация перед подсчётом: lower-case, `ё→е`, удаление пунктуации и
дефисов, числа в слова (`num2words`), схлопывание пробелов. Поэтому WER
здесь — про качество распознавания, а не про пунктуацию; пунктуация
оценивается отдельно и не входит в WER.

## Воспроизведение

```bash
pip install sherpa-onnx jiwer soundfile numpy num2words pyarrow psutil matplotlib
python scripts/download.py
python scripts/download_parakeet.py
python scripts/download_whisper_attention.py   # опционально: точные тайминги Whisper
python scripts/download_llm.py --model qwen2.5-1.5b  # опционально: локальная пунктуация
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
