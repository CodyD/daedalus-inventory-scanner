package com.codyd.daedalus.scanner.data

import org.json.JSONArray
import org.json.JSONObject

/** A Daedalus library part a scanned part number can be linked to (the inventory item's defId). */
data class LibraryPart(
    val id: String,
    val name: String,
    val category: String,
    val manufacturer: String? = null,
    val pins: Int? = null,
)

/**
 * The built-in parts (assets/library.json, written by `npm run scanner:library`
 * in daedalus-electron) plus the parts the paired computer has imported.
 */
class Library(val parts: List<LibraryPart>) {

    /**
     * Parts a printed part number can be recognised as: ones whose id carries a
     * number (NE555, 74HC595), longest first so 74HC595 wins over a shorter id
     * it contains. Generic parts (RES, LED-RED, BUTTON) are left out.
     */
    private val matchable = parts
        .filter { p -> p.category != "passive" && p.id.any { it.isDigit() } }
        .map { it to alnum(it.id) }
        .sortedByDescending { it.second.length }

    /** The library part whose id is inside [partNumber], e.g. SN74HC595N -> 74HC595. */
    fun match(partNumber: String): LibraryPart? {
        val pn = alnum(partNumber)
        return matchable.firstOrNull { pn.contains(it.second) }?.first
    }

    /** What is printed after the library id, e.g. "N" for SN74HC595N; null without a match. */
    fun suffixAfter(part: LibraryPart, partNumber: String): String? {
        val pn = alnum(partNumber)
        val at = pn.indexOf(alnum(part.id))
        return if (at < 0) null else pn.substring(at + alnum(part.id).length)
    }

    operator fun plus(more: List<LibraryPart>): Library {
        val ids = parts.map { it.id }.toSet()
        return Library(parts + more.filter { it.id !in ids })
    }

    companion object {
        fun alnum(s: String) = s.uppercase().filter { it.isLetterOrDigit() }

        fun fromJson(json: String): Library = Library(parseParts(JSONObject(json).getJSONArray("parts")))

        /** Library parts from assets/library.json or a GET /parts listing (ComponentDefs). */
        fun parseParts(array: JSONArray): List<LibraryPart> = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            LibraryPart(
                id = o.getString("id"),
                name = o.optString("name", o.getString("id")),
                category = o.optString("category", "misc"),
                manufacturer = o.optString("manufacturer").ifEmpty { null },
                pins = when {
                    o.has("pins") && o.get("pins") is Number -> o.getInt("pins")
                    o.optJSONArray("pins") != null -> o.getJSONArray("pins").length()
                    else -> null
                },
            )
        }
    }
}
