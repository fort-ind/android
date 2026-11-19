package com.fortind.zenith.composer

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleRulesTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")

    private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0, millis: Int = 0): Long =
        Calendar.getInstance(zone).run {
            clear()
            set(year, month - 1, day, hour, minute, second)
            set(Calendar.MILLISECOND, millis)
            timeInMillis
        }

    @Test
    fun theDefaultTimeIsTheNextWholeHour() {
        val now = at(utc, 2026, 11, 20, 14, 20)

        assertEquals(at(utc, 2026, 11, 20, 15, 0), nextWholeHour(now, utc))
    }

    @Test
    fun anExactHourStillMovesToTheNextOne() {
        val now = at(utc, 2026, 11, 20, 14, 0)

        assertEquals(at(utc, 2026, 11, 20, 15, 0), nextWholeHour(now, utc))
    }

    @Test
    fun theDefaultNeverFallsInsideTheMinimumLead() {
        val now = at(utc, 2026, 11, 20, 14, 59, 30)

        assertEquals(at(utc, 2026, 11, 20, 16, 0), nextWholeHour(now, utc))
    }

    @Test
    fun aHourExactlyOneMinuteAwayIsAllowed() {
        val now = at(utc, 2026, 11, 20, 13, 59, 0)

        assertEquals(at(utc, 2026, 11, 20, 14, 0), nextWholeHour(now, utc))
    }

    @Test
    fun theDefaultFollowsTheLocalZoneNotUtc() {
        val now = at(losAngeles, 2026, 11, 20, 23, 30)

        assertEquals(at(losAngeles, 2026, 11, 21, 0, 0), nextWholeHour(now, losAngeles))
    }

    @Test
    fun thePickerDateIsTheLocalDayAtUtcMidnight() {
        val instant = at(losAngeles, 2026, 11, 20, 23, 30)

        assertEquals(at(utc, 2026, 11, 20, 0, 0), localDateAsUtcMillis(instant, losAngeles))
        assertEquals(23, hourOf(instant, losAngeles))
        assertEquals(30, minuteOf(instant, losAngeles))
    }

    @Test
    fun scheduledPostsCannotBeLocalOnly() {
        assertEquals(ScheduleProblem.LocalOnly, scheduleProblem(localOnly = true, contentWarning = null, poll = null))
    }

    @Test
    fun theContentWarningLimitCountsCodePointsNotUtf16Units() {
        val hundredEmoji = "😀".repeat(SCHEDULE_CW_LIMIT)

        assertNull(scheduleProblem(false, hundredEmoji, null))
        assertEquals(ScheduleProblem.ContentWarningTooLong, scheduleProblem(false, hundredEmoji + "a", null))
    }

    @Test
    fun scheduledPollChoicesAreShorterThanNormalOnes() {
        val fits = PollDraft(choices = listOf("a".repeat(SCHEDULE_POLL_CHOICE_LIMIT), "b"))
        val tooLong = PollDraft(choices = listOf("a".repeat(SCHEDULE_POLL_CHOICE_LIMIT + 1), "b"))

        assertNull(scheduleProblem(false, null, fits))
        assertEquals(ScheduleProblem.PollChoiceTooLong, scheduleProblem(false, null, tooLong))
    }

    @Test
    fun theTimeMustBeAtLeastAMinuteAway() {
        val now = 1_000_000L

        assertEquals(ScheduleProblem.TooSoon, scheduleTimeProblem(now + SCHEDULE_MIN_LEAD_MILLIS - 1, now, null))
        assertNull(scheduleTimeProblem(now + SCHEDULE_MIN_LEAD_MILLIS, now, null))
    }

    @Test
    fun aCustomPollEndMustComeAfterThePostGoesOut() {
        val now = 1_000_000L
        val postAt = now + 3_600_000L

        assertEquals(
            ScheduleProblem.PollEndsBeforePost,
            scheduleTimeProblem(postAt, now, PollDraft(endsAtMillis = postAt - 1)),
        )
        assertNull(scheduleTimeProblem(postAt, now, PollDraft(endsAtMillis = postAt + 1)))
        assertNull(scheduleTimeProblem(postAt, now, PollDraft(endsAfterMillis = 600_000L)))
    }
}
