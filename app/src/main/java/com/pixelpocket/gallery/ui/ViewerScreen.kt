package com.pixelpocket.gallery.ui

import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.pixelpocket.gallery.data.MediaItem
import java.text.DateFormat
import java.util.Date

@Composable
fun ViewerScreen(vm: GalleryViewModel, startKey: String) {
    val context = LocalContext.current
    val items = vm.visibleItems
    val startIndex = remember(startKey) { items.indexOfFirst { it.key == startKey }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    BackHandler { vm.screen = Screen.Gallery }

    LaunchedEffect(items.isEmpty(), vm.hasLoaded) {
        if (items.isEmpty() && vm.hasLoaded) vm.screen = Screen.Gallery
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    if (items.isEmpty()) return

    val current: MediaItem = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            key = { items.getOrNull(it)?.key ?: it },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            if (item.isVideo) {
                VideoPage(item, isCurrent = page == pagerState.currentPage, onTap = { chromeVisible = !chromeVisible })
            } else {
                ZoomableImage(item, onTap = { chromeVisible = !chromeVisible })
            }
        }

        if (chromeVisible) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.screen = Screen.Gallery }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(current.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(current.dateMillis)),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(onClick = { showInfo = true }) { Icon(Icons.Outlined.Info, "Details", tint = Color.White) }
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding()
                    .padding(vertical = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            ) {
                ViewerAction(Icons.Filled.Share, "Share") { shareMedia(context, listOf(current)) }
                if (!current.isVideo) {
                    ViewerAction(Icons.Filled.Crop, "Crop") {
                        vm.screen = Screen.Crop(current, current.key)
                    }
                }
                ViewerAction(Icons.AutoMirrored.Filled.DriveFileMove, "Move") { showMove = true }
                ViewerAction(Icons.Filled.Delete, "Delete") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vm.delete(listOf(current)) else confirmDelete = true
                }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 72.dp),
        )
    }

    if (showInfo) InfoDialog(current) { showInfo = false }
    if (showMove) {
        MoveDialog(vm, 1, onDismiss = { showMove = false }) { folder ->
            showMove = false
            vm.move(listOf(current), folder)
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(1, onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            vm.delete(listOf(current))
        }
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White)
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ZoomableImage(item: MediaItem, onTap: () -> Unit) {
    var scale by remember(item.key) { mutableFloatStateOf(1f) }
    var offset by remember(item.key) { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = item.uri,
        contentDescription = item.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.key) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = (center - tap) * 1.5f
                        }
                    },
                )
            }
            .pointerInput(item.key) {
                // Pinch to zoom; only steal drags from the pager while zoomed in or pinching.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinching = event.changes.count { it.pressed } > 1
                        if (pinching || scale > 1f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            val maxX = size.width * (newScale - 1f) / 2f
                            val maxY = size.height * (newScale - 1f) / 2f
                            val moved = offset + event.calculatePan()
                            scale = newScale
                            offset = if (newScale <= 1f) Offset.Zero else Offset(
                                moved.x.coerceIn(-maxX, maxX),
                                moved.y.coerceIn(-maxY, maxY),
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

@Composable
private fun VideoPage(item: MediaItem, isCurrent: Boolean, onTap: () -> Unit) {
    var playing by remember(item.key) { mutableStateOf(false) }
    LaunchedEffect(isCurrent) { if (!isCurrent) playing = false }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (playing && isCurrent) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(item.uri)
                        setOnPreparedListener { start() }
                        setOnCompletionListener { controller.show(0) }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = item.uri,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.key) { detectTapGestures(onTap = { onTap() }) },
            )
            IconButton(onClick = { playing = true }, modifier = Modifier.size(84.dp)) {
                Icon(Icons.Filled.PlayCircle, "Play", tint = Color.White, modifier = Modifier.size(84.dp))
            }
        }
    }
}

@Composable
private fun InfoDialog(item: MediaItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column {
                InfoRow("Name", item.name)
                InfoRow("Date", DateFormat.getDateTimeInstance().format(Date(item.dateMillis)))
                InfoRow("Type", item.mimeType)
                InfoRow("Size", formatSize(item.size))
                if (item.width > 0 && item.height > 0) InfoRow("Resolution", "${item.width} × ${item.height}")
                if (item.isVideo) InfoRow("Duration", formatDuration(item.durationMs))
                InfoRow("Album", item.album)
                InfoRow("Location", item.dataPath ?: item.relativePath)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
