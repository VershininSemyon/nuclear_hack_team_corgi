# transcription_models — локальные ASR-модели для Android/Kotlin

Воспроизводимый бенчмарк русскоязычных моделей распознавания речи, которые
запускаются на устройстве (без облака) через один и тот же рантайм —
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) с Kotlin API.

Итоговый лидерборд и рекомендации: **[../transcription_leaderboard.md](../transcription_leaderboard.md)**.

## Что внутри

| Файл | Назначение |
|---|---|
| `scripts/download.py` | скачивание моделей и датасета FLEURS ru |
| `scripts/download_parakeet.py` | Parakeet TDT 0.6B v3 (докачка) |
| `scripts/eval.py` | WER/CER + скорость на FLEURS ru test (200 фраз) |
| `scripts/eval_golos.py` | WER/CER на Golos 10h crowd (200 фраз, спонтанная речь) |
| `scripts/speed.py` | RTF и RAM на 10 сегментах по ~20 с (каждый в своём процессе) |
| `scripts/make_report.py` | таблица + график `results/pareto.png` |
| `scripts/analyze.py` | разбор худших/лучших примеров |
| `kotlin/SherpaTranscriber.kt` | готовая обёртка для Android |
| `results/*.json` | сырые метрики (WER, CER, CI, тайминги, гипотезы) |

## Воспроизведение

```bash
pip install sherpa-onnx jiwer soundfile numpy num2words pyarrow psutil matplotlib
python scripts/download.py
python scripts/download_parakeet.py
python scripts/eval.py            # N_UTTS=200 THREADS=4
python scripts/eval_golos.py
python scripts/speed.py
python scripts/make_report.py
```

Модели и аудио не коммитятся (см. `.gitignore`) — они скачиваются скриптами.

## Условия замера

- sherpa-onnx 1.13.8 (Python), CPU, int8-веса, `num_threads=4`.
- AMD Ryzen 5 5500 (6C/12T), Windows 11.
- Аудио: 16 кГц моно.
- Нормализация текста: lower-case, `ё→е`, пунктуация/дефисы удалены,
  числа переведены в слова (`num2words`), лишние пробелы схлопнуты.
  Это уравнивает модели с ITN и без него.
- WER/CER — [jiwer](https://github.com/jitsi/jiwer), 95% доверительный интервал —
  bootstrap по фразам (2000 ресэмплов).

### Датасеты

- **FLEURS ru_ru test** — 200 фраз (2317 с), чтение вслух, чистый звук,
  88 уникальных предложений в выборке (FLEURS даёт несколько дикторов на фразу).
- **Golos 10h crowd** — 200 фраз (~13 мин), короткие спонтанные команды,
  шум/телефонный звук, разметка без пунктуации.

### Ограничения

- Замер скорости сделан на настольном CPU при фоновой нагрузке на машину,
  абсолютные RTF завышены; сравнение между моделями корректно, но на телефоне
  цифры будут другими. Для точного замера нужен прогон на устройстве.
- FLEURS — чтение, Golos — короткие команды. Совещания/лекции с длинными
  сегментами и спонтанной речью будут сложнее для всех моделей.
