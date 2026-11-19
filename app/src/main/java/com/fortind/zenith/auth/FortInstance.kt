package com.fortind.zenith.auth

object FortInstance {
    const val HOST = "social.fort1nd.com"
    const val APP_NAME = "fort.android"
    const val SETTINGS_URL = "https://$HOST/settings"
    const val SCOPE_READ_NOTES_SCHEDULE = "read:notes-schedule"
    const val SCOPE_WRITE_NOTES_SCHEDULE = "write:notes-schedule"

    val SCOPES = listOf(
        "read:account",
        "read:notifications",
        "write:notifications",
        "write:following",
        "write:reactions",
        "write:notes",
        "write:drive",
        "write:votes",
        "read:favorites",
        "write:favorites",
        "write:account",
        "read:mutes",
        "write:mutes",
        "read:blocks",
        "write:blocks",
        "write:report-abuse",
        "read:following",
        "read:drive",
        SCOPE_READ_NOTES_SCHEDULE,
        SCOPE_WRITE_NOTES_SCHEDULE,
    )
}
