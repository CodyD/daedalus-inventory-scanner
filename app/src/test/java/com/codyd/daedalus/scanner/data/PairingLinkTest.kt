package com.codyd.daedalus.scanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PairingLinkTest {
    private val default = "https://daedalus-api.cd-cloud.workers.dev"

    /** Exactly what the desktop puts in the QR (pair:start in daedalus-electron/src/main/index.ts). */
    @Test fun `parses the desktop's link`() {
        val link = PairingLink.parse("daedalus://pair?api=https%3A%2F%2Fdaedalus-api.cd-cloud.workers.dev&code=K7QM-29XR", default, allowHttp = false)
        assertEquals(PairingLink(default, "K7QM-29XR"), link)
    }

    @Test fun `allows plain http only when asked`() {
        val raw = "daedalus://pair?api=http%3A%2F%2F10.0.2.2%3A8787&code=K7QM-29XR"
        assertNull(PairingLink.parse(raw, default, allowHttp = false))
        assertEquals("http://10.0.2.2:8787", PairingLink.parse(raw, default, allowHttp = true)!!.apiUrl)
    }

    @Test fun `falls back to the default API and rejects other links`() {
        assertEquals(default, PairingLink.parse("daedalus://pair?code=ABCD-EFGH", default, false)!!.apiUrl)
        assertNull(PairingLink.parse("daedalus://pair?api=x", default, false))
        assertNull(PairingLink.parse("https://example.com/pair?code=ABCD-EFGH", default, false))
        assertFalse(PairingLink.isAllowedApi("javascript:alert(1)", true))
    }
}
