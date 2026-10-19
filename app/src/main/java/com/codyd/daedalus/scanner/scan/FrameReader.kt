package com.codyd.daedalus.scanner.scan

import com.codyd.daedalus.scanner.data.Library
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.text.Text

/** Turns one camera frame's ML Kit results into part-number candidates. */
object FrameReader {

    /** A bag label names the part outright; it outranks anything read off a chip. */
    private const val LABEL_SCORE = 8.0
    /** A plain 1D barcode on a bag is often just the part number. */
    private const val PLAIN_BARCODE_SCORE = 2.5
    private val PLAIN_PART = Regex("^[A-Z0-9][A-Z0-9\\-/.+]{2,30}$")

    fun read(text: Text?, barcodes: List<Barcode>?, library: Library): List<Candidate> {
        val words = text?.textBlocks.orEmpty().flatMap { block ->
            block.lines.flatMap { line ->
                line.elements.map { OcrWord(it.text, (it.boundingBox?.height() ?: 0).toFloat()) }
            }
        }
        return barcodes.orEmpty().mapNotNull { fromBarcode(it, library) } + PartNumberExtractor.extract(words, library)
    }

    private fun fromBarcode(b: Barcode, library: Library): Candidate? {
        val raw = b.rawValue ?: return null
        DistributorBarcode.parse(raw)?.let { label ->
            val pn = label.partNumber.uppercase()
            val lib = library.match(pn)
            return Candidate(
                partNumber = pn,
                score = LABEL_SCORE,
                libraryId = lib?.id,
                packageGuess = lib?.let { PackageGuess.guess(library.suffixAfter(it, pn) ?: "", it.id, it.pins) },
                qty = label.qty,
                source = CandidateSource.BARCODE,
            )
        }
        val plain = raw.trim().uppercase()
        if (b.format == Barcode.FORMAT_QR_CODE || !PLAIN_PART.matches(plain) || plain.none { it.isDigit() } || plain.none { it.isLetter() }) {
            return null
        }
        return Candidate(plain, PLAIN_BARCODE_SCORE, library.match(plain)?.id, source = CandidateSource.BARCODE)
    }
}
