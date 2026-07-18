package com.example.modulo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.modulo.helpers.NotesHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.abs

/**
 * This enum class encapsulates the states of syncing
 */
enum class SyncState{
    OFFLINE, UNSYNCED, SYNCING, SYNCED
}

/**
 * This enum class encapsulates the states of app startup
 */
enum class StartupState{
    LOADING, TUTORIAL, SIGN_IN, AUTHENTICATE, HANDBOOK, READY
}

/**
 * This enum class encapsulates the sorting option of tasks
 */
enum class SortOption(val displayName: String) {
    DUE_DATE("Due Date"),
    MODULE_CODE("Module Code"),
    TYPE("Task Type"),
    TITLE("Name")
}

enum class EducationLevel(val json: String, val displayName: String) {
    PRIMARY("primary", "Primary"),
    SECONDARY("secondary", "Secondary"),
    JC("jc", "JC"),
    POLY("poly", "Polytechnic"),
    UNIVERSITY("university", "University");

    companion object {
        fun fromJson(json: String?): EducationLevel? {
            return entries.find { it.json == json }
        }
        fun getDisplay(json: String?): String {
            return fromJson(json)?.displayName ?: ""
        }
    }
}

@Serializable
data class Task(
    val id: Long,
    val module: String = "",
    val title: String,
    val due: String = "",
    val type: String,
    val done: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class Grade(
    val id: String = UUID.randomUUID().toString(),
    val module: String = "",
    val credits: Double = 0.0,
    val grade: String = "",
    val su: Boolean = false
)

@Serializable
data class Timetable(
    val educationLevel: String,
    val modules: List<Module> = emptyList()
)

@Serializable
data class Module(
    val code: String,
    val name: String,
    val slots: List<Slot> = emptyList()
)

@Serializable
data class Slot(
    val day: String,
    val start: String,
    val end: String,
    val location: String = "",
    val sessionType: String = "",
    val classNo: String = "",
    val week: String = "all"
)

@Serializable
data class Break(
    val start: String,
    val end: String
)

@Serializable
data class StudySession(
    val id: String = UUID.randomUUID().toString(),
    val start: String,
    val end: String,
    val durationMins: Int,
    val rating: Int? = null,
    val createdAt: String? = null
)

@Serializable
data class City(
    val buildings: List<Building> = emptyList()
)

@Serializable
data class Building(
    val x: Int,
    val y: Int,
    val floors: Int
)

@Serializable
data class Handbook(
    val id: String,
    val educationLevel: String? = null,
    val academicYear: String? = null,
    val semester: Int? = null,
    val termStart: String? = null,
    val termEnd: String? = null,
    val breaks: List<Break> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val grades: List<Grade> = emptyList(),
    val hiddenModules: List<String> = emptyList(),
    val timetable: Timetable? = null
)

/**
 * This data class encapsulates all the information that needs to be saved
 */
@Serializable
data class AppData(
    val schemaVersion: Int = 2,
    val handbookId: String = "",
    val educationLevel: String? = null,
    val academicYear: String? = null,
    val semester: Int? = null,
    val termStart: String? = null,
    val termEnd: String? = null,
    val breaks: List<Break> = emptyList(),
    val updatedAt: String? = null,
    val tasks: List<Task> = emptyList(),
    val grades: List<Grade> = emptyList(),
    val studySessions: List<StudySession> = emptyList(),
    val hiddenModules: List<String> = emptyList(),
    val city: City = City(),
    val timetable: Timetable? = null,
    val otherHandbooks: List<Handbook> = emptyList()
)
/**
 * This data class encapsulates all the timetable information to send to proxy
 */
@Serializable
data class ParsingData(
    val image: String,
    val mimeType: String,
    val educationLevel: String
)

@Serializable
val syncJsonParser = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

sealed interface TimetableState {
    object Idle : TimetableState
    object Processing : TimetableState
    data class ReviewData(val timetable: Timetable, val append: Boolean = false) : TimetableState
    data class Error(val message: String) : TimetableState
}

data class NotesData(
    val notes: List<NotesHelper.Note>? = null,
    val loading: Boolean = false,
    val error: String? = null
)

data class ModuleColor(val container: Color, val onContainer: Color)

val lightColors = listOf(
    ModuleColor(Color(0xFF2F6DF0), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF1C7ED6), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF1B6EC2), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF3B5BDB), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF4263EB), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF5C7CFA), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF6741D9), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF7048E8), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF5F3DC4), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF862E9C), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF1098AD), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF0C8599), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF0E8F9D), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF087F8C), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF0CA678), Color(0xFFFFFFFF)),
    ModuleColor(Color(0xFF099268), Color(0xFFFFFFFF))
)

val darkColors = listOf(
    ModuleColor(Color(0xFF4DABF7), Color(0xFF001D3D)),
    ModuleColor(Color(0xFF74C0FC), Color(0xFF001D3D)),
    ModuleColor(Color(0xFF5C7CFA), Color(0xFF00104B)),
    ModuleColor(Color(0xFF748FFC), Color(0xFF00104B)),
    ModuleColor(Color(0xFF9775FA), Color(0xFF25005B)),
    ModuleColor(Color(0xFF845EF7), Color(0xFF1A004B)),
    ModuleColor(Color(0xFFBE4BDB), Color(0xFF30004A)),
    ModuleColor(Color(0xFFCC5DE8), Color(0xFF370054)),
    ModuleColor(Color(0xFF22B8CF), Color(0xFF002026)),
    ModuleColor(Color(0xFF3BC9DB), Color(0xFF002026)),
    ModuleColor(Color(0xFF66D9E8), Color(0xFF002026)),
    ModuleColor(Color(0xFF20C997), Color(0xFF002014)),
    ModuleColor(Color(0xFF12B886), Color(0xFF002014)),
    ModuleColor(Color(0xFF38D9A9), Color(0xFF002014)),
    ModuleColor(Color(0xFF40C057), Color(0xFF002204)),
    ModuleColor(Color(0xFF51CF66), Color(0xFF002204))
)

val emojis = arrayOf("\uD83D\uDDD1\uFE0F", "\uD83D\uDCA9", "\uD83D\uDE10", "\uD83D\uDD25", "\uD83D\uDCA1")

@Composable
fun getModuleColor(code: String): ModuleColor {
    val colors = if (isSystemInDarkTheme()) darkColors else lightColors
    val index = abs(code.hashCode()) % colors.size
    return colors[index]
}