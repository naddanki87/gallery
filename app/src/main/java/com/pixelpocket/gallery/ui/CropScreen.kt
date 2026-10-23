package com.pixelpocket.gallery.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pixelpocket.gallery.data.MediaItem
import com.pixelpocket.gallery.data.SaveFormat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private enum class AspectOption(val label: String, val ratio: Float?) {
    FREE("Free", null),
    ORIGINAL("Original", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_FOUR("3:4", 3f / 4f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    NINE_SIXTEEN("9:16", 9f / 16f),
}

private enum class Handle { NONE, MOVE, TL, TR, BL, BR, L, T, R, B }

@Composable
fun CropScreen(vm: GalleryViewModel, item: MediaItem, returnKey: String) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(AspectOption.FREE) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var imageRect by remember { mutableStateOf(Rect.Zero) }
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    var showSave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val goBack = { vm.screen = Screen.Viewer(returnKey) }
    BackHandler { goBack() }

    LaunchedEffect(item.uri) {
        val loaded = vm.repo.loadBitmap(item.uri)
        if (loaded == null) failed = true else bitmap = loaded
    }

    fun ratioFor(bmp: Bitmap): Float? = when (aspect) {
        AspectOption.FREE -> null
        AspectOption.ORIGINAL -> bmp.width.toFloat() / bmp.height
        else -> aspect.ratio
    }

    fun resetCrop() {
        val bmp = bitmap ?: return
        if (container == IntSize.Zero) return
        imageRect = fitRect(bmp.width, bmp.height, container)
        cropRect = initialCrop(imageRect, ratioFor(bmp))
    }

    LaunchedEffect(bitmap, container, aspect) { resetCrop() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = goBack) { Icon(Icons.Filled.Close, "Cancel", tint = Color.White) }
            Text("Crop", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { resetCrop() }) { Icon(Icons.Filled.RestartAlt, "Reset", tint = Color.White) }
            IconButton(onClick = {
                bitmap = bitmap?.let { transform(it) { postRotate(90f) } }
            }) { Icon(Icons.Filled.RotateRight, "Rotate", tint = Color.White) }
            IconButton(onClick = {
                bitmap = bitmap?.let { transform(it) { postScale(-1f, 1f) } }
            }) { Icon(Icons.Filled.Flip, "Flip", tint = Color.White) }
            Button(
                enabled = bitmap != null && !saving,
                onClick = { showSave = true },
                modifier = Modifier.padding(end = 8.dp),
            ) { Text("Save") }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp)
                .onSizeChanged { container = it },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            when {
                failed -> Text("Couldn't open this image", color = Color.White)
                bmp == null -> CircularProgressIndicator()
                else -> {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    var handle by remember { mutableStateOf(Handle.NONE) }
                    val touch = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }
                    val minSize = touch * 1.6f
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(bmp) {
                                detectDragGestures(
                                    onDragStart = { pos -> handle = hitTest(pos, cropRect, touch, ratioFor(bmp) != null) },
                                    onDragEnd = { handle = Handle.NONE },
                                    onDragCancel = { handle = Handle.NONE },
                                ) { change, drag ->
                                    if (handle != Handle.NONE) {
                                        change.consume()
                                        cropRect = adjust(cropRect, handle, drag, imageRect, ratioFor(bmp), minSize)
                                    }
                                }
                            }
                    ) {
                        drawImage(
                            image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(image.width, image.height),
                            dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                            dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt()),
                        )
                        drawCropOverlay(imageRect, cropRect)
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AspectOption.entries.forEach { option ->
                FilterChip(
                    selected = aspect == option,
                    onClick = { aspect = option },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White),
                )
            }
        }
    }

    val bmp = bitmap
    if (showSave && bmp != null) {
        SaveDialog(
            vm = vm,
            defaultName = item.name.substringBeforeLast('.') + "_cropped",
            onDismiss = { showSave = false },
        ) { name, format, quality, album, keepAsDefault ->
            showSave = false
            saving = true
            val cropped = cropBitmap(bmp, imageRect, cropRect)
            vm.saveCropped(cropped, name, format, quality, album, keepAsDefault) { ok ->
                saving = false
                if (ok) vm.screen = Screen.Gallery
            }
        }
    }
}

@Composable
private fun SaveDialog(
    vm: GalleryViewModel,
    defaultName: String,
    onDismiss: () -> Unit,
    onSave: (name: String, format: SaveFormat, quality: Int, album: String, remember: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    var format by remember { mutableStateOf(vm.prefs.saveFormat) }
    var quality by remember { mutableFloatStateOf(vm.prefs.saveQuality.toFloat()) }
    var album by remember { mutableStateOf(vm.prefs.saveAlbum) }
    var rememberChoice by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save cropped image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("File name") }, singleLine = true)
                OutlinedTextField(
                    album,
                    { album = it.replace("/", "") },
                    label = { Text("Album (Pictures/…)") },
                    singleLine = true,
                )
                Text("Format", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaveFormat.entries.forEach { f ->
                        FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.label) })
                    }
                }
                if (format.supportsQuality) {
                    Text("Quality: ${quality.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                    Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..100f)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Text("Remember as my default")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, format, quality.roundToInt(), album, rememberChoice) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ geometry

private fun fitRect(w: Int, h: Int, container: IntSize): Rect {
    val scale = min(container.width / w.toFloat(), container.height / h.toFloat())
    val dw = w * scale
    val dh = h * scale
    val left = (container.width - dw) / 2f
    val top = (container.height - dh) / 2f
    return Rect(left, top, left + dw, top + dh)
}

private fun initialCrop(bounds: Rect, ratio: Float?): Rect {
    if (ratio == null) return bounds
    var w = bounds.width
    var h = w / ratio
    if (h > bounds.height) {
        h = bounds.height
        w = h * ratio
    }
    val c = bounds.center
    return Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)
}

private fun hitTest(p: Offset, r: Rect, touch: Float, locked: Boolean): Handle {
    fun near(a: Offset) = abs(p.x - a.x) <= touch && abs(p.y - a.y) <= touch
    return when {
        near(r.topLeft) -> Handle.TL
        near(r.topRight) -> Handle.TR
        near(r.bottomLeft) -> Handle.BL
        near(r.bottomRight) -> Handle.BR
        !locked && abs(p.x - r.left) <= touch && p.y in r.top..r.bottom -> Handle.L
        !locked && abs(p.x - r.right) <= touch && p.y in r.top..r.bottom -> Handle.R
        !locked && abs(p.y - r.top) <= touch && p.x in r.left..r.right -> Handle.T
        !locked && abs(p.y - r.bottom) <= touch && p.x in r.left..r.right -> Handle.B
        r.contains(p) -> Handle.MOVE
        else -> Handle.NONE
    }
}

private fun adjust(r: Rect, h: Handle, d: Offset, bounds: Rect, ratio: Float?, minSize: Float): Rect {
    if (h == Handle.MOVE) {
        val dx = d.x.coerceIn(bounds.left - r.left, bounds.right - r.right)
        val dy = d.y.coerceIn(bounds.top - r.top, bounds.bottom - r.bottom)
        return r.translate(dx, dy)
    }
    val minW = min(minSize, bounds.width)
    val minH = min(minSize, bounds.height)
    var l = r.left
    var t = r.top
    var rr = r.right
    var b = r.bottom
    val leftSide = h == Handle.TL || h == Handle.BL || h == Handle.L
    val rightSide = h == Handle.TR || h == Handle.BR || h == Handle.R
    val topSide = h == Handle.TL || h == Handle.TR || h == Handle.T
    val bottomSide = h == Handle.BL || h == Handle.BR || h == Handle.B
    if (leftSide) l = (l + d.x).coerceIn(bounds.left, rr - minW)
    if (rightSide) rr = (rr + d.x).coerceIn(l + minW, bounds.right)
    if (topSide) t = (t + d.y).coerceIn(bounds.top, b - minH)
    if (bottomSide) b = (b + d.y).coerceIn(t + minH, bounds.bottom)

    if (ratio != null && (leftSide || rightSide) && (topSide || bottomSide)) {
        // Keep the opposite corner anchored and derive height from width.
        var w = rr - l
        var hh = w / ratio
        val maxH = if (topSide) b - bounds.top else bounds.bottom - t
        if (hh > maxH) {
            hh = maxH
            w = hh * ratio
        }
        if (leftSide) l = rr - w else rr = l + w
        if (topSide) t = b - hh else b = t + hh
    }
    return Rect(l, t, rr, b)
}

private fun DrawScope.drawCropOverlay(image: Rect, crop: Rect) {
    val shade = Color.Black.copy(alpha = 0.6f)
    // Darken everything outside the crop area.
    drawRect(shade, Offset(image.left, image.top), Size(image.width, crop.top - image.top))
    drawRect(shade, Offset(image.left, crop.bottom), Size(image.width, image.bottom - crop.bottom))
    drawRect(shade, Offset(image.left, crop.top), Size(crop.left - image.left, crop.height))
    drawRect(shade, Offset(crop.right, crop.top), Size(image.right - crop.right, crop.height))

    drawRect(Color.White, crop.topLeft, crop.size, style = Stroke(width = 2.dp.toPx()))
    val grid = Color.White.copy(alpha = 0.45f)
    for (i in 1..2) {
        val x = crop.left + crop.width * i / 3f
        val y = crop.top + crop.height * i / 3f
        drawLine(grid, Offset(x, crop.top), Offset(x, crop.bottom), strokeWidth = 1.dp.toPx())
        drawLine(grid, Offset(crop.left, y), Offset(crop.right, y), strokeWidth = 1.dp.toPx())
    }
    val len = 20.dp.toPx()
    val w = 4.dp.toPx()
    listOf(
        crop.topLeft to Offset(1f, 1f),
        crop.topRight to Offset(-1f, 1f),
        crop.bottomLeft to Offset(1f, -1f),
        crop.bottomRight to Offset(-1f, -1f),
    ).forEach { (corner, dir) ->
        drawLine(Color.White, corner, corner + Offset(len * dir.x, 0f), strokeWidth = w)
        drawLine(Color.White, corner, corner + Offset(0f, len * dir.y), strokeWidth = w)
    }
}

private fun transform(src: Bitmap, block: Matrix.() -> Unit): Bitmap {
    val m = Matrix().apply(block)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

private fun cropBitmap(src: Bitmap, image: Rect, crop: Rect): Bitmap {
    val scale = src.width / image.width
    val x = ((crop.left - image.left) * scale).roundToInt().coerceIn(0, src.width - 1)
    val y = ((crop.top - image.top) * scale).roundToInt().coerceIn(0, src.height - 1)
    val w = (crop.width * scale).roundToInt().coerceIn(1, src.width - x)
    val h = (crop.height * scale).roundToInt().coerceIn(1, src.height - y)
    return Bitmap.createBitmap(src, x, y, w, h)
}
