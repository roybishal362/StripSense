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

class PointAtUnreadTest {
    @Test fun aFingerOnPrintThatCannotBeReadIsAskedToComeCloserOnceAndNotAtOnce() {
        val closer = ScanMessage(ScanHint.HAND, hand = HandCue.Closer)
        assertTrue(Sentences.handCue(HandCue.Closer, "en").contains("Bring the phone closer"))
        assertTrue(Sentences.handCue(HandCue.Closer, "hi").contains("पास लाइए"))
        val queue = ScanAnnouncements()
        // A finger passing over far print is not asked anything.
        assertNull(queue.next(closer, 0))
        assertNull(queue.next(closer, ScanAnnouncements.GUESS_MS - 1))
        assertEquals(closer, queue.next(closer, ScanAnnouncements.GUESS_MS))
        for (t in 1..10) assertNull(queue.next(closer, ScanAnnouncements.GUESS_MS + t * 150L))
        // It never cuts off a sentence that is being spoken.
        assertFalse(queue.urgent(closer))
    }
}
