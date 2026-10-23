package com.pixelpocket.gallery.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pixelpocket.gallery.data.MediaFilter
import com.pixelpocket.gallery.data.MediaItem
import com.pixelpocket.gallery.data.SortOption
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(vm: GalleryViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showSort by remember { mutableStateOf(false) }
    var moveTargets by remember { mutableStateOf<List<MediaItem>?>(null) }
    var deleteTargets by remember { mutableStateOf<List<MediaItem>?>(null) }
    val gridState = rememberLazyGridState()

    // Pick up photos taken / changed while the app was in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (vm.hasLoaded) vm.load(showSpinner = false)
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    LaunchedEffect(vm.sort, vm.filter) { gridState.scrollToItem(0) }

    BackHandler(enabled = vm.selectionMode) { vm.clearSelection() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (vm.selectionMode) {
                TopAppBar(
                    title = { Text("${vm.selectedKeys.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Exit selection") }
                    },
                    actions = {
                        IconButton(onClick = vm::selectAllToggle) {
                            if (vm.allSelected) Icon(Icons.Filled.Deselect, "Deselect all")
                            else Icon(Icons.Filled.SelectAll, "Select all")
                        }
                        val hasSelection = vm.selectedKeys.isNotEmpty()
                        IconButton(enabled = hasSelection, onClick = { shareMedia(context, vm.selectedItems) }) {
                            Icon(Icons.Filled.Share, "Share")
                        }
                        IconButton(enabled = hasSelection, onClick = { moveTargets = vm.selectedItems }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, "Move")
                        }
                        IconButton(enabled = hasSelection, onClick = { deleteTargets = vm.selectedItems }) {
                            Icon(Icons.Filled.Delete, "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Memories", fontWeight = FontWeight.Bold)
                            Text(
                                "${vm.visibleItems.size} items · ${vm.sort.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showSort = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
                            }
                            SortMenu(expanded = showSort, current = vm.sort, onDismiss = { showSort = false }) {
                                vm.sort = it
                                showSort = false
                            }
                        }
                        IconButton(onClick = { vm.load(showSpinner = false) }) { Icon(Icons.Filled.Refresh, "Refresh") }
                        IconButton(onClick = { vm.screen = Screen.Settings }) { Icon(Icons.Filled.Settings, "Settings") }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaFilter.entries.forEach { f ->
                    FilterChip(selected = vm.filter == f, onClick = { vm.filter = f }, label = { Text(f.label) })
                }
            }
            if (vm.isBusy || (vm.isLoading && vm.allItems.isNotEmpty())) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when {
                !vm.hasLoaded || (vm.isLoading && vm.allItems.isEmpty()) -> LoadingView()
                vm.visibleItems.isEmpty() -> EmptyView(vm.filter)
                else -> MediaGrid(vm, gridState)
            }
        }
    }

    moveTargets?.let { targets ->
        MoveDialog(
            vm = vm,
            count = targets.size,
            onDismiss = { moveTargets = null },
            onMove = { folder ->
                moveTargets = null
                vm.move(targets, folder)
            },
        )
    }

    deleteTargets?.let { targets ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android shows its own confirmation dialog.
            LaunchedEffect(targets) {
                deleteTargets = null
                vm.delete(targets)
            }
        } else {
            ConfirmDeleteDialog(
                count = targets.size,
                onDismiss = { deleteTargets = null },
                onConfirm = {
                    deleteTargets = null
                    vm.delete(targets)
                },
            )
        }
    }
}

@Composable
fun SortMenu(expanded: Boolean, current: SortOption, onDismiss: () -> Unit, onPick: (SortOption) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            "Sort by",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                leadingIcon = { RadioButton(selected = option == current, onClick = null) },
                onClick = { onPick(option) },
            )
        }
    }
}

@Composable
private fun LoadingView() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.size(16.dp))
        Text("Loading your photos & videos…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyView(filter: MediaFilter) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            when (filter) {
                MediaFilter.ALL -> "No photos or videos yet"
                MediaFilter.PHOTOS -> "No photos found"
                MediaFilter.VIDEOS -> "No videos found"
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(vm: GalleryViewModel, state: androidx.compose.foundation.lazy.grid.LazyGridState) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(vm.gridColumns),
        state = state,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            items = vm.gridEntries,
            key = { it.key },
            span = { entry -> if (entry is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { if (it is GridEntry.Header) 0 else 1 },
        ) { entry ->
            when (entry) {
                is GridEntry.Header -> DayHeader(
                    label = entry.label,
                    selectionMode = vm.selectionMode,
                    onSelectDay = { vm.toggleDay(entry.label) },
                )
                is GridEntry.Media -> {
                    val item = entry.item
                    MediaTile(
                        item = item,
                        selectionMode = vm.selectionMode,
                        selected = item.key in vm.selectedKeys,
                        onClick = {
                            if (vm.selectionMode) vm.toggle(item) else vm.screen = Screen.Viewer(item.key)
                        },
                        onLongClick = { if (vm.selectionMode) vm.toggle(item) else vm.startSelection(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(label: String, selectionMode: Boolean, onSelectDay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (selectionMode) {
            TextButton(onClick = onSelectDay) { Text("Select day") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    item: MediaItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val scale by animateFloatAsState(if (selected) 0.86f else 1f, label = "tileScale")
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(item.uri).crossfade(true).build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(if (selected) 10.dp else 0.dp)),
        )
        if (item.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text(formatDuration(item.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (selectionMode) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Filled.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Outlined.Circle, "Not selected", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun MoveDialog(vm: GalleryViewModel, count: Int, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var newAlbum by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move $count ${if (count == 1) "item" else "items"} to…") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newAlbum,
                        onValueChange = { newAlbum = it.replace("/", "") },
                        label = { Text("New album name") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    enabled = newAlbum.isNotBlank(),
                    onClick = { onMove("Pictures/${newAlbum.trim()}/") },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Create & move") }
                HorizontalDivider()
                Text(
                    "Existing albums",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(vm.albums, key = { it.relativePath }) { album ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onMove(album.relativePath) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${album.relativePath} · ${album.count}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDeleteDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, null) },
        title = { Text("Delete $count ${if (count == 1) "item" else "items"}?") },
        text = { Text("This permanently removes them from your device.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Opens the Android share sheet so photos/videos can be sent to any other app. */
fun shareMedia(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val uris = ArrayList<Uri>(items.map { it.uri })
    val mime = when {
        items.all { !it.isVideo } -> if (items.map { it.mimeType }.distinct().size == 1) items.first().mimeType else "image/*"
        items.all { it.isVideo } -> if (items.map { it.mimeType }.distinct().size == 1) items.first().mimeType else "video/*"
        else -> "*/*"
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.apply {
        type = mime
        clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val title = if (items.size == 1) "Share ${items.first().name}" else "Share ${items.size} items"
    context.startActivity(Intent.createChooser(intent, title))
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
