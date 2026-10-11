package app.stripsense.data

import app.stripsense.core.domain.DoseEvent
import app.stripsense.core.domain.EventKind
import app.stripsense.core.domain.EventSource
import app.stripsense.core.domain.Look
import app.stripsense.core.domain.StoreData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StoreTest {

    private fun tempDir(): File = Files.createTempDirectory("store").toFile()
    private val event = DoseEvent("e1", "fever-tablet", "2026-10-10T08:00", 1L, EventKind.TAKEN, EventSource.PROMPT_TAP)

    @Test fun dataSurvivesEncodeAndDecode() {
        val d = DemoData.fresh().copy(events = listOf(event), language = "hi")
        val text = Store.JSON.encodeToString(StoreData.serializer(), d)
        assertEquals(d, Store.JSON.decodeFromString(StoreData.serializer(), text))
    }

    @Test fun reopenedStoreKeepsItsData() {
        val dir = tempDir()
        val a = Store(dir, DemoData::fresh)
        a.setLanguage("hi")
        a.addEvent(event)
        a.setClockOffset(12345L)
        a.close()
        val b = Store(dir, DemoData::fresh)
        assertEquals("hi", b.data.value.language)
        assertEquals(listOf(event), b.data.value.events)
        assertEquals(12345L, b.data.value.clockOffsetMs)
        b.close()
    }

    @Test fun brokenFileIsNeverWrittenOver() {
        val dir = tempDir()
        val file = File(dir, "store.json")
        file.writeText("{ not json")
        val s = Store(dir, DemoData::fresh)
        assertTrue(s.unreadable.value)
        s.setLanguage("hi")
        s.addEvent(event)
        s.flush()
        assertEquals("{ not json", file.readText())
        assertFalse(s.saveLook(Look("l1", "fever-tablet", listOf(1f), "")))
        assertFalse(s.completeSetup(DemoData.person))
        assertTrue(s.writeFailed.value)
        s.close()
        assertEquals("{ not json", file.readText())
    }

    @Test fun brokenFileIsReadAgainOnceItIsReadable() {
        val dir = tempDir()
        val file = File(dir, "store.json")
        val good = DemoData.fresh().copy(events = listOf(event), language = "hi")
        file.writeText("{ not json")
        val s = Store(dir, DemoData::fresh)
        assertFalse(s.retryLoad())
        file.writeText(Store.JSON.encodeToString(StoreData.serializer(), good))
        assertTrue(s.retryLoad())
        assertFalse(s.unreadable.value)
        assertEquals(good, s.data.value)
        s.setLanguage("en")
        s.close()
        assertEquals("en", Store(dir, DemoData::fresh).data.value.language)
    }

    @Test fun startingEmptyKeepsTheUnreadableFile() {
        val dir = tempDir()
        File(dir, "store.json").writeText("{ not json")
        val s = Store(dir, DemoData::fresh)
        assertTrue(s.startEmpty(77L))
        assertFalse(s.unreadable.value)
        assertEquals("{ not json", File(dir, "store-unreadable-77.json").readText())
        s.addEvent(event)
        s.close()
        val again = Store(dir, DemoData::fresh)
        assertFalse(again.unreadable.value)
        assertEquals(listOf(event), again.data.value.events)
        again.close()
    }

    /** Stands in for the phone's keystore: not real secrecy, but it changes every byte and notices a changed one. */
    private val cipher = object : StoreCipher {
        override fun seal(plain: ByteArray) = ByteArray(plain.size + 1).also { out ->
            for (i in plain.indices) out[i] = (plain[i].toInt() xor 0x5A).toByte()
            out[plain.size] = plain.sum().toByte()
        }
        override fun open(sealed: ByteArray): ByteArray {
            val plain = ByteArray(sealed.size - 1) { (sealed[it].toInt() xor 0x5A).toByte() }
            check(plain.sum().toByte() == sealed.last()) { "changed" }
            return plain
        }
    }

    @Test fun sealedStoreKeepsItsDataAndTheFileShowsNothing() {
        val dir = tempDir()
        val a = Store(dir, cipher, DemoData::fresh)
        a.setPerson(DemoData.person.copy(pin = "4821"))
        a.addEvent(event)
        a.close()
        val onDisk = File(dir, "store.json").readBytes()
        assertTrue(Sealed.isSealed(onDisk))
        assertFalse(String(onDisk, Charsets.ISO_8859_1).contains("4821"))
        assertFalse(String(onDisk, Charsets.ISO_8859_1).contains("fever"))
        val b = Store(dir, cipher, DemoData::fresh)
        assertFalse(b.unreadable.value)
        assertEquals("4821", b.data.value.person.pin)
        assertEquals(listOf(event), b.data.value.events)
        b.close()
    }

    @Test fun plainFileFromAnOlderBuildIsReadOnceAndThenSealed() {
        val dir = tempDir()
        val old = Store(dir, DemoData::fresh)
        old.addEvent(event)
        old.close()
        assertFalse(Sealed.isSealed(File(dir, "store.json").readBytes()))
        val s = Store(dir, cipher, DemoData::fresh)
        assertEquals(listOf(event), s.data.value.events)
        s.setLanguage("hi")
        s.close()
        assertTrue(Sealed.isSealed(File(dir, "store.json").readBytes()))
        assertEquals(listOf(event), Store(dir, cipher, DemoData::fresh).data.value.events)
    }

    @Test fun changedSealedFileIsUnreadableAndIsKept() {
        val dir = tempDir()
        val a = Store(dir, cipher, DemoData::fresh)
        a.addEvent(event)
        a.close()
        val file = File(dir, "store.json")
        val changed = file.readBytes().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
        file.writeBytes(changed)
        val b = Store(dir, cipher, DemoData::fresh)
        assertTrue(b.unreadable.value)
        b.setLanguage("hi")
        b.close()
        assertTrue(file.readBytes().contentEquals(changed))
    }

    @Test fun sealedFileWithoutAKeyIsUnreadableNotEmpty() {
        val dir = tempDir()
        Store(dir, cipher, DemoData::fresh).apply { addEvent(event); close() }
        val before = File(dir, "store.json").readBytes()
        val s = Store(dir, DemoData::fresh)
        assertTrue(s.unreadable.value)
        s.addEvent(event)
        s.close()
        assertTrue(File(dir, "store.json").readBytes().contentEquals(before))
    }

    @Test fun aCipherThatCannotReadItsOwnWorkNeverReplacesTheFile() {
        val broken = object : StoreCipher {
            override fun seal(plain: ByteArray) = plain
            override fun open(sealed: ByteArray) = ByteArray(sealed.size)
        }
        val dir = tempDir()
        val s = Store(dir, broken, DemoData::fresh)
        s.addEvent(event)
        s.flush()
        assertTrue(s.writeFailed.value)
        assertFalse(File(dir, "store.json").exists())
        assertFalse(s.saveLook(Look("l1", "fever-tablet", listOf(1f), "")))
        s.close()
    }

    @Test fun setupIsSavedBeforeItIsReported() {
        val dir = tempDir()
        val s = Store(dir, DemoData::fresh)
        val person = DemoData.person.copy(pin = "4821")
        assertTrue(s.completeSetup(person))
        val again = Store(dir, DemoData::fresh)
        assertEquals(person, again.data.value.person)
        assertTrue(again.data.value.checkSeen)
        s.close(); again.close()
    }

    @Test fun unknownKeysAreIgnored() {
        val dir = tempDir()
        File(dir, "store.json").writeText("""{"language":"hi","somethingNew":42}""")
        val s = Store(dir, DemoData::fresh)
        assertEquals("hi", s.data.value.language)
        s.close()
    }

    @Test fun changedMedicineLosesItsLooks() {
        val dir = tempDir()
        val s = Store(dir, DemoData::fresh)
        s.addLook(Look("l1", "fever-tablet", listOf(1f), ""))
        s.addLook(Look("l2", "vitamin-tablet", listOf(1f), ""))
        val changed = DemoData.medicines.map { if (it.id == "fever-tablet") it.copy(brand = "OTHER") else it.copy(nickEn = it.nickEn + "!") }
        s.setMedicines(changed, 99L)
        assertEquals(listOf("l2"), s.data.value.looks.map { it.id })
        assertEquals(99L, s.data.value.listSavedAtMillis)
        assertTrue(s.data.value.medicines.first().brand == "OTHER")
        s.close()
    }
}
