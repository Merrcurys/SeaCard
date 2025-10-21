package ru.merrcurys.seacard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
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
import java.io.InputStream
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import ru.merrcurys.seacard.ui.theme.SeaCardTheme

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

        val frameWidthPx = remember { 320f }
        val frameHeightPx = remember { frameWidthPx / aspectRatio }

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
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        offset += pan
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Image
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
                                        translationY = offset.y
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
                                // Скруглённая рамка
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(left, top),
                                    size = Size(frameW, frameH),
                                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
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

                        val bitmapW = bitmap.width.toFloat()
                        val bitmapH = bitmap.height.toFloat()
                        val fitScale = minOf(boxW / bitmapW, boxH / bitmapH)
                        val displayedW = bitmapW * fitScale * scale
                        val displayedH = bitmapH * fitScale * scale
                        val imageLeft = (boxW - displayedW) / 2f + offset.x
                        val imageTop = (boxH - displayedH) / 2f + offset.y

                        val cropLeft = ((frameLeft - imageLeft) / displayedW) * bitmapW
                        val cropTop = ((frameTop - imageTop) / displayedH) * bitmapH
                        val cropWidth = (frameW / displayedW) * bitmapW
                        val cropHeight = (frameH / displayedH) * bitmapH

                        val cropLeftClamped = cropLeft.coerceIn(0f, bitmapW - 1)
                        val cropTopClamped = cropTop.coerceIn(0f, bitmapH - 1)
                        val cropWidthClamped = cropWidth.coerceAtMost(bitmapW - cropLeftClamped)
                        val cropHeightClamped = cropHeight.coerceAtMost(bitmapH - cropTopClamped)

                        val rect = Rect(
                            cropLeftClamped.toInt(),
                            cropTopClamped.toInt(),
                            (cropLeftClamped + cropWidthClamped).toInt().coerceAtMost(bitmap.width),
                            (cropTopClamped + cropHeightClamped).toInt().coerceAtMost(bitmap.height)
                        )
                        val croppedWidth = rect.width()
                        val croppedHeight = rect.height()
                        if (croppedWidth > 0 && croppedHeight > 0) {
                            val cropped = Bitmap.createBitmap(
                                bitmap,
                                rect.left,
                                rect.top,
                                croppedWidth,
                                croppedHeight
                            )
                            onCrop(cropped)
                        } else {
                            Toast.makeText(context, "Некорректная область кадрирования", Toast.LENGTH_LONG).show()
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