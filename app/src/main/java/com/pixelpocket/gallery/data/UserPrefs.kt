package com.pixelpocket.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.os.Build

enum class SaveFormat(val label: String, val extension: String, val mimeType: String) {
    JPEG("JPEG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WEBP", "webp", "image/webp");

    @Suppress("DEPRECATION")
    val compressFormat: Bitmap.CompressFormat
        get() = when (this) {
            JPEG -> Bitmap.CompressFormat.JPEG
            PNG -> Bitmap.CompressFormat.PNG
            WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
        }

    val supportsQuality: Boolean get() = this != PNG
}

/** One-time registration details and the user's save preferences. */
class UserPrefs(context: Context) {
    private val sp = context.getSharedPreferences("pixelpocket_prefs", Context.MODE_PRIVATE)

    var isRegistered: Boolean
        get() = sp.getBoolean(KEY_REGISTERED, false)
        set(value) = sp.edit().putBoolean(KEY_REGISTERED, value).apply()

    var userName: String
        get() = sp.getString(KEY_NAME, "") ?: ""
        set(value) = sp.edit().putString(KEY_NAME, value).apply()

    var email: String
        get() = sp.getString(KEY_EMAIL, "") ?: ""
        set(value) = sp.edit().putString(KEY_EMAIL, value).apply()

    var phone: String
        get() = sp.getString(KEY_PHONE, "") ?: ""
        set(value) = sp.edit().putString(KEY_PHONE, value).apply()

    var registeredAt: Long
        get() = sp.getLong(KEY_REGISTERED_AT, 0L)
        set(value) = sp.edit().putLong(KEY_REGISTERED_AT, value).apply()

    var saveFormat: SaveFormat
        get() = runCatching { SaveFormat.valueOf(sp.getString(KEY_FORMAT, null) ?: "") }
            .getOrDefault(SaveFormat.JPEG)
        set(value) = sp.edit().putString(KEY_FORMAT, value.name).apply()

    var saveQuality: Int
        get() = sp.getInt(KEY_QUALITY, 92)
        set(value) = sp.edit().putInt(KEY_QUALITY, value.coerceIn(10, 100)).apply()

    var saveAlbum: String
        get() = sp.getString(KEY_ALBUM, DEFAULT_ALBUM)?.ifBlank { DEFAULT_ALBUM } ?: DEFAULT_ALBUM
        set(value) = sp.edit().putString(KEY_ALBUM, value.trim()).apply()

    var gridColumns: Int
        get() = sp.getInt(KEY_COLUMNS, 4)
        set(value) = sp.edit().putInt(KEY_COLUMNS, value.coerceIn(2, 6)).apply()

    companion object {
        const val DEFAULT_ALBUM = "PixelPocket"
        private const val KEY_REGISTERED = "registered"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE = "phone"
        private const val KEY_REGISTERED_AT = "registered_at"
        private const val KEY_FORMAT = "save_format"
        private const val KEY_QUALITY = "save_quality"
        private const val KEY_ALBUM = "save_album"
        private const val KEY_COLUMNS = "grid_columns"
    }
}
