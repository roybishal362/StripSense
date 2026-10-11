package app.stripsense.data

import org.junit.Assert.*
import org.junit.Test

/** The phone's sealing against values computed by the family server's own code (backend/stripsense_backend/link.py). */
class LinkSealTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test fun thePhoneAndTheServerMakeTheSameKeyFromTheSameCode() {
        assertEquals("b7b16df0c34bcb3a6c7cfefb9a78268479c4caa6f3121c1575dd49e94a3a3c40", hex(LinkSeal.key("ABCD-EFGH-JKLM")))
        // Typed without the dashes, or in small letters, it is the same code.
        assertEquals(hex(LinkSeal.key("ABCD-EFGH-JKLM")), hex(LinkSeal.key("abcdefghjklm")))
    }
    @Test fun aMessageSealedOnThePhoneIsByteForByteWhatTheServerExpects() {
        val sealed = LinkSeal.seal("{\"battery\":64}".toByteArray(), LinkSeal.key("ABCD-EFGH-JKLM"), ByteArray(12) { it.toByte() })
        assertEquals("000102030405060708090a0b7b41553a56634c2cfffe7a986cde8efe15aa61d9d8db98f921a71b259885", hex(sealed))
    }
    @Test fun everyMessageHasItsOwnNonce() {
        val key = LinkSeal.key("ABCD-EFGH-JKLM")
        assertNotEquals(hex(LinkSeal.seal("x".toByteArray(), key)), hex(LinkSeal.seal("x".toByteArray(), key)))
    }
    @Test fun theBoxNameMatchesTheRelayAndIsNotTheKey() {
        val key = LinkSeal.key("ABCD-EFGH-JKLM")
        assertEquals("3fa0b975068b4fbf5b3f326842af7322", LinkSeal.boxId(key))
        assertFalse(hex(key).contains(LinkSeal.boxId(key)))
    }
    @Test fun aNewFamilyCodeHasTwelveCharactersAndNoLookalikes() {
        val code = LinkSeal.newCode()
        assertEquals(14, code.length)
        assertTrue(code.none { it in "01IO" })
        assertNotEquals(code, LinkSeal.newCode())
    }
    @Test fun theMessageCarriesEachPersonsWeekAndThePhonesStateAndNothingElse() {
        val papa = app.stripsense.core.domain.Profile("papa", "Papa")
        val med = app.stripsense.core.domain.Medicine("g", "Glycomet", "", "GLYCOMET", listOf("METFORMIN"), emptyList(), times = listOf("08:00"), personId = "papa")
        val data = app.stripsense.core.domain.StoreData(medicines = listOf(med), people = listOf(papa))
        val text = FamilyLink.message(data, 1_791_600_000_000, 64, true)
        assertTrue(text, text.startsWith("{\"sent_at\":\"") && text.contains("\"battery\":64,\"charging\":true"))
        assertTrue(text.contains("\"name\":\"Maa\"") && text.contains("\"name\":\"Papa\""))
        assertTrue(text.contains("Glycomet"))
        assertFalse(text.contains("pin", ignoreCase = true) || text.contains("photo", ignoreCase = true))
    }
}
