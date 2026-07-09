package com.cmux.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileNotificationIdPayloadTest {
    @Test
    fun firstNotificationIdArrayNormalizesSupportedIds() {
        val payload = JSONObject()
            .put(
                "ids",
                JSONArray()
                    .put(" n-1 ")
                    .put(42)
                    .put(42.5)
                    .put(true)
                    .put(JSONObject().put("id", "ignored"))
                    .put("")
            )
            .put("handled_ids", JSONArray().put("fallback"))

        assertEquals(
            listOf("n-1", "42", "42.5"),
            payload.optFirstNotificationIdArray("ids", "handled_ids")
        )
    }

    @Test
    fun firstNotificationIdArrayFallsBackWhenEarlierArraysHaveNoIds() {
        val payload = JSONObject()
            .put("ids", JSONArray().put("").put(true))
            .put("handled_ids", JSONArray().put(" handled-1 "))

        assertEquals(
            listOf("handled-1"),
            payload.optFirstNotificationIdArray("ids", "handled_ids", "notification_ids")
        )
    }
}
