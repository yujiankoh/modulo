package com.example.modulo.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.modulo.AppViewModel
import com.example.modulo.R
import com.example.modulo.getModuleColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.text.isNotBlank

@Composable
fun TimetablePage(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onUploadTimetable: () -> Unit,
) {
    val appData by viewModel.appData.collectAsState()
    val timetable = appData.timetable

    var termStartDate by remember { mutableStateOf(if (appData.termStart == null) null else LocalDate.parse(appData.termStart)) }
    var termEndDate by remember { mutableStateOf(if (appData.termEnd == null) null else LocalDate.parse(appData.termEnd)) }

    // Rounded down to the earliest Monday and latest Friday
    val baseMonday = remember(termStartDate) {
        termStartDate?.let { start -> start.minusDays(start.dayOfWeek.value - 1L) }
    }
    val endFriday = remember(termEndDate) {
        termEndDate?.let { date ->
            val daysToFriday = (5 - date.dayOfWeek.value).let {
                if (it < 0) it + 7 else it
            }
            date.plusDays(daysToFriday.toLong())
        }
    }

    val today = LocalDate.now()
    val isOutsideTerm = remember(baseMonday, endFriday) {
        if (baseMonday != null && endFriday != null) {
            today.isBefore(baseMonday) || today.isAfter(endFriday)
        } else {
            false
        }
    }

    val totalWeeks = remember(baseMonday, endFriday) {
        if (baseMonday != null && endFriday != null) {
            val daysTotal = ChronoUnit.DAYS.between(baseMonday, endFriday)
            ((daysTotal / 7) + 1).toInt()
        } else {
            1
        }
    }
    val currentWeekNum = remember(baseMonday) {
        baseMonday?.let {
            val daysPassed = ChronoUnit.DAYS.between(it, today)
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
                    week = slot.week.lowercase(),
                    classNo = slot.classNo
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
                    .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row (
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.arrow_left), contentDescription = "Go Back")
                    }

                    Text(text = "Timetable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                // Reupload Button
                Button(
                    onClick = onUploadTimetable,
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        top = ButtonDefaults.ContentPadding.calculateTopPadding(),
                        end = ButtonDefaults.ContentPadding.calculateEndPadding(layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr),
                        bottom = ButtonDefaults.ContentPadding.calculateBottomPadding()
                    )
                ) {
                    Icon(painter = painterResource(R.drawable.rotate), contentDescription = "Reupload")
                    Spacer(modifier = Modifier.padding(6.dp))
                    Text("Reupload")
                }
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
                    onDateSelected = { newDate ->
                        termStartDate = newDate
                        viewModel.saveTermStart(newDate)
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.padding(8.dp))

                DatePickerMenu(
                    selectedDate = termEndDate,
                    label = "Term End",
                    onDateSelected = { newDate ->
                        termEndDate = newDate
                        viewModel.saveTermEnd(newDate)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // No timetable warning
            if (timetable == null) {
                MissingTimetableBanner(onUploadClicked = onUploadTimetable)
                return@Scaffold
            }

            if (termStartDate != null && baseMonday != null) {
                if (isOutsideTerm) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "The term has not started yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val weekStart = baseMonday.plusWeeks(selectedWeekNum - 1L)
                    val weekEnd = weekStart.plusDays(4) // Friday
                    val formatter = DateTimeFormatter.ofPattern("dd MMM")

                    // Week Selector
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (selectedWeekNum > 1) selectedWeekNum-- },
                            enabled = selectedWeekNum > 1
                        ) {
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
                        IconButton(
                            onClick = { if (selectedWeekNum < totalWeeks) selectedWeekNum++ },
                            enabled = selectedWeekNum < totalWeeks
                        ) {
                            Icon(painter = painterResource(R.drawable.chevron_right), contentDescription = "Next Week")
                        }
                    }
                }
            } else if (hasEvenOddSplit) {
                // Even / Odd option
                SecondaryTabRow(
                    selectedTabIndex = if (manualTab == "odd") 0 else 1,
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    Tab(selected = manualTab == "odd", onClick = { manualTab = "odd" }, text = { Text("Odd Week") })
                    Tab(selected = manualTab == "even", onClick = { manualTab = "even" }, text = { Text("Even Week") })
                }
            }

            if (!isOutsideTerm) {
                val slotsToShow = allDisplaySlots.filter { it.week == "all" || it.week == activeWeekType }
                val dayNames = listOf("MON", "TUE", "WED", "THU", "FRI")

                // Calculate the start and end timing of timetable (minimum 1000 - 1600)
                val allTimes = slotsToShow.flatMap {
                    listOf(parseTimeString(it.start), parseTimeString(it.end))
                }
                val earliestClass = allTimes.minOrNull() ?: LocalTime.of(10, 0)
                val latestClass = allTimes.maxOrNull() ?: LocalTime.of(16, 0)
                val minTime = if (earliestClass.withMinute(0).isAfter(LocalTime.of(10, 0))) {
                    LocalTime.of(10, 0)
                } else {
                    earliestClass.withMinute(0)
                }
                val maxTime = if (latestClass.minute == 0) {
                    latestClass
                } else {
                    latestClass.withMinute(0).plusHours(1)
                }.let {
                    if (it.isBefore(LocalTime.of(16, 0))) LocalTime.of(16, 0) else it
                }
                val totalHours = ChronoUnit.HOURS.between(minTime, maxTime).toInt()

                // Grid dimensions
                val hourWidth = 120.dp
                val hourWidthPx = with(LocalDensity.current) { hourWidth.toPx() }
                val dayHeight = 90.dp
                val timeHeaderHeight = 30.dp

                // Scroll State & Auto-Scroll to Current Time
                val scrollState = rememberScrollState()
                val verticalScrollState = rememberScrollState()
                val screenSize = LocalWindowInfo.current.containerSize
                val halfScreenWidthPx = with(LocalDensity.current) { (screenSize.width.toDp() / 2).toPx() }
                val currentTime = LocalTime.now()

                LaunchedEffect(Unit) {
                    // Center the current-time line on the screen
                    val currentOffsetPx = calculateOffset(currentTime, minTime, hourWidthPx)
                    val targetPx = currentOffsetPx - halfScreenWidthPx
                    if (targetPx > 0) {
                        scrollState.scrollTo(targetPx.toInt())
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(verticalScrollState)
                        .padding(top = 16.dp)
                ) {
                    // Days Column
                    Column(
                        modifier = Modifier
                            .width(36.dp)
                            .background(MaterialTheme.colorScheme.background)
                            .zIndex(1f)
                    ) {
                        Spacer(modifier = Modifier.height(timeHeaderHeight))
                        dayNames.forEach { day ->
                            Box(
                                modifier = Modifier
                                    .height(dayHeight)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.map { it.toString() }.joinToString("\n"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Timetable
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState)
                    ) {
                        val gridTotalWidth = hourWidth * totalHours
                        val gridTotalHeight = dayHeight * dayNames.size

                        Box(
                            modifier = Modifier
                                .width(gridTotalWidth)
                                .height(gridTotalHeight + timeHeaderHeight)
                        ) {
                            // Horizontal dividing lines
                            for (i in 0..dayNames.size) {
                                val yOffset = timeHeaderHeight + (dayHeight * i)
                                Box(modifier = Modifier.offset(y = yOffset).width(gridTotalWidth).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
                            }

                            // Vertical dividing lines
                            for (i in 0..totalHours) {
                                val xOffset = hourWidth * i

                                // Vertical Line
                                Box(modifier = Modifier.offset(x = xOffset, y = timeHeaderHeight).width(1.dp).height(gridTotalHeight).background(MaterialTheme.colorScheme.outlineVariant))

                                // Time Header Text
                                Text(
                                    text = minTime.plusHours(i.toLong()).format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.offset(x = xOffset + 4.dp, y = timeHeaderHeight - 20.dp)
                                )
                            }
                        }

                        // Module Blocks
                        slotsToShow.forEach { slot ->
                            val slotStart = parseTimeString(slot.start)
                            val slotEnd = parseTimeString(slot.end)
                            val dayIndex = dayNames.indexOf(slot.day)

                            if (dayIndex >= 0) {
                                val xOffset = calculateOffset(slotStart, minTime, hourWidth.value).dp
                                val yOffset = timeHeaderHeight + (dayHeight * dayIndex)
                                val blockWidth = calculateWidth(slotStart, slotEnd, hourWidth.value).dp

                                Box(
                                    modifier = Modifier
                                        .offset(x = xOffset, y = yOffset)
                                        .width(blockWidth)
                                        .height(dayHeight)
                                        .padding(horizontal = 2.dp, vertical = 4.dp)
                                ) {
                                    TimetableGridBlock(educationLevel = timetable.educationLevel, slot = slot)
                                }
                            }
                        }

                        // Current-Time line
                        if (currentTime.isAfter(minTime) && currentTime.isBefore(maxTime)) {
                            val timeLineOffset = calculateOffset(currentTime, minTime, hourWidth.value).dp
                            Box(
                                modifier = Modifier
                                    .offset(x = timeLineOffset, y = timeHeaderHeight)
                                    .width(2.dp)
                                    .height(gridTotalHeight)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .zIndex(2f)
                            )
                        }
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
    val week: String,
    val classNo: String,
)

@Composable
fun TimetableGridBlock(
    educationLevel: String,
    slot: DisplaySlot
) {
    val theme = getModuleColor(slot.moduleCode.ifBlank { slot.moduleName })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(theme.container)
            .clickable {
                // TODO: Open Edit Dialog
            }
            .padding(8.dp)
    ) {
        if (educationLevel == "poly" || educationLevel == "university") {
            Text(
                text = slot.moduleCode,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = theme.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (slot.moduleName.isNotBlank()) {
                Text(
                    text = slot.moduleName,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.onContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = slot.moduleName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = theme.onContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Location Badge
        if (slot.location.isNotBlank()) {
            Text(
                text = slot.location,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = theme.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun parseTimeString(timeStr: String): LocalTime {
    // Handles standard formats like "0800", "08:00", or "14:30"
    val cleanStr = timeStr.replace(":", "")
    val hour = cleanStr.take(2).toIntOrNull() ?: 0
    val minute = cleanStr.drop(2).take(2).toIntOrNull() ?: 0
    return LocalTime.of(hour, minute)
}

fun calculateOffset(startTime: LocalTime, minTime: LocalTime, hourWidthDp: Float): Float {
    val minutesFromStart = ChronoUnit.MINUTES.between(minTime, startTime)
    return (minutesFromStart / 60f) * hourWidthDp
}

fun calculateWidth(startTime: LocalTime, endTime: LocalTime, hourWidthDp: Float): Float {
    val durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime)
    return (durationMinutes / 60f) * hourWidthDp
}