package com.cmux.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AuthCallbackParserTest {
    private val parser = AuthCallbackParser()

    @Test
    fun parsesAuthCallbackWithRawAccessToken() {
        val tokens = parser.parse(
            "cmux-ios://auth-callback?stack_refresh=refresh-1&stack_access=access-1&cmux_auth_state=state-1",
            expectedState = "state-1"
        )

        assertEquals("refresh-1", tokens?.refreshToken)
        assertEquals("access-1", tokens?.accessToken)
    }

    @Test
    fun parsesAuthCallbackWithStackAccessCookiePair() {
        val accessCookie = encode("[\"refresh-2\",\"access-2\"]")
        val tokens = parser.parse(
            "cmux-ios-dev://auth-callback?stack_refresh=refresh-2&stack_access=$accessCookie&cmux_auth_state=state-2",
            expectedState = "state-2"
        )

        assertEquals("refresh-2", tokens?.refreshToken)
        assertEquals("access-2", tokens?.accessToken)
    }

    @Test
    fun rejectsCallbackWhenStateDoesNotMatch() {
        val tokens = parser.parse(
            "cmux-ios://auth-callback?stack_refresh=refresh-1&stack_access=access-1&cmux_auth_state=other",
            expectedState = "state-1"
        )

        assertNull(tokens)
    }

    @Test
    fun identifiesAuthCallbackWithoutParsingTokens() {
        assertEquals(true, parser.isAuthCallback("cmux-ios://auth-callback?other=1"))
        assertEquals(false, parser.isAuthCallback("cmux-ios://attach?v=2&r=100.64.0.5:58465"))
    }

    @Test
    fun keepsFirstQueryValueForTokenParameters() {
        val tokens = parser.parse(
            "cmux-ios://auth-callback?stack_refresh=refresh-1&stack_refresh=attacker&stack_access=access-1&stack_access=attacker",
            expectedState = null
        )

        assertEquals("refresh-1", tokens?.refreshToken)
        assertEquals("access-1", tokens?.accessToken)
    }

    @Test
    fun ignoresPairingUrls() {
        assertNull(parser.parse("cmux-ios://attach?v=2&r=100.64.0.5:58465"))
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}
