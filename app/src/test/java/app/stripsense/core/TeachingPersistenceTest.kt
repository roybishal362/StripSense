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

import app.stripsense.core.Fixtures.M1
import app.stripsense.data.Store
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TeachingPersistenceTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun explicitSaveSurvivesClosingAndReopeningTheStore() {
        val folder = directory.newFolder()
        val store = Store(folder) { StoreData(medicines = listOf(M1), setupVersion = 1) }
        val look = Look("view", M1.id, emptyList(), "view.jpg", verifiedPrint = false, fingerprint = listOf(1f, 0f))
        assertTrue(store.saveLook(look))
        store.close()
        val restarted = Store(folder) { StoreData() }
        assertEquals(listOf(look), restarted.data.value.looks)
        restarted.close()
    }
    @Test fun aMissingMedicineCannotReportATeachingSuccess() {
        val store = Store(directory.newFolder()) { StoreData(setupVersion = 1) }
        assertFalse(store.saveLook(Look("a", "missing", emptyList(), "a.jpg")))
        assertTrue(store.data.value.looks.isEmpty())
        store.close()
    }
    @Test fun failedDiskWriteDoesNotPublishATaughtReference() {
        val folder = directory.newFile()
        val store = Store(folder) { StoreData(medicines = listOf(M1), setupVersion = 1) }
        assertFalse(store.saveLook(Look("a", M1.id, emptyList(), "a.jpg")))
        assertTrue(store.data.value.looks.isEmpty())
        assertTrue(store.writeFailed.value)
        store.close()
    }
    @Test fun addingEightViewsKeepsAllPreviousViewsAndRefusesANinth() {
        val store = Store(directory.newFolder()) { StoreData(medicines = listOf(M1), setupVersion = 1) }
        for (i in 1..8) assertTrue(store.saveLook(Look("$i", M1.id, emptyList(), "$i.jpg")))
        assertFalse(store.saveLook(Look("9", M1.id, emptyList(), "9.jpg")))
        assertEquals((1..8).map { "$it" }, store.data.value.looks.map { it.id })
        store.close()
    }
}
