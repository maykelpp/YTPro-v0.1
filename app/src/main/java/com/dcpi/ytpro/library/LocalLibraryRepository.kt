package com.dcpi.ytpro.library

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

data class LocalTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long
)

class LocalLibraryRepository(private val context: Context) {
    fun scanAudio(): List<LocalTrack> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )
        val result = mutableListOf<LocalTrack>()
        context.contentResolver.query(
            collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += LocalTrack(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    title = cursor.getString(titleCol).orEmpty().ifBlank { "Sin título" },
                    artist = cursor.getString(artistCol).orEmpty().ifBlank { "Artista desconocido" },
                    album = cursor.getString(albumCol).orEmpty().ifBlank { "Álbum desconocido" },
                    durationMs = cursor.getLong(durationCol),
                    sizeBytes = cursor.getLong(sizeCol)
                )
            }
        }
        return result
    }
}
