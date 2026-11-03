package com.fortind.zenith.account

import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.translateAuthError
import org.json.JSONObject

const val REPORT_MAX_CODE_POINTS = 2048

fun reportNotePrefix(webUrl: String, remoteUrl: String?): String {
    val line = if (remoteUrl != null) "Note: $remoteUrl" else "Local Note: $webUrl"
    return "$line\n-----\n"
}

fun reportTextLimit(prefix: String): Int = REPORT_MAX_CODE_POINTS - prefix.codePointCount(0, prefix.length)

interface ReportApi {
    suspend fun report(token: String, userId: String, comment: String)
}

class HttpReportApi(private val http: FortHttp) : ReportApi {
    override suspend fun report(token: String, userId: String, comment: String) {
        val body = JSONObject().put("i", token).put("userId", userId).put("comment", comment)
        try {
            http.postDiscardingResponse("/api/users/report-abuse", body)
        } catch (e: HttpStatusException) {
            throw translateAuthError(e)
        }
    }
}
