package com.cmux.android

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MobileWebSocketClientTest {
    private lateinit var server: MockWebServer
    private val clients = mutableListOf<MobileWebSocketClient>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        clients.forEach { it.shutdown() }
        try {
            server.shutdown()
        } catch (error: IOException) {
            if (error.message != "Gave up waiting for queue to shut down") {
                throw error
            }
        }
    }

    @Test
    fun sendsLengthPrefixedBinaryFrameToHost() {
        val receivedPayloads = LinkedBlockingQueue<String>()
        val accepted = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    accepted.countDown()
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    receivedPayloads.add(readSingleFrame(bytes.toByteArray()))
                }
            })
        )
        val callback = RecordingCallback()
        val client = mobileClient(callback)

        client.connect(route())
        assertTrue(callback.opened.await(2, TimeUnit.SECONDS))
        assertTrue(accepted.await(2, TimeUnit.SECONDS))
        client.sendFrame("""{"method":"mobile.workspace.list"}""")

        assertEquals("""{"method":"mobile.workspace.list"}""", receivedPayloads.poll(2, TimeUnit.SECONDS))
        client.close("test complete")
    }

    @Test
    fun receivesLengthPrefixedBinaryFramesAcrossWebSocketMessages() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val payload = """{"id":1,"ok":true}""".toByteArray(Charsets.UTF_8)
                    val frame = ByteBuffer.allocate(4 + payload.size)
                        .putInt(payload.size)
                        .put(payload)
                        .array()
                    webSocket.send(ByteString.of(*frame.copyOfRange(0, 2)))
                    webSocket.send(ByteString.of(*frame.copyOfRange(2, 9)))
                    webSocket.send(ByteString.of(*frame.copyOfRange(9, frame.size)))
                }
            })
        )
        val callback = RecordingCallback()
        val client = mobileClient(callback)

        client.connect(route())

        assertTrue(callback.opened.await(2, TimeUnit.SECONDS))
        assertEquals("""{"id":1,"ok":true}""", callback.frames.poll(2, TimeUnit.SECONDS))
        client.close("test complete")
    }

    @Test
    fun acceptsUppercaseWebSocketScheme() {
        val accepted = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    accepted.countDown()
                }
            })
        )
        val callback = RecordingCallback()
        val client = mobileClient(callback)
        val baseRoute = route()
        val uppercaseRoute = baseRoute.copy(url = baseRoute.url?.replace("ws://", "WS://"))

        client.connect(uppercaseRoute)

        assertTrue(callback.opened.await(2, TimeUnit.SECONDS))
        assertTrue(accepted.await(2, TimeUnit.SECONDS))
        client.close("test complete")
    }

    @Test
    fun rejectsInvalidBinaryFrameLength() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(ByteString.of(*ByteBuffer.allocate(4).putInt(0).array()))
                    webSocket.close(1000, "invalid frame sent")
                }
            })
        )
        val callback = RecordingCallback()
        val client = mobileClient(callback)

        client.connect(route())

        assertTrue(callback.opened.await(2, TimeUnit.SECONDS))
        assertEquals("invalid websocket frame length: 0", callback.errors.poll(2, TimeUnit.SECONDS))
        assertEquals("invalid websocket frame", callback.closes.poll(2, TimeUnit.SECONDS))
    }

    @Test
    fun rejectsNonWebSocketRouteWithoutNetworkRequest() {
        val callback = RecordingCallback()
        val client = mobileClient(callback)

        client.connect(
            CmuxRoute(
                id = "bad",
                kind = "websocket",
                host = "",
                port = 0,
                url = "https://cmux.example.test/mobile"
            )
        )

        assertEquals("invalid websocket route", callback.errors.poll(2, TimeUnit.SECONDS))
        assertEquals("invalid websocket route", callback.closes.poll(2, TimeUnit.SECONDS))
        assertEquals(0, server.requestCount)
    }

    private fun mobileClient(callback: RecordingCallback): MobileWebSocketClient {
        val client = MobileWebSocketClient(callback)
        clients.add(client)
        return client
    }

    private fun route(): CmuxRoute {
        return CmuxRoute(
            id = "websocket",
            kind = "websocket",
            host = "",
            port = 0,
            url = server.url("/mobile").toString().replace("http://", "ws://")
        )
    }

    private fun readSingleFrame(bytes: ByteArray): String {
        val length = ByteBuffer.wrap(bytes, 0, 4).int
        return String(bytes.copyOfRange(4, 4 + length), Charsets.UTF_8)
    }

    private class RecordingCallback : MobileFrameClient.Callback {
        val opened = CountDownLatch(1)
        val frames = LinkedBlockingQueue<String>()
        val errors = LinkedBlockingQueue<String>()
        val closes = LinkedBlockingQueue<String>()

        override fun onOpen() {
            opened.countDown()
        }

        override fun onFrame(payload: String) {
            frames.add(payload)
        }

        override fun onClose(reason: String) {
            closes.add(reason)
        }

        override fun onError(message: String) {
            errors.add(message)
        }
    }
}
