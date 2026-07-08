package com.steady.habittracker

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Habit
import com.steady.habittracker.reminders.NotificationHelper
import com.steady.habittracker.ui.HistoryScreen
import com.steady.habittracker.ui.LogEntryDialog
import com.steady.habittracker.ui.ManageScreen
import com.steady.habittracker.ui.OnboardingScreen
import com.steady.habittracker.ui.SkipPromptDialog
import com.steady.habittracker.ui.SteadyViewModel
import com.steady.habittracker.ui.TabButton
import com.steady.habittracker.ui.TodayScreen
import com.steady.habittracker.ui.TOUR_STEPS
import com.steady.habittracker.ui.TourCoach
import com.steady.habittracker.ui.TourHeaderIndicator
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

    /** Deep-link extras from widget / notifications (survives onNewIntent). */
    val deepLinkLogHabit = mutableStateOf<String?>(null)
    val deepLinkOpenGroup = mutableStateOf<String?>(null)

    private fun captureDeepLinks(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra("log_habit")?.let {
            deepLinkLogHabit.value = it
            intent.removeExtra("log_habit")
        }
        intent.getStringExtra("open_group")?.let {
            deepLinkOpenGroup.value = it
            intent.removeExtra("open_group")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureDeepLinks(intent)

        // Create repo once (Android specific). ViewModel will hold reference.
        val repository = AndroidHabitRepository(this)

        // Schedule any configured reminders (best effort on launch / after boot handled by receiver)
        try {
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            scope.launch {
                val data = repository.appDataFlow.first()
                com.steady.habittracker.reminders.AlarmScheduler.scheduleAll(this@MainActivity, data)
            }
        } catch (_: Exception) {}

        setContent {
            val viewModel: SteadyViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return SteadyViewModel(repository, application) as T
                    }
                }
            )
            SteadyApp(viewModel = viewModel, repository = repository)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDeepLinks(intent)
    }
}

@Composable
fun SteadyApp(
    viewModel: SteadyViewModel,
    repository: AndroidHabitRepository
) {
    val appData by viewModel.appData.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var promptHabitId by remember { mutableStateOf<String?>(null) }
    var progressExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Help / interactive onboarding tour ("run onboarding")
    var showHelpTour by remember { mutableStateOf(false) }
    var tourStep by remember { mutableIntStateOf(0) }
    var showWelcomeGuide by remember { mutableStateOf(false) }

    val completionRate by viewModel.completionRate.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val todayEntries by viewModel.todayEntries.collectAsState()
    val weeklyRates by viewModel.weeklyRates.collectAsState()
    val groupWeeklyRates by viewModel.groupWeeklyRates.collectAsState()
    val today = viewModel.today
    val period by viewModel.currentPeriod.collectAsState()

    val dueToday = remember(appData, today) {
        com.steady.habittracker.data.HabitDomain.habitsDueOn(appData, java.time.LocalDate.parse(today))
    }
    val doneCount = dueToday.count { h -> (todayEntries[h.id]?.value ?: 0.0) >= 0.5 }
    val total = dueToday.size.coerceAtLeast(1)

    val promptHabit = promptHabitId?.let { id -> appData.habits.find { it.id == id } }

    // For NOTE / richer type log dialogs (gratitude, duration, counters etc.)
    var logHabit by remember { mutableStateOf<Habit?>(null) }

    val context = LocalContext.current
    val activity = context as? MainActivity
    val accent = getAccentColor(appData.colorScheme)

    val deepLogState = activity?.deepLinkLogHabit ?: remember { mutableStateOf<String?>(null) }
    val deepGroupState = activity?.deepLinkOpenGroup ?: remember { mutableStateOf<String?>(null) }
    val deepLog by deepLogState
    val deepGroup by deepGroupState

    // Deep-links from widget (log_habit) and notifications (open_group)
    LaunchedEffect(deepLog, appData.habits) {
        val logId = deepLog ?: return@LaunchedEffect
        val h = appData.habits.find { it.id == logId && !it.archived }
        if (h != null) {
            selectedTab = 0
            logHabit = h
        }
        activity?.deepLinkLogHabit?.value = null
    }
    LaunchedEffect(deepGroup) {
        if (deepGroup != null) {
            selectedTab = 0
            activity?.deepLinkOpenGroup?.value = null
        }
    }

    // Notification permission (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored; Settings shows status */ }

    LaunchedEffect(Unit) {
        NotificationHelper.ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Drive tab changes when tour step changes (takes user around the app)
    LaunchedEffect(tourStep, showHelpTour) {
        if (showHelpTour) {
            when (tourStep) {
                0, 1, 2 -> if (selectedTab != 0) selectedTab = 0
                3 -> selectedTab = 0
                4 -> selectedTab = 1
                5 -> selectedTab = 2
                // later steps stay on the relevant tab
            }
        }
    }

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
                    IconButton(onClick = {
                        showHelpTour = true
                        tourStep = 0
                        selectedTab = 0
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Help / Tour", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

            if (showHelpTour) {
                TourHeaderIndicator(stepIndex = tourStep, onEnd = { showHelpTour = false })
            }

            Spacer(Modifier.height(12.dp))

            // Tab content area always takes remaining vertical space so inner lists and top actions (like +Capture/+Log) have proper layout.
            Box(modifier = Modifier.weight(1f)) {
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
                        onAddHabit = { name, gid, type, isSupp, preset, weekdays, interval, dates ->
                            viewModel.addHabit(
                                name = name,
                                groupId = gid,
                                type = type,
                                isSupplement = isSupp,
                                showPreset = preset,
                                weekdays = weekdays,
                                intervalDays = interval,
                                specificDates = dates
                            )
                        },
                        onDeleteHabit = viewModel::deleteHabit,
                        onSetReminder = viewModel::setReminder,
                        onToggleReminder = viewModel::toggleReminder,
                        onArchiveGroup = { viewModel.deleteGroup(it) },
                        onExportCsv = { exportLauncher.launch("steady_backup_${java.time.LocalDate.now()}.json") },
                        onImportCsv = { },
                        onUpdateHabit = viewModel::updateHabit,
                        onUnarchiveGroup = { viewModel.unarchiveGroup(it) },
                        onUnarchiveHabit = { viewModel.unarchiveHabit(it) },
                        onReorderHabit = viewModel::reorderHabit,
                        onMoveHabitToGroup = viewModel::moveHabitToGroup,
                        onApplySchedulePreset = viewModel::applySchedulePreset,
                        onSetActiveSchedule = viewModel::setActiveSchedule,
                        onUpdateScheduleBlocks = viewModel::updateScheduleBlocks,
                        schedules = appData.schedules,
                        activeScheduleId = appData.activeScheduleId
                    )
                }
            }

            // Interactive help / onboarding tour coach (sits below the tab content when active)
            if (showHelpTour) {
                TourCoach(
                    stepIndex = tourStep,
                    onNext = {
                        if (tourStep >= TOUR_STEPS.lastIndex) {
                            showHelpTour = false
                        } else {
                            val next = tourStep + 1
                            tourStep = next
                            // Drive the UI to "take the user around"
                            when (next) {
                                3 -> selectedTab = 0   // Today deep dive
                                4 -> selectedTab = 1   // History
                                5 -> selectedTab = 2   // Manage
                                else -> { /* keep current tab */ }
                            }
                        }
                    },
                    onPrev = {
                        val prev = (tourStep - 1).coerceAtLeast(0)
                        tourStep = prev
                        when (prev) {
                            3 -> selectedTab = 0
                            4 -> selectedTab = 1
                            5 -> selectedTab = 2
                            else -> { /* keep */ }
                        }
                    },
                    onEnd = { showHelpTour = false }
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
            title = { Text("Settings") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // --- Reminders ---
                    Text("Reminders", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(Modifier.weight(1f)) {
                            Column {
                                Text("Habit reminders", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                Text("Regular notifications for your routines", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = appData.remindersMasterEnabled,
                            onCheckedChange = { viewModel.setRemindersMasterEnabled(it) }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    appData.reminders.sortedBy { it.time }.forEach { rem ->
                        val gName = rem.groupId?.let { gid -> appData.groups.find { it.id == gid }?.name } ?: "Daily Review"
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.weight(1f)) {
                                Column {
                                    Text("$gName - ${rem.time}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                                    Text("${rem.days.size} day(s)/week", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                }
                            }
                            Switch(
                                checked = rem.enabled && appData.remindersMasterEnabled,
                                enabled = appData.remindersMasterEnabled,
                                onCheckedChange = { viewModel.toggleReminder(rem.id) }
                            )
                        }
                    }
                    Text("Edit times in Manage > open a group > bell icon.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else true
                    val am = context.getSystemService(AlarmManager::class.java)
                    val exactOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        am?.canScheduleExactAlarms() == true
                    } else true
                    if (!notifOk) {
                        TextButton(onClick = {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) { Text("Allow notifications", fontSize = 12.sp) }
                    }
                    if (!exactOk) {
                        TextButton(onClick = {
                            try {
                                val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                i.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(i)
                            } catch (_: Exception) {
                                try {
                                    val i2 = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    i2.data = Uri.parse("package:${context.packageName}")
                                    context.startActivity(i2)
                                } catch (_: Exception) {}
                            }
                        }) { Text("Allow exact alarms", fontSize = 12.sp) }
                    }
                    if (notifOk && exactOk) {
                        Text("Notifications & exact alarms: OK", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                    }

                    Spacer(Modifier.height(16.dp))
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

                    Spacer(Modifier.height(16.dp))
                    Text("Help", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            showSettings = false
                            showHelpTour = true
                            tourStep = 0
                            selectedTab = 0
                        }) {
                            Text("Guided tour")
                        }
                        OutlinedButton(onClick = {
                            showSettings = false
                            showWelcomeGuide = true
                        }) {
                            Text("Welcome guide")
                        }
                    }
                    Text("Guided tour walks the screens. Welcome guide is the first-run summary. Header ? also starts the tour.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("Close") }
            }
        )
    }

    // Welcome / onboarding guide dialog (replayable "run onboarding" summary)
    if (showWelcomeGuide) {
        AlertDialog(
            onDismissRequest = { showWelcomeGuide = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Welcome to Steady") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                ) {
                    Text(
                        "Evidence-based habits. Simple daily tracking.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    val features = listOf(
                        "📅 Groups by time of day (Morning, Focus, Evening, Mindset...)",
                        "✅ Tap to log. Rich types: checkboxes, counters, minutes, scales, notes.",
                        "📈 See your streak and weekly trends at a glance.",
                        "🔔 Reminders you control in Manage.",
                        "🧩 The home-screen widget shows exactly what to do right now.",
                        "💾 Export your data anytime (even when just starting).",
                        "🗄 Archive instead of delete — your history stays safe."
                    )
                    features.forEach { f ->
                        Text(
                            f,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "We've started you with a proven set of high-ROI habits.\nCustomize freely in the Manage tab.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tip: Add the Steady widget to your home screen for 1-tap logging.",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showWelcomeGuide = false }) { Text("Got it") }
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
