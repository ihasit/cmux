package com.cmux.android

import java.nio.ByteBuffer
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class MobileWebSocketClient(
    private val callback: MobileFrameClient.Callback
) : MobileFrameClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val closed = AtomicBoolean(true)
    private val readBuffer = ByteArrayOutputStream()
    private var webSocket: WebSocket? = null

    override fun connect(route: CmuxRoute) {
        close("reconnecting")
        synchronized(readBuffer) {
            readBuffer.reset()
        }
        val url = route.url?.trim().orEmpty()
        val lowerUrl = url.lowercase()
        if (!lowerUrl.startsWith("ws://") && !lowerUrl.startsWith("wss://")) {
            callback.onError("invalid websocket route")
            callback.onClose("invalid websocket route")
            return
        }
        closed.set(false)
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener())
    }

    override fun sendFrame(payload: String) {
        val socket = webSocket
        if (socket == null) {
            callback.onError("not connected")
            return
        }
        val bytes = payload.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FRAME_BYTES) {
            callback.onError("websocket frame too large: ${bytes.size}")
            close("frame too large")
            return
        }
        val frame = ByteBuffer.allocate(4 + bytes.size)
            .putInt(bytes.size)
            .put(bytes)
            .array()
        if (!socket.send(ByteString.of(*frame))) {
            callback.onError("websocket send failed")
            close("send failed")
        }
    }

    override fun close(reason: String) {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        val socket = webSocket
        webSocket = null
        socket?.close(1000, reason.take(120))
        callback.onClose(reason)
    }

    override fun shutdown() {
        close("activity destroyed")
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun listener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!closed.get()) {
                    callback.onOpen()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                readFrames(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!closed.get()) {
                    callback.onError("unexpected websocket text frame")
                    close("invalid websocket frame")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
                close(reason.ifBlank { "websocket closing" })
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close(reason.ifBlank { "websocket closed" })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closed.get()) {
                    callback.onError(t.message ?: t.javaClass.simpleName)
                    close("websocket failed")
                }
            }
        }
    }

    private fun readFrames(bytes: ByteArray) {
        val frames = mutableListOf<String>()
        synchronized(readBuffer) {
            readBuffer.write(bytes)
            var buffer = readBuffer.toByteArray()
            var offset = 0
            while (buffer.size - offset >= 4) {
                val length = ByteBuffer.wrap(buffer, offset, 4).int
                if (length <= 0 || length > MAX_FRAME_BYTES) {
                    callback.onError("invalid websocket frame length: $length")
                    readBuffer.reset()
                    close("invalid websocket frame")
                    return
                }
                if (buffer.size - offset < 4 + length) {
                    break
                }
                val payload = buffer.copyOfRange(offset + 4, offset + 4 + length)
                frames.add(String(payload, Charsets.UTF_8))
                offset += 4 + length
            }
            if (offset > 0) {
                readBuffer.reset()
                if (offset < buffer.size) {
                    readBuffer.write(buffer, offset, buffer.size - offset)
                }
            }
        }
        frames.forEach { frame ->
            if (!closed.get()) {
                callback.onFrame(frame)
            }
        }
    }

    private companion object {
        const val MAX_FRAME_BYTES = 8 * 1024 * 1024
    }
}
