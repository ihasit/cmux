package com.cmux.android

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MobileTcpClient(
    private val callback: MobileFrameClient.Callback,
    private val connectTimeoutMillis: Int = 15_000
) : MobileFrameClient {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(true)
    private var socket: Socket? = null
    private var output: BufferedOutputStream? = null

    override fun connect(route: CmuxRoute) {
        close("reconnecting")
        closed.set(false)
        executor.execute {
            try {
                val nextSocket = Socket()
                nextSocket.tcpNoDelay = true
                nextSocket.connect(InetSocketAddress(route.host, route.port), connectTimeoutMillis)

                socket = nextSocket
                output = BufferedOutputStream(nextSocket.getOutputStream())
                callback.onOpen()

                readLoop(BufferedInputStream(nextSocket.getInputStream()))
            } catch (error: Exception) {
                if (!closed.get()) {
                    callback.onError(error.message ?: error.javaClass.simpleName)
                    close("connect failed")
                }
            }
        }
    }

    override fun sendFrame(payload: String) {
        executor.execute {
            try {
                val bytes = payload.toByteArray(Charsets.UTF_8)
                val header = ByteBuffer.allocate(4).putInt(bytes.size).array()
                val stream = output ?: throw IllegalStateException("not connected")
                stream.write(header)
                stream.write(bytes)
                stream.flush()
            } catch (error: Exception) {
                if (!closed.get()) {
                    callback.onError(error.message ?: error.javaClass.simpleName)
                    close("send failed")
                }
            }
        }
    }

    override fun close(reason: String) {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            output?.close()
        } catch (_: Exception) {
        }
        output = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        callback.onClose(reason)
    }

    override fun shutdown() {
        close("activity destroyed")
        executor.shutdownNow()
    }

    private fun readLoop(input: BufferedInputStream) {
        val header = ByteArray(4)
        while (!closed.get()) {
            if (!readFully(input, header)) {
                close("remote closed")
                return
            }
            val length = ByteBuffer.wrap(header).int
            if (length <= 0 || length > MAX_FRAME_BYTES) {
                callback.onError("invalid frame length: $length")
                close("invalid frame")
                return
            }
            val body = ByteArray(length)
            if (!readFully(input, body)) {
                close("remote closed")
                return
            }
            callback.onFrame(String(body, Charsets.UTF_8))
        }
    }

    private fun readFully(input: BufferedInputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size && !closed.get()) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) {
                return false
            }
            offset += count
        }
        return offset == buffer.size
    }

    private companion object {
        const val MAX_FRAME_BYTES = 8 * 1024 * 1024
    }
}
