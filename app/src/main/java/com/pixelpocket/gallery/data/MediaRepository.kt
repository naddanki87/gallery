package com.pixelpocket.gallery.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.max

class MediaRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    private val storageRoot: String by lazy {
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') + "/"
    }

    // ---------------------------------------------------------------- query

    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false) +
            queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)
    }

    private fun queryCollection(collection: Uri, isVideo: Boolean): List<MediaItem> {
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            COL_DATE_TAKEN,
            COL_BUCKET,
            COL_WIDTH,
            COL_HEIGHT,
        )
        if (isVideo) projection += COL_DURATION
        projection += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) COL_RELATIVE_PATH else COL_DATA

        val result = ArrayList<MediaItem>()
        val cursor = runCatching {
            resolver.query(collection, projection.toTypedArray(), null, null, null)
        }.getOrNull() ?: return result

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val addedCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val takenCol = c.getColumnIndex(COL_DATE_TAKEN)
            val bucketCol = c.getColumnIndex(COL_BUCKET)
            val widthCol = c.getColumnIndex(COL_WIDTH)
            val heightCol = c.getColumnIndex(COL_HEIGHT)
            val durationCol = c.getColumnIndex(COL_DURATION)
            val relCol = c.getColumnIndex(COL_RELATIVE_PATH)
            val dataCol = c.getColumnIndex(COL_DATA)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = if (takenCol >= 0 && !c.isNull(takenCol)) c.getLong(takenCol) else 0L
                val added = if (addedCol >= 0) c.getLong(addedCol) * 1000L else 0L
                val dataPath = if (dataCol >= 0) c.getString(dataCol) else null
                val relativePath = when {
                    relCol >= 0 -> c.getString(relCol) ?: ""
                    dataPath != null -> relativeFolderOf(dataPath)
                    else -> ""
                }
                val name = (if (nameCol >= 0) c.getString(nameCol) else null)
                    ?: dataPath?.substringAfterLast('/') ?: "Untitled"
                result += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = name,
                    mimeType = (if (mimeCol >= 0) c.getString(mimeCol) else null)
                        ?: if (isVideo) "video/*" else "image/*",
                    isVideo = isVideo,
                    dateMillis = if (taken > 0) taken else added,
                    size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L,
                    width = if (widthCol >= 0) c.getInt(widthCol) else 0,
                    height = if (heightCol >= 0) c.getInt(heightCol) else 0,
                    durationMs = if (durationCol >= 0) c.getLong(durationCol) else 0L,
                    album = (if (bucketCol >= 0) c.getString(bucketCol) else null)
                        ?: relativePath.trimEnd('/').substringAfterLast('/').ifBlank { "Storage" },
                    relativePath = relativePath,
                    dataPath = dataPath,
                )
            }
        }
        return result
    }

    private fun relativeFolderOf(absolutePath: String): String {
        val parent = absolutePath.substringBeforeLast('/', "") + "/"
        return if (parent.startsWith(storageRoot)) parent.removePrefix(storageRoot) else parent.trimStart('/')
    }

    // --------------------------------------------------------------- delete

    /** Android 11+: system dialog that asks the user to confirm deleting media we don't own. */
    fun deleteRequest(items: List<MediaItem>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(resolver, items.map { it.uri }).intentSender
        } else {
            null
        }

    /** Android 10 and below: delete directly (legacy storage access). */
    suspend fun deleteDirect(items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        items.count { item ->
            runCatching {
                val removed = resolver.delete(item.uri, null, null) > 0
                if (!removed) item.dataPath?.let { File(it).delete() } ?: false else true
            }.getOrDefault(false)
        }
    }

    // ----------------------------------------------------------------- move

    /** Android 11+: system dialog granting us write access so we can move the files. */
    fun writeRequest(items: List<MediaItem>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(resolver, items.map { it.uri }).intentSender
        } else {
            null
        }

    /**
     * Moves items into [targetFolder] (relative path such as "Pictures/Trips/").
     * Returns the number of items moved.
     */
    suspend fun move(items: List<MediaItem>, targetFolder: String): Int = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            items.count { item ->
                val dest = validFolderFor(item, targetFolder)
                if (dest == item.relativePath) return@count false
                runCatching {
                    val values = ContentValues().apply { put(COL_RELATIVE_PATH, dest) }
                    resolver.update(item.uri, values, null, null) > 0
                }.getOrDefault(false)
            }
        } else {
            val scanPaths = ArrayList<String>()
            val moved = items.count { item ->
                val source = item.dataPath?.let(::File) ?: return@count false
                val destDir = File(storageRoot + validFolderFor(item, targetFolder))
                if (source.parentFile?.absolutePath == destDir.absolutePath) return@count false
                destDir.mkdirs()
                val dest = uniqueFile(destDir, source.name)
                val ok = source.renameTo(dest) || (runCatching {
                    source.copyTo(dest)
                    source.delete()
                }.getOrDefault(false))
                if (ok) {
                    scanPaths += source.absolutePath
                    scanPaths += dest.absolutePath
                }
                ok
            }
            scan(scanPaths)
            moved
        }
    }

    /** Images may only live under DCIM/ or Pictures/, videos under DCIM/, Movies/ or Pictures/. */
    private fun validFolderFor(item: MediaItem, folder: String): String {
        val normalized = folder.trim('/') + "/"
        val top = normalized.substringBefore('/')
        val allowed = if (item.isVideo) listOf("DCIM", "Movies", "Pictures") else listOf("DCIM", "Pictures")
        if (top in allowed) return normalized
        val albumName = normalized.trimEnd('/').substringAfterLast('/')
        val base = if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        return "$base/$albumName/"
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        return candidate
    }

    private suspend fun scan(paths: List<String>) {
        if (paths.isEmpty()) return
        suspendCancellableCoroutine { cont ->
            val remaining = AtomicInteger(paths.size)
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                if (remaining.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
            }
        }
    }

    // ------------------------------------------------------------ crop/save

    /** Decodes an image (respecting EXIF rotation) down-sampled to at most [maxDim] px. */
    suspend fun loadBitmap(uri: Uri, maxDim: Int = 3000): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val largest = max(info.size.width, info.size.height)
                    if (largest > maxDim) {
                        val scale = maxDim.toFloat() / largest
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                legacyDecode(uri, maxDim)
            }
        }.getOrNull()
    }

    private fun legacyDecode(uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Saves [bitmap] as a new image in Pictures/[album]. Returns true on success. */
    suspend fun saveBitmap(
        bitmap: Bitmap,
        baseName: String,
        format: SaveFormat,
        quality: Int,
        album: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val safeAlbum = album.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { UserPrefs.DEFAULT_ALBUM }
        val safeName = baseName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "PixelPocket" }
        val fileName = "$safeName.${format.extension}"
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                    put(COL_RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$safeAlbum/")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values) ?: return@runCatching false
                val written = resolver.openOutputStream(uri)?.use {
                    bitmap.compress(format.compressFormat, quality, it)
                } ?: false
                if (!written) {
                    resolver.delete(uri, null, null)
                    return@runCatching false
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), safeAlbum)
                dir.mkdirs()
                val file = uniqueFile(dir, fileName)
                val written = file.outputStream().use { bitmap.compress(format.compressFormat, quality, it) }
                if (written) scan(listOf(file.absolutePath))
                written
            }
        }.getOrDefault(false)
    }

    companion object {
        private const val COL_DATE_TAKEN = "datetaken"
        private const val COL_BUCKET = "bucket_display_name"
        private const val COL_WIDTH = "width"
        private const val COL_HEIGHT = "height"
        private const val COL_DURATION = "duration"
        private const val COL_RELATIVE_PATH = "relative_path"
        private const val COL_DATA = "_data"
    }
}
