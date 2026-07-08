package com.cmux.android

object PairedMacSelector {
    fun startupMac(macs: List<PairedMac>): PairedMac? {
        return macs.firstOrNull { it.supportedRoutes().isNotEmpty() }
    }
}
