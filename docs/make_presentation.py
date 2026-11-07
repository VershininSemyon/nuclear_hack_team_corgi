"""Generate the hackathon defense presentation.

Run from the repository root:
    python docs/make_presentation.py
"""
from __future__ import annotations

import os
from pathlib import Path

import qrcode
from PIL import Image
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Emu, Inches, Pt

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "hackathon_defense.pptx"

NAVY = RGBColor(0x0B, 0x1F, 0x3A)
NAVY_2 = RGBColor(0x13, 0x2E, 0x55)
BLUE = RGBColor(0x0B, 0x63, 0xD6)
CYAN = RGBColor(0x00, 0xB8, 0xD9)
RED = RGBColor(0xD9, 0x2D, 0x20)
GREEN = RGBColor(0x1E, 0x8E, 0x3E)
AMBER = RGBColor(0xB4, 0x6A, 0x00)
GRAY = RGBColor(0x5B, 0x67, 0x70)
LIGHT = RGBColor(0xF4, 0xF7, 0xFB)
LIGHT_2 = RGBColor(0xE8, 0xF1, 0xFB)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
BORDER = RGBColor(0xD8, 0xE0, 0xEA)

FONT = "Segoe UI"
FONT_BOLD = "Segoe UI Semibold"

# Единый коэффициент: весь текст в колоде чуть крупнее.
SCALE = 1.16


def set_bg(slide, color: RGBColor) -> None:
    fill = slide.background.fill
    fill.solid()
    fill.fore_color.rgb = color


def text(
    slide,
    value: str,
    left: float,
    top: float,
    width: float,
    height: float,
    size: float = 18,
    bold: bool = False,
    color: RGBColor = NAVY,
    align=PP_ALIGN.LEFT,
    font: str = FONT,
    valign=MSO_ANCHOR.TOP,
    margin: float = 0.04,
):
    size = float(size) * SCALE
    box = slide.shapes.add_textbox(
        Inches(left), Inches(top), Inches(width), Inches(height)
    )
    tf = box.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.vertical_anchor = valign
    tf.margin_left = Inches(margin)
    tf.margin_right = Inches(margin)
    tf.margin_top = Inches(margin)
    tf.margin_bottom = Inches(margin)
    p = tf.paragraphs[0]
    p.alignment = align
    for i, line in enumerate(str(value).split("\n")):
        paragraph = p if i == 0 else tf.add_paragraph()
        paragraph.alignment = align
        paragraph.space_after = Pt(0)
        run = paragraph.add_run()
        run.text = line
        run.font.size = Pt(size)
        run.font.bold = bold
        run.font.name = font
        run.font.color.rgb = color
    return box


def bullets(
    slide,
    items: list[str],
    left: float,
    top: float,
    width: float,
    height: float,
    size: float = 16,
    color: RGBColor = NAVY,
    gap: float = 6,
    bullet_char: str = "•",
):
    size = float(size) * SCALE
    box = slide.shapes.add_textbox(
        Inches(left), Inches(top), Inches(width), Inches(height)
    )
    tf = box.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.margin_left = Inches(0.04)
    tf.margin_right = Inches(0.04)
    for i, item in enumerate(items):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.text = f"{bullet_char}  {item}"
        p.font.size = Pt(size)
        p.font.name = FONT
        p.font.color.rgb = color
        p.space_after = Pt(gap)
    return box


def card(
    slide,
    left: float,
    top: float,
    width: float,
    height: float,
    fill: RGBColor = LIGHT,
    line: RGBColor | None = BORDER,
    radius: float = 0.10,
    shape=MSO_SHAPE.ROUNDED_RECTANGLE,
):
    shp = slide.shapes.add_shape(
        shape, Inches(left), Inches(top), Inches(width), Inches(height)
    )
    shp.fill.solid()
    shp.fill.fore_color.rgb = fill
    if line is None:
        shp.line.fill.background()
    else:
        shp.line.color.rgb = line
        shp.line.width = Pt(1)
    if shape == MSO_SHAPE.ROUNDED_RECTANGLE:
        try:
            shp.adjustments[0] = radius
        except Exception:
            pass
    return shp


def card_text(
    slide,
    title: str,
    subtitle: str,
    left: float,
    top: float,
    width: float,
    height: float,
    fill: RGBColor = LIGHT,
    title_color: RGBColor = NAVY,
    subtitle_color: RGBColor = GRAY,
    title_size: float = 16,
    subtitle_size: float = 12,
    line: RGBColor | None = BORDER,
):
    card(slide, left, top, width, height, fill=fill, line=line)
    text(
        slide,
        title,
        left + 0.18,
        top + 0.16,
        width - 0.36,
        0.55,
        size=title_size,
        bold=True,
        color=title_color,
        font=FONT_BOLD,
    )
    text(
        slide,
        subtitle,
        left + 0.18,
        top + 0.70,
        width - 0.36,
        height - 0.78,
        size=subtitle_size,
        color=subtitle_color,
    )


def title_slide(slide, title: str, kicker: str | None = None):
    # Левая акцентная полоса из трёх цветов.
    for i, color in enumerate([CYAN, BLUE, RGBColor(0x6A, 0x3D, 0xC7)]):
        bar = slide.shapes.add_shape(
            MSO_SHAPE.RECTANGLE,
            Inches(0),
            Inches(0.0 + i * 2.5),
            Inches(0.12),
            Inches(2.5),
        )
        bar.fill.solid()
        bar.fill.fore_color.rgb = color
        bar.line.fill.background()

    # Декоративное кольцо в правом верхнем углу.
    ring = slide.shapes.add_shape(
        MSO_SHAPE.OVAL, Inches(11.55), Inches(-1.05), Inches(2.7), Inches(2.7)
    )
    ring.fill.background()
    ring.line.color.rgb = LIGHT_2
    ring.line.width = Pt(12)

    if kicker:
        text(
            slide,
            kicker.upper(),
            0.55,
            0.40,
            8.0,
            0.30,
            11.5,
            True,
            CYAN,
            font=FONT_BOLD,
        )

    text(slide, title, 0.55, 0.78, 11.9, 0.90, 34, True, NAVY, font=FONT_BOLD)

    line = slide.shapes.add_shape(
        MSO_SHAPE.RECTANGLE, Inches(0.55), Inches(1.68), Inches(1.15), Inches(0.06)
    )
    line.fill.solid()
    line.fill.fore_color.rgb = CYAN
    line.line.fill.background()


def footer(slide, n: int):
    text(slide, "On-device ASR + diarization", 0.55, 7.10, 4.0, 0.24, 9.5, False, GRAY)
    text(slide, f"{n:02d}", 12.15, 7.08, 0.6, 0.25, 9.5, True, GRAY, PP_ALIGN.RIGHT, FONT_BOLD)


def add_table(slide, data, left, top, width, height, font_size=13.0, col_widths=None):
    font_size = float(font_size) * SCALE
    rows, cols = len(data), len(data[0])
    table_shape = slide.shapes.add_table(rows, cols, Inches(left), Inches(top), Inches(width), Inches(height))
    table = table_shape.table
    if col_widths:
        total = sum(col_widths)
        for i, w in enumerate(col_widths):
            table.columns[i].width = Emu(int(Inches(width) * w / total))
    for r in range(rows):
        for c in range(cols):
            cell = table.cell(r, c)
            cell.text = str(data[r][c])
            cell.margin_left = Inches(0.07)
            cell.margin_right = Inches(0.07)
            cell.margin_top = Inches(0.03)
            cell.margin_bottom = Inches(0.03)
            for p in cell.text_frame.paragraphs:
                p.font.size = Pt(font_size)
                p.font.name = FONT
                p.font.bold = r == 0
                p.font.color.rgb = WHITE if r == 0 else NAVY
            cell.fill.solid()
            cell.fill.fore_color.rgb = NAVY if r == 0 else (LIGHT if r % 2 == 0 else WHITE)
    return table


def arrow(slide, left, top, width=0.35, height=0.30, color=CYAN):
    shp = slide.shapes.add_shape(
        MSO_SHAPE.RIGHT_ARROW, Inches(left), Inches(top), Inches(width), Inches(height)
    )
    shp.fill.solid()
    shp.fill.fore_color.rgb = color
    shp.line.fill.background()
    return shp


def qr_code(path: Path, url: str):
    img = qrcode.make(url)
    img.save(path)


def add_notes(slide, value: str):
    slide.notes_slide.notes_text_frame.text = value


# ---------------------------------------------------------------------------
prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)

qr_path = ROOT / "docs" / "repo_qr.png"
qr_code(qr_path, "https://github.com/VershininSemyon/nuclear_hack_team_corgi")

# 1. Title -----------------------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, NAVY)
text(slide, "ХАКАТОН · ON-DEVICE AI", 0.72, 0.62, 7.0, 0.3, 12, True, CYAN, font=FONT_BOLD)
text(slide, "Локальная расшифровка встреч", 0.72, 1.12, 8.9, 1.45, 30, True, WHITE, font=FONT_BOLD)
text(
    slide,
    "ASR + диаризация + локальная LLM\nна Android, без облака",
    0.72,
    2.78,
    7.5,
    1.05,
    20,
    False,
    RGBColor(0xC9, 0xD7, 0xE8),
)
chips = ["100% on-device", "GigaAM v3", "Qwen2.5", "JSON"]
for i, chip in enumerate(chips):
    left = 0.72 + i * 1.78
    card(slide, left, 4.05, 1.62, 0.44, fill=NAVY_2, line=CYAN, radius=0.25)
    text(slide, chip, left, 4.13, 1.62, 0.28, 10.5, True, WHITE, PP_ALIGN.CENTER)

# phone mockup
phone = card(slide, 9.55, 1.05, 2.85, 5.25, fill=RGBColor(0x11, 0x27, 0x46), line=CYAN, radius=0.12)
card(slide, 9.78, 1.28, 2.39, 4.78, fill=RGBColor(0x08, 0x16, 0x29), line=RGBColor(0x2A, 0x45, 0x6A), radius=0.08)
text(slide, "встреча.mp3", 9.95, 1.48, 2.05, 0.28, 10, True, WHITE)
text(slide, "00:12:34", 9.95, 1.73, 2.05, 0.22, 8.5, False, RGBColor(0x8F, 0xA8, 0xC4))
for i in range(11):
    h = 0.18 + (i % 4) * 0.12
    bar = card(slide, 9.98 + i * 0.195, 2.35 - h / 2, 0.10, h, fill=CYAN if i % 3 else WHITE, line=None, radius=0.04)
for i, (label, color) in enumerate([("speaker_1", CYAN), ("speaker_2", RGBColor(0xFB, 0xBC, 0x04))]):
    card(slide, 9.98, 3.05 + i * 0.72, 1.95, 0.50, fill=RGBColor(0x15, 0x2C, 0x4C), line=color, radius=0.12)
    text(slide, f"●  {label}", 10.12, 3.15 + i * 0.72, 1.65, 0.25, 9.5, True, color)
text(slide, "JSON готов", 9.98, 4.75, 1.95, 0.28, 10, True, GREEN)

text(slide, "Команда Corgi · 2026", 0.72, 6.82, 5.0, 0.3, 10.5, False, RGBColor(0x8F, 0xA8, 0xC4))
add_notes(slide, "20 секунд: локальная расшифровка встреч на Android. Всё считается на устройстве: ASR, диаризация и локальная LLM.")

# 2. Problem -> solution ---------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, WHITE)
title_slide(slide, "Проблема → решение", "Зачем это нужно")
for i, (t, s) in enumerate(
    [
        ("Данные в облаке", "Записи встреч уходят на чужие серверы"),
        ("Ручные заметки", "Пока слушаешь — теряешь нить разговора"),
        ("Непонятно, кто говорит", "Текст есть, а спикеры — нет"),
    ]
):
    card_text(slide, t, s, 0.65 + i * 4.12, 2.05, 3.82, 1.55, fill=LIGHT, title_size=17, subtitle_size=12)
card(slide, 0.65, 4.15, 12.0, 1.08, fill=LIGHT_2, line=CYAN, radius=0.12)
text(slide, "Локальный пайплайн", 0.95, 4.32, 2.25, 0.35, 16, True, NAVY, font=FONT_BOLD)
steps = ["аудио", "текст", "спикеры", "JSON"]
for i, step in enumerate(steps):
    left = 3.35 + i * 2.15
    card(slide, left, 4.42, 1.55, 0.48, fill=NAVY, line=NAVY, radius=0.18)
    text(slide, step, left, 4.50, 1.55, 0.28, 11, True, WHITE, PP_ALIGN.CENTER)
    if i < len(steps) - 1:
        arrow(slide, left + 1.60, 4.53, 0.35, 0.25)
text(slide, "Без API-ключей. После загрузки моделей сеть не нужна.", 0.98, 5.45, 11.3, 0.35, 13, False, GRAY)
footer(slide, 2)
add_notes(slide, "25 секунд: три боли — приватность, ручные заметки и отсутствие спикеров. Решение — локальный пайплайн до JSON.")

# 3. Requirements ----------------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, WHITE)
title_slide(slide, "Обязательные требования закрыты", "Чек-лист хакатона")
items = [
    ("Android OS", "Compose + Room, minSdk 24"),
    ("Локальная LLM", "Qwen2.5 1.5B GGUF, llama.cpp"),
    ("Импорт аудио", "WAV / MP3 / MP4 / M4A"),
    ("Речь → текст", "GigaAM v3, WER 2.44%"),
    ("Диаризация", "pyannote + CAM++, auto-K"),
    ("Тайминги", "start_ms / end_ms"),
    ("Отображение", "редактор и спикеры"),
    ("Экспорт", "JSON {\"transcript\":[...]}"),
]
for i, (t, s) in enumerate(items):
    col = i % 2
    row = i // 2
    left = 0.65 + col * 6.2
    top = 2.05 + row * 1.10
    card(slide, left, top, 5.9, 0.88, fill=LIGHT, line=BORDER, radius=0.10)
    circle = card(slide, left + 0.15, top + 0.17, 0.52, 0.52, fill=GREEN, line=None, radius=0.50)
    text(slide, "✓", left + 0.15, top + 0.18, 0.52, 0.45, 18, True, WHITE, PP_ALIGN.CENTER, FONT_BOLD)
    text(slide, t, left + 0.82, top + 0.10, 2.0, 0.3, 15, True, NAVY, font=FONT_BOLD)
    text(slide, s, left + 0.82, top + 0.43, 4.7, 0.3, 10.5, False, GRAY)
text(slide, "Демо: файл из памяти → расшифровка → редактор спикеров → JSON", 0.65, 6.55, 12.0, 0.3, 13, True, BLUE)
footer(slide, 3)
add_notes(slide, "20 секунд: все восемь обязательных пунктов закрыты. Ключевое — локальная LLM и полный офлайн-пайплайн.")

# 4. Architecture ----------------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, WHITE)
title_slide(slide, "Архитектура решения", "Один сквозной пайплайн")
# input
card(slide, 0.65, 2.55, 1.55, 0.75, fill=NAVY, line=NAVY, radius=0.12)
text(slide, "Аудио", 0.65, 2.68, 1.55, 0.3, 14, True, WHITE, PP_ALIGN.CENTER)
text(slide, "файл / запись", 0.65, 2.98, 1.55, 0.2, 8.5, False, RGBColor(0xC9, 0xD7, 0xE8), PP_ALIGN.CENTER)
arrow(slide, 2.28, 2.78, 0.32, 0.28)
card(slide, 2.65, 2.55, 1.65, 0.75, fill=LIGHT_2, line=BLUE, radius=0.12)
text(slide, "AudioDecoder", 2.65, 2.67, 1.65, 0.3, 11, True, NAVY, PP_ALIGN.CENTER, FONT_BOLD)
text(slide, "16 кГц, моно", 2.65, 2.97, 1.65, 0.2, 8.5, False, GRAY, PP_ALIGN.CENTER)
# fork arrows
arrow(slide, 4.38, 2.20, 0.30, 0.25)
arrow(slide, 4.38, 3.32, 0.30, 0.25)
# ASR and diarization
card(slide, 4.78, 1.72, 2.25, 0.82, fill=RGBColor(0xE8, 0xF4, 0xEC), line=GREEN, radius=0.12)
text(slide, "GigaAM v3", 4.78, 1.84, 2.25, 0.3, 12, True, GREEN, PP_ALIGN.CENTER, FONT_BOLD)
text(slide, "ASR + тайминги слов", 4.78, 2.16, 2.25, 0.2, 8.5, False, GRAY, PP_ALIGN.CENTER)
card(slide, 4.78, 3.05, 2.25, 0.82, fill=RGBColor(0xFB, 0xEE, 0xE6), line=RGBColor(0xE6, 0x7E, 0x22), radius=0.12)
text(slide, "pyannote + CAM++", 4.78, 3.17, 2.25, 0.3, 11, True, RGBColor(0xB4, 0x5F, 0x06), PP_ALIGN.CENTER, FONT_BOLD)
text(slide, "диаризация, auto-K", 4.78, 3.49, 2.25, 0.2, 8.5, False, GRAY, PP_ALIGN.CENTER)
# converge
arrow(slide, 7.12, 2.20, 0.28, 0.25)
arrow(slide, 7.12, 3.32, 0.28, 0.25)
card(slide, 7.50, 2.42, 2.15, 0.86, fill=LIGHT_2, line=BLUE, radius=0.12)
text(slide, "WordSpeakerAssigner", 7.50, 2.54, 2.15, 0.3, 10, True, NAVY, PP_ALIGN.CENTER, FONT_BOLD)
text(slide, "слово → спикер", 7.50, 2.86, 2.15, 0.2, 8.5, False, GRAY, PP_ALIGN.CENTER)
arrow(slide, 9.78, 2.72, 0.30, 0.26)
card(slide, 10.15, 2.42, 1.55, 0.86, fill=LIGHT_2, line=BLUE, radius=0.12)
text(slide, "UtteranceBuilder", 10.15, 2.54, 1.55, 0.3, 9.5, True, NAVY, PP_ALIGN.CENTER, FONT_BOLD)
text(slide, "реплики", 10.15, 2.86, 1.55, 0.2, 8.5, False, GRAY, PP_ALIGN.CENTER)
arrow(slide, 11.78, 2.72, 0.28, 0.26)
card(slide, 12.02, 2.30, 1.10, 1.05, fill=RGBColor(0xED, 0xE7, 0xFF), line=RGBColor(0x6A, 0x3D, 0xC7), radius=0.12)
text(slide, "Qwen\n2.5", 12.02, 2.44, 1.10, 0.5, 9.5, True, RGBColor(0x4A, 0x2A, 0x8A), PP_ALIGN.CENTER, FONT_BOLD)
text(slide, "JSON", 12.02, 3.00, 1.10, 0.2, 8.5, True, RGBColor(0x4A, 0x2A, 0x8A), PP_ALIGN.CENTER)
# bottom notes
card(slide, 0.65, 4.55, 12.0, 1.55, fill=LIGHT, line=BORDER, radius=0.10)
text(slide, "Ключевые решения", 0.90, 4.72, 3.0, 0.3, 13, True, NAVY, font=FONT_BOLD)
bullets(
    slide,
    [
        "ASR и диаризация независимы: можно менять модель, не трогая остальное.",
        "Слова → реплики: смена спикера, пауза > 1.2 с или длина > 15 с.",
        "LLM правит каждую реплику отдельно; при ошибке остаётся сырой ASR-текст.",
    ],
    0.90,
    5.05,
    11.4,
    0.95,
    11.5,
    GRAY,
    3,
)
footer(slide, 4)
add_notes(slide, "40 секунд: слева аудио, две ветки — ASR и диаризация, затем назначение слов, склейка реплик, LLM и JSON.")

# 5. ASR -------------------------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, WHITE)
title_slide(slide, "ASR: качество vs скорость", "Транскрипция")
add_table(
    slide,
    [
        ["Модель", "WER", "RTF", "Размер"],
        ["GigaAM v3", "2.44%", "0.043", "229 МБ"],
        ["Zipformer-RU", "3.55%", "0.019", "73 МБ"],
        ["Parakeet TDT", "7.39%", "0.064", "670 МБ"],
        ["Whisper turbo", "18.18%", "0.382", "1037 МБ"],
    ],
    0.65,
    2.05,
    4.55,
    2.55,
    11.5,
    [1.5, 0.9, 0.8, 0.9],
)
card(slide, 0.65, 4.90, 4.55, 1.45, fill=LIGHT_2, line=CYAN, radius=0.10)
text(slide, "GigaAM v3", 0.85, 5.05, 2.0, 0.3, 14, True, NAVY, font=FONT_BOLD)
text(slide, "2.44% WER · RTF 0.043 · 337 МБ RAM", 0.85, 5.40, 4.1, 0.3, 11, False, GRAY)
text(slide, "Zipformer — самый быстрый (RTF 0.019)", 0.85, 5.75, 4.1, 0.3, 10.5, False, GRAY)
asr_img = Image.open(ROOT / "transcription" / "results" / "pareto.png")
max_w = 6.9
w = max_w
h = w * asr_img.height / asr_img.width
slide.shapes.add_picture(
    str(ROOT / "transcription" / "results" / "pareto.png"),
    Inches(5.35),
    Inches(2.05 + max(0, (4.3 - h) / 2)),
    width=Inches(w),
    height=Inches(h),
)
footer(slide, 5)
add_notes(slide, "35 секунд: GigaAM даёт лучшее качество на русском при RTF 0.043; Zipformer — для слабых устройств.")

# 6. Diarization -----------------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, WHITE)
title_slide(slide, "Диаризация: 12 моделей, auto-K", "Кто когда говорил")
add_table(
    slide,
    [
        ["Модель", "DER auto", "DER oracle", "RTF"],
        ["CAM++ zh/en", "33.20%", "27.69%", "0.094"],
        ["ERes2Net en", "33.43%", "27.28%", "0.202"],
        ["TitaNet small", "35.22%", "29.61%", "0.084"],
        ["ERes2NetV2", "40.67%", "35.24%", "0.501"],
    ],
    0.65,
    2.05,
    4.55,
    2.55,
    11.5,
    [1.45, 0.9, 0.95, 0.75],
)
card(slide, 0.65, 4.90, 4.55, 1.45, fill=LIGHT_2, line=CYAN, radius=0.10)
text(slide, "CAM++ zh/en", 0.85, 5.05, 2.0, 0.3, 14, True, NAVY, font=FONT_BOLD)
text(slide, "лучший auto-K DER 33.20% · RTF 0.094", 0.85, 5.40, 4.1, 0.3, 11, False, GRAY)
text(slide, "ERes2Net en — лучший oracle: 27.28%", 0.85, 5.75, 4.1, 0.3, 10.5, False, GRAY)
d_img = Image.open(ROOT / "diarization" / "results" / "pareto_diarization.png")
w = 7.2
h = w * d_img.height / d_img.width
slide.shapes.add_picture(
    str(ROOT / "diarization" / "results" / "pareto_diarization.png"),
    Inches(5.55),
    Inches(2.05 + max(0, (4.3 - h) / 2)),
    width=Inches(w),
    height=Inches(h),
)
footer(slide, 6)
add_notes(slide, "35 секунд: 12 моделей, на Android выбрали CAM++; ERes2Net en чуть лучше при oracle K, но медленнее.")

# 7. Diarization pipeline --------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, WHITE)
title_slide(slide, "Как работает диаризация", "Полный пайплайн")
steps = [
    ("1", "pyannote 3.0", "10-с окна,\n7 классов"),
    ("2", "Чистые окна", "2 с / 1 с\n3 с / 1.5 с"),
    ("3", "Эмбеддинги", "L2, 12\nмоделей"),
    ("4", "AHC", "complete\nlinkage"),
    ("5", "Auto-K", "gap / sweep\n+ decode"),
]
for i, (num, head, sub) in enumerate(steps):
    left = 0.65 + i * 2.48
    card(slide, left, 2.05, 2.20, 1.55, fill=LIGHT, line=BORDER, radius=0.10)
    circle = card(slide, left + 0.14, 2.18, 0.38, 0.38, fill=CYAN, line=None, radius=0.50)
    text(slide, num, left + 0.14, 2.22, 0.38, 0.26, 10, True, WHITE, PP_ALIGN.CENTER, FONT_BOLD)
    text(slide, head, left + 0.14, 2.67, 1.92, 0.3, 12.5, True, NAVY, font=FONT_BOLD)
    text(slide, sub, left + 0.14, 3.02, 1.92, 0.45, 9.5, False, GRAY)
    if i < len(steps) - 1:
        arrow(slide, left + 2.22, 2.68, 0.22, 0.24)
card(slide, 0.65, 4.05, 5.85, 1.95, fill=LIGHT_2, line=BLUE, radius=0.10)
text(slide, "Gap-статистика (offline)", 0.88, 4.22, 4.0, 0.3, 13, True, NAVY, font=FONT_BOLD)
bullets(
    slide,
    ["PCA-bounding-box эталон", "10 bootstrap-повторов", "K=1 — валидный монолог"],
    0.88,
    4.58,
    5.25,
    1.0,
    10.5,
    GRAY,
    3,
)
card(slide, 6.80, 4.05, 5.85, 1.95, fill=RGBColor(0xFF, 0xF4, 0xE5), line=RGBColor(0xE6, 0x7E, 0x22), radius=0.10)
text(slide, "Sweep порогов (Android)", 7.03, 4.22, 4.0, 0.3, 13, True, RGBColor(0xB4, 0x5F, 0x06), font=FONT_BOLD)
bullets(
    slide,
    ["5 прогонов, пороги 0.4–0.8", "выбор K по плато", "быстро, но AMI — риск"],
    7.03,
    4.58,
    5.25,
    1.0,
    10.5,
    GRAY,
    3,
)
text(slide, "Фиксированный порог 0.5 на AMI/Vox даёт K=8–22 — поэтому заменён на auto-K.", 0.65, 6.30, 12.0, 0.3, 11.5, True, RED)
footer(slide, 7)
add_notes(slide, "40 секунд: сегментация, чистые окна, эмбеддинги, complete-linkage AHC и auto-K. Порог 0.5 нестабилен.")

# 8. Full pipeline Dialogs -------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, WHITE)
title_slide(slide, "Полный пайплайн на русском диалоговом корпусе", "Собственный тест")
text(slide, "Dialogs · Interspeech 2026 · 5 диалогов · 2 спикера · 8.3 мин", 0.65, 1.78, 7.5, 0.3, 11, False, GRAY)
metrics = [("18.97%", "DER"), ("88.11%", "word accuracy"), ("5/5", "K exact")]
for i, (value, label) in enumerate(metrics):
    left = 0.65 + i * 1.95
    card(slide, left, 2.25, 1.75, 1.45, fill=LIGHT_2, line=CYAN, radius=0.10)
    text(slide, value, left, 2.42, 1.75, 0.55, 21, True, BLUE, PP_ALIGN.CENTER, FONT_BOLD)
    text(slide, label, left, 3.10, 1.75, 0.3, 9.5, False, GRAY, PP_ALIGN.CENTER)
card(slide, 0.65, 4.10, 5.40, 1.70, fill=LIGHT, line=BORDER, radius=0.10)
text(slide, "Как считали", 0.88, 4.25, 3.0, 0.3, 12, True, NAVY, font=FONT_BOLD)
bullets(
    slide,
    ["GigaAM → CAM++ → WordSpeakerAssigner", "Реплики: пауза > 1.2 с, длина > 15 с", "JSON: speaker_id — целое число"],
    0.88,
    4.58,
    4.85,
    1.0,
    10,
    GRAY,
    2,
)
code = card(slide, 6.35, 2.25, 6.35, 3.55, fill=RGBColor(0x0B, 0x12, 0x20), line=RGBColor(0x2A, 0x45, 0x6A), radius=0.08)
text(
    slide,
    '{\n  "transcript": [\n    {\n      "speaker_id": 1,\n      "start_ms": 1250,\n      "end_ms": 4380,\n      "text": "Добрый день!"\n    },\n    {\n      "speaker_id": 2,\n      "start_ms": 4500,\n      "end_ms": 6000,\n      "text": "Привет."\n    }\n  ]\n}',
    6.55,
    2.42,
    5.95,
    3.20,
    10.5,
    False,
    RGBColor(0xD6, 0xE4, 0xF5),
    font="Consolas",
)
text(slide, "Примеры: diarization/results/transcripts/", 6.35, 5.95, 6.3, 0.3, 9.5, False, GRAY)
footer(slide, 8)
add_notes(slide, "35 секунд: на реальном русском диалоговом корпусе DER 18.97%, точность спикера по словам 88.11%, K угадан 5 из 5.")

# 9. Android app -----------------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, WHITE)
title_slide(slide, "Android-приложение", "Готовый APK")
features = [
    ("Импорт", "WAV / MP3 / MP4"),
    ("Запись", "микрофон, WAV 16 кГц"),
    ("Модели", "скачивание и проверка"),
    ("Диаризация", "ERes2Net, auto-K"),
    ("Спикеры", "имена и цвета"),
    ("Редактор", "правка текста"),
    ("LLM", "Qwen2.5, локально"),
    ("Экспорт", "JSON в файл"),
]
for i, (head, sub) in enumerate(features):
    col = i % 2
    row = i // 2
    left = 0.65 + col * 3.30
    top = 1.95 + row * 1.05
    card(slide, left, top, 3.05, 0.88, fill=LIGHT, line=BORDER, radius=0.10)
    text(slide, head, left + 0.15, top + 0.12, 2.7, 0.28, 12, True, NAVY, font=FONT_BOLD)
    text(slide, sub, left + 0.15, top + 0.45, 2.7, 0.25, 9.5, False, GRAY)
# phone mockup
card(slide, 8.05, 1.55, 4.45, 4.95, fill=NAVY, line=CYAN, radius=0.10)
card(slide, 8.28, 1.80, 3.99, 4.45, fill=WHITE, line=BORDER, radius=0.06)
text(slide, "Библиотека", 8.50, 2.00, 3.5, 0.3, 14, True, NAVY, font=FONT_BOLD)
for i, (name, dur, color) in enumerate([("Встреча 12 мая", "12:34", CYAN), ("Интервью", "08:12", RGBColor(0xFB, 0xBC, 0x04))]):
    card(slide, 8.50, 2.45 + i * 0.78, 3.55, 0.62, fill=LIGHT, line=BORDER, radius=0.08)
    text(slide, name, 8.65, 2.53 + i * 0.78, 2.2, 0.25, 10.5, True, NAVY)
    text(slide, dur, 10.95, 2.55 + i * 0.78, 0.9, 0.22, 9, False, GRAY, PP_ALIGN.RIGHT)
    text(slide, "●  speaker_1 / speaker_2", 8.65, 2.78 + i * 0.78, 3.3, 0.2, 8, True, color)
card(slide, 8.50, 4.15, 3.55, 1.75, fill=LIGHT_2, line=CYAN, radius=0.08)
text(slide, "Расшифровка", 8.68, 4.30, 3.2, 0.28, 11.5, True, NAVY, font=FONT_BOLD)
text(slide, "Добрый день!\nПривет, как дела?\nВсё готово к встрече.", 8.68, 4.65, 3.2, 0.8, 9.5, False, GRAY)
text(slide, "APK ~90 МБ · minSdk 24 · arm64-v8a + x86_64", 0.65, 6.45, 7.2, 0.3, 10.5, False, GRAY)
footer(slide, 9)
add_notes(slide, "30 секунд: приложение умеет импорт, запись, скачивание моделей, диаризацию, редактор спикеров, LLM и JSON-экспорт.")

# 10. Benchmarks -----------------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, WHITE)
title_slide(slide, "Бенчмарки: что и с чем сравнивали", "Цифры вместо обещаний")
add_table(
    slide,
    [
        ["Компонент", "Модель", "Метрика", "RTF"],
        ["ASR", "GigaAM v3", "WER 2.44%", "0.043"],
        ["ASR", "Zipformer-RU", "WER 3.55%", "0.019"],
        ["ASR", "Whisper turbo", "WER 18.18%", "0.382"],
        ["Диаризация", "CAM++ · auto K", "DER 33.20%", "0.094"],
        ["Диаризация", "ERes2Net en · oracle", "DER 27.28%", "0.202"],
        ["Диалоги", "GigaAM + CAM++", "DER 18.97%", "—"],
    ],
    0.55,
    1.95,
    8.10,
    3.45,
    12.5,
    [1.05, 2.35, 1.55, 0.75],
)
metrics = [
    ("2.44%", "WER GigaAM v3", BLUE),
    ("18.97%", "DER на диалогах", GREEN),
    ("88.11%", "точность слов", RGBColor(0x6A, 0x3D, 0xC7)),
    ("5/5", "K угадан", RGBColor(0xB4, 0x5F, 0x06)),
]
for i, (value, label, color) in enumerate(metrics):
    top = 1.95 + i * 0.93
    card(slide, 8.90, top, 3.85, 0.80, fill=LIGHT, line=BORDER, radius=0.10)
    text(slide, value, 9.02, top + 0.10, 1.55, 0.55, 21, True, color, font=FONT_BOLD)
    text(slide, label, 10.60, top + 0.23, 2.05, 0.35, 10.5, False, GRAY)
card(slide, 0.55, 5.72, 12.20, 1.12, fill=LIGHT_2, line=CYAN, radius=0.10)
text(slide, "Вывод", 0.80, 5.87, 1.3, 0.30, 13.5, True, NAVY, font=FONT_BOLD)
text(
    slide,
    "Русский → GigaAM v3. Слабые устройства → Zipformer. Auto-K обязателен: "
    "фиксированный порог 0.5 даёт K=8–22 на AMI/Vox.",
    2.05,
    5.88,
    10.5,
    0.80,
    11.5,
    False,
    GRAY,
)
footer(slide, 10)
add_notes(
    slide,
    "40 секунд: сравнили ASR и 12 моделей диаризации. GigaAM — 2.44% WER; "
    "CAM++ — 33.2% DER auto; на диалогах 18.97% DER и 88.11% точности слов.",
)

# 11. Limitations ---------------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, WHITE)
title_slide(slide, "Ограничения", "Говорим честно")
limits = [
    ("Auto-K", "На коротких записях и 4 спикерах K=1–3; нужен ручной override."),
    ("Перекрытия", "Декодер выдаёт одного спикера на кадр; сильный overlap штрафуется."),
    ("Русские данные", "Синтетика TTS + Dialogs; живая речь с шумом — отдельный тест."),
    ("Dialogs", "Реплики склеены с паузами; естественных перекрытий нет."),
    ("CPU", "RTF cold 0.08–0.50; на телефоне медленнее, нужен CAM++/TitaNet."),
    ("LLM", "Qwen2.5 опционален; без модели или при ошибке — сырой ASR."),
]
for i, (head, body) in enumerate(limits):
    col = i % 2
    row = i // 2
    left = 0.65 + col * 6.2
    top = 1.95 + row * 1.45
    card(slide, left, top, 5.9, 1.20, fill=LIGHT, line=BORDER, radius=0.10)
    badge = card(slide, left + 0.14, top + 0.18, 0.46, 0.46, fill=AMBER, line=None, radius=0.50)
    text(slide, "!", left + 0.14, top + 0.20, 0.46, 0.3, 14, True, WHITE, PP_ALIGN.CENTER, FONT_BOLD)
    text(slide, head, left + 0.75, top + 0.13, 4.9, 0.28, 12, True, NAVY, font=FONT_BOLD)
    text(slide, body, left + 0.75, top + 0.45, 4.9, 0.65, 9.5, False, GRAY)
text(slide, "Что улучшим: overlap-aware декодер, реальные русские встречи, калибровка auto-K.", 0.65, 6.55, 12.0, 0.3, 11, True, BLUE)
footer(slide, 11)
add_notes(slide, "25 секунд: честно про auto-K, перекрытия, данные и скорость. Все ограничения описаны в README.")

# 12. Demo / links ---------------------------------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[6])
set_bg(slide, NAVY)
text(slide, "ДЕМО И ССЫЛКИ", 0.72, 0.55, 6.0, 0.3, 12, True, CYAN, font=FONT_BOLD)
text(slide, "Спасибо!", 0.72, 0.98, 7.0, 0.8, 36, True, WHITE, font=FONT_BOLD)
steps = [
    ("1", "Импорт аудио", "файл или запись с микрофона"),
    ("2", "Расшифровка", "ASR + спикеры + тайминги"),
    ("3", "Экспорт", "JSON с speaker_id и репликами"),
]
for i, (num, head, body) in enumerate(steps):
    top = 2.20 + i * 1.15
    circle = card(slide, 0.75, top + 0.08, 0.48, 0.48, fill=CYAN, line=None, radius=0.50)
    text(slide, num, 0.75, top + 0.12, 0.48, 0.3, 13, True, NAVY, PP_ALIGN.CENTER, FONT_BOLD)
    text(slide, head, 1.42, top, 4.8, 0.32, 16, True, WHITE, font=FONT_BOLD)
    text(slide, body, 1.42, top + 0.35, 5.3, 0.3, 11, False, RGBColor(0xC9, 0xD7, 0xE8))
card(slide, 7.35, 1.55, 2.55, 2.55, fill=WHITE, line=None, radius=0.06)
slide.shapes.add_picture(str(qr_path), Inches(7.48), Inches(1.68), width=Inches(2.28), height=Inches(2.28))
text(slide, "Репозиторий", 7.45, 4.20, 2.4, 0.3, 10, True, CYAN, PP_ALIGN.CENTER)
text(slide, "github.com/VershininSemyon/\nnuclear_hack_team_corgi", 7.45, 4.48, 2.4, 0.6, 8.5, False, WHITE, PP_ALIGN.CENTER)
text(slide, "APK: android/app/build/outputs/apk/debug/app-debug.apk", 0.72, 5.85, 6.5, 0.3, 10, False, RGBColor(0xC9, 0xD7, 0xE8))
text(slide, "Видео: ссылка в README / reports", 0.72, 6.18, 6.5, 0.3, 10, False, RGBColor(0xC9, 0xD7, 0xE8))
text(slide, "Команда Corgi · On-device ASR + diarization", 0.72, 6.70, 7.0, 0.3, 10, False, RGBColor(0x8F, 0xA8, 0xC4))
add_notes(slide, "15 секунд: три шага демо и QR на репозиторий. Спасибо за внимание.")

prs.save(OUT)
print(f"wrote {OUT}")
