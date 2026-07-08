package com.cmux.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PairedMacStore(context: Context) {
    private val preferences = context.getSharedPreferences("cmux_mobile", Context.MODE_PRIVATE)

    fun list(): List<PairedMac> {
        val raw = preferences.getString(KEY_PAIRED_MACS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                PairedMac.fromJson(array.getJSONObject(index))
            }.getOrNull()
        }
    }

    fun save(mac: PairedMac): List<PairedMac> {
        val updated = list()
            .filterNot { it.id == mac.id }
            .toMutableList()
            .apply { add(0, mac) }
        write(updated)
        return updated
    }

    fun forget(id: String): List<PairedMac> {
        val updated = list().filterNot { it.id == id }
        write(updated)
        return updated
    }

    private fun write(macs: List<PairedMac>) {
        val array = JSONArray()
        macs.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_PAIRED_MACS, array.toString()).apply()
    }

    private companion object {
        const val KEY_PAIRED_MACS = "paired_macs"
    }
}
