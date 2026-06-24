package com.example.modulo.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.modulo.AppViewModel
import com.example.modulo.EducationLevel
import com.example.modulo.R
import com.example.modulo.getModuleColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun TimetablePage(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onUploadTimetable: () -> Unit,
) {
    val appData by viewModel.appData.collectAsState()
    val timetable = appData.timetable

    var termStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var termEndDate by remember { mutableStateOf<LocalDate?>(null) }
    val baseMonday = remember(termStartDate) {
        termStartDate?.minusDays(termStartDate!!.dayOfWeek.value - 1L)
    }
    val currentWeekNum = remember(baseMonday) {
        baseMonday?.let {
            val daysPassed = ChronoUnit.DAYS.between(it, LocalDate.now())
            if (daysPassed < 0) 1 else (daysPassed / 7).toInt() + 1
        } ?: 1
    }

    var selectedWeekNum by remember(currentWeekNum) { mutableIntStateOf(currentWeekNum) }
    var manualTab by remember { mutableStateOf("odd") }

    val allDisplaySlots = remember(timetable) {
        timetable?.modules?.flatMap { module ->
            module.slots.map { slot ->
                DisplaySlot(
                    moduleCode = module.code,
                    moduleName = module.name,
                    day = slot.day,
                    start = slot.start,
                    end = slot.end,
                    location = slot.location,
                    sessionType = slot.sessionType,
                    week = slot.week.lowercase()
                )
            }
        } ?: emptyList()
    }
    val hasEvenOddSplit = allDisplaySlots.any { it.week == "even" || it.week == "odd" }

    val activeWeekType = if (termStartDate != null) {
        if (selectedWeekNum % 2 == 0) "even" else "odd"
    } else {
        manualTab
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(painter = painterResource(R.drawable.arrow_left), contentDescription = "Go Back")
                }

                Text(text = "Timetable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DatePickerMenu(
                    selectedDate = termStartDate,
                    label = "Term Start",
                    onDateSelected = { newDate -> termStartDate = newDate },
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.padding(8.dp))

                DatePickerMenu(
                    selectedDate = termEndDate,
                    label = "Term End",
                    onDateSelected = { newDate -> termEndDate = newDate },
                    modifier = Modifier.weight(1f)
                )
            }

            // No timetable warning
            if (timetable == null) {
                MissingTimetableBanner(onUploadClicked = onUploadTimetable)
                return@Scaffold
            }

            if (termStartDate != null && baseMonday != null) {
                val weekStart = baseMonday.plusWeeks(selectedWeekNum - 1L)
                val weekEnd = weekStart.plusDays(4) // Friday
                val formatter = DateTimeFormatter.ofPattern("dd MMM")

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (selectedWeekNum > 1) selectedWeekNum-- }) {
                        Icon(painter = painterResource(R.drawable.chevron_left), contentDescription = "Previous Week")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Week $selectedWeekNum", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${weekStart.format(formatter)} - ${weekEnd.format(formatter)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { selectedWeekNum++ }) {
                        Icon(painter = painterResource(R.drawable.chevron_right), contentDescription = "Next Week")
                    }
                }
            } else if (hasEvenOddSplit) {
                SecondaryTabRow(selectedTabIndex = if (manualTab == "odd") 0 else 1) {
                    Tab(selected = manualTab == "odd", onClick = { manualTab = "odd" }, text = { Text("Odd Week") })
                    Tab(selected = manualTab == "even", onClick = { manualTab = "even" }, text = { Text("Even Week") })
                }
            }

            val slotsToShow = allDisplaySlots.filter { it.week == "all" || it.week == activeWeekType }
            val dayNames = listOf("MON", "TUE", "WED", "THU", "FRI")
            val groupedSlots = slotsToShow.groupBy { it.day }
                .toSortedMap(compareBy { dayNames.indexOf(it) })

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedSlots.forEach { (day, slotsForDay) ->
                    item {
                        val dayHeader = if (baseMonday != null) {
                            val dayOffset = dayNames.indexOf(day).toLong()
                            val targetDate = baseMonday.plusWeeks(selectedWeekNum - 1L).plusDays(dayOffset)
                            "$day (${targetDate.format(DateTimeFormatter.ofPattern("dd MMM"))})"
                        } else {
                            day
                        }

                        Text(
                            text = dayHeader,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                        )
                    }

                    items(slotsForDay.sortedBy { it.start }) { slot ->
                        TimetableSlotCard(
                            educationLevel = appData.educationLevel ?: "",
                            slot = slot
                        )
                    }
                }
            }
        }
    }
}

data class DisplaySlot(
    val moduleCode: String,
    val moduleName: String,
    val day: String,
    val start: String,
    val end: String,
    val location: String,
    val sessionType: String,
    val week: String
)

@Composable
fun TimetableSlotCard(
    educationLevel: String,
    slot: DisplaySlot
) {
    val theme = getModuleColor(slot.moduleCode)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.container)
            .clickable {
                // TODO: Part 2b - Open Edit Dialog
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time Column
        Column(
            modifier = Modifier.width(80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(slot.start, fontWeight = FontWeight.Bold)
            Text("to", style = MaterialTheme.typography.labelSmall)
            Text(slot.end, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Details Column
        Column(modifier = Modifier.weight(1f)) {
            if (educationLevel == "poly" || educationLevel == "university") {
                Text(
                    text = slot.moduleCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.onContainer
                )
                if (slot.moduleName.isNotBlank()) {
                    Text(
                        text = slot.moduleName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.onContainer.copy(alpha = 0.8f)
                    )
                }
            } else {
                Text(
                    text = slot.moduleName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.onContainer
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (slot.location.isNotBlank()) {
                    Badge(containerColor = Color.White.copy(alpha = 0.5f)) {
                        Text(slot.location, color = theme.onContainer)
                    }
                }
                if (slot.sessionType.isNotBlank()) {
                    Badge(containerColor = Color.White.copy(alpha = 0.5f)) {
                        Text(slot.sessionType.uppercase(), color = theme.onContainer)
                    }
                }
            }
        }
    }
}