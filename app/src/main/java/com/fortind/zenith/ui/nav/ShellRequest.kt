package com.fortind.zenith.ui.nav

enum class ShellRequest {
    SEARCH,
    SIGN_IN,
    ;

    companion object {
        fun fromName(name: String?): ShellRequest? = entries.firstOrNull { it.name == name }
    }
}
