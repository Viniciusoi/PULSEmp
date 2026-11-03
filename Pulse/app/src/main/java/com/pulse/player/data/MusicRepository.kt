package com.pulse.player.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    /** Lê todas as músicas do aparelho pelo MediaStore. */
    suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val out = ArrayList<Track>()
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        try {
            context.contentResolver.query(collection, projection, selection, null, order)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val path = c.getString(iData) ?: ""
                    val rawTitle = c.getString(iTitle)
                    val title = if (rawTitle.isNullOrBlank()) {
                        path.substringAfterLast('/').substringBeforeLast('.')
                    } else rawTitle
                    val rawArtist = c.getString(iArtist)
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") {
                        "Artista desconhecido"
                    } else rawArtist
                    val rawAlbum = c.getString(iAlbum)
                    val album = if (rawAlbum.isNullOrBlank()) "Álbum desconhecido" else rawAlbum
                    out.add(
                        Track(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = c.getLong(iAlbumId),
                            durationMs = c.getLong(iDur),
                            path = path,
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // sem permissão de armazenamento: devolve lista vazia
        }
        out
    }
}
