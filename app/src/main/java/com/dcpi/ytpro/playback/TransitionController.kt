package com.dcpi.ytpro.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Fade transition for a single ExoPlayer instance. It fades down, switches
 * the item, then fades up. This is not beat-matched dual-deck AutoMix.
 */
class TransitionController(private val player: ExoPlayer) {
    suspend fun playWithFade(uri: String, durationMs: Long = 650L) {
        val steps = 10
        val original = player.volume
        for (step in steps downTo 1) {
            player.volume = original * (step - 1) / steps
            delay(max(1L, durationMs / steps))
        }
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
        for (step in 1..steps) {
            player.volume = original * step / steps
            delay(max(1L, durationMs / steps))
        }
    }
}
