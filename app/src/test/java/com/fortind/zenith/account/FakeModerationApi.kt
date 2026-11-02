package com.fortind.zenith.account

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class FakeModerationApi : ModerationApi {
    val calls = CopyOnWriteArrayList<String>()
    val pages = mutableMapOf<ModerationKind, MutableList<List<ModerationEntry>>>()
    @Volatile var failure: IOException? = null

    override suspend fun list(token: String, kind: ModerationKind, untilId: String?, limit: Int): List<ModerationEntry> {
        calls += "list:$kind:${untilId.orEmpty()}"
        failure?.let { throw it }
        return pages[kind]?.let { queue -> if (queue.isEmpty()) emptyList() else queue.removeAt(0) }.orEmpty()
    }

    override suspend fun mute(token: String, userId: String, expiresAtMillis: Long?) = record("mute:$userId:${expiresAtMillis ?: "forever"}")

    override suspend fun unmute(token: String, userId: String) = record("unmute:$userId")

    override suspend fun hideRenotes(token: String, userId: String) = record("hideRenotes:$userId")

    override suspend fun showRenotes(token: String, userId: String) = record("showRenotes:$userId")

    override suspend fun block(token: String, userId: String) = record("block:$userId")

    override suspend fun unblock(token: String, userId: String) = record("unblock:$userId")

    private fun record(call: String) {
        calls += call
        failure?.let { throw it }
    }
}

fun moderationEntry(
    id: String,
    userId: String = "u$id",
    kind: ModerationKind = ModerationKind.Mute,
    expiresAtMillis: Long? = null,
) = ModerationEntry(id, kind, ModeratedUser(userId, "user$id", null, "User $id", null), expiresAtMillis)
