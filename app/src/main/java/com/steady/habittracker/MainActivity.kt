package com.steady.habittracker

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
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

            Spacer(Modifier.height(16.dp))

            // Enhanced Progress Card: circle + weekly tracker + trend arrow + expandable per-group
            val yesterdayRate = weeklyRates.getOrNull(5)?.second ?: 0f  // 7 days reversed: index 6=today, 5=yest
            val trendDelta = ((completionRate - yesterdayRate) * 100).toInt()
            val trendPositive = completionRate >= yesterdayRate

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { progressExpanded = !progressExpanded },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "TODAY • ${period.lowercase().replaceFirstChar { it.uppercase() }}",
                            fontSize = 11.sp,
                            color = accent,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${doneCount}/${total}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            if (weeklyRates.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (trendDelta == 0) "•" else if (trendPositive) "▲${if (trendDelta>0) "+" else ""}${trendDelta}%" else "▼${trendDelta}%",
                                    color = if (trendPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(120.dp)
                    ) {
                        Canvas(modifier = Modifier.size(120.dp)) {
                            val strokeWidth = 12.dp.toPx()
                            drawArc(
                                color = surfaceVariantColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                size = Size(size.width, size.height)
                            )
                            drawArc(
                                color = accent,
                                startAngle = -90f,
                                sweepAngle = completionRate * 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                size = Size(size.width, size.height)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${(completionRate * 100).toInt()}%",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("today", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }

                    // Weekly tracker: 7 small circles
                    if (weeklyRates.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            weeklyRates.forEachIndexed { idx, (date, rate) ->
                                val c = when {
                                    rate >= 0.85f -> accent
                                    rate >= 0.5f -> accent.copy(alpha = 0.7f)
                                    rate > 0f -> accent.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                val label = try { java.time.LocalDate.parse(date).dayOfWeek.name.take(1) } catch (_: Exception) { "?" }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Canvas(Modifier.size(14.dp)) {
                                        drawCircle(color = c, radius = 7.dp.toPx())
                                    }
                                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                                }
                            }
                        }
                    }

                    // Expanded: per-group weekly circles
                    if (progressExpanded && groupWeeklyRates.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("This week by group", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            groupWeeklyRates.forEach { (gName, gRate) ->
                                val gc = when {
                                    gRate >= 0.85f -> accent
                                    gRate >= 0.5f -> accent.copy(alpha = 0.7f)
                                    gRate > 0f -> accent.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Canvas(Modifier.size(22.dp)) {
                                        drawCircle(color = gc, radius = 11.dp.toPx())
                                    }
                                    Text(gName.take(10), color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp)
                                    Text("${(gRate*100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

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
                    onQuickCapture = viewModel::addCapture  // #10 quick capture wired
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
                    schedules = appData.schedules,
                    activeScheduleId = appData.activeScheduleId
                )
            }

            // Log dialog inside the themed scope so it uses dark custom colors (fixes white popup #4)
            logHabit?.let { h ->
                LogEntryDialog(
                    habit = h,
                    onDismiss = { logHabit = null },
                    onLog = { value, note ->
                        viewModel.logEntry(h.id, value, note)
                        logHabit = null
                    }
                )
            }
        }
    }

    // Settings now inside the MaterialTheme so all dialogs use correct bg/surface/foreground
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Settings") },
            text = {
                Column {
                    Text("Background", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    val backgrounds = listOf(
                        "dark" to "Dark",
                        "amoled" to "AMOLED / OLED (pure black)",
                        "light" to "Light"
                    )
                    backgrounds.forEach { (key, label) ->
                        TextButton(onClick = {
                            viewModel.setBackgroundMode(key)
                            showSettings = false
                        }) {
                            Text(label, color = if (appData.backgroundMode == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Accent (highlight / foreground)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    val schemes = listOf(
                        "default" to "Green",
                        "blue" to "Blue",
                        "orange" to "Orange",
                        "purple" to "Purple",
                        "slate" to "Slate",
                        "teal" to "Teal",
                        "red" to "Red"
                    )
                    schemes.forEach { (key, label) ->
                        TextButton(onClick = {
                            viewModel.setColorScheme(key)
                            showSettings = false
                        }) {
                            Text(label, color = if (appData.colorScheme == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Changes apply instantly. OLED uses pure black for battery savings on AMOLED screens.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
