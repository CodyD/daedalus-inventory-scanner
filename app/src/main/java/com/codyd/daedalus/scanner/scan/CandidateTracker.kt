package com.codyd.daedalus.scanner.scan

/**
 * Smooths candidates across camera frames. OCR of a small chip flickers from
 * frame to frame; a part number read in many frames builds up score while
 * one-off misreads fade, so the list the user taps from stays steady.
 *
 * Once shown, a candidate stays for [holdMillis] after its last strong read,
 * and keeps its place in the list, so it can't vanish or move under a thumb.
 */
class CandidateTracker(
    private val decay: Double = 0.8,
    private val holdMillis: Long = 10_000,
    private val maxShown: Int = 8,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(var candidate: Candidate, var score: Double) {
        /** When it first went on screen, or null while hidden. */
        var shownSince: Long? = null
        var heldUntil = 0L
    }

    private val entries = mutableMapOf<String, Entry>()

    /** Adds one frame's candidates and returns the ones on screen, in the order they appeared. */
    fun update(frame: List<Candidate>): List<Candidate> {
        val now = clock()
        for (e in entries.values) e.score *= decay
        for (c in frame) {
            val e = entries.getOrPut(c.partNumber) { Entry(c, 0.0) }
            e.candidate = c
            e.score += c.score
        }
        for (e in entries.values) {
            if (e.score >= MIN_SHOWN) {
                if (e.shownSince == null) e.shownSince = now
                e.heldUntil = now + holdMillis
            } else if (e.shownSince != null && now >= e.heldUntil) {
                e.shownSince = null
            }
        }
        entries.values.removeAll { it.shownSince == null && it.score < MIN_KEPT }
        return shown()
    }

    fun shown(): List<Candidate> = entries.values
        .filter { it.shownSince != null }
        .sortedByDescending { it.score }
        .take(maxShown)
        .sortedBy { it.shownSince }
        .map { it.candidate.copy(score = it.score) }

    fun clear() = entries.clear()

    private companion object {
        const val MIN_KEPT = 0.3
        const val MIN_SHOWN = 2.0
    }
}
