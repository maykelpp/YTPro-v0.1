package com.dcpi.ytpro.playback

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

/** One process-wide player shared by the UI and the MediaSessionService. */
object PlaybackPlayerHolder {
    @Volatile private var instance: ExoPlayer? = null
    @Synchronized fun get(context: Context): ExoPlayer =
        instance ?: ExoPlayer.Builder(context.applicationContext).build().also { instance = it }
    @Synchronized fun release() { instance?.release(); instance = null }
}
