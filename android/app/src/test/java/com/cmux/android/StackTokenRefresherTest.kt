package com.cmux.android

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StackTokenRefresherTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun refreshPostsStackOAuthTokenRequestAndReturnsAccessToken() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"new-access-token","refresh_token":"new-refresh-token"}""")
        )
        val refresher = refresher()

        val outcome = refresher.refresh(" refresh token/with spaces ")

        assertEquals(StackRefreshOutcome.Success("new-access-token", "new-refresh-token"), outcome)
        val request = server.takeRequest()
        assertEquals("/stack-base/api/v1/auth/oauth/token", request.path)
        assertEquals("POST", request.method)
        assertEquals("project-1", request.getHeader("x-stack-project-id"))
        assertEquals("publishable-1", request.getHeader("x-stack-publishable-client-key"))
        assertEquals("client", request.getHeader("x-stack-access-type"))
        assertEquals(
            "grant_type=refresh_token&refresh_token=refresh%20token%2Fwith%20spaces&client_id=project-1&client_secret=publishable-1",
            request.body.readUtf8()
        )
    }

    @Test
    fun refreshTreatsInvalidGrantStatusAsDefinitiveRejection() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))

        val outcome = refresher().refresh("refresh-token")

        assertEquals(StackRefreshOutcome.DefinitivelyRejected, outcome)
    }

    @Test
    fun refreshTreatsServerErrorsAsTransientFailure() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        val outcome = refresher().refresh("refresh-token")

        assertEquals(StackRefreshOutcome.TransientFailure, outcome)
    }

    @Test
    fun refreshTreatsMalformedSuccessBodyAsTransientFailure() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val outcome = refresher().refresh("refresh-token")

        assertEquals(StackRefreshOutcome.TransientFailure, outcome)
    }

    @Test
    fun refreshRejectsBlankRefreshTokenWithoutNetworkCall() {
        val outcome = refresher().refresh("   ")

        assertEquals(StackRefreshOutcome.DefinitivelyRejected, outcome)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun refreshTreatsMissingConfigurationAsTransientFailureWithoutNetworkCall() {
        val outcome = StackTokenRefresher(
            baseUrl = server.url("/").toString(),
            projectId = "",
            publishableClientKey = "publishable-1"
        ).refresh("refresh-token")

        assertEquals(StackRefreshOutcome.TransientFailure, outcome)
        assertEquals(0, server.requestCount)
    }

    private fun refresher(): StackTokenRefresher {
        val url = server.url("/stack-base/").toString()
        assertTrue(url.endsWith("/"))
        return StackTokenRefresher(
            baseUrl = url,
            projectId = "project-1",
            publishableClientKey = "publishable-1"
        )
    }
}
