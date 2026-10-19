package com.codyd.daedalus.scanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The API answered with an error (as opposed to not being reachable, which is an IOException). */
class ApiException(val status: Int, message: String) : Exception(message)

data class PairResult(val deviceId: String, val linkedId: String, val token: String)

data class ProfileSummary(val id: String, val name: String, val items: Int)

/** Mirrors InventoryItem in daedalus-electron/src/shared/inventory.ts. */
data class InventoryItem(
    val id: String? = null,
    val defId: String? = null,
    val name: String,
    val value: String? = null,
    val pkg: String? = null,
    val qty: Int,
    val location: String? = null,
    val notes: String? = null,
)

data class AddResult(val item: InventoryItem, val merged: Boolean)

/**
 * daedalus-api, as a paired phone uses it: redeem a pairing code, then read
 * profiles and parts and add inventory items with the linked token.
 */
class ApiClient(baseUrl: String, private val token: String? = null) {
    private val base = baseUrl.trimEnd('/')

    suspend fun pair(code: String, label: String): PairResult {
        val r = call("POST", "/devices/pair", JSONObject().put("code", code).put("label", label)) as JSONObject
        return PairResult(r.getString("deviceId"), r.getString("linkedId"), r.getString("token"))
    }

    suspend fun profiles(): List<ProfileSummary> {
        val a = call("GET", "/profiles") as JSONArray
        return (0 until a.length()).map { i ->
            a.getJSONObject(i).let { ProfileSummary(it.getString("id"), it.getString("name"), it.getInt("items")) }
        }
    }

    suspend fun inventory(profileId: String): List<InventoryItem> {
        val inv = (call("GET", "/profiles/${enc(profileId)}") as JSONObject).getJSONArray("inventory")
        return (0 until inv.length()).map { itemFrom(inv.getJSONObject(it)) }
    }

    /** Parts the paired computer has imported (built-in ones ship in assets/library.json). */
    suspend fun parts(): List<LibraryPart> = Library.parseParts(call("GET", "/parts") as JSONArray)

    suspend fun addItem(profileId: String, item: InventoryItem, merge: Boolean): AddResult {
        val body = JSONObject().put("item", itemJson(item)).put("merge", merge)
        val r = call("POST", "/profiles/${enc(profileId)}/inventory/items", body) as JSONObject
        return AddResult(itemFrom(r.getJSONObject("item")), r.getBoolean("merged"))
    }

    /** Revokes this phone's own token. */
    suspend fun unpair(linkedId: String) {
        call("DELETE", "/devices/linked/${enc(linkedId)}")
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): Any? = withContext(Dispatchers.IO) {
        val conn = URL("$base$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (status !in 200..299) {
                val errors = json?.optJSONArray("errors")
                val msg = errors?.let { e -> (0 until e.length()).joinToString("; ") { e.getJSONObject(it).optString("message") } }
                throw ApiException(status, msg?.ifBlank { null } ?: "HTTP $status")
            }
            json?.opt("result") ?: throw IOException("Unexpected response from $base")
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000

        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

        fun itemFrom(o: JSONObject) = InventoryItem(
            id = o.str("id"),
            defId = o.str("defId"),
            name = o.getString("name"),
            value = o.str("value"),
            pkg = o.str("package"),
            qty = o.getInt("qty"),
            location = o.str("location"),
            notes = o.str("notes"),
        )

        /** Unset and blank fields are left out, as the desktop app does. */
        fun itemJson(it: InventoryItem) = JSONObject().apply {
            put("name", it.name)
            put("qty", it.qty)
            it.defId?.takeIf(String::isNotBlank)?.let { v -> put("defId", v) }
            it.value?.takeIf(String::isNotBlank)?.let { v -> put("value", v) }
            it.pkg?.takeIf(String::isNotBlank)?.let { v -> put("package", v) }
            it.location?.takeIf(String::isNotBlank)?.let { v -> put("location", v) }
            it.notes?.takeIf(String::isNotBlank)?.let { v -> put("notes", v) }
        }
    }
}
