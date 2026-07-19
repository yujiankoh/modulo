package com.example.modulo.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.modulo.AppData
import com.example.modulo.AppViewModel
import com.example.modulo.Grade
import com.example.modulo.R
import com.example.modulo.components.DropDownMenu
import com.example.modulo.getModuleColor
import com.example.modulo.helpers.GpaHelper
import com.example.modulo.helpers.GpaHelper.SchemeResult
import com.example.modulo.ui.theme.ModuloTheme
import java.util.Locale

@Composable
fun GPAPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val appData by viewModel.appData.collectAsState()
    val extraModules = remember { mutableStateListOf<String>() }
    var addInput by remember { mutableStateOf("") }

    val schemeResult = GpaHelper.schemeForLevel(appData.educationLevel)

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(painter = painterResource(R.drawable.arrow_left), contentDescription = "Go Back")
                }
                Text(
                    text = "Grades",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (schemeResult is SchemeResult.Supported) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", schemeResult.scheme.maxPoints)} scale",
                        style = MaterialTheme.typography.labelLarge,
                        color = ModuloTheme.colors.subText,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }

            (schemeResult as? SchemeResult.Supported)?.let { supported ->
                SupportedContent(
                    viewModel = viewModel,
                    appData = appData,
                    scheme = supported.scheme,
                    extraModules = extraModules,
                    addInput = addInput,
                    onAddInputChange = { addInput = it },
                    onAddModule = {
                        val label = addInput.trim()
                        addInput = ""
                        if (label.isNotBlank() && label !in extraModules) extraModules.add(label)
                    }
                )
            }

            SemesterHistory(appData)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SupportedContent(
    viewModel: AppViewModel,
    appData: AppData,
    scheme: GpaHelper.Scheme,
    extraModules: MutableList<String>,
    addInput: String,
    onAddInputChange: (String) -> Unit,
    onAddModule: () -> Unit
) {
    val semester = GpaHelper.computeGPA(appData.grades, scheme)
    val cumulative = GpaHelper.cumulativeGPA(appData)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GpaStatCard(
            label = "Semester GPA",
            value = formatGPA(semester.gpa),
            accent = false,
            modifier = Modifier.weight(1f)
        )
        GpaStatCard(
            label = "Cumulative GPA",
            value = formatGPA(cumulative?.gpa),
            accent = true,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    val rows = buildRows(appData, extraModules)
    if (rows.isEmpty()) {
        Text(
            text = "No modules yet. Parse your timetable, or add a module below.",
            style = MaterialTheme.typography.bodyMedium,
            color = ModuloTheme.colors.subText,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in rows) {
                key(row.module) {
                    GradeRowCard(
                        row = row,
                        scheme = scheme,
                        onPersist = { credits, grade, su ->
                            if (!(grade.isBlank() && row.id == null)) {
                                viewModel.upsertGrade(row.id, row.module, credits, grade, su)
                            }
                        },
                        onRemoveExtra = { extraModules.remove(row.module) }
                    )
                }
            }
        }

        if (semester.skippedCount > 0) {
            Text(
                text = if (semester.skippedCount == 1)
                    "1 graded module isn't counted. Check its credits."
                else
                    "${semester.skippedCount} graded modules aren't counted. Check their credits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Add a new module
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = addInput,
            onValueChange = onAddInputChange,
            singleLine = true,
            placeholder = { Text("Add another module") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onAddModule) {
            Icon(painter = painterResource(R.drawable.plus), contentDescription = "Add module")
        }
    }
}

@Composable
private fun GpaStatCard(
    label: String,
    value: String,
    accent: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (accent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (accent) MaterialTheme.colorScheme.onPrimary
                else ModuloTheme.colors.subText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (accent) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GradeRowCard(
    row: GradeRow,
    scheme: GpaHelper.Scheme,
    onPersist: (credits: Double, grade: String, su: Boolean) -> Unit,
    onRemoveExtra: () -> Unit
) {
    var creditsText by remember { mutableStateOf(formatCredits(row.credits)) }
    var su by remember { mutableStateOf(row.su) }

    fun currentCredits(): Double = creditsText.trim().toDoubleOrNull() ?: 0.0
    val theme = getModuleColor(row.module)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(theme.container)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = row.module,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (row.isExtra && !row.stored) {
                    IconButton(onClick = onRemoveExtra, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.trash_2),
                            contentDescription = "Remove ${row.module}"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = creditsText,
                    onValueChange = { text ->
                        creditsText = text
                        if (row.stored && row.grade.isNotBlank()) {
                            onPersist(text.trim().toDoubleOrNull() ?: 0.0, row.grade, su)
                        }
                    },
                    label = { Text("Credits") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(110.dp)
                )

                DropDownMenu(
                    label = "Grade",
                    selectedItem = row.grade,
                    items = gradeOptions(scheme, row.grade),
                    itemToText = { if (it.isNullOrBlank()) "—" else it },
                    onItemSelected = { g -> onPersist(currentCredits(), g, su) },
                    modifier = Modifier.weight(1f)
                )

                if (scheme.suElection) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "S/U",
                            style = MaterialTheme.typography.labelSmall,
                            color = ModuloTheme.colors.subText
                        )
                        Checkbox(
                            checked = su,
                            onCheckedChange = { checked ->
                                su = checked
                                if (row.stored && row.grade.isNotBlank()) {
                                    onPersist(currentCredits(), row.grade, checked)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class HistoryEntry(
    val level: String?,
    val academicYear: String?,
    val semester: Int?,
    val grades: List<Grade>,
    val active: Boolean,
    val label: String
)

private fun parseStartYear(academicYear: String?): Int? {
    if (academicYear.isNullOrBlank()) return null
    return academicYear.split("/").firstOrNull()?.toIntOrNull()?.let { 2000 + it }
}

@Composable
private fun SemesterHistory(appData: AppData) {
    val entries = remember(appData) {
        val active = HistoryEntry(
            level = appData.educationLevel,
            academicYear = appData.academicYear,
            semester = appData.semester,
            grades = appData.grades,
            active = true,
            label = handbookTitle(appData)
        )

        val all = (listOf(active) + appData.otherHandbooks.map {
            HistoryEntry(it.educationLevel, it.academicYear, it.semester, it.grades, active = false, label = handbookTitle(it))
        }).filter { GpaHelper.schemeForLevel(it.level) is SchemeResult.Supported }

        all.sortedBy { h ->
            val year = parseStartYear(h.academicYear)
            if (year == null) Long.MAX_VALUE else year * 10L + (h.semester ?: 0)
        }
    }

    if (entries.size < 2) return

    val schemeIds = entries.map {
        (GpaHelper.schemeForLevel(it.level) as SchemeResult.Supported).scheme.id
    }.toSet()
    val mixed = schemeIds.size > 1

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Semester history",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            entries.forEachIndexed { index, h ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                SemesterHistoryRow(h, mixed)
            }
        }
    }
}

@Composable
private fun SemesterHistoryRow(entry: HistoryEntry, mixed: Boolean) {
    val scheme = (GpaHelper.schemeForLevel(entry.level) as SchemeResult.Supported).scheme
    val value = formatGPA(GpaHelper.computeGPA(entry.grades, scheme).gpa)
    val gpaText = if (mixed) "$value (${String.format(Locale.US, "%.1f", scheme.maxPoints)})" else value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyMedium
            )
            if (entry.active) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "current",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Text(
            text = gpaText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private data class GradeRow(
    val id: String?,
    val module: String,
    val credits: Double,
    val grade: String,
    val su: Boolean,
    val stored: Boolean,
    val isExtra: Boolean
)

// Format to XX.XX
private fun formatGPA(gpa: Double?): String {
    return if (gpa == null) "-" else String.format(Locale.US, "%.2f", gpa)
}

// Whole numbers show without a trailing ".0"; halves keep the decimal (2.5).
private fun formatCredits(credits: Double): String {
    return if (credits <= 0) "" else if (credits % 1.0 == 0.0)
        credits.toInt().toString() else credits.toString()
}

private fun defaultCredits(level: String?): Double = if (level == "university") 4.0 else 0.0

private fun timetableModuleLabels(data: AppData): List<String> =
    data.timetable?.modules.orEmpty()
        .map { it.code.ifBlank { it.name } }
        .filter { it.isNotBlank() }
        .distinct()

private fun buildRows(data: AppData, extras: List<String>): List<GradeRow> {
    val rows = mutableListOf<GradeRow>()
    val seen = mutableSetOf<String>()

    for (g in data.grades) {
        rows.add(GradeRow(g.id, g.module, g.credits, g.grade, g.su, stored = true, isExtra = false))
        seen.add(g.module)
    }
    for (label in timetableModuleLabels(data) + extras) {
        if (!seen.add(label)) continue
        rows.add(
            GradeRow(
                id = null, module = label, credits = defaultCredits(data.educationLevel),
                grade = "", su = false, stored = false, isExtra = label in extras
            )
        )
    }
    rows.sortBy { it.module.lowercase(Locale.getDefault()) }
    return rows
}

private fun gradeOptions(scheme: GpaHelper.Scheme, currentGrade: String): List<String> {
    val opts = mutableListOf("")
    opts.addAll(scheme.points.keys)
    val dropdownExcluded =
        if (scheme.suElection) scheme.excluded.filter { it != "S" && it != "U" } else scheme.excluded
    opts.addAll(dropdownExcluded)
    if (currentGrade.isNotBlank() && currentGrade !in opts) opts.add(currentGrade)
    return opts
}
