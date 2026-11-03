package com.fortind.zenith.account

import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.parseCurrentUser
import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.translatingAuthErrors
import org.json.JSONObject

interface AccountApi {
    suspend fun updateProfile(token: String, edit: ProfileEdit): CurrentUser

    suspend fun updateSetting(token: String, key: String, value: Any): CurrentUser
}

class HttpAccountApi(private val http: FortHttp) : AccountApi {
    override suspend fun updateProfile(token: String, edit: ProfileEdit): CurrentUser =
        parseCurrentUser(translatingAuthErrors { http.postJson("/api/i/update", edit.toRequest(token)) })

    override suspend fun updateSetting(token: String, key: String, value: Any): CurrentUser =
        parseCurrentUser(translatingAuthErrors { http.postJson("/api/i/update", JSONObject().put("i", token).put(key, value)) })
}
