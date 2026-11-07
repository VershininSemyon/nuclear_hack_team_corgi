# Протокол оценки

Документ описывает, **как** получены цифры в лидербордах, чтобы их можно было
воспроизвести и корректно сравнивать. Ограничения — в
[`LIMITATIONS.md`](LIMITATIONS.md), сводка результатов — в
[`TEST_RESULTS.md`](TEST_RESULTS.md).

## 1. Среда

| Параметр | Значение |
|---|---|
| CPU | AMD Ryzen 5 5500, 6C/12T |
| GPU | нет |
| ОС | Windows 11 |
| Python | 3.11.3 |
| sherpa-onnx | 1.13.8 |
| onnxruntime | 1.31.0 |
| scikit-learn | 1.9.1 |
| scipy | 1.17.1 |
| Java | Android Studio JBR (JDK 17) |
| Android SDK | compileSdk 34 |

Все замеры — CPU-only, без GPU и без облака.

## 2. Датасеты

| Датасет | Сплиты | Файлы | Язык | Источник / лицензия |
|---|---|---|---:|---|
| AMI IHM | validation, test | 6 + 10 | англ. | `diarizers-community/ami`, CC BY 4.0 |
| VoxConverse | dev, test | 8 + 10 | англ. | `diarizers-community/voxconverse`, CC BY 4.0 |
| RU synthetic | dev, test | 32 + 48 | рус. | `ivkond/synthetic-speech-diarization-ru`, MIT |
| Dialogs | test (собранные диалоги) | 5 | рус. | `langswap/dialogs-ru-emotional-conversations`, OpenRAIL |

### Выборка

- **Screen (dev):** AMI validation 01–02, Vox dev 001, RU dev 001_spk1–4
  (по одному файлу на K=1..4). Все 12 моделей.
- **Final (test):** AMI test 01, Vox test 001, RU test 001_spk1–4 и
  002_spk1–4 (2 файла на K). 4 модели.
- **Dialogs:** 5 диалогов, собранных из test-сплита; 2 спикера в каждом.
- Файлы выбираются детерминированно: сортировка по имени, затем первые N;
  для RU — по одному/двум файлам на каждое истинное K.

### Подготовка аудио

- Всё приводится к **16 кГц, моно, PCM**.
- AMI/Vox: без обрезки эталона.
- RU synthetic: эталон обрезается по энергии (тишина внутри реплик удаляется),
  потому что TTS-разметка помечает всю реплику одним сегментом, включая
  длинные паузы. Это описано в лидерборде.
- Dialogs: отдельные реплики склеиваются с паузой 0.45 с; границы и спикеры
  сохраняются в `marking.json`.

## 3. Диаризация

### Пайплайн

1. pyannote segmentation 3.0 (int8, 10-секундные окна, powerset 7 классов).
2. Чистые окна речи: purity ≥ 0.7, речи ≥ 50%; масштабы 2 с/1 с и/или 3 с/1.5 с.
3. Speaker embedding: L2-нормированный вектор на окно.
4. Complete-linkage AHC по косинусу.
5. K: oracle (эталон) или auto-K:
   - gap-статистика на окнах 2 с/1 с (offline);
   - перебор порогов 0.4–0.8 (Android).
6. Покадровое декодирование: голосование окон, top-K спикеров на кадр,
   склейка пауз (`min_duration_on=0.3`, `min_duration_off=0.5`).

### DER

```
DER = (confusion + miss + false_alarm) / ref_speech
```

- collar = **0.25 с** по обе стороны границы;
- UEM = весь файл `0..duration`;
- перекрытия **включены** (`skip_overlap=False`);
- `ref_speech` — суммарная длительность эталонной речи после trim (для RU).
- На датасете считается **micro-DER**: ошибки взвешиваются по длительности
  эталонной речи, а не усредняются по файлам.
- В финальной таблице DER = среднее трёх micro-DER (AMI, Vox, RU).

### Число спикеров

- **K exact** — доля файлов, где `hyp_K == ref_K`.
- **K MAE** — `mean(abs(hyp_K - ref_K))`.
- **K bias** — `mean(hyp_K - ref_K)`; положительный = завышаем K.
- В финальной таблице K exact/MAE взвешены по числу файлов, а не по датасетам.

### Word accuracy

- Каждому слову ASR назначается спикер по максимальному перекрытию; слово в
  паузе — к ближайшему сегменту.
- Эталонный спикер слова берётся, если он покрывает **≥ 50%** длительности
  слова; иначе слово не оценивается.
- Кластеры сопоставляются с эталонными спикерами через Hungarian assignment,
  максимизирующий число правильных слов.
- `coverage` — доля оценённых слов от всех слов.

### RTF

- `RTF = processing_seconds / audio_seconds`.
- **Cold**: `--no-cache`, без кэша сегментации/эмбеддингов.
- Финальные значения: Vox test 3 с/1.5 с + auto-K 2 с/1 с; RU test 2 с/1 с.
- Меньше = быстрее; `<1` — быстрее реального времени.

## 4. ASR

- Модели: GigaAM v3, Zipformer-RU, Parakeet TDT 0.6B v3, Whisper turbo/small.
- FLEURS ru_ru test — 200 фраз (2317 с).
- Golos 10h crowd — 200 коротких спонтанных фраз.
- Нормализация: lower-case, `ё→е`, удаление пунктуации/дефисов, числа в слова
  (`num2words`), схлопывание пробелов.
- **WER** = `(S + D + I) / N`; **CER** — на символах.
- 95% CI — bootstrap по фразам, 2000 ресэмплов.
- RTF и RAM — на 10 сегментах ~20 с, каждый в отдельном процессе.

## 5. Режимы диаризации

| Режим | K | Кластеризация |
|---|---|---|
| `auto` | gap-статистика | complete-linkage AHC |
| `oracle` | эталонное K | complete-linkage AHC |
| `sherpa_auto` | порог 0.5 | `OfflineSpeakerDiarization` |
| `sherpa_oracle` | эталонное K | `OfflineSpeakerDiarization` |
| `sherpa_sweep` | плато порогов 0.4–0.8 | `OfflineSpeakerDiarization` |

`auto`/`oracle` — собственный пайплайн; `sherpa_*` — baseline sherpa-onnx.
`sherpa_sweep` повторяет Kotlin `SpeakerCountSweep`.

## 6. Детерминизм и статистика

- Gap-статистика: `seed=0`, `bootstrap=10`, `k_max=12`.
- AHC — deterministic complete-linkage.
- Выбор файлов — сортировка + фиксированные N, без random.
- Для DER/K не строились доверительные интервалы: выборка test мала
  (1 AMI + 1 Vox + 8 RU), поэтому цифры — ориентир, а не окончательная
  статистика. Это отмечено в `LIMITATIONS.md`.

## 7. Воспроизведение

```bash
# Модели и данные
python diarization/scripts/download.py
python diarization/scripts/download_data.py

# Screen
python diarization/scripts/benchmark.py --stage screen --models all \
  --modes oracle --scales 2.0:1.0 --per-dataset 2 --ru-per-k 1

# Final test: AMI/Vox на 3 с, RU на 2 с
python diarization/scripts/benchmark.py --stage test \
  --models campplus_zh_en,eres2net_en,titanet_small,eres2netv2_zh \
  --modes auto,oracle,sherpa_auto,sherpa_oracle \
  --scales 3.0:1.5 --auto-k-scales 2.0:1.0 \
  --datasets ami_test,vox_test --per-dataset 1

python diarization/scripts/benchmark.py --stage test \
  --models campplus_zh_en,eres2net_en,titanet_small,eres2netv2_zh \
  --modes auto,oracle,sherpa_auto,sherpa_oracle \
  --scales 2.0:1.0 --datasets ru_test --ru-per-k 2

# Русский Dialogs, полный пайплайн
python diarization/scripts/prepare_dialogs_ru.py --dialogues 6
GIGAAM_MODEL_DIR=... python diarization/scripts/full_pipeline_eval.py \
  --dataset dialogs_ru --model campplus_zh_en --scales 2.0:1.0

# Отчёт
python diarization/scripts/report.py
```

## 8. Где лежат результаты

- `diarization/results/raw/*.jsonl` — по строке на файл/модель/режим;
- `diarization/results/leaderboard.csv/json` — агрегаты;
- `diarization/leaderboard.md` — таблицы и графики;
- `diarization/results/transcripts/*.json` — примеры полного JSON.
