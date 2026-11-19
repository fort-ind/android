package com.fortind.zenith.mfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnixTimeTest {

    private val now = 1_800_000_000_000L
    private val nowSeconds = now / 1_000L

    private fun agoOf(secondsAgo: Long, extraMillis: Long = 0L): Ago = ago(nowSeconds - secondsAgo, now + extraMillis)

    private fun aheadOf(secondsAhead: Long): Ago = ago(nowSeconds + secondsAhead, now)

    @Test
    fun parsesPlainDigitsLikeParseInt() {
        assertEquals(1_794_775_475L, parseUnixSeconds("1794775475"))
        assertEquals(12L, parseUnixSeconds("  12abc"))
        assertEquals(1L, parseUnixSeconds("1.5"))
        assertEquals(-5L, parseUnixSeconds("-5"))
        assertEquals(5L, parseUnixSeconds("+5"))
        assertEquals(0L, parseUnixSeconds("0"))
    }

    @Test
    fun refusesWhatIsNotATime() {
        assertNull(parseUnixSeconds(""))
        assertNull(parseUnixSeconds("soon"))
        assertNull(parseUnixSeconds("-"))
        assertNull(parseUnixSeconds("1234567890123"))
        assertNull(parseUnixSeconds("999999999999"))
        assertNull(parseUnixSeconds("-999999999999"))
    }

    @Test
    fun theRangeEndsAreAccepted() {
        assertEquals(253_402_300_799L, parseUnixSeconds("253402300799"))
        assertEquals(-62_135_596_800L, parseUnixSeconds("-62135596800"))
    }

    @Test
    fun justNowCoversTenSecondsBackToThreeSecondsAhead() {
        assertEquals(Ago.JustNow, agoOf(0))
        assertEquals(Ago.JustNow, agoOf(9))
        assertEquals(Ago.JustNow, aheadOf(3))
        assertEquals(Ago.Past(AgoUnit.Seconds, 10), agoOf(10))
    }

    @Test
    fun secondsAreFlooredAndMinutesAreFloored() {
        assertEquals(Ago.Past(AgoUnit.Seconds, 45), agoOf(45, extraMillis = 900))
        assertEquals(Ago.Past(AgoUnit.Seconds, 59), agoOf(59))
        assertEquals(Ago.Past(AgoUnit.Minutes, 1), agoOf(60))
        assertEquals(Ago.Past(AgoUnit.Minutes, 17), agoOf(17 * 60 + 59))
    }

    @Test
    fun largerUnitsAreRoundedHalfUp() {
        assertEquals(Ago.Past(AgoUnit.Hours, 1), agoOf(3_600))
        assertEquals(Ago.Past(AgoUnit.Hours, 2), agoOf(5_400))
        assertEquals(Ago.Past(AgoUnit.Hours, 1), agoOf(5_399))
        assertEquals(Ago.Past(AgoUnit.Days, 1), agoOf(86_400))
        assertEquals(Ago.Past(AgoUnit.Weeks, 1), agoOf(604_800))
        assertEquals(Ago.Past(AgoUnit.Months, 1), agoOf(2_592_000))
        assertEquals(Ago.Past(AgoUnit.Years, 1), agoOf(31_536_000))
        assertEquals(Ago.Past(AgoUnit.Years, 3), agoOf(31_536_000L * 3))
    }

    @Test
    fun theFutureUsesTheSameLadderWithStrictBoundaries() {
        assertEquals(Ago.Ahead(AgoUnit.Seconds, 30), aheadOf(30))
        assertEquals(Ago.Ahead(AgoUnit.Minutes, 1), aheadOf(61))
        assertEquals(Ago.Ahead(AgoUnit.Minutes, 59), aheadOf(3_599))
        assertEquals(Ago.Ahead(AgoUnit.Hours, 1), aheadOf(3_601))
        assertEquals(Ago.Ahead(AgoUnit.Days, 2), aheadOf(172_900))
        assertEquals(Ago.Ahead(AgoUnit.Years, 2), aheadOf(31_536_000L * 2 + 1))
    }

    @Test
    fun aTimeExactlyAMinuteAheadReadsAsZeroSecondsLikeSharkey() {
        assertEquals(Ago.Ahead(AgoUnit.Seconds, 0), aheadOf(60))
    }

    @Test
    fun theFarthestPastAndFutureTimesStayInYears() {
        val past = ago(-62_135_596_800L, now) as Ago.Past
        val future = ago(253_402_300_799L, now) as Ago.Ahead

        assertEquals(AgoUnit.Years, past.unit)
        assertEquals(AgoUnit.Years, future.unit)
    }
}
