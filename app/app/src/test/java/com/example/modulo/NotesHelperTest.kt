package com.example.modulo

import com.example.modulo.helpers.NotesHelper
import com.example.modulo.helpers.NotesHelper.NoteFile
import com.example.modulo.helpers.NotesHelper.NoteSort
import com.example.modulo.helpers.NotesHelper.Validation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure notes logic ([NotesHelper])
 */
class NotesHelperTest {

    private fun note(
        id: String,
        name: String = "",
        module: String = "",
        handbook: String = "",
        modifiedTime: String = ""
    ) = NotesHelper.Note(
        id = id,
        name = name,
        module = module,
        handbook = handbook,
        modifiedTime = modifiedTime
    )

    @Test
    fun `a null file is invalid, not a crash`() {
        assertTrue(NotesHelper.validateNoteFile(null) is Validation.Invalid)
    }

    @Test
    fun `a pdf under the limit is Ok`() {
        val file = NoteFile(name = "lecture.pdf", size = 1_000L, type = "application/pdf")
        assertEquals(Validation.Ok, NotesHelper.validateNoteFile(file))
    }

    @Test
    fun `any image subtype is allowed`() {
        for (type in listOf("image/png", "image/jpeg", "image/webp", "image/heic")) {
            val result = NotesHelper.validateNoteFile(NoteFile("photo", 500L, type))
            assertEquals("$type should be allowed", Validation.Ok, result)
        }
    }

    @Test
    fun `non-pdf non-image types are rejected`() {
        for (type in listOf("text/plain", "application/msword", "video/mp4", "")) {
            val result = NotesHelper.validateNoteFile(NoteFile("f", 500L, type))
            assertTrue("$type should be rejected", result is Validation.Invalid)
        }
    }

    @Test
    fun `an empty file is invalid`() {
        val result = NotesHelper.validateNoteFile(NoteFile("empty.pdf", 0L, "application/pdf"))
        assertTrue(result is Validation.Invalid)
    }

    @Test
    fun `a file exactly at the limit is Ok but one byte over is not`() {
        val atLimit = NoteFile("big.pdf", NotesHelper.MAX_NOTE_BYTES, "application/pdf")
        val overLimit = NoteFile("bigger.pdf", NotesHelper.MAX_NOTE_BYTES + 1, "application/pdf")
        assertEquals(Validation.Ok, NotesHelper.validateNoteFile(atLimit))
        assertTrue(NotesHelper.validateNoteFile(overLimit) is Validation.Invalid)
    }

    @Test
    fun `an oversize file names the actual size and the limit in its reason`() {
        val huge = NoteFile("huge.pdf", 10L * 1024 * 1024, "application/pdf")
        val reason = (NotesHelper.validateNoteFile(huge) as Validation.Invalid).reason
        assertTrue("mentions the file size", reason.contains("10"))
        assertTrue("mentions the 5 MB limit", reason.contains("5 MB"))
    }

    @Test
    fun `formatSize renders bytes, KB and MB with one decimal`() {
        assertEquals("512 B", NotesHelper.formatSize(512L))
        assertEquals("1 KB", NotesHelper.formatSize(1024L))
        assertEquals("1.5 KB", NotesHelper.formatSize(1536L))
        assertEquals("1 MB", NotesHelper.formatSize(1024L * 1024))
        assertEquals("2.5 MB", NotesHelper.formatSize((2.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `formatSize returns empty for null or negative`() {
        assertEquals("", NotesHelper.formatSize(null))
        assertEquals("", NotesHelper.formatSize(-1L))
    }

    @Test
    fun `no filters returns every note`() {
        val notes = listOf(note("1", "a"), note("2", "b"), note("3", "c"))
        assertEquals(3, NotesHelper.visibleNotes(notes).size)
    }

    @Test
    fun `handbook filter keeps only notes in that semester`() {
        val notes = listOf(
            note("1", "a", handbook = "SEM1"),
            note("2", "b", handbook = "SEM2"),
            note("3", "c", handbook = "SEM1"),
        )
        val shown = NotesHelper.visibleNotes(notes, handbookId = "SEM1")
        assertEquals(listOf("1", "3"), shown.map { it.id })
    }

    @Test
    fun `module filter keeps only that module`() {
        val notes = listOf(
            note("1", "a", module = "CS2030S"),
            note("2", "b", module = "MA1521"),
            note("3", "c", module = "CS2030S"),
        )
        val shown = NotesHelper.visibleNotes(notes, module = "CS2030S")
        assertEquals(listOf("1", "3"), shown.map { it.id })
    }

    @Test
    fun `handbook and module filters combine`() {
        val notes = listOf(
            note("1", "a", module = "CS2030S", handbook = "SEM1"),
            note("2", "b", module = "CS2030S", handbook = "SEM2"),
            note("3", "c", module = "MA1521", handbook = "SEM1"),
        )
        val shown = NotesHelper.visibleNotes(notes, handbookId = "SEM1", module = "CS2030S")
        assertEquals(listOf("1"), shown.map { it.id })
    }

    @Test
    fun `default sort is A to Z, case-insensitively`() {
        val notes = listOf(note("1", "banana"), note("2", "Apple"), note("3", "cherry"))
        val shown = NotesHelper.visibleNotes(notes)
        assertEquals(listOf("Apple", "banana", "cherry"), shown.map { it.name })
    }

    @Test
    fun `name sort is natural - note 2 comes before note 10`() {
        val notes = listOf(
            note("1", "Week 10"),
            note("2", "Week 2"),
            note("3", "Week 1"),
        )
        val shown = NotesHelper.visibleNotes(notes, sort = NoteSort.NAME)
        assertEquals(listOf("Week 1", "Week 2", "Week 10"), shown.map { it.name })
    }

    @Test
    fun `newest sort orders by modifiedTime descending`() {
        val notes = listOf(
            note("1", "a", modifiedTime = "2026-01-01T00:00:00Z"),
            note("2", "b", modifiedTime = "2026-03-01T00:00:00Z"),
            note("3", "c", modifiedTime = "2026-02-01T00:00:00Z"),
        )
        val shown = NotesHelper.visibleNotes(notes, sort = NoteSort.NEWEST)
        assertEquals(listOf("2", "3", "1"), shown.map { it.id })
    }

    @Test
    fun `visibleNotes never mutates the input list`() {
        val notes = listOf(note("1", "banana"), note("2", "Apple"))
        val snapshot = notes.map { it.id }
        NotesHelper.visibleNotes(notes, sort = NoteSort.NAME)
        assertEquals(snapshot, notes.map { it.id })
    }

    @Test
    fun `an empty list stays empty`() {
        assertTrue(NotesHelper.visibleNotes(emptyList()).isEmpty())
    }

    @Test
    fun `noteModules is the distinct, sorted, non-blank set of modules`() {
        val notes = listOf(
            note("1", module = "MA1521"),
            note("2", module = "CS2030S"),
            note("3", module = "CS2030S"),
            note("4", module = ""),
        )
        assertEquals(listOf("CS2030S", "MA1521"), NotesHelper.noteModules(notes))
    }

    @Test
    fun `noteModules is empty when nothing has a module`() {
        val notes = listOf(note("1"), note("2", module = ""))
        assertTrue(NotesHelper.noteModules(notes).isEmpty())
    }

    @Test
    fun `NOTE_MIME_TYPES advertises pdf and images to the picker`() {
        assertTrue(NotesHelper.NOTE_MIME_TYPES.contains("application/pdf"))
        assertTrue(NotesHelper.NOTE_MIME_TYPES.any { it.startsWith("image/") })
        assertFalse(NotesHelper.NOTE_MIME_TYPES.contains("*/*"))
    }
}