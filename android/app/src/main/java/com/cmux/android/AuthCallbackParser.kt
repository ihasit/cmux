package com.cmux.android

import org.json.JSONArray
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class StackAuthTokens(
    val refreshToken: String,
    val accessToken: String
)

class AuthCallbackParser {
    fun isAuthCallback(rawValue: String?): Boolean {
        val value = rawValue?.trim().orEmpty()
        if (value.isEmpty()) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return isAuthCallbackUri(uri)
    }

    fun parse(rawValue: String?, expectedState: String? = null): StackAuthTokens? {
        val value = rawValue?.trim().orEmpty()
        if (value.isEmpty()) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!isAuthCallbackUri(uri)) return null

        val query = queryParameters(uri.rawQuery)
        val callbackState = query["cmux_auth_state"]?.trim()
        if (expectedState != null && callbackState != expectedState) return null
        val refreshToken = query["stack_refresh"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val accessCookie = query["stack_access"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val accessToken = decodeAccessToken(accessCookie)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return StackAuthTokens(refreshToken = refreshToken, accessToken = accessToken)
    }

    private fun isAuthCallbackUri(uri: URI): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "cmux-ios" && scheme != "cmux-ios-dev") return false
        val target = uri.host?.trim('/')?.lowercase()
            ?: uri.path.trim('/').lowercase()
        return target == "auth-callback"
    }

    private fun queryParameters(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        val result = linkedMapOf<String, String>()
        rawQuery.split("&")
            .filter { it.isNotEmpty() }
            .forEach { item ->
                val separator = item.indexOf("=")
                val rawName = if (separator >= 0) item.substring(0, separator) else item
                val rawValue = if (separator >= 0) item.substring(separator + 1) else ""
                val name = urlDecode(rawName)
                if (!result.containsKey(name)) {
                    result[name] = urlDecode(rawValue)
                }
            }
        return result
    }

    private fun decodeAccessToken(accessCookie: String): String? {
        if (!accessCookie.startsWith("[")) return accessCookie
        val array = runCatching { JSONArray(accessCookie) }.getOrNull() ?: return null
        return array.optString(1).takeIf { it.isNotBlank() }
    }

    private fun urlDecode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}
