package com.pixelpocket.gallery.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val isVideo: Boolean,
    /** Date taken when known, otherwise the date it was added, in epoch millis. */
    val dateMillis: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val album: String,
    /** Folder relative to shared storage root, always ending in "/", e.g. "DCIM/Camera/". */
    val relativePath: String,
    /** Absolute file path; only populated on Android 10 and below. */
    val dataPath: String?,
) {
    /** Image and video ids live in separate tables, so they can collide. */
    val key: String get() = (if (isVideo) "v" else "i") + id
}

data class Album(val name: String, val relativePath: String, val count: Int)

enum class SortOption(val label: String) {
    DATE_DESC("Date · newest first"),
    DATE_ASC("Date · oldest first"),
    NAME_ASC("Name · A to Z"),
    NAME_DESC("Name · Z to A"),
    SIZE_DESC("Size · largest first"),
    SIZE_ASC("Size · smallest first");

    val isByDate: Boolean get() = this == DATE_DESC || this == DATE_ASC
}

enum class MediaFilter(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos") }
