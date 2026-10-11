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

class TouchTest {
    private val foil = 0xFFB8BCC0.toInt()
    private val skinLight = 0xFFE0AC8A.toInt()
    private val skinDark = 0xFF8D5524.toInt()

    @Test fun skinOfDifferentTonesIsSkinAndFoilIsNot() {
        assertTrue(Touch.isSkin(skinLight))
        assertTrue(Touch.isSkin(skinDark))
        assertFalse(Touch.isSkin(foil))
        assertFalse(Touch.isSkin(0xFFFFFFFF.toInt()))
        assertFalse(Touch.isSkin(0xFF202020.toInt()))
        assertEquals(0.5f, Touch.fraction(intArrayOf(foil, skinLight, foil, skinDark)), 0.001f)
    }
    @Test fun aStripIsTouchedWhenSkinAppearsOverItNotBecauseOfItsOwnColour() {
        val usual = HashMap<String, Float>()
        // One strip has a lot of skin-coloured print. That is how it always looks, so it is not a hand.
        repeat(5) { assertNull(Touch.pick(mapOf("a" to 0.05f, "b" to 0.40f), usual)) }
        assertEquals("a", Touch.pick(mapOf("a" to 0.60f, "b" to 0.41f), usual))
        assertEquals("b", Touch.pick(mapOf("a" to 0.05f, "b" to 0.85f), usual))
        assertNull(Touch.pick(mapOf("a" to 0.06f, "b" to 0.42f), usual))
    }
    @Test fun aHandRestingOnAStripDoesNotBecomeItsUsualColour() {
        val usual = HashMap<String, Float>()
        Touch.pick(mapOf("a" to 0.05f), usual)
        repeat(50) { assertEquals("a", Touch.pick(mapOf("a" to 0.70f), usual)) }
    }
}
