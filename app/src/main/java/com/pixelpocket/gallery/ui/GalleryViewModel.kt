package com.pixelpocket.gallery.ui

import android.app.Application
import android.content.IntentSender
import android.graphics.Bitmap
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelpocket.gallery.data.Album
import com.pixelpocket.gallery.data.MediaFilter
import com.pixelpocket.gallery.data.MediaItem
import com.pixelpocket.gallery.data.MediaRepository
import com.pixelpocket.gallery.data.SaveFormat
import com.pixelpocket.gallery.data.SortOption
import com.pixelpocket.gallery.data.UserPrefs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed interface Screen {
    data object Register : Screen
    data object Permission : Screen
    data object Gallery : Screen
    data class Viewer(val startKey: String) : Screen
    data class Crop(val item: MediaItem, val returnKey: String) : Screen
    data object Settings : Screen
}

sealed interface GridEntry {
    val key: String

    data class Header(val label: String) : GridEntry {
        override val key: String get() = "h_$label"
    }

    data class Media(val item: MediaItem) : GridEntry {
        override val key: String get() = item.key
    }
}

/** An operation waiting for the system's media-access confirmation dialog. */
private sealed interface PendingOp {
    data class Delete(val items: List<MediaItem>) : PendingOp
    data class Move(val items: List<MediaItem>, val folder: String) : PendingOp
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = UserPrefs(app)
    val repo = MediaRepository(app)

    var screen by mutableStateOf<Screen>(if (prefs.isRegistered) Screen.Permission else Screen.Register)

    var allItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var hasLoaded by mutableStateOf(false)
        private set
    var isBusy by mutableStateOf(false)
        private set

    /** Always starts as newest-first each time the app opens. */
    var sort by mutableStateOf(SortOption.DATE_DESC)
    var filter by mutableStateOf(MediaFilter.ALL)
    var gridColumns by mutableStateOf(prefs.gridColumns)
        private set

    var selectedKeys by mutableStateOf<Set<String>>(emptySet())
        private set
    var selectionMode by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
    var intentRequest by mutableStateOf<IntentSender?>(null)
        private set
    private var pendingOp: PendingOp? = null

    val visibleItems: List<MediaItem> by derivedStateOf {
        val filtered = when (filter) {
            MediaFilter.ALL -> allItems
            MediaFilter.PHOTOS -> allItems.filter { !it.isVideo }
            MediaFilter.VIDEOS -> allItems.filter { it.isVideo }
        }
        when (sort) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.dateMillis }
            SortOption.DATE_ASC -> filtered.sortedBy { it.dateMillis }
            SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.size }
            SortOption.SIZE_ASC -> filtered.sortedBy { it.size }
        }
    }

    /** Grid rows, with day headers when sorted by date. */
    val gridEntries: List<GridEntry> by derivedStateOf {
        val items = visibleItems
        if (!sort.isByDate) return@derivedStateOf items.map { GridEntry.Media(it) }
        val out = ArrayList<GridEntry>(items.size + 32)
        var lastLabel: String? = null
        for (item in items) {
            val label = dayLabel(item.dateMillis)
            if (label != lastLabel) {
                out += GridEntry.Header(label)
                lastLabel = label
            }
            out += GridEntry.Media(item)
        }
        out
    }

    val albums: List<Album> by derivedStateOf {
        allItems.filter { it.relativePath.isNotBlank() }
            .groupBy { it.relativePath }
            .map { (path, items) -> Album(items.first().album, path, items.size) }
            .sortedBy { it.name.lowercase() }
    }

    val selectedItems: List<MediaItem>
        get() = visibleItems.filter { it.key in selectedKeys }

    // ----------------------------------------------------------- loading

    fun load(showSpinner: Boolean = !hasLoaded) {
        if (isLoading) return
        viewModelScope.launch {
            if (showSpinner) isLoading = true
            val items = runCatching { repo.loadAll() }.getOrElse {
                message = "Couldn't read your photos: ${it.message}"
                emptyList()
            }
            allItems = items
            val existing = items.mapTo(HashSet()) { it.key }
            selectedKeys = selectedKeys.filterTo(HashSet()) { it in existing }
            isLoading = false
            hasLoaded = true
        }
    }

    // --------------------------------------------------------- selection

    fun startSelection(item: MediaItem) {
        selectionMode = true
        selectedKeys = selectedKeys + item.key
    }

    fun toggle(item: MediaItem) {
        selectedKeys = if (item.key in selectedKeys) selectedKeys - item.key else selectedKeys + item.key
        if (selectedKeys.isEmpty()) selectionMode = false
    }

    fun toggleDay(label: String) {
        val dayKeys = visibleItems.filter { dayLabel(it.dateMillis) == label }.map { it.key }.toSet()
        selectedKeys = if (selectedKeys.containsAll(dayKeys)) selectedKeys - dayKeys else selectedKeys + dayKeys
        selectionMode = selectedKeys.isNotEmpty()
    }

    val allSelected: Boolean
        get() = visibleItems.isNotEmpty() && selectedKeys.size >= visibleItems.size &&
            visibleItems.all { it.key in selectedKeys }

    fun selectAllToggle() {
        selectedKeys = if (allSelected) emptySet() else visibleItems.mapTo(HashSet()) { it.key }
        selectionMode = true
    }

    fun clearSelection() {
        selectedKeys = emptySet()
        selectionMode = false
    }

    // ------------------------------------------------------ delete / move

    fun delete(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val sender = runCatching { repo.deleteRequest(items) }.getOrNull()
        if (sender != null) {
            pendingOp = PendingOp.Delete(items)
            intentRequest = sender
        } else {
            viewModelScope.launch {
                isBusy = true
                val n = repo.deleteDirect(items)
                isBusy = false
                finishOp("Deleted $n ${plural(n)}")
            }
        }
    }

    fun move(items: List<MediaItem>, folder: String) {
        if (items.isEmpty()) return
        val sender = runCatching { repo.writeRequest(items) }.getOrNull()
        if (sender != null) {
            pendingOp = PendingOp.Move(items, folder)
            intentRequest = sender
        } else {
            performMove(items, folder)
        }
    }

    private fun performMove(items: List<MediaItem>, folder: String) {
        viewModelScope.launch {
            isBusy = true
            val n = repo.move(items, folder)
            isBusy = false
            val skipped = items.size - n
            finishOp(
                "Moved $n ${plural(n)} to ${folder.trimEnd('/').substringAfterLast('/')}" +
                    if (skipped > 0) " ($skipped already there or skipped)" else ""
            )
        }
    }

    fun onIntentLaunched() {
        intentRequest = null
    }

    fun onIntentResult(approved: Boolean) {
        val op = pendingOp
        pendingOp = null
        if (!approved || op == null) {
            message = "Cancelled"
            return
        }
        when (op) {
            is PendingOp.Delete -> finishOp("Deleted ${op.items.size} ${plural(op.items.size)}")
            is PendingOp.Move -> performMove(op.items, op.folder)
        }
    }

    private fun finishOp(text: String) {
        message = text
        clearSelection()
        load(showSpinner = false)
    }

    // ---------------------------------------------------------- crop save

    fun saveCropped(
        bitmap: Bitmap,
        name: String,
        format: SaveFormat,
        quality: Int,
        album: String,
        rememberChoice: Boolean,
        onDone: (Boolean) -> Unit,
    ) {
        if (rememberChoice) {
            prefs.saveFormat = format
            prefs.saveQuality = quality
            prefs.saveAlbum = album
        }
        viewModelScope.launch {
            isBusy = true
            val ok = repo.saveBitmap(bitmap, name, format, quality, album)
            isBusy = false
            message = if (ok) "Saved to Pictures/${album.ifBlank { UserPrefs.DEFAULT_ALBUM }}" else "Couldn't save image"
            if (ok) load(showSpinner = false)
            onDone(ok)
        }
    }

    // ------------------------------------------------------- registration

    fun register(name: String, email: String, phone: String) {
        prefs.userName = name.trim()
        prefs.email = email.trim()
        prefs.phone = phone.trim()
        prefs.registeredAt = System.currentTimeMillis()
        prefs.isRegistered = true
        screen = Screen.Permission
    }

    fun updateGridColumns(columns: Int) {
        prefs.gridColumns = columns
        gridColumns = prefs.gridColumns
    }

    // ------------------------------------------------------------ helpers

    private fun plural(n: Int) = if (n == 1) "item" else "items"

    private val dayFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    private val dayCache = HashMap<Long, String>()

    fun dayLabel(millis: Long): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStart = cal.timeInMillis
        return dayCache.getOrPut(dayStart) {
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            when (dayStart) {
                today -> "Today"
                today - DAY_MS -> "Yesterday"
                else -> dayFormat.format(dayStart)
            }
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
