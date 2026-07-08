package com.cmux.android

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MobileTcpClientTest {
    private val servers = mutableListOf<ServerSocket>()
    private val clients = mutableListOf<MobileTcpClient>()

    @After
    fun tearDown() {
        clients.forEach { it.shutdown() }
        servers.forEach { it.close() }
    }

    @Test
    fun sendsLengthPrefixedFrameToHost() {
        val server = testServer()
        val accepted = CountDownLatch(1)
        val receivedPayloads = LinkedBlockingQueue<String>()
        Thread {
            server.accept().use { socket ->
                accepted.countDown()
                val input = BufferedInputStream(socket.getInputStream())
                receivedPayloads.add(readFrame(input))
            }
        }.start()
        val callback = RecordingCallback()
        val client = mobileClient(callback)

        client.connect(route(server))
        assertTrue(callback.opened.await(2, TimeUnit.SECONDS))
        assertTrue(accepted.await(2, TimeUnit.SECONDS))
        client.sendFrame("""{"method":"mobile.host.status"}""")

        assertEquals("""{"method":"mobile.host.status"}""", receivedPayloads.poll(2, TimeUnit.SECONDS))
    }

    @Test
    fun receivesLengthPrefixedFrameFromHostEvenWhenBytesArriveInChunks() {
        val server = testServer()
        Thread {
            server.accept().use { socket ->
                val output = BufferedOutputStream(socket.getOutputStream())
                val payload = """{"id":1,"ok":true}""".toByteArray(Charsets.UTF_8)
                val header = ByteBuffer.allocate(4).putInt(payload.size).array()
                output.write(header, 0, 2)
                output.flush()
                Thread.sleep(25)
                output.write(header, 2, 2)
                output.write(payload, 0, 5)
                output.flush()
                Thread.sleep(25)
                output.write(payload, 5, payload.size - 5)
                output.flush()
                Thread.sleep(25)
            }
        }.start()
        val callback = RecordingCallback()
        val client = mobileClient(callback)

        client.connect(route(server))

        assertTrue(callback.opened.await(2, TimeUnit.SECONDS))
        assertEquals("""{"id":1,"ok":true}""", callback.frames.poll(2, TimeUnit.SECONDS))
    }

    @Test
    fun rejectsInvalidFrameLength() {
        val server = testServer()
        Thread {
            server.accept().use { socket ->
                val output = BufferedOutputStream(socket.getOutputStream())
                output.write(ByteBuffer.allocate(4).putInt(0).array())
                output.flush()
                Thread.sleep(25)
            }
        }.start()
        val callback = RecordingCallback()
        val client = mobileClient(callback)

        client.connect(route(server))

        assertTrue(callback.opened.await(2, TimeUnit.SECONDS))
        assertEquals("invalid frame length: 0", callback.errors.poll(2, TimeUnit.SECONDS))
        assertEquals("invalid frame", callback.closes.poll(2, TimeUnit.SECONDS))
    }

    private fun testServer(): ServerSocket {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        servers.add(server)
        return server
    }

    private fun mobileClient(callback: RecordingCallback): MobileTcpClient {
        val client = MobileTcpClient(callback, connectTimeoutMillis = 1_000)
        clients.add(client)
        return client
    }

    private fun route(server: ServerSocket): CmuxRoute {
        return CmuxRoute(
            id = "loopback",
            kind = "debug_loopback",
            host = "127.0.0.1",
            port = server.localPort
        )
    }

    private fun readFrame(input: BufferedInputStream): String {
        val header = input.readNBytes(4)
        val length = ByteBuffer.wrap(header).int
        val body = input.readNBytes(length)
        return String(body, Charsets.UTF_8)
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
