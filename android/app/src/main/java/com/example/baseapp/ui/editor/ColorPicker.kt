package com.example.baseapp.ui.editor

import com.example.baseapp.i18n.tr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Преобразование упакованного ARGB (0xFFRRGGBB) в HSV. */
internal fun argbToHsv(argb: Long): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    var hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    if (hue < 0f) hue += 360f

    val saturation = if (max == 0f) 0f else delta / max
    return floatArrayOf(hue, saturation, max)
}

/** Преобразование HSV в упакованный ARGB (0xFFRRGGBB). */
internal fun hsvToArgb(hue: Float, saturation: Float, value: Float): Long {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)

    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c

    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    fun channel(f: Float): Long =
        ((f + m) * 255f).roundToInt().coerceIn(0, 255).toLong()

    return (0xFFL shl 24) or
        (channel(r1) shl 16) or
        (channel(g1) shl 8) or
        channel(b1)
}

private val HUE_STOPS = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

/**
 * Полная палитра: квадрат насыщенность/яркость + полоса оттенка.
 * Курсор двигается пальцем, значение применяется кнопкой «Выбрать».
 */
@Composable
fun HsvColorPickerDialog(
    initialColor: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initial = remember(initialColor) { argbToHsv(initialColor) }
    var hue by remember { mutableStateOf(initial[0]) }
    var saturation by remember { mutableStateOf(initial[1]) }
    var value by remember { mutableStateOf(initial[2]) }

    val current = hsvToArgb(hue, saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Цвет спикера", "Speaker color")) },
        text = {
            Column {
                // Квадрат: по X — насыщенность, по Y — яркость.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                saturation =
                                    (change.position.x / size.width).coerceIn(0f, 1f)
                                value =
                                    1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { position ->
                                saturation =
                                    (position.x / size.width).coerceIn(0f, 1f)
                                value =
                                    1f - (position.y / size.height).coerceIn(0f, 1f)
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.White, Color.hsv(hue, 1f, 1f)),
                            ),
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black),
                            ),
                        )

                        val cx = saturation * size.width
                        val cy = (1f - value) * size.height
                        drawCircle(
                            color = Color.White,
                            radius = 9.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.5.dp.toPx()),
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.6f),
                            radius = 11.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Полоса оттенка.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                hue = (change.position.x / size.width)
                                    .coerceIn(0f, 1f) * 360f
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { position ->
                                hue = (position.x / size.width)
                                    .coerceIn(0f, 1f) * 360f
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(brush = Brush.horizontalGradient(HUE_STOPS))

                        val x = hue / 360f * size.width
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(x - 2.dp.toPx(), 0f),
                            size = Size(4.dp.toPx(), size.height),
                        )
                        drawRect(
                            color = Color.Black.copy(alpha = 0.35f),
                            topLeft = Offset(x - 3.dp.toPx(), 0f),
                            size = Size(6.dp.toPx(), size.height),
                        )
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(x - 2.dp.toPx(), 0f),
                            size = Size(4.dp.toPx(), size.height),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(current.toInt()), CircleShape)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "#%06X".format(current and 0xFFFFFF),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) {
                Text(tr("Выбрать", "Select"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Отмена", "Cancel"))
            }
        },
    )
}
