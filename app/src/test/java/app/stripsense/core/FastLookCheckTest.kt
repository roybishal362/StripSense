package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*

import org.junit.Assert.*
import org.junit.Test

class FastLookCheckTest {
    private val med = Fixtures.M1
    private val match = Verdict.Match(med)
    private val look = Look("a", med.id, listOf(0f, 1f), "a.jpg", fingerprint = listOf(1f, 0f))
    @Test fun localAcceptanceDoesNotRunEmbedding() {
        assertEquals(match, LookCheck.withFallback(match, listOf(look), true, .55f, listOf(1f, 0f)) { error("unnecessary embedding") })
    }
    @Test fun failedLocalCheckFallsBackToTheSameEmbeddingRule() {
        var calls = 0
        assertEquals(match, LookCheck.withFallback(match, listOf(look), true, .55f, listOf(0f, 1f)) { calls++; look.vector })
        assertEquals(1, calls)
    }
    @Test fun unavailableOrDifferentFeaturesStillRefuse() {
        assertTrue(LookCheck.withFallback(match, listOf(look), true, .55f, listOf(0f, 1f)) { null } is Verdict.NotShown)
        assertTrue(LookCheck.withFallback(match, listOf(look), true, .55f, listOf(0f, 1f)) { listOf(1f, 0f) } is Verdict.NotShown)
    }
    @Test fun conflictingPrintCannotBePromotedByEitherFeature() {
        val wrong = Verdict.Different(med, "650 mg", "500 mg")
        assertEquals(wrong, LookCheck.withFallback(wrong, listOf(look), true, .55f, look.fingerprint) { error("conflict must not embed") })
    }
    @Test fun untaughtAndDisabledChecksDoNotStartANativeModel() {
        assertTrue(LookCheck.withFallback(match, listOf(look.copy(medId = "other")), true, .55f, look.fingerprint) { error("untaught") } is Verdict.NotTaught)
        assertEquals(match, LookCheck.withFallback(match, listOf(look), false, .55f, emptyList()) { error("disabled") })
    }
}
