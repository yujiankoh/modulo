package com.example.modulo

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.example.modulo.helpers.DriveNoteException
import com.example.modulo.helpers.NotesHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Integration tests for [AppViewModel]'s notes operations against a mocked Drive
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesIntegrationTest : AppViewModelTestBase() {

    private fun note(id: String, name: String = "note", module: String = "") =
        NotesHelper.Note(id = id, name = name, module = module, mimeType = "application/pdf")

    @Test
    fun `notes are gated until Drive sync is enabled`() = runTest {
        assertTrue("a local-only user must be gated out of notes", viewModel.notesGated())

        signInWithMockedDrive()

        assertFalse("signing in with Drive lifts the gate", viewModel.notesGated())
    }

    @Test
    fun `loadNotes populates notesData from the Drive listing`() = runTest {
        val listing = listOf(note("1", "Week 1"), note("2", "Week 2"))
        coEvery { syncingHelper.listNotes() } returns listing
        signInWithMockedDrive()

        viewModel.loadNotes()
        advanceUntilIdle()

        val state = viewModel.notesData.value
        assertEquals(listing, state.notes)
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun `loadNotes surfaces a Drive failure as an error and clears loading`() = runTest {
        coEvery { syncingHelper.listNotes() } throws DriveNoteException("Google Drive is full")
        signInWithMockedDrive()

        viewModel.loadNotes()
        advanceUntilIdle()

        val state = viewModel.notesData.value
        assertNull(state.notes)
        assertFalse(state.loading)
        assertEquals("Google Drive is full", state.error)
    }

    @Test
    fun `loadNotes does nothing once notes are already cached`() = runTest {
        coEvery { syncingHelper.listNotes() } returns listOf(note("1"))
        signInWithMockedDrive()

        viewModel.loadNotes()
        advanceUntilIdle()
        viewModel.loadNotes() // second call should be a no-op
        advanceUntilIdle()

        coVerify(exactly = 1) { syncingHelper.listNotes() }
    }

    @Test
    fun `refreshNotes re-fetches even when notes are already loaded`() = runTest {
        coEvery { syncingHelper.listNotes() } returns listOf(note("1"))
        signInWithMockedDrive()

        viewModel.loadNotes()
        advanceUntilIdle()
        viewModel.refreshNotes()
        advanceUntilIdle()

        coVerify(exactly = 2) { syncingHelper.listNotes() }
    }

    @Test
    fun `uploadNote appends the created note to the cached list`() = runTest {
        coEvery { syncingHelper.listNotes() } returns emptyList()
        val created = note("new", "lecture.pdf")
        coEvery { syncingHelper.uploadNote(any(), any(), any(), any(), any()) } returns created
        stubContentResolverFor("lecture.pdf", size = 2048L, type = "application/pdf")
        signInWithMockedDrive()
        viewModel.loadNotes()
        advanceUntilIdle()

        var callbackError: String? = "unset"
        viewModel.uploadNote(mockk(relaxed = true), module = "CS2030S") { callbackError = it }
        advanceUntilIdle()

        assertNull("a successful upload reports no error", callbackError)
        assertEquals(listOf(created), viewModel.notesData.value.notes)
    }

    @Test
    fun `uploadNote rejects an unreadable file before touching Drive`() = runTest {
        signInWithMockedDrive()
        // No content-resolver stubbing: name/size resolve empty, so validation fails.

        var callbackError: String? = null
        viewModel.uploadNote(mockk(relaxed = true), module = "") { callbackError = it }
        advanceUntilIdle()

        assertTrue("an invalid file returns a reason", !callbackError.isNullOrBlank())
        coVerify(exactly = 0) { syncingHelper.uploadNote(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `renameNote replaces the note in place`() = runTest {
        coEvery { syncingHelper.listNotes() } returns listOf(note("1", "old"), note("2", "keep"))
        val renamed = note("1", "new name")
        coEvery { syncingHelper.renameNote("1", "new name") } returns renamed
        signInWithMockedDrive()
        viewModel.loadNotes()
        advanceUntilIdle()

        viewModel.renameNote("1", "new name")
        advanceUntilIdle()

        val names = viewModel.notesData.value.notes?.map { it.name }
        assertEquals(listOf("new name", "keep"), names)
    }

    @Test
    fun `deleteNote removes the note from the cached list`() = runTest {
        coEvery { syncingHelper.listNotes() } returns listOf(note("1"), note("2"))
        coEvery { syncingHelper.deleteNote("1") } returns Unit
        signInWithMockedDrive()
        viewModel.loadNotes()
        advanceUntilIdle()

        viewModel.deleteNote("1")
        advanceUntilIdle()

        assertEquals(listOf("2"), viewModel.notesData.value.notes?.map { it.id })
    }

    @Test
    fun `deleteNote failure leaves the list intact and reports an error`() = runTest {
        coEvery { syncingHelper.listNotes() } returns listOf(note("1"), note("2"))
        coEvery { syncingHelper.deleteNote("1") } throws DriveNoteException("Request failed")
        signInWithMockedDrive()
        viewModel.loadNotes()
        advanceUntilIdle()

        viewModel.deleteNote("1")
        advanceUntilIdle()

        val state = viewModel.notesData.value
        assertEquals("nothing is removed on failure", listOf("1", "2"), state.notes?.map { it.id })
        assertEquals("Request failed", state.error)
    }

    @Test
    fun `downloadNote returns the bytes from Drive`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        coEvery { syncingHelper.downloadNote("1") } returns bytes
        signInWithMockedDrive()

        val result = viewModel.downloadNote("1")

        assertTrue(bytes.contentEquals(result))
    }

    @Test
    fun `note operations are refused when sign-in has expired`() = runTest {
        // Drive sync is enabled but no syncingHelper exists (never authenticated this session).
        viewModel.saveSyncPreference(true)
        advanceUntilIdle()

        var uploadError: String? = null
        viewModel.uploadNote(mockk(relaxed = true), module = "") { uploadError = it }
        advanceUntilIdle()

        assertTrue("expired sign-in yields a reconnect prompt", uploadError?.contains("reconnect") == true)
    }

    /** Stub the content resolver so [AppViewModel.uploadNote] resolves a real name/size/type/stream. */
    private fun stubContentResolverFor(name: String, size: Long, type: String) {
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { mockApplication.contentResolver } returns resolver
        every { resolver.getType(any()) } returns type
        every { resolver.openInputStream(any()) } returns ByteArrayInputStream(ByteArray(size.toInt().coerceAtMost(16)))

        val cursor = mockk<Cursor>(relaxed = true)
        every { resolver.query(any<Uri>(), any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 1
        every { cursor.getString(0) } returns name
        every { cursor.isNull(1) } returns false
        every { cursor.getLong(1) } returns size
    }
}