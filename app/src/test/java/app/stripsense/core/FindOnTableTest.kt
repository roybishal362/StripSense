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

class FindOnTableTest {
    @Test fun theWantedStripByNameOnlyIsNeverCalledNotThisOne() {
        val med = Fixtures.M1
        val strip = PlacedStrip(med.id, med, PlacedKind.NAME, CropBox(100, 100, 300, 200), 0, 0)
        val cue = HandGuide.cue(200, 150, listOf(strip), setOf(med.id), { MedicationTiming(TimingKind.NEXT) })
        assertTrue(cue.toString(), cue is HandCue.Info)
    }
    @Test fun lookingForOneMedicineSaysWhichOne() {
        val text = Sentences.scanGuidance(ScanMessage(ScanHint.START, steerMed = Fixtures.M1), "en")
        assertTrue(text, text.startsWith("Looking for your "))
        val asked = ScanMessage(ScanHint.FOUND, ScanTarget(1, Fixtures.M2, MedicationTiming(TimingKind.MARKED), ScanSide.SINGLE, false),
            elsewhere = Fixtures.M1 to Toward.RIGHT, asked = true)
        assertTrue(Sentences.scanGuidance(asked, "en").contains("The one you asked for"))
    }
}
