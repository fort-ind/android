package com.fortind.zenith.composer

import com.fortind.zenith.account.codePointLength
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.mfm.MfmContainer
import com.fortind.zenith.mfm.MfmMention
import com.fortind.zenith.mfm.MfmNode
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.Poll
import com.fortind.zenith.timeline.PollBody
import java.util.Calendar
import java.util.TimeZone

const val MIN_POLL_CHOICES = 2
const val MAX_POLL_CHOICES = 10
const val MAX_POLL_CHOICE_LENGTH = 150

data class PollDraft(
    val choices: List<String> = listOf("", ""),
    val multiple: Boolean = false,
    val endsAfterMillis: Long? = null,
    val endsAtMillis: Long? = null,
) {
    val cleanChoices: List<String> get() = choices.map(String::trim).filter(String::isNotEmpty)

    val isBlank: Boolean get() = cleanChoices.isEmpty()

    fun toBody(): PollBody = PollBody(
        choices = cleanChoices,
        multiple = multiple,
        expiresAtMillis = endsAtMillis,
        expiredAfterMillis = endsAfterMillis,
    )
}

enum class PollProblem { TooFewChoices, DuplicateChoices, ChoiceTooLong, EndInPast }

fun PollDraft.problem(nowMillis: Long): PollProblem? {
    val clean = cleanChoices
    return when {
        clean.size < MIN_POLL_CHOICES -> PollProblem.TooFewChoices
        clean.distinct().size != clean.size -> PollProblem.DuplicateChoices
        clean.any { it.codePointLength() > MAX_POLL_CHOICE_LENGTH } -> PollProblem.ChoiceTooLong
        endsAtMillis != null && endsAtMillis <= nowMillis -> PollProblem.EndInPast
        else -> null
    }
}

fun Poll.toDraft(nowMillis: Long): PollDraft = PollDraft(
    choices = choices.map { it.text },
    multiple = multiple,
    endsAtMillis = expiresAtMillis?.takeIf { it > nowMillis },
)

fun pollChanged(original: Poll?, draft: PollDraft?): Boolean = when {
    original == null -> draft != null
    draft == null -> true
    draft.endsAfterMillis != null -> true
    else -> draft.cleanChoices != original.choices.map { it.text } ||
        draft.multiple != original.multiple ||
        draft.endsAtMillis != original.expiresAtMillis
}

fun pollEditResetsVotes(original: Poll?, draft: PollDraft?): Boolean =
    original != null && original.totalVotes > 0 && pollChanged(original, draft)

fun customPollEnd(dateUtcMillis: Long, hour: Int, minute: Int, zone: TimeZone = TimeZone.getDefault()): Long {
    val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateUtcMillis }
    return Calendar.getInstance(zone).run {
        clear()
        set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
        timeInMillis
    }
}

fun mentionsIn(nodes: List<MfmNode>): List<MfmMention> = buildList {
    fun walk(items: List<MfmNode>) {
        items.forEach { node ->
            when (node) {
                is MfmMention -> add(node)
                is MfmContainer -> walk(node.children)
                else -> Unit
            }
        }
    }
    walk(nodes)
}

private fun isLocal(host: String?): Boolean = host == null || host.equals(FortInstance.HOST, ignoreCase = true)

fun replyPrefix(parent: Note, myUsername: String?): String {
    val isMe = { username: String, host: String? -> isLocal(host) && username.equals(myUsername, ignoreCase = true) }
    val handles = LinkedHashSet<String>()
    if (!isMe(parent.user.username, parent.user.host)) handles += parent.user.handle
    mentionsIn(parent.textMfm).forEach { mention ->
        val host = mention.host ?: parent.user.host
        if (!isMe(mention.username, host)) {
            handles += if (isLocal(host)) "@${mention.username}" else "@${mention.username}@$host"
        }
    }
    return if (handles.isEmpty()) "" else handles.joinToString(" ", postfix = " ")
}
