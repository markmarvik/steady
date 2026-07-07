package com.steady.habittracker

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Habit
import com.steady.habittracker.ui.HistoryScreen
import com.steady.habittracker.ui.LogEntryDialog
import com.steady.habittracker.ui.ManageScreen
import com.steady.habittracker.ui.OnboardingScreen
import com.steady.habittracker.ui.SkipPromptDialog
import com.steady.habittracker.ui.SteadyViewModel
import com.steady.habittracker.ui.TabButton
import com.steady.habittracker.ui.TodayScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create repo once (Android specific). ViewModel will hold reference.
        val repository = AndroidHabitRepository(this)

        // Schedule any configured reminders (best effort on launch / after boot handled by receiver)
        try {
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            scope.launch {
                val data = repository.appDataFlow.first()
                com.steady.habittracker.reminders.AlarmScheduler.scheduleAll(this@MainActivity, data.reminders)
            }
        } catch (_: Exception) {}

        setContent {
            val viewModel: SteadyViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return SteadyViewModel(repository) as T
                    }
                }
            )
            // Theme is resolved inside SteadyApp using appData.colorScheme (dynamic)
            SteadyApp(viewModel = viewModel, repository = repository)
        }
    }
}

@Composable
fun SteadyApp(viewModel: SteadyViewModel, repository: AndroidHabitRepository) {
    val appData by viewModel.appData.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var promptHabitId by remember { mutableStateOf<String?>(null) }
    var progressExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val completionRate by viewModel.completionRate.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val todayEntries by viewModel.todayEntries.collectAsState()
    val weeklyRates by viewModel.weeklyRates.collectAsState()
    val groupWeeklyRates by viewModel.groupWeeklyRates.collectAsState()
    val today = viewModel.today
    val period by viewModel.currentPeriod.collectAsState()

    val doneCount = todayEntries.count { (_, e) -> e.value >= 0.5 }
    val total = appData.habits.size.coerceAtLeast(1)

    val promptHabit = promptHabitId?.let { id -> appData.habits.find { it.id == id } }

    // For NOTE / richer type log dialogs (gratitude, duration, counters etc.)
    var logHabit by remember { mutableStateOf<Habit?>(null) }

    val context = LocalContext.current
    val accent = getAccentColor(appData.colorScheme)

    // SAF launcher for JSON backup export (works for empty data: groups/habits structure only)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { u ->
            try {
                val json = Json { prettyPrint = true }
                val payload = json.encodeToString(appData)
                context.contentResolver.openOutputStream(u)?.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Backup exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (!appData.onboarded) {
        // Onboarding gate (full screen for first run)
        OnboardingScreen(onComplete = { viewModel.completeOnboarding() })
    } else {
    // Full dynamic theming: background (dark/amoled/light) + accent (foreground highlight)
    // All UI elements below should use MaterialTheme.colorScheme.* or derived values.
    val isLight = appData.backgroundMode == "light"
    val isAmoled = appData.backgroundMode == "amoled"

    val bgColor = when {
        isAmoled -> Color.Black
        isLight -> Color(0xFFF8FAFC)
        else -> Color(0xFF0F172A)
    }
    val surfaceColor = when {
        isAmoled -> Color(0xFF0F0F0F)
        isLight -> Color.White
        else -> Color(0xFF1E2937)
    }
    val onSurfaceColor = if (isLight) Color(0xFF0F172A) else Color(0xFFE2E8F0)
    val onSurfaceVariant = if (isLight) Color(0xFF475569) else Color(0xFF94A3B8)

    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            background = bgColor,
            onBackground = onSurfaceColor,
            surface = surfaceColor,
            onSurface = onSurfaceColor,
            surfaceVariant = if (isAmoled) Color(0xFF1A1A1A) else Color(0xFF334155),
            onSurfaceVariant = onSurfaceVariant,
            outline = onSurfaceVariant
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            background = bgColor,
            onBackground = onSurfaceColor,
            surface = surfaceColor,
            onSurface = onSurfaceColor,
            surfaceVariant = if (isAmoled) Color(0xFF1A1A1A) else Color(0xFF334155),
            onSurfaceVariant = onSurfaceVariant,
            outline = onSurfaceVariant
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
    Scaffold(
        containerColor = bgColor
        // No FAB: removed per request (adds only via Manage now)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Header with gear for Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Steady",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        if (streak > 0) {
                            Text("🔥 $streak day streak", color = accent, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Compact progress bar at top (always visible, works across all tabs, minimal screen space)
            // Tap to expand details in a dialog (big circle no longer permanently occupies space)
            val yesterdayRate = weeklyRates.getOrNull(5)?.second ?: 0f
            val trendDelta = ((completionRate - yesterdayRate) * 100).toInt()
            val trendPositive = completionRate >= yesterdayRate

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { progressExpanded = !progressExpanded },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TODAY • ${period.lowercase().replaceFirstChar { it.uppercase() }}",
                            fontSize = 10.sp,
                            color = accent,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text("${(completionRate * 100).toInt()}%  ${doneCount}/${total}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (trendDelta != 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (trendPositive) "▲${trendDelta}%" else "▼${trendDelta}%",
                                color = if (trendPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 10.sp
                            )
                        }
                        if (streak > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text("🔥$streak", color = accent, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { completionRate },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = accent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    // Weekly dots (compact)
                    if (weeklyRates.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            weeklyRates.forEachIndexed { _, (date, rate) ->
                                val c = when {
                                    rate >= 0.85f -> accent
                                    rate >= 0.5f -> accent.copy(alpha = 0.7f)
                                    rate > 0f -> accent.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                Canvas(Modifier.size(8.dp)) {
                                    drawCircle(color = c, radius = 4.dp.toPx())
                                }
                            }
                        }
                    }
                }
            }

            // Expandable details dialog (big circle + per-group when user taps the compact bar)
            if (progressExpanded) {
                AlertDialog(
                    onDismissRequest = { progressExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurface,
                    title = { Text("Progress Details") },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Big circle (now only in dialog, scrollable when inside Today if desired)
                            val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                                Canvas(modifier = Modifier.size(110.dp)) {
                                    val strokeWidth = 10.dp.toPx()
                                    drawArc(color = surfaceVariantColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                                    drawArc(color = accent, startAngle = -90f, sweepAngle = completionRate * 360f, useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${(completionRate * 100).toInt()}%", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("today", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                }
                            }
                            if (groupWeeklyRates.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("This week by group", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                Spacer(Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    groupWeeklyRates.forEach { (gName, gRate) ->
                                        val gc = when {
                                            gRate >= 0.85f -> accent
                                            gRate >= 0.5f -> accent.copy(alpha = 0.7f)
                                            gRate > 0f -> accent.copy(alpha = 0.4f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Canvas(Modifier.size(18.dp)) { drawCircle(color = gc, radius = 9.dp.toPx()) }
                                            Text(gName.take(8), color = MaterialTheme.colorScheme.onSurface, fontSize = 8.sp)
                                            Text("${(gRate*100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { progressExpanded = false }) { Text("Close") } }
                )
            }

            Spacer(Modifier.height(10.dp))

            // Tabs (Today / History / Manage) — 3 tabs kept
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                    .padding(4.dp)
            ) {
                TabButton("Today", selectedTab == 0, modifier = Modifier.weight(1f)) { selectedTab = 0 }
                TabButton("History", selectedTab == 1, modifier = Modifier.weight(1f)) { selectedTab = 1 }
                TabButton("Manage", selectedTab == 2, modifier = Modifier.weight(1f)) { selectedTab = 2 }
            }

            Spacer(Modifier.height(12.dp))

            when (selectedTab) {
                0 -> TodayScreen(
                    appData = appData,
                    todayEntries = todayEntries,
                    onToggle = viewModel::toggleCheckbox,
                    onLogEntry = viewModel::logEntry,  // kept for compatibility in some paths
                    onRequestLog = { h -> logHabit = h },
                    onSkip = viewModel::skipHabit,
                    onShowSkipPrompt = { id -> promptHabitId = id },
                    onQuickCapture = viewModel::addCapture,
                    onProcessCapture = viewModel::markCaptureProcessed,
                    onDeleteCapture = viewModel::deleteCapture,
                    onCreateMetric = { name -> viewModel.addMetricHabit(name) },
                    onLogMetric = viewModel::logEntry
                )
                1 -> HistoryScreen(appData = appData)
                2 -> ManageScreen(
                    appData = appData,
                    onAddGroup = { n, h, p -> viewModel.addGroup(n, h, p) },
                    onAddHabit = { name, why, gid, type, isSupp -> viewModel.addHabit(name, why, gid, type, isSupp) },
                    onDeleteHabit = viewModel::deleteHabit,  // now archives
                    onSetReminder = viewModel::setReminder,
                    onToggleReminder = viewModel::toggleReminder,
                    onArchiveGroup = { viewModel.deleteGroup(it) },
                    onExportCsv = { exportLauncher.launch("steady_backup_${java.time.LocalDate.now()}.json") },
                    onImportCsv = { /* TODO: can add OpenDocument for JSON restore later */ },
                    onUpdateHabit = viewModel::updateHabit,
                    onUnarchiveGroup = { viewModel.unarchiveGroup(it) },
                    onUnarchiveHabit = { viewModel.unarchiveHabit(it) },
                    onApplySchedulePreset = viewModel::applySchedulePreset,
                    onSetActiveSchedule = viewModel::setActiveSchedule,
                    onUpdateScheduleBlocks = viewModel::updateScheduleBlocks,
                    schedules = appData.schedules,
                    activeScheduleId = appData.activeScheduleId
                )
            }

            // Log dialog inside the themed scope so it uses dark custom colors (fixes white popup #4)
            logHabit?.let { h ->
                LogEntryDialog(
                    habit = h,
                    onDismiss = { logHabit = null },
                    onLog = { value, note, date ->
                        viewModel.logEntry(h.id, value, note, date)
                        logHabit = null
                    }
                )
            }
        }
    }

    // Settings now inside the MaterialTheme so all dialogs use correct bg/surface/foreground
    if (showSettings) {
        // Compute current accent for preview (matches resolveThemeColors)
        val currentAccent = when (appData.colorScheme) {
            "blue" -> Color(0xFF3B82F6)
            "orange" -> Color(0xFFF97316)
            "purple" -> Color(0xFF8B5CF6)
            "slate" -> Color(0xFF64748B)
            "teal" -> Color(0xFF14B8A6)
            "red" -> Color(0xFFEF4444)
            else -> Color(0xFF22C55E)
        }
        val previewBg = when (appData.backgroundMode) {
            "amoled" -> Color.Black
            "light" -> Color(0xFFF8FAFC)
            else -> Color(0xFF0F172A)
        }
        val previewSurface = when (appData.backgroundMode) {
            "amoled" -> Color(0xFF0F0F0F)
            "light" -> Color.White
            else -> Color(0xFF1E2937)
        }
        val previewOn = if (appData.backgroundMode == "light") Color(0xFF0F172A) else Color(0xFFE2E8F0)

        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Settings • Theme") },
            text = {
                Column {
                    Text("Background Mode (OLED / Light / Dark)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    val backgrounds = listOf(
                        "dark" to "Dark",
                        "amoled" to "AMOLED / OLED",
                        "light" to "Light"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        backgrounds.forEach { (key, label) ->
                            val isSel = appData.backgroundMode == key
                            val swatchBg = when (key) {
                                "amoled" -> Color.Black
                                "light" -> Color(0xFFF8FAFC)
                                else -> Color(0xFF0F172A)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(swatchBg)
                                    .border(
                                        if (isSel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        viewModel.setBackgroundMode(key)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (key == "light") Color(0xFF0F172A) else Color.White, fontSize = 10.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Accent Color (foreground / highlights)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))

                    val schemes = listOf(
                        Triple("default", "Green", Color(0xFF22C55E)),
                        Triple("blue", "Blue", Color(0xFF3B82F6)),
                        Triple("orange", "Orange", Color(0xFFF97316)),
                        Triple("purple", "Purple", Color(0xFF8B5CF6)),
                        Triple("slate", "Slate", Color(0xFF64748B)),
                        Triple("teal", "Teal", Color(0xFF14B8A6)),
                        Triple("red", "Red", Color(0xFFEF4444))
                    )
                    // Visual swatches grid/row
                    Column {
                        schemes.chunked(4).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { (key, label, col) ->
                                    val isSel = appData.colorScheme == key
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(col)
                                                .border(
                                                    if (isSel) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color(0x33FFFFFF)),
                                                    CircleShape
                                                )
                                                .clickable { viewModel.setColorScheme(key) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSel) {
                                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Text(label, fontSize = 9.sp, color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                                // pad if short row
                                repeat(4 - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Live Preview (with current bg + accent)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    // Mini preview card simulating UI elements
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = previewSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            // fake header
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Steady", color = previewOn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Box(Modifier.size(10.dp).background(currentAccent, CircleShape))
                            }
                            Spacer(Modifier.height(6.dp))
                            // fake habit row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(previewBg.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(14.dp).background(currentAccent, RoundedCornerShape(3.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text("Morning Sunlight", color = previewOn, fontSize = 11.sp)
                                Spacer(Modifier.weight(1f))
                                Box(Modifier.size(18.dp).background(currentAccent.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("✓", color = currentAccent, fontSize = 9.sp)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            // fake button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(currentAccent, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Log Entry", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Instant updates. AMOLED for pure black (battery friendly on OLED). All cards, texts, progress & widget use theme.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("Close") }
            }
        )
    }

    // Skip prompt (inside theme)
    promptHabit?.let { h ->
        SkipPromptDialog(
            habit = h,
            skipCount = viewModel.getRecentSkipCount(h.id),
            onDismiss = { promptHabitId = null },
            onEdit = { /* open edit - for now just clear */ promptHabitId = null },
            onArchive = { viewModel.deleteHabit(h.id); promptHabitId = null },
            onLockIn = {
                val locked = h.copy(canSkip = false)
                viewModel.updateHabit(locked)
                promptHabitId = null
            }
        )
    }
    } // end Scaffold
    } // end MaterialTheme + else (non-onboarding)
}

// Accent resolver (used for dynamic theme + progress). Matches resolveThemeColors.
private fun getAccentColor(scheme: String): Color = when (scheme) {
    "blue" -> Color(0xFF3B82F6)
    "orange" -> Color(0xFFF97316)
    "purple" -> Color(0xFF8B5CF6)
    "slate" -> Color(0xFF64748B)
    "teal" -> Color(0xFF14B8A6)
    "red" -> Color(0xFFEF4444)
    else -> Color(0xFF22C55E) // default green
}

// UI composables have been extracted to ui/ package
// (keeps MainActivity small and focused)
