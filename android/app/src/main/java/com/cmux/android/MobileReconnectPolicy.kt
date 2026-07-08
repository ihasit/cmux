package com.cmux.android

class MobileReconnectPolicy(
    private val delaysMillis: List<Long> = listOf(1_000L, 2_000L, 5_000L, 10_000L)
) {
    private var attempt: Int = 0

    fun reset() {
        attempt = 0
    }

    fun nextDelayMillis(): Long {
        val safeDelays = delaysMillis.ifEmpty { listOf(1_000L) }
        val delay = safeDelays[attempt.coerceAtMost(safeDelays.lastIndex)]
        attempt += 1
        return delay
    }

    fun shouldReconnect(closedDetail: String?, hasActiveMac: Boolean, userRequestedDisconnect: Boolean): Boolean {
        if (!hasActiveMac || userRequestedDisconnect) return false
        return closedDetail != "no supported route"
    }
}
