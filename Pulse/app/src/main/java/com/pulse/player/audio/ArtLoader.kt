package com.pulse.player.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.pulse.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Carrega a capa da música (embutida no arquivo, miniatura do sistema ou capa do álbum). */
object ArtLoader {
    private val cache = LruCache<String, Bitmap>(80)
    private val missing = HashSet<String>()

    suspend fun load(context: Context, track: Track, px: Int): Bitmap? {
        val key = "${track.id}:$px"
        cache.get(key)?.let { return it }
        if (synchronized(missing) { key in missing }) return null
        val bmp = withContext(Dispatchers.IO) { decode(context, track, px) }
        if (bmp != null) cache.put(key, bmp) else synchronized(missing) { missing.add(key) }
        return bmp
    }

    private fun soft(b: Bitmap): Bitmap =
        if (Build.VERSION.SDK_INT >= 26 && b.config == Bitmap.Config.HARDWARE) {
            b.copy(Bitmap.Config.ARGB_8888, false) ?: b
        } else b

    private fun decode(context: Context, track: Track, px: Int): Bitmap? {
        // 1) capa embutida no arquivo
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, track.uri)
                val data = r.embeddedPicture
                if (data != null) {
                    decodeBytes(data, px)?.let { return it }
                }
            } finally {
                r.release()
            }
        } catch (e: Throwable) {
        }
        // 2) miniatura do sistema (Android 10+)
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return soft(context.contentResolver.loadThumbnail(track.uri, Size(px, px), null))
            } catch (e: Throwable) {
            }
        }
        // 3) capa do álbum (Android antigo)
        try {
            context.contentResolver.openInputStream(track.artUri)?.use { s ->
                BitmapFactory.decodeStream(s)?.let { return it }
            }
        } catch (e: Throwable) {
        }
        return null
    }

    private fun decodeBytes(data: ByteArray, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }
}
