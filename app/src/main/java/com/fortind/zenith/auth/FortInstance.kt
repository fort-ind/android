package com.fortind.zenith.auth

object FortInstance {
    const val HOST = "social.fort1nd.com"
    const val APP_NAME = "fort.android"
    const val SETTINGS_URL = "https://$HOST/settings"

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
    )
}
