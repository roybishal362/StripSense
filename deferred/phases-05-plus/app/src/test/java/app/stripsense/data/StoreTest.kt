package app.stripsense.data

import app.stripsense.core.DoseEvent
import app.stripsense.core.EventKind
import app.stripsense.core.EventSource
import app.stripsense.core.Look
import app.stripsense.core.StoreData
import org.junit.Assert.assertEquals
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

    @Test fun brokenFileFallsBackToFresh() {
        val dir = tempDir()
        File(dir, "store.json").writeText("{ not json")
        val s = Store(dir, DemoData::fresh)
        assertEquals(DemoData.fresh(), s.data.value)
        s.close()
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
