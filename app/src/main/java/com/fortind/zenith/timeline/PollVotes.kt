package com.fortind.zenith.timeline

val Poll.hasVoted: Boolean get() = choices.any { it.isVoted }

fun Poll.isEnded(now: Long): Boolean = expiresAtMillis?.let { it <= now } == true

fun Poll.canVote(now: Long): Boolean = !hasVoted && !isEnded(now)

fun Poll.withVotes(picked: Collection<Int>): Poll = copy(
    choices = choices.mapIndexed { index, choice ->
        if (index in picked && !choice.isVoted) choice.copy(votes = choice.votes + 1, isVoted = true) else choice
    },
)

fun Poll.withoutVotes(picked: Collection<Int>): Poll = copy(
    choices = choices.mapIndexed { index, choice ->
        if (index in picked && choice.isVoted) choice.copy(votes = (choice.votes - 1).coerceAtLeast(0), isVoted = false) else choice
    },
)

fun Poll.withOtherVote(index: Int): Poll = copy(
    choices = choices.mapIndexed { i, choice -> if (i == index) choice.copy(votes = choice.votes + 1) else choice },
)
