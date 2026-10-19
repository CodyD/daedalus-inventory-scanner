package com.codyd.daedalus.scanner.scan

import com.codyd.daedalus.scanner.data.Library

/** One word ML Kit read, with the height of its box so bigger print can count for more. */
data class OcrWord(val text: String, val height: Float)

enum class CandidateSource { OCR, BARCODE }

/** A possible part number for what is in front of the camera. */
data class Candidate(
    val partNumber: String,
    val score: Double,
    /** Library part it is, when its id is inside the part number. */
    val libraryId: String? = null,
    val packageGuess: String? = null,
    /** Quantity, when a distributor bag label says so. */
    val qty: Int? = null,
    val source: CandidateSource = CandidateSource.OCR,
)

/**
 * Picks likely part numbers out of the text on a chip or label. Chips print a
 * logo, the part number, then date and lot codes; the part number is usually
 * the largest text and looks like letters + digits + a package suffix
 * (NE555P, SN74HC595N, ATMEGA328P-PU, LM358DR). Each word is scored on that
 * shape, a known family prefix, its size, and above all on matching a library
 * part; date and lot codes score low.
 */
object PartNumberExtractor {

    private const val MIN_SCORE = 1.2
    private const val MAX_CANDIDATES = 5

    /** Prefixes of common part families (manufacturer prefixes and generic series). */
    private val FAMILY = Regex(
        "^(SN|CD|HD|MC|LM|LMV|NE|SA|SE|TL|TLC|TLV|UA|LF|OP|OPA|AD|MAX|MCP|PIC|ATMEGA|ATTINY|ATXMEGA|AT\\d|" +
            "STM32|ESP|IRF|IRL|IRLZ|BC|BD|BS|BSS|BZX|BAT|1N|2N|3N|74|L78|L79|LD|AMS|NCP|TPS|INA|DRV|ULN|" +
            "PCF|PCA|DS|W25|24C|93C|CH34|FT2|CP21|NRF|SX12|MPU|BMP|BME|ADS|LTC|LT|MIC|AO|SI|FQP|TIP|MJE|" +
            "KA|UC|TC|TS|SG|NJM|RC|XL|MP|HCF|HEF|CD4|MM74|DM74|ULN|L293|L298|PC8|TCA|TDA)",
    )
    /** Letters then digits (NE555, LM358N) or digits-letters-digits (74HC595, 2N2222, 1N4148). */
    private val SHAPE = Regex("^([A-Z]{1,7}\\d{2,}|\\d{1,2}[A-Z]{1,4}\\d{2,})")
    /** Date and lot codes: 2231, 915A, 2231AB. */
    private val DATE_CODE = Regex("^\\d{3,4}[A-Z]{0,2}$")
    /** Words chips and labels print that are never the part number. */
    private val STOPWORDS = setOf(
        "TEXAS", "INSTRUMENTS", "PHILIPS", "NXP", "MALAYSIA", "CHINA", "MEXICO", "THAILAND", "PHILIPPINES",
        "TAIWAN", "KOREA", "JAPAN", "USA", "ROHS", "LOT", "QTY", "DATE", "MADE", "IN", "PB", "FREE",
        "ESD", "MSL", "REEL", "TAPE", "TUBE",
    )
    /** Letters OCR reads in place of digits. Only fixed between two digits ("NE5S5" -> "NE555"). */
    private val LOOKALIKE = mapOf('O' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'L' to '1', 'S' to '5', 'B' to '8', 'Z' to '2', 'G' to '6')

    fun extract(words: List<OcrWord>, library: Library): List<Candidate> {
        val maxHeight = words.maxOfOrNull { it.height }?.takeIf { it > 0 } ?: 1f
        val best = mutableMapOf<String, Candidate>()
        for (w in words) {
            val c = score(w, maxHeight, library) ?: continue
            if (c.score >= MIN_SCORE && c.score > (best[c.partNumber]?.score ?: 0.0)) best[c.partNumber] = c
        }
        return best.values.sortedByDescending { it.score }.take(MAX_CANDIDATES)
    }

    internal fun clean(raw: String): String =
        raw.uppercase().trim { !it.isLetterOrDigit() }.filter { it.isLetterOrDigit() || it in "-/.+" }

    internal fun fixLookalikes(s: String): String {
        val out = s.toCharArray()
        for (i in 1 until s.length - 1) {
            val fix = LOOKALIKE[s[i]] ?: continue
            if (s[i - 1].isDigit() && s[i + 1].isDigit()) out[i] = fix
        }
        return String(out)
    }

    private fun score(w: OcrWord, maxHeight: Float, library: Library): Candidate? {
        val pn = fixLookalikes(clean(w.text))
        if (pn.length !in 3..24 || pn in STOPWORDS || pn.none { it.isDigit() }) return null
        val lib = library.match(pn)
        // Bare numbers and date codes only count when they name a library part.
        if (lib == null && (pn.all { it.isDigit() } || DATE_CODE.matches(pn))) return null

        var s = 1.0
        if (SHAPE.containsMatchIn(pn)) s += 1.0
        if (FAMILY.containsMatchIn(pn)) s += 1.0
        if (lib != null) s += 3.0
        if (runs(pn) > 4) s -= 1.0 // lot codes alternate letters and digits: 9AK2C3M
        s *= 0.6 + 0.4 * (w.height / maxHeight)

        val pkg = lib?.let { PackageGuess.guess(library.suffixAfter(it, pn) ?: "", it.id, it.pins) }
            ?: PackageGuess.guess(pn.takeLastWhile { !it.isDigit() }, pn, null)
        return Candidate(pn, s, lib?.id, pkg)
    }

    /** Letter/digit runs: SN74HC595N has 5. */
    private fun runs(s: String): Int {
        val t = s.filter { it.isLetterOrDigit() }
        if (t.isEmpty()) return 0
        return 1 + (1 until t.length).count { t[it].isDigit() != t[it - 1].isDigit() }
    }
}

/** Package from a part number's suffix, using the common manufacturer conventions. A starting point the user can edit. */
object PackageGuess {
    fun guess(suffix: String, partId: String, pins: Int?): String? {
        val s = Library.alnum(suffix)
        val id = partId.uppercase()
        val family = when {
            id.startsWith("AT") && s.endsWith("PU") -> "DIP"
            id.startsWith("AT") && s.endsWith("AU") -> "TQFP"
            id.startsWith("AT") && s.endsWith("MU") -> "QFN"
            id.startsWith("AT") && (s.endsWith("SU") || s.endsWith("SSU")) -> "SOIC"
            id.startsWith("L78") || id.startsWith("L79") -> if (s.startsWith("CV") || s.startsWith("CT")) "TO-220" else null
            s.startsWith("PW") -> "TSSOP"
            s.startsWith("DGK") -> "VSSOP"
            s == "N" || s == "P" || s == "NE4" || s == "BE" || s == "PE4" -> "DIP"
            s == "D" || s == "DR" || s == "DT" || s == "M" || s == "M96" -> "SOIC"
            else -> null
        } ?: return null
        return if (pins != null && pins > 3 && family in setOf("DIP", "SOIC", "TSSOP", "VSSOP")) "$family-$pins" else family
    }
}
