package com.dcpi.ytpro.metadata

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class TrackMetadata(val title: String, val artist: String, val album: String)

class TrackMetadataEditor(private val context: Context) {
    fun update(uri: Uri, metadata: TrackMetadata): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, metadata.title.trim())
            put(MediaStore.Audio.Media.ARTIST, metadata.artist.trim())
            put(MediaStore.Audio.Media.ALBUM, metadata.album.trim())
        }
        return runCatching { context.contentResolver.update(uri, values, null, null) > 0 }.getOrDefault(false)
    }
}
