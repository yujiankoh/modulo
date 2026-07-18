package com.example.modulo.helpers

object NotesHelper {
    data class Note(
        val id: String,
        val name: String = "",
        val size: Long? = null,
        val mimeType: String = "",
        val module: String = "",
        val handbook: String = "",
        val modifiedTime: String = "" 
    )

    // A file the user picked to upload (name/size/type resolved from its Uri).
    data class NoteFile(
        val name: String,
        val size: Long?,
        val type: String
    )

    sealed interface Validation {
        object Ok : Validation
        data class Invalid(val reason: String) : Validation
    }

    enum class NoteSort { NAME, NEWEST }
    
    const val MAX_NOTE_BYTES = 5L * 1024 * 1024
    
    val NOTE_MIME_TYPES = arrayOf("application/pdf", "image/*")

    private fun isAllowedType(mime: String): Boolean =
        mime == "application/pdf" || mime.startsWith("image/")
    
    fun validateNoteFile(file: NoteFile?): Validation {
        val size = file?.size ?: return Validation.Invalid("That file couldn't be read.")
        if (!isAllowedType(file.type)) return Validation.Invalid("Only PDFs and images can be uploaded for now.")
        if (size == 0L) return Validation.Invalid("That file is empty.")
        if (size > MAX_NOTE_BYTES) {
            return Validation.Invalid(
                "That file is ${formatSize(size)} — the limit is ${formatSize(MAX_NOTE_BYTES)}. " +
                    "Notes use your own Google Drive storage."
            )
        }
        return Validation.Ok
    }
    
    fun formatSize(bytes: Long?): String {
        val n = bytes ?: return ""
        if (n < 0) return ""
        if (n < 1024) return "$n B"
        val kb = n / 1024.0
        if (kb < 1024) return "${round1(kb)} KB"
        return "${round1(kb / 1024)} MB"
    }
    
    private fun round1(x: Double): String {
        val r = Math.round(x * 10) / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }
    
    fun visibleNotes(
        notes: List<Note>,
        handbookId: String? = null,
        module: String? = null,
        sort: NoteSort = NoteSort.NAME
    ): List<Note> {
        val shown = notes.filter { note ->
            if (handbookId != null && note.handbook != handbookId) return@filter false
            if (module != null && note.module != module) return@filter false
            true
        }

        return if (sort == NoteSort.NEWEST) {
            shown.sortedByDescending { it.modifiedTime }
        } else {
            shown.sortedWith(Comparator { a, b -> naturalCompare(a.name, b.name) })
        }
    }
    
    fun noteModules(notes: List<Note>): List<String> =
        notes.mapNotNull { it.module.takeIf { m -> m.isNotEmpty() } }.distinct().sorted()
    
    private fun naturalCompare(a: String, b: String): Int {
        val x = a.lowercase(); val y = b.lowercase()
        var i = 0; var j = 0
        while (i < x.length && j < y.length) {
            val cx = x[i]; val cy = y[j]
            if (cx.isDigit() && cy.isDigit()) {
                val si = i; val sj = j
                while (i < x.length && x[i].isDigit()) i++
                while (j < y.length && y[j].isDigit()) j++
                val nx = x.substring(si, i).trimStart('0').ifEmpty { "0" }
                val ny = y.substring(sj, j).trimStart('0').ifEmpty { "0" }
                val cmp = if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
                if (cmp != 0) return cmp
            } else {
                if (cx != cy) return cx - cy
                i++; j++
            }
        }
        return (x.length - i) - (y.length - j)
    }
}