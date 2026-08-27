package ru.merrcurys.seacard.features.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import java.io.InputStream
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import ru.merrcurys.seacard.core.design.SeaCardTheme
import androidx.core.graphics.createBitmap

/** Полоска со шкалой градусов: тянем влево/вправо — шкала прокручивается, угол 0..360°. */
@Composable
private fun RotationScaleStrip(
    rotationDegrees: Float,
    color: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val stepPx = 24f
        val stepDeg = 15f
        val degToPx = stepPx / stepDeg
        val margin = 20f
        val startDeg = floor((rotationDegrees - (centerX + margin) / degToPx) / stepDeg) * stepDeg
        val endDeg = ceil((rotationDegrees + (w - centerX + margin) / degToPx) / stepDeg) * stepDeg
        val numSteps = ((endDeg - startDeg) / stepDeg).toInt() + 1
        for (i in 0 until numSteps) {
            val d = startDeg + i * stepDeg
            val x = centerX + (d - rotationDegrees) * degToPx
            if (x in -margin..(w + margin)) {
                val degInt = (d.toInt() % 360 + 360) % 360
                val tickH = if (degInt == 0 || degInt == 90 || degInt == 180 || degInt == 270) h * 0.5f
                else if (d.toInt() % 45 == 0) h * 0.35f
                else h * 0.2f
                drawLine(color = color, start = Offset(x, h / 2f - tickH / 2f), end = Offset(x, h / 2f + tickH / 2f), strokeWidth = 1.5f)
            }
        }
        drawLine(color = color.copy(alpha = 0.9f), start = Offset(centerX, 0f), end = Offset(centerX, h), strokeWidth = 2f)
    }
}

/** Поворачивает bitmap на заданный угол (в градусах), возвращает новый bitmap. */
private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return source
    val matrix = Matrix().apply { postRotate(degrees) }
    val rect = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
    matrix.mapRect(rect)
    val newWidth = ceil(rect.width()).toInt().coerceAtLeast(1)
    val newHeight = ceil(rect.height()).toInt().coerceAtLeast(1)
    val result = createBitmap(newWidth, newHeight)
    val canvas = Canvas(result)
    canvas.translate(newWidth / 2f, newHeight / 2f)
    canvas.rotate(degrees)
    canvas.translate(-source.width / 2f, -source.height / 2f)
    canvas.drawBitmap(source, 0f, 0f, null)
    return result
}

@Composable
fun ImageCropDialog(
    imageUri: Uri,
    // Используем соотношение сторон 1.574
    aspectRatio: Float = 1.574f,
    onCrop: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    SeaCardTheme {
        val context = LocalContext.current
        val bitmap = remember(imageUri) {
            val input: InputStream? = context.contentResolver.openInputStream(imageUri)
            val bmp = BitmapFactory.decodeStream(input)
            input?.close()
            bmp
        }
        if (bitmap == null) {
            onDismiss()
            return@SeaCardTheme
        }

        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        // Поворот ограничен 0..360°
        var rotationDegrees by remember { mutableFloatStateOf(0f) }

        val colorScheme = MaterialTheme.colorScheme
        val isDark = colorScheme.background == Color(0xFF111111) || colorScheme.background == Color(0xFF232323)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Кадрирование обложки", color = colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .onSizeChanged { canvasSize = it },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.1f, 5f)
                                        offset += pan
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Поворот только через graphicsLayer — без пересоздания bitmap, без лагов
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp))
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y,
                                        rotationZ = rotationDegrees
                                    )
                            )
                            // Overlay & crop frame
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val boxW = size.width
                                val boxH = size.height
                                val frameW = (boxW * 0.85f).coerceAtMost(boxW)
                                val frameH = frameW / aspectRatio
                                val left = (boxW - frameW) / 2f
                                val top = (boxH - frameH) / 2f
                                val right = left + frameW
                                val bottom = top + frameH
                                // Затемнение вне рамки — четыре прямоугольника
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    topLeft = Offset(0f, 0f),
                                    size = Size(boxW, top)
                                )
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    topLeft = Offset(0f, bottom),
                                    size = Size(boxW, boxH - bottom)
                                )
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    topLeft = Offset(0f, top),
                                    size = Size(left, frameH)
                                )
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    topLeft = Offset(right, top),
                                    size = Size(boxW - right, frameH)
                                )
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(left - 2, top - 2),
                                    size = Size(frameW + 4, 2f)
                                )
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(left - 2, bottom),
                                    size = Size(frameW + 4, 2f)
                                )
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(left - 2, top),
                                    size = Size(2f, frameH)
                                )
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(right, top),
                                    size = Size(2f, frameH)
                                )
                                
                                // Сетка, для лучшего центрирования
                                val gridLines = 2
                                val gridStroke = Stroke(1f)
                                for (i in 1..gridLines) {
                                    // Vertical lines
                                    val vX = left + (frameW / (gridLines + 1)) * i
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.7f),
                                        start = Offset(vX, top),
                                        end = Offset(vX, bottom),
                                        strokeWidth = gridStroke.width
                                    )
                                    
                                    val hY = top + (frameH / (gridLines + 1)) * i
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.7f),
                                        start = Offset(left, hY),
                                        end = Offset(right, hY),
                                        strokeWidth = gridStroke.width
                                    )
                                }
                            }
                        }
                    }
                    // Бесконечно прокручиваемая шкала поворота (полоска с делениями)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Поворот: ${rotationDegrees.toInt()}°",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { _, dragAmount ->
                                        rotationDegrees = (rotationDegrees + dragAmount * 0.5f).coerceIn(0f, 360f)
                                    }
                                }
                        ) {
                            RotationScaleStrip(
                                rotationDegrees = rotationDegrees,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Двигайте и масштабируйте изображение, чтобы выбрать область",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val boxW = canvasSize.width.toFloat()
                        val boxH = canvasSize.height.toFloat()
                        val frameW = (boxW * 0.85f).coerceAtMost(boxW)
                        val frameH = frameW / aspectRatio
                        val frameLeft = (boxW - frameW) / 2f
                        val frameTop = (boxH - frameH) / 2f
                        val frameRight = frameLeft + frameW
                        val frameBottom = frameTop + frameH

                        val bitmapW = bitmap.width.toFloat()
                        val bitmapH = bitmap.height.toFloat()
                        val fitScale = minOf(boxW / bitmapW, boxH / bitmapH)
                        val finalScale = fitScale * scale
                        val centerX = boxW / 2f + offset.x
                        val centerY = boxH / 2f + offset.y
                        val angleRad = rotationDegrees * PI / 180.0
                        val cosA = cos(angleRad).toFloat()
                        val sinA = sin(angleRad).toFloat()

                        fun screenToOriginalBitmap(sx: Float, sy: Float): Pair<Float, Float> {
                            val relX = sx - centerX
                            val relY = sy - centerY
                            val newRelX = relX * cosA + relY * sinA
                            val newRelY = -relX * sinA + relY * cosA
                            val bx = bitmapW / 2f + newRelX / finalScale
                            val by = bitmapH / 2f + newRelY / finalScale
                            return bx to by
                        }

                        val normalizedAngle = ((rotationDegrees % 360f) + 360f) % 360f
                        val rotatedBitmap = rotateBitmap(bitmap, normalizedAngle)
                        val rotatedW = rotatedBitmap.width.toFloat()
                        val rotatedH = rotatedBitmap.height.toFloat()
                        val angleRadPos = normalizedAngle * PI / 180.0
                        val cosR = cos(angleRadPos).toFloat()
                        val sinR = sin(angleRadPos).toFloat()

                        fun originalToRotated(bx: Float, by: Float): Pair<Float, Float> {
                            val dx = (bx - bitmapW / 2f) * cosR - (by - bitmapH / 2f) * sinR
                            val dy = (bx - bitmapW / 2f) * sinR + (by - bitmapH / 2f) * cosR
                            return rotatedW / 2f + dx to rotatedH / 2f + dy
                        }

                        val corners = listOf(
                            screenToOriginalBitmap(frameLeft, frameTop),
                            screenToOriginalBitmap(frameRight, frameTop),
                            screenToOriginalBitmap(frameRight, frameBottom),
                            screenToOriginalBitmap(frameLeft, frameBottom)
                        ).map { (bx, by) -> originalToRotated(bx, by) }

                        var minX = corners.minOf { it.first }.toInt()
                        var minY = corners.minOf { it.second }.toInt()
                        var maxX = corners.maxOf { it.first }.toInt()
                        var maxY = corners.maxOf { it.second }.toInt()
                        minX = minX.coerceAtLeast(0)
                        minY = minY.coerceAtLeast(0)
                        maxX = maxX.coerceAtMost(rotatedBitmap.width)
                        maxY = maxY.coerceAtMost(rotatedBitmap.height)
                        val cropW = (maxX - minX).coerceAtLeast(1).coerceAtMost(rotatedBitmap.width - minX)
                        val cropH = (maxY - minY).coerceAtLeast(1).coerceAtMost(rotatedBitmap.height - minY)

                        if (cropW >= 1 && cropH >= 1 && minX + cropW <= rotatedBitmap.width && minY + cropH <= rotatedBitmap.height) {
                            try {
                                val cropped = Bitmap.createBitmap(rotatedBitmap, minX, minY, cropW, cropH)
                                onCrop(cropped)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Ошибка при кадрировании: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Выберите большую область для кадрирования", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("Обрезать", color = colorScheme.onSurface, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) { Text("Отмена", color = colorScheme.onSurfaceVariant) }
            },
            containerColor = if (isDark) colorScheme.onPrimary else colorScheme.surface,
            titleContentColor = colorScheme.onSurface,
            textContentColor = colorScheme.onSurface
        )
    }
}