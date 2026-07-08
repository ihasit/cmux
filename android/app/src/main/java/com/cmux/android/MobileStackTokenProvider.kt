package com.cmux.android

import android.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class StackTokenPair(
    val refreshToken: String?,
    val accessToken: String?
)

interface StackAccessTokenProvider {
    fun likelyValidAccessToken(): StackTokenPair
    fun forceRefreshAccessToken(): StackTokenPair
}

class StoredStackAccessTokenProvider(
    private val authStore: MobileAuthStore,
    private val refresher: StackTokenRefresher = StackTokenRefresher(),
    private val onTokensChanged: () -> Unit = {}
) : StackAccessTokenProvider {
    @Synchronized
    override fun likelyValidAccessToken(): StackTokenPair {
        val refreshToken = authStore.stackRefreshToken()
        val accessToken = authStore.stackAccessToken()
        if (refreshToken.isNullOrBlank()) {
            return StackTokenPair(refreshToken = null, accessToken = accessToken)
        }
        if (isTokenFreshEnough(accessToken)) {
            return StackTokenPair(refreshToken = refreshToken, accessToken = accessToken)
        }
        return when (val outcome = refresher.refresh(refreshToken)) {
            is StackRefreshOutcome.Success -> {
                val nextRefreshToken = outcome.refreshToken ?: refreshToken
                authStore.saveStackTokensIfRefreshTokenMatches(refreshToken, outcome.accessToken, nextRefreshToken)
                onTokensChanged()
                StackTokenPair(refreshToken = nextRefreshToken, accessToken = outcome.accessToken)
            }
            StackRefreshOutcome.DefinitivelyRejected -> {
                authStore.clearStackTokensIfRefreshTokenMatches(refreshToken)
                onTokensChanged()
                StackTokenPair(refreshToken = null, accessToken = null)
            }
            StackRefreshOutcome.TransientFailure -> {
                StackTokenPair(
                    refreshToken = refreshToken,
                    accessToken = accessToken?.takeUnless(::isTokenExpired)
                )
            }
        }
    }

    @Synchronized
    override fun forceRefreshAccessToken(): StackTokenPair {
        val refreshToken = authStore.stackRefreshToken() ?: return StackTokenPair(refreshToken = null, accessToken = null)
        return when (val outcome = refresher.refresh(refreshToken)) {
            is StackRefreshOutcome.Success -> {
                val nextRefreshToken = outcome.refreshToken ?: refreshToken
                authStore.saveStackTokensIfRefreshTokenMatches(refreshToken, outcome.accessToken, nextRefreshToken)
                onTokensChanged()
                StackTokenPair(refreshToken = nextRefreshToken, accessToken = outcome.accessToken)
            }
            StackRefreshOutcome.DefinitivelyRejected -> {
                authStore.clearStackTokensIfRefreshTokenMatches(refreshToken)
                onTokensChanged()
                StackTokenPair(refreshToken = null, accessToken = null)
            }
            StackRefreshOutcome.TransientFailure -> {
                StackTokenPair(refreshToken = refreshToken, accessToken = null)
            }
        }
    }
}

sealed class StackRefreshOutcome {
    data class Success(val accessToken: String, val refreshToken: String? = null) : StackRefreshOutcome()
    data object DefinitivelyRejected : StackRefreshOutcome()
    data object TransientFailure : StackRefreshOutcome()
}

class StackTokenRefresher(
    private val baseUrl: String = BuildConfig.CMUX_STACK_BASE_URL,
    private val projectId: String = BuildConfig.CMUX_STACK_PROJECT_ID,
    private val publishableClientKey: String = BuildConfig.CMUX_STACK_PUBLISHABLE_CLIENT_KEY,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    fun refresh(refreshToken: String): StackRefreshOutcome {
        val trimmedRefreshToken = refreshToken.trim()
        if (trimmedRefreshToken.isEmpty()) return StackRefreshOutcome.DefinitivelyRejected
        val trimmedBaseUrl = baseUrl.trim().trimEnd('/')
        val trimmedProjectId = projectId.trim()
        val trimmedPublishableKey = publishableClientKey.trim()
        if (trimmedBaseUrl.isEmpty() || trimmedProjectId.isEmpty() || trimmedPublishableKey.isEmpty()) {
            return StackRefreshOutcome.TransientFailure
        }

        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", trimmedRefreshToken)
            .add("client_id", trimmedProjectId)
            .add("client_secret", trimmedPublishableKey)
            .build()
        val request = Request.Builder()
            .url("$trimmedBaseUrl/api/v1/auth/oauth/token")
            .post(body)
            .header("x-stack-project-id", trimmedProjectId)
            .header("x-stack-publishable-client-key", trimmedPublishableKey)
            .header("x-stack-access-type", "client")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val json = response.body?.string()
                            ?.let { JSONObject(it) }
                        val accessToken = json
                            ?.optStrictString("access_token")
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                        if (accessToken == null) {
                            StackRefreshOutcome.TransientFailure
                        } else {
                            val refreshToken = json.optStrictString("refresh_token")
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                            StackRefreshOutcome.Success(accessToken, refreshToken)
                        }
                    }
                    400, 401 -> {
                        val errorCode = response.body?.string()
                            ?.let(::stackOAuthErrorCode)
                        if (isDefinitiveRefreshTokenError(errorCode)) {
                            StackRefreshOutcome.DefinitivelyRejected
                        } else {
                            StackRefreshOutcome.TransientFailure
                        }
                    }
                    else -> StackRefreshOutcome.TransientFailure
                }
            }
        }.getOrElse { StackRefreshOutcome.TransientFailure }
    }
}

private fun stackOAuthErrorCode(body: String): String? {
    return runCatching {
        JSONObject(body).optStrictString("error")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

private fun isDefinitiveRefreshTokenError(errorCode: String?): Boolean {
    return errorCode == "invalid_grant" || errorCode == "invalid_refresh_token"
}

private fun isTokenFreshEnough(accessToken: String?): Boolean {
    val payload = jwtPayload(accessToken) ?: return false
    val expiresAt = payload.optLong("exp", 0L)
    val issuedAt = payload.optLong("iat", 0L)
    val nowSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
    return expiresAt - nowSeconds > 20 && nowSeconds - issuedAt < 75
}

private fun isTokenExpired(accessToken: String?): Boolean {
    val payload = jwtPayload(accessToken) ?: return true
    val expiresAt = payload.optLong("exp", 0L)
    val nowSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
    return expiresAt <= nowSeconds
}

private fun jwtPayload(accessToken: String?): JSONObject? {
    val segments = accessToken?.trim()?.split(".") ?: return null
    if (segments.size < 2) return null
    return runCatching {
        val payload = Base64.decode(segments[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(String(payload, Charsets.UTF_8))
    }.getOrNull()
}

private fun JSONObject.optStrictString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return opt(name) as? String
}
