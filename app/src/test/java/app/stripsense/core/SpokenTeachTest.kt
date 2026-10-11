package app.stripsense.core

import app.stripsense.core.teach.SpokenTeach
import org.junit.Assert.*
import org.junit.Test

class SpokenTeachTest {
    @Test fun theTimesComeFromTheWordsInEnglishHindiAndRomanHindi() {
        assertEquals(listOf("08:00", "20:00"), SpokenTeach.times("One in the morning and one at night"))
        assertEquals(listOf("08:00", "20:00"), SpokenTeach.times("subah aur raat ko khana khane ke baad"))
        assertEquals(listOf("08:00", "14:00", "20:00"), SpokenTeach.times("सुबह, दोपहर और रात"))
        assertEquals(listOf("20:00"), SpokenTeach.times("sirf raat ko sone se pehle"))
        assertEquals(listOf("08:00", "20:00"), SpokenTeach.times("din mein do baar"))
        assertEquals(listOf("08:00", "14:00", "20:00"), SpokenTeach.times("three times a day"))
        assertEquals(listOf("08:00"), SpokenTeach.times("once daily"))
        assertEquals(listOf("08:00", "20:00"), SpokenTeach.times("1-0-1"))
    }
    @Test fun wordsWithNoTimeGiveNoTimeSoNothingIsInvented() {
        assertTrue(SpokenTeach.times("this is the BP medicine").isEmpty())
        assertTrue(SpokenTeach.times("").isEmpty())
    }
    @Test fun onlyTheSpokenLineIsKeptFromTheModelsReply() {
        assertEquals("subah aur raat ko", SpokenTeach.transcript("\n\"subah aur raat ko\"\nThe speaker says..."))
        assertEquals("", SpokenTeach.transcript("   "))
        assertFalse(SpokenTeach.QUESTION.contains("time", ignoreCase = true))
    }
}
