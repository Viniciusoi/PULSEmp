package com.pulse.player.data

import android.content.ContentUris
import android.net.Uri

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
) {
    val folder: String get() = path.substringBeforeLast('/', "")
    val artUri: Uri
        get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
}

data class Playlist(val id: String, val name: String, val trackIds: List<Long>)

data class FolderInfo(val path: String, val name: String, val count: Int, val included: Boolean)
