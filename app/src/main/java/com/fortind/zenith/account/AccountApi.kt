package com.fortind.zenith.account

import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.parseCurrentUser
import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.translatingAuthErrors

interface AccountApi {
    suspend fun updateProfile(token: String, edit: ProfileEdit): CurrentUser
}

class HttpAccountApi(private val http: FortHttp) : AccountApi {
    override suspend fun updateProfile(token: String, edit: ProfileEdit): CurrentUser =
        parseCurrentUser(translatingAuthErrors { http.postJson("/api/i/update", edit.toRequest(token)) })
}
