package app.stripsense.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class VoiceNotesTest {
    /** Not real sealing: enough to see that what is written is not what was recorded, and opens back to it. */
    private val cipher = object : StoreCipher {
        override fun seal(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun open(sealed: ByteArray) = sealed.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
    }
    private val recording = ByteArray(2000) { (it % 251).toByte() }

    @Test fun aMessageIsSealedOnDiskAndComesBackAsRecorded() {
        val dir = Files.createTempDirectory("notes").toFile()
        val notes = VoiceNotes(dir, cipher)
        assertFalse(notes.has("med-1"))
        assertTrue(notes.save("med-1", recording))
        assertTrue(notes.has("med-1"))
        val onDisk = dir.listFiles()!!.single().readBytes()
        assertTrue(Sealed.isSealed(onDisk))
        assertFalse(onDisk.copyOfRange(4, onDisk.size).contentEquals(recording))
        assertArrayEquals(recording, VoiceNotes(dir, cipher).load("med-1"))
        // Without this phone's key it cannot be opened.
        assertNull(VoiceNotes(dir, null).load("med-1"))
        notes.delete("med-1")
        assertFalse(notes.has("med-1"))
        assertNull(notes.load("med-1"))
    }
    @Test fun aMedicineIdCannotReachOutsideTheFolder() {
        val dir = Files.createTempDirectory("notes").toFile()
        val notes = VoiceNotes(dir, cipher)
        assertTrue(notes.save("../../evil", recording))
        assertEquals(dir.canonicalFile, dir.listFiles()!!.single().canonicalFile.parentFile)
        assertFalse(File(dir.parentFile, "evil.note").exists())
    }
}
