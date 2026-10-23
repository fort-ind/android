package com.fortind.zenith.auth

/** fort.android talks to exactly one Sharkey instance; there is deliberately no way to change it. */
object FortInstance {
    const val HOST = "social.fort1nd.com"
    const val APP_NAME = "fort.android"
    const val SETTINGS_URL = "https://$HOST/settings"

    /** Misskey permission kinds requested during MiAuth. `read:account` is what `/api/i` needs. */
    val SCOPES = listOf("read:account")
}
