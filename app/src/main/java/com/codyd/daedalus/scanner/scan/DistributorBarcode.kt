package com.codyd.daedalus.scanner.scan

/**
 * Part number and quantity from the 2D barcode on a distributor's parts bag.
 *
 * - Digi-Key, Mouser, Arrow and most others print ECIA labels: an ISO/IEC 15434
 *   message (`[)>` RS `06` GS field GS field ... RS EOT) of ANSI MH10.8.2 data
 *   identifiers. `1P` is the manufacturer part number, `P` the distributor's
 *   own, `Q` the quantity.
 * - LCSC prints a QR like `{pbn:...,pc:C46749,pm:NE555DR,qty:10,...}`: `pm` is
 *   the manufacturer part number, `pc` LCSC's own.
 */
object DistributorBarcode {

    private const val GS = '\u001D'
    private const val RS = '\u001E'
    private const val EOT = '\u0004'
    private val DATA_IDENTIFIER = Regex("^(\\d{0,3}[A-Z])(.+)$")

    data class Label(val partNumber: String, val qty: Int?, val distributorPart: String?)

    fun parse(raw: String): Label? = when {
        raw.contains("[)>") -> parseEcia(raw)
        raw.trimStart().startsWith("{") && raw.contains("pm:") -> parseLcsc(raw)
        else -> null
    }

    private fun parseEcia(raw: String): Label? {
        val fields = mutableMapOf<String, String>()
        for (part in raw.substringAfter("[)>").split(GS, RS, EOT)) {
            val m = DATA_IDENTIFIER.matchEntire(part.trim()) ?: continue
            fields.putIfAbsent(m.groupValues[1], m.groupValues[2].trim())
        }
        val mpn = fields["1P"]?.takeIf { it.isNotBlank() } ?: return null
        return Label(mpn, fields["Q"]?.toIntOrNull(), fields["P"])
    }

    private fun parseLcsc(raw: String): Label? {
        val fields = raw.trim().removePrefix("{").removeSuffix("}").split(',')
            .mapNotNull { kv -> kv.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
            .toMap()
        val mpn = fields["pm"]?.takeIf { it.isNotBlank() } ?: return null
        return Label(mpn, fields["qty"]?.toIntOrNull(), fields["pc"])
    }
}
