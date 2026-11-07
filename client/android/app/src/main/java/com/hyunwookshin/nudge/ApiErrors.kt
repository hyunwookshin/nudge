package com.hyunwookshin.nudge

import org.json.JSONObject
import retrofit2.Response

object ApiErrors {
    /** The server's {"message": "..."} from an error response (e.g. "Not part of the project's date range"). */
    fun message(response: Response<*>, fallback: String): String {
        val body = runCatching { response.errorBody()?.string() }.getOrNull() ?: return fallback
        return runCatching { JSONObject(body).optString("message", fallback) }
            .getOrDefault(fallback)
            .ifBlank { fallback }
    }
}
