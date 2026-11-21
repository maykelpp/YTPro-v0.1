package com.dcpi.ytpro.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer

class AudioEffectsController(audioSessionId: Int) : AutoCloseable {
    private val equalizer = runCatching { Equalizer(0, audioSessionId).apply { enabled = true } }.getOrNull()
    private val bass = runCatching { BassBoost(0, audioSessionId).apply { enabled = true } }.getOrNull()
    private val loudness = runCatching { LoudnessEnhancer(audioSessionId).apply { enabled = true } }.getOrNull()

    val bandCount: Int get() = equalizer?.numberOfBands?.toInt() ?: 0
    fun bandRange(): IntRange {
        val range = equalizer?.bandLevelRange ?: return 0..0
        return range[0].toInt()..range[1].toInt()
    }
    fun setBand(index: Int, level: Int) {
        val eq = equalizer ?: return
        if (index in 0 until eq.numberOfBands.toInt()) {
            val range = bandRange()
            eq.setBandLevel(index.toShort(), level.coerceIn(range).toShort())
        }
    }
    fun setBass(value: Int) { bass?.setStrength(value.coerceIn(0, 1000).toShort()) }
    fun setLoudness(gainMb: Int) { loudness?.setTargetGain(gainMb.coerceIn(0, 1200)) }
    fun enable(enabled: Boolean) {
        equalizer?.enabled = enabled
        bass?.enabled = enabled
        loudness?.enabled = enabled
    }
    override fun close() {
        runCatching { equalizer?.release() }
        runCatching { bass?.release() }
        runCatching { loudness?.release() }
    }
}
