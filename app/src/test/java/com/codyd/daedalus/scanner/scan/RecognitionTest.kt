package com.codyd.daedalus.scanner.scan

import com.codyd.daedalus.scanner.data.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecognitionTest {

    /** The real bundled index, so tests follow the library as it grows. */
    private val library = Library.fromJson(File("src/main/assets/library.json").readText())

    private fun words(vararg w: Pair<String, Float>) = w.map { OcrWord(it.first, it.second) }

    @Test fun `picks the part number off a 555 over logo and date code`() {
        val c = PartNumberExtractor.extract(words("TI" to 20f, "NE555P" to 30f, "2231" to 15f, "E4" to 15f), library)
        assertEquals("NE555P", c.first().partNumber)
        assertEquals("NE555", c.first().libraryId)
        assertEquals("DIP-8", c.first().packageGuess)
        assertTrue(c.none { it.partNumber == "2231" || it.partNumber == "E4" })
    }

    @Test fun `matches the longest library id inside a prefixed part number`() {
        val c = PartNumberExtractor.extract(words("SN74HC595N" to 20f, "9AK2C3M" to 20f), library)
        assertEquals("SN74HC595N", c.first().partNumber)
        assertEquals("74HC595", c.first().libraryId)
        assertEquals("DIP-16", c.first().packageGuess)
        // The lot code may survive, but well below the part number.
        assertTrue(c.drop(1).all { it.score < c.first().score / 2 })
    }

    @Test fun `atmega package suffix`() {
        val c = PartNumberExtractor.extract(words("ATMEGA328P-PU" to 20f, "1838" to 12f), library)
        assertEquals("ATMEGA328P-PU", c.single().partNumber)
        assertEquals("ATMEGA328P", c.single().libraryId)
        assertEquals("DIP-28", c.single().packageGuess)
    }

    @Test fun `fixes letters misread between digits`() {
        assertEquals("NE555P", PartNumberExtractor.fixLookalikes("NE5S5P"))
        assertEquals("LM358", PartNumberExtractor.fixLookalikes("LM3S8"))
        assertEquals("1N4148", PartNumberExtractor.fixLookalikes("1N4I48"))
        // Letters next to only one digit are left alone: 74LS00 stays.
        assertEquals("SN74LS00N", PartNumberExtractor.fixLookalikes("SN74LS00N"))
        val c = PartNumberExtractor.extract(words("NE5S5P" to 20f), library)
        assertEquals("NE555", c.single().libraryId)
    }

    @Test fun `keeps plausible parts the library does not have`() {
        val c = PartNumberExtractor.extract(words("STM32F103C8T6" to 20f, "ARM" to 10f), library)
        assertEquals("STM32F103C8T6", c.single().partNumber)
        assertNull(c.single().libraryId)
    }

    @Test fun `bigger print wins between two plausible words`() {
        val c = PartNumberExtractor.extract(words("LM317T" to 40f, "MC34063" to 10f), library)
        assertEquals("LM317T", c.first().partNumber)
    }

    @Test fun `ignores words without digits and bare numbers`() {
        assertTrue(PartNumberExtractor.extract(words("TEXAS" to 10f, "INSTRUMENTS" to 10f, "12345" to 10f), library).isEmpty())
    }

    @Test fun `reads digikey ECIA labels`() {
        val raw = "[)>\u001E06\u001DP296-1411-5-ND\u001D1PNE555P\u001DK\u001D1K12345678\u001D10K13579\u001D9D2231\u001D1TABC123\u001D11K1\u001D4LMY\u001DQ25\u001D11ZPICK\u001E\u0004"
        val label = DistributorBarcode.parse(raw)!!
        assertEquals("NE555P", label.partNumber)
        assertEquals(25, label.qty)
        assertEquals("296-1411-5-ND", label.distributorPart)
    }

    @Test fun `reads lcsc labels`() {
        val label = DistributorBarcode.parse("{pbn:PICK2102020001,on:SO2102020001,pc:C46749,pm:NE555DR,qty:10,mc:,cc:1,pdi:12345,hp:null,wc:JS}")!!
        assertEquals("NE555DR", label.partNumber)
        assertEquals(10, label.qty)
        assertEquals("C46749", label.distributorPart)
    }

    @Test fun `other barcodes are not labels`() {
        assertNull(DistributorBarcode.parse("https://example.com"))
        assertNull(DistributorBarcode.parse("daedalus://pair?code=ABCD-EFGH"))
        assertNull(DistributorBarcode.parse("[)>\u001E06\u001DQ5\u001E\u0004"))
    }

    @Test fun `tracker keeps steady reads and drops one-offs`() {
        var now = 0L
        val t = CandidateTracker(clock = { now })
        val good = Candidate("NE555P", 3.0, "NE555")
        val faint = Candidate("NE5SSP", 1.5)
        t.update(listOf(good, faint))
        repeat(5) { now += 100; t.update(listOf(good)) }
        val shown = t.shown()
        assertEquals("NE555P", shown.first().partNumber)
        assertTrue(shown.none { it.partNumber == "NE5SSP" })
    }

    @Test fun `tracker holds a shown candidate for 10 seconds after its last read`() {
        var now = 0L
        val t = CandidateTracker(clock = { now })
        t.update(listOf(Candidate("NE555P", 2.0)))
        while (now < 9_900) { now += 100; t.update(emptyList()) }
        assertEquals(listOf("NE555P"), t.shown().map { it.partNumber })
        now += 100; t.update(emptyList())
        assertTrue(t.shown().isEmpty())
    }

    @Test fun `tracker keeps shown candidates in the order they appeared`() {
        var now = 0L
        val t = CandidateTracker(clock = { now })
        t.update(listOf(Candidate("LM358", 2.5)))
        now = 100; t.update(listOf(Candidate("NE555P", 9.0)))
        assertEquals(listOf("LM358", "NE555P"), t.shown().map { it.partNumber })
    }

    @Test fun `package guesses`() {
        assertEquals("SOIC-8", PackageGuess.guess("DR", "LM358", 8))
        assertEquals("TSSOP-16", PackageGuess.guess("PWR", "74HC595", 16))
        assertEquals("TO-220", PackageGuess.guess("CV", "L7805", null))
        assertNull(PackageGuess.guess("XYZ", "LM358", 8))
    }
}
