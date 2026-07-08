package com.cmux.android

interface MobileFrameClient {
    interface Callback {
        fun onOpen()
        fun onFrame(payload: String)
        fun onClose(reason: String)
        fun onError(message: String)
    }

    fun connect(route: CmuxRoute)
    fun sendFrame(payload: String)
    fun close(reason: String = "closed")
    fun shutdown()
}
