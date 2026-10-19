package com.codyd.daedalus.scanner.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Pairs with a running daedalus-api and adds an item, the way the app does.
 * Skipped unless DAEDALUS_LIVE_API (e.g. http://127.0.0.1:8787) and
 * DAEDALUS_LIVE_CODE (a fresh code from POST /devices/pairing-code) are set.
 */
class ApiClientLiveTest {
    @Test fun `pair, read, add and unpair`() = runBlocking {
        val api = System.getenv("DAEDALUS_LIVE_API")
        val code = System.getenv("DAEDALUS_LIVE_CODE")
        assumeTrue(api != null && code != null)

        val paired = ApiClient(api!!).pair(code!!, "JVM test")
        val client = ApiClient(api, paired.token)

        assertTrue(client.profiles().any { it.id == "default" })
        val first = client.addItem("default", InventoryItem(name = "NE555P", defId = "NE555", pkg = "DIP-8", qty = 2, location = "bin 1"), merge = true)
        assertFalse(first.merged)
        val second = client.addItem("default", InventoryItem(name = "ne555p", pkg = "dip-8", qty = 3), merge = true)
        assertTrue(second.merged)
        assertEquals(5, second.item.qty)
        assertEquals("NE555", client.inventory("default").single { it.name == "NE555P" }.defId)
        client.parts()

        client.unpair(paired.linkedId)
        val revoked = runCatching { client.profiles() }.exceptionOrNull()
        assertEquals(401, (revoked as ApiException).status)
    }
}
