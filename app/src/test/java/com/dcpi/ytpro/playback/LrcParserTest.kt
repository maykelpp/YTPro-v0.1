package com.dcpi.ytpro.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test fun parsesMinuteAndSecond() { assertEquals(65000L, LrcParser.parse("[01:05]Hola").single().timeMs) }
    @Test fun parsesOneFractionDigit() { assertEquals(12300L, LrcParser.parse("[00:12.3]Hola").single().timeMs) }
    @Test fun parsesTwoFractionDigits() { assertEquals(12340L, LrcParser.parse("[00:12.34]Hola").single().timeMs) }
    @Test fun parsesThreeFractionDigits() { assertEquals(12345L, LrcParser.parse("[00:12.345]Hola").single().timeMs) }
    @Test fun parsesColonFraction() { assertEquals(12340L, LrcParser.parse("[00:12:34]Hola").single().timeMs) }
    @Test fun parsesMultipleTimestampsOnOneLine() { assertEquals(listOf(1000L, 2000L), LrcParser.parse("[00:01.00][00:02.00]Hola").map { it.timeMs }) }
    @Test fun sortsOutOfOrderLines() { assertEquals(listOf("primero", "segundo"), LrcParser.parse("[00:02]segundo\n[00:01]primero").map { it.text }) }
    @Test fun ignoresLinesWithoutTimestamp() { assertTrue(LrcParser.parse("solo texto").isEmpty()) }
    @Test fun ignoresEmptyLyricText() { assertTrue(LrcParser.parse("[00:01]").isEmpty()) }
    @Test fun trimsLyricWhitespace() { assertEquals("Hola", LrcParser.parse(" [00:01] Hola ").single().text) }
    @Test fun rejectsSecondsAboveRange() { assertTrue(LrcParser.parse("[00:60]No válido").isEmpty()) }
    @Test fun handlesZeroTime() { assertEquals(0L, LrcParser.parse("[00:00]Inicio").single().timeMs) }
    @Test fun handlesLargeMinuteValue() { assertEquals(6000000L, LrcParser.parse("[100:00]Largo").single().timeMs) }
    @Test fun returnsMinusOneBeforeFirstLine() { assertEquals(-1, LrcParser.activeIndex(LrcParser.parse("[00:02]A"), 1999)) }
    @Test fun selectsFirstLineAtExactBoundary() { assertEquals(0, LrcParser.activeIndex(LrcParser.parse("[00:02]A"), 2000)) }
    @Test fun selectsLatestLineBeforeNextBoundary() { assertEquals(0, LrcParser.activeIndex(LrcParser.parse("[00:02]A\n[00:04]B"), 3999)) }
    @Test fun advancesAtNextBoundary() { assertEquals(1, LrcParser.activeIndex(LrcParser.parse("[00:02]A\n[00:04]B"), 4000)) }
    @Test fun returnsMinusOneForEmptyList() { assertEquals(-1, LrcParser.activeIndex(emptyList(), 1000)) }
    @Test fun handlesPositionAfterLastLine() { assertEquals(1, LrcParser.activeIndex(LrcParser.parse("[00:01]A\n[00:02]B"), 90000)) }
    @Test fun supportsDotAndColonStylesTogether() { assertEquals(listOf(12300L, 12400L), LrcParser.parse("[00:12.3]A\n[00:12:40]B").map { it.timeMs }) }
}
