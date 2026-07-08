package com.cmux.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileRpcParamsTest {
    @Test
    fun createWorkspaceCarriesAndroidClientId() {
        val params = MobileRpcParams.createWorkspace()

        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun createTerminalCarriesWorkspaceAndAndroidClientId() {
        val params = MobileRpcParams.createTerminal("workspace-1")

        assertEquals("workspace-1", params.getString("workspace_id"))
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }
}
