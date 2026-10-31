package com.fortind.zenith.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollVotesTest {

    private fun poll(multiple: Boolean = false, expiresAt: Long? = null, voted: Set<Int> = emptySet()) = Poll(
        choices = listOf(3, 0, 5).mapIndexed { index, votes -> PollChoice("c$index", votes, index in voted) },
        multiple = multiple,
        expiresAtMillis = expiresAt,
    )

    @Test
    fun aPollWithoutAVoteOfMineCanBeVotedOnUntilItEnds() {
        assertTrue(poll().canVote(now = 10))
        assertTrue(poll(expiresAt = 11).canVote(now = 10))
        assertFalse(poll(expiresAt = 10).canVote(now = 10))
        assertFalse(poll(voted = setOf(1)).canVote(now = 10))
    }

    @Test
    fun votingCountsAndMarksEachPickedChoiceOnce() {
        val voted = poll(multiple = true).withVotes(listOf(0, 2))

        assertEquals(listOf(4, 0, 6), voted.choices.map { it.votes })
        assertEquals(listOf(true, false, true), voted.choices.map { it.isVoted })
        assertTrue(voted.hasVoted)
        assertEquals(voted, voted.withVotes(listOf(0, 2)))
    }

    @Test
    fun takingBackVotesRestoresTheCountsOfThoseChoicesOnly() {
        val voted = poll(multiple = true).withVotes(listOf(0, 2))

        val restored = voted.withoutVotes(listOf(2))

        assertEquals(listOf(4, 0, 5), restored.choices.map { it.votes })
        assertEquals(listOf(true, false, false), restored.choices.map { it.isVoted })
        assertEquals(poll(multiple = true), restored.withoutVotes(listOf(0)))
    }

    @Test
    fun otherPeoplesVotesBumpTheCountWithoutMarkingMine() {
        val updated = poll().withOtherVote(1)

        assertEquals(listOf(3, 1, 5), updated.choices.map { it.votes })
        assertFalse(updated.hasVoted)
    }
}
