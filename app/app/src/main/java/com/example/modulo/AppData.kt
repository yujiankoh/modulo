package com.example.modulo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * This enum class encapsulates the states of syncing
 */
enum class SyncState{
    UNSYNCED, SYNCING, SYNCED
}

/**
 * This data class encapsulates the information of a task
 */
@Serializable
data class Task(
    val id: Long,
    val title: String,
    val due: String = "",
    val type: String,
    val done: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * This data class encapsulates the information of a timetable
 */
@Serializable
data class Timetable(
    val modules: List<Module> = emptyList()
)

/**
 * This data class encapsulates the information of a module
 */
@Serializable
data class Module(
    val code: String,
    val name: String,
    val color: String,
    val slots: List<Slot> = emptyList()
)

/**
 * This data class encapsulates the information of a module slot in the timetable
 */
@Serializable
data class Slot(
    val day: String,
    val start: String,
    val end: String,
    val location: String = "",
    val type: String = ""
)

/**
 * This data class encapsulates all the information that needs to be saved
 */
@Serializable
data class AppData(
    val schemaVersion: Int = 1,
    val updatedAt: String? = null,
    val tasks: List<Task> = emptyList(),
    val timetable: Timetable? = null
)

val syncJsonParser = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}