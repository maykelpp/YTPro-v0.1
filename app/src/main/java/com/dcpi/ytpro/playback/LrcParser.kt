package com.dcpi.ytpro.playback

data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {
    private val timestamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")

    fun parse(source: String): List<LyricLine> = source.lineSequence().flatMap { rawLine ->
        val line = rawLine.trim()
        val matches = timestamp.findAll(line).toList()
        if (matches.isEmpty()) return@flatMap emptySequence()
        val text = line.substring(matches.last().range.last + 1).trim()
        if (text.isEmpty()) return@flatMap emptySequence()
        matches.mapNotNull { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            if (seconds >= 60) return@mapNotNull null
            val fractionText = match.groupValues[3]
            val fraction = when (fractionText.length) {
                1 -> (fractionText.toLongOrNull() ?: 0L) * 100L
                2 -> (fractionText.toLongOrNull() ?: 0L) * 10L
                3 -> fractionText.toLongOrNull() ?: 0L
                else -> 0L
            }
            LyricLine((minutes * 60 + seconds) * 1000 + fraction, text)
        }.asSequence()
    }.sortedBy { it.timeMs }.toList()

    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var answer = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) { answer = mid; low = mid + 1 }
            else high = mid - 1
        }
        return answer
    }
}
