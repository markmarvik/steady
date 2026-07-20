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
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Habit
import com.steady.habittracker.reminders.NotificationHelper
import com.steady.habittracker.data.SleepNightSession
import com.steady.habittracker.ui.AccentHuePicker
import com.steady.habittracker.ui.HistoryScreen
import com.steady.habittracker.ui.LogEntryDialog
import com.steady.habittracker.ui.ManageScreen
import com.steady.habittracker.ui.OnboardingScreen
import com.steady.habittracker.ui.SkipPromptDialog
import com.steady.habittracker.ui.SleepNightDetailScreen
import com.steady.habittracker.ui.SteadyViewModel
import com.steady.habittracker.ui.TabButton
import com.steady.habittracker.ui.TodayScreen
import com.steady.habittracker.ui.TOUR_STEPS
import com.steady.habittracker.ui.TourCoach
import com.steady.habittracker.ui.TourHeaderIndicator
import com.steady.habittracker.ui.WorkoutSessionScreen
import com.steady.habittracker.ui.PathScreen
import com.steady.habittracker.ui.DreamlineWizard
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
    /** Widget "+ Capture" — open Today capture dialog. */
    val deepLinkOpenCapture = mutableStateOf(false)
    /** Widget "+ Log" — open Today metric log dialog. */
    val deepLinkOpenMetricLog = mutableStateOf(false)

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
        if (intent.getStringExtra("open_capture") == "1" || intent.getBooleanExtra("open_capture", false)) {
            deepLinkOpenCapture.value = true
            intent.removeExtra("open_capture")
        }
        if (intent.getStringExtra("open_log") == "1" || intent.getBooleanExtra("open_log", false)) {
            deepLinkOpenMetricLog.value = true
            intent.removeExtra("open_log")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
    var openSleepNight by remember { mutableStateOf<SleepNightSession?>(null) }

    // Help / interactive onboarding tour ("run onboarding")
    var showHelpTour by remember { mutableStateOf(false) }
    var tourStep by remember { mutableIntStateOf(0) }
    var showWelcomeGuide by remember { mutableStateOf(false) }

    val completionRate by viewModel.completionRate.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val todayPoints by viewModel.todayPoints.collectAsState()
    val momentumLevel by viewModel.momentumLevel.collectAsState()
    val pointsToNextLevel by viewModel.pointsToNextLevel.collectAsState()
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
    // Active workout session (full-screen logger)
    var activeWorkoutRoutine by remember {
        mutableStateOf<com.steady.habittracker.data.ExerciseRoutine?>(null)
    }
    var showDreamlineWizard by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? MainActivity
    val accent = getAccentColor(appData.colorScheme)

    // Keep LAN web server in sync with prefs (#38)
    LaunchedEffect(appData.localWebPrefs.enabled, appData.localWebPrefs.port, appData.localWebPrefs.pin) {
        com.steady.habittracker.web.LocalWebServer.setEnabled(context, appData)
    }

    val deepLogState = activity?.deepLinkLogHabit ?: remember { mutableStateOf<String?>(null) }
    val deepGroupState = activity?.deepLinkOpenGroup ?: remember { mutableStateOf<String?>(null) }
    val deepCaptureState = activity?.deepLinkOpenCapture ?: remember { mutableStateOf(false) }
    val deepMetricLogState = activity?.deepLinkOpenMetricLog ?: remember { mutableStateOf(false) }
    val deepLog by deepLogState
    val deepGroup by deepGroupState
    val deepCapture by deepCaptureState
    val deepMetricLog by deepMetricLogState

    // Deep-links from widget (log_habit / capture / log) and notifications (open_group)
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
    LaunchedEffect(deepCapture) {
        if (deepCapture) selectedTab = 0
    }
    LaunchedEffect(deepMetricLog) {
        if (deepMetricLog) selectedTab = 0
    }

    // Extension log side-effects: open capture / workout / sleep review (#33)
    val pendingCapture by viewModel.pendingOpenCapture.collectAsState()
    val pendingWorkout by viewModel.pendingOpenWorkout.collectAsState()
    val pendingSleep by viewModel.pendingOpenSleepReview.collectAsState()
    val extSummary by viewModel.lastExtensionSummary.collectAsState()
    LaunchedEffect(pendingCapture) {
        if (pendingCapture) {
            selectedTab = 0
            activity?.deepLinkOpenCapture?.value = true
            viewModel.pendingOpenCapture.value = false
        }
    }
    LaunchedEffect(pendingWorkout) {
        if (pendingWorkout) {
            selectedTab = 0
            // Prefer first active routine if any
            val rt = appData.routines.firstOrNull { !it.archived }
            if (rt != null) activeWorkoutRoutine = rt
            viewModel.pendingOpenWorkout.value = false
        }
    }
    LaunchedEffect(pendingSleep) {
        if (pendingSleep) {
            selectedTab = 2
            openSleepNight = appData.sleepNights.firstOrNull()
            viewModel.pendingOpenSleepReview.value = false
        }
    }
    LaunchedEffect(extSummary) {
        if (extSummary.isNotBlank()) {
            Toast.makeText(context, extSummary.take(120), Toast.LENGTH_LONG).show()
            viewModel.lastExtensionSummary.value = ""
        }
    }

    // Notification permission (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored; Settings shows status */ }

    LaunchedEffect(Unit) {
        viewModel.ensureScoreFinalized()
        viewModel.ensureAutoLogScheduled()
        viewModel.runAutoLogNow()
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
    // Tabs: 0 Today, 1 Path, 2 History, 3 Manage
    LaunchedEffect(tourStep, showHelpTour) {
        if (showHelpTour) {
            when (tourStep) {
                0, 1, 2 -> if (selectedTab != 0) selectedTab = 0
                3 -> selectedTab = 0
                4 -> selectedTab = 1 // Path
                5 -> selectedTab = 2 // History
                6 -> selectedTab = 3 // Manage
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
                val payload = repository.encodeBackupJson(appData, pretty = true)
                context.contentResolver.openOutputStream(u)?.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Backup exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Full JSON restore (includes Momentum score, notification prefs, entries, goals, …)
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { u ->
            try {
                val text = context.contentResolver.openInputStream(u)?.use { ins ->
                    ins.bufferedReader(Charsets.UTF_8).readText()
                }
                if (text.isNullOrBlank()) {
                    Toast.makeText(context, "Backup file is empty", Toast.LENGTH_LONG).show()
                } else {
                    pendingImportJson = text
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not read backup: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (pendingImportJson != null) {
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "This replaces all current Steady data (habits, logs, Momentum score, reminder settings, Path goals) with the backup file."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val jsonText = pendingImportJson
                    pendingImportJson = null
                    if (jsonText != null) {
                        viewModel.importBackupJson(jsonText) { err ->
                            if (err == null) {
                                Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Import failed: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) { Text("Replace data") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) { Text("Cancel") }
            }
        )
    }

    // Full dynamic theming: background catalog + accent (foreground highlight)
    val bgOpt = com.steady.habittracker.data.backgroundModeOption(appData.backgroundMode)
    val isLight = bgOpt.isLight

    val bgColor = Color(bgOpt.backgroundArgb)
    val surfaceColor = Color(bgOpt.surfaceArgb)
    val onSurfaceColor = if (isLight) Color(0xFF0F172A) else Color(0xFFE2E8F0)
    val onSurfaceVariant = if (isLight) Color(0xFF475569) else Color(0xFF94A3B8)
    val surfaceVariantColor = Color(bgOpt.surfaceVariantArgb)

    // Map secondary/primary containers to accent so FilterChips, checkboxes, etc. follow scheme (#23)
    val onAccent = if (isLight) Color.White else Color.Black
    val accentMuted = accent.copy(alpha = 0.22f)
    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentMuted,
            onPrimaryContainer = accent,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = accentMuted,
            onSecondaryContainer = accent,
            tertiary = accent,
            onTertiary = onAccent,
            tertiaryContainer = accentMuted,
            onTertiaryContainer = accent,
            background = bgColor,
            onBackground = onSurfaceColor,
            surface = surfaceColor,
            onSurface = onSurfaceColor,
            surfaceVariant = surfaceVariantColor,
            onSurfaceVariant = onSurfaceVariant,
            outline = onSurfaceVariant
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentMuted,
            onPrimaryContainer = accent,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = accentMuted,
            onSecondaryContainer = accent,
            tertiary = accent,
            onTertiary = onAccent,
            tertiaryContainer = accentMuted,
            onTertiaryContainer = accent,
            background = bgColor,
            onBackground = onSurfaceColor,
            surface = surfaceColor,
            onSurface = onSurfaceColor,
            surfaceVariant = surfaceVariantColor,
            onSurfaceVariant = onSurfaceVariant,
            outline = onSurfaceVariant
        )
    }

    // Status / nav bar icon contrast under edge-to-edge (issue #27)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val lightIcons = bgColor.luminance() > 0.5f
        controller.isAppearanceLightStatusBars = lightIcons
        controller.isAppearanceLightNavigationBars = lightIcons
    }

    if (!appData.onboarded) {
        // Onboarding must use themed colors (was outside theme → dark text on dark bg)
        MaterialTheme(colorScheme = colorScheme) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                color = bgColor
            ) {
                OnboardingScreen(
                    groups = appData.groups,
                    tags = appData.tags,
                    initialSleep = appData.sleep,
                    onComplete = { drafts, colorScheme, schedule, backgroundMode ->
                        viewModel.completeOnboardingWithHabits(
                            drafts,
                            colorScheme,
                            schedule,
                            backgroundMode
                        )
                    }
                )
            }
        }
    } else {
    MaterialTheme(colorScheme = colorScheme) {
    // Full-screen workout session, sleep-audio detail, or Dreamline wizard
    val workoutRt = activeWorkoutRoutine
    val sleepNight = openSleepNight
    when {
        sleepNight != null -> {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                color = bgColor
            ) {
                // Refresh night from latest appData if still present
                val live = appData.sleepNights.find { it.id == sleepNight.id } ?: sleepNight
                SleepNightDetailScreen(
                    night = live,
                    onBack = { openSleepNight = null }
                )
            }
        }
        workoutRt != null -> {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                color = bgColor
            ) {
                WorkoutSessionScreen(
                    routine = workoutRt,
                    onFinish = { session ->
                        viewModel.finishWorkoutSession(session)
                        activeWorkoutRoutine = null
                        Toast.makeText(context, "Workout saved — great work!", Toast.LENGTH_SHORT).show()
                    },
                    onDiscard = { activeWorkoutRoutine = null }
                )
            }
        }
        showDreamlineWizard -> {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                color = bgColor
            ) {
                DreamlineWizard(
                    onComplete = { goals ->
                        viewModel.applyDreamlineGoals(goals, replaceExistingDreamline = true)
                        showDreamlineWizard = false
                        selectedTab = 1 // Path tab
                        Toast.makeText(
                            context,
                            "Created ${goals.size} goal(s) — see Path tab",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onCancel = { showDreamlineWizard = false }
                )
            }
        }
        else -> {
    Scaffold(
        containerColor = bgColor,
        // Edge-to-edge (API 35+ / enableEdgeToEdge): pad content below status & nav bars
        contentWindowInsets = WindowInsets.safeDrawing
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
                        Text(
                            "⚡ $todayPoints pts · Lv $momentumLevel",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Info (i) button removed (#31) — tour/help lives in Settings
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
                        Spacer(Modifier.width(8.dp))
                        Text("⚡$todayPoints", color = accent, fontSize = 11.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("Lv$momentumLevel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "⚡ $todayPoints Steady points · Level $momentumLevel",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "$pointsToNextLevel pts to next level · ${com.steady.habittracker.data.HabitDomain.levelTitle(momentumLevel)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
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

            // Tabs: Today | Path | History | Manage (#26 Path)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                    .padding(4.dp)
            ) {
                TabButton("Today", selectedTab == 0, modifier = Modifier.weight(1f)) { selectedTab = 0 }
                TabButton("Path", selectedTab == 1, modifier = Modifier.weight(1f)) { selectedTab = 1 }
                TabButton("History", selectedTab == 2, modifier = Modifier.weight(1f)) { selectedTab = 2 }
                TabButton("Manage", selectedTab == 3, modifier = Modifier.weight(1f)) { selectedTab = 3 }
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
                        onLogMetric = viewModel::logEntry,
                        onStartRoutine = { rt -> activeWorkoutRoutine = rt },
                        openCaptureRequest = deepCapture,
                        onOpenCaptureConsumed = {
                            activity?.deepLinkOpenCapture?.value = false
                            deepCaptureState.value = false
                        },
                        openLogRequest = deepMetricLog,
                        onOpenLogConsumed = {
                            activity?.deepLinkOpenMetricLog?.value = false
                            deepMetricLogState.value = false
                        },
                        onAcceptAutoSuggestion = viewModel::acceptAutoSuggestion,
                        onDismissAutoSuggestion = viewModel::dismissAutoSuggestion,
                        onRunAutoLog = { viewModel.runAutoLogNow() }
                    )
                    1 -> PathScreen(
                        appData = appData,
                        onOpenWizard = { showDreamlineWizard = true },
                        onUpdateGoal = viewModel::updateGoal,
                        onSaveAlignment = viewModel::savePathAlignment,
                        onArchiveGoal = viewModel::archiveGoal
                    )
                    2 -> HistoryScreen(
                        appData = appData,
                        onOpenSleepNight = { openSleepNight = it }
                    )
                    3 -> ManageScreen(
                        appData = appData,
                        onAddGroup = { n, h, p, icon -> viewModel.addGroup(n, h, p, icon) },
                        onAddHabit = { name, gid, type, isSupp, tags, preset, weekdays, interval, dates, moreGroups, icon ->
                            viewModel.addHabit(
                                name = name,
                                groupId = gid,
                                type = type,
                                isSupplement = isSupp,
                                tags = tags,
                                showPreset = preset,
                                weekdays = weekdays,
                                intervalDays = interval,
                                specificDates = dates,
                                additionalGroupIds = moreGroups,
                                icon = icon
                            )
                        },
                        onAddExtensionBlock = { type, gid -> viewModel.addExtensionBlock(type, gid) },
                        onUpdateLocalWebPrefs = viewModel::updateLocalWebPrefs,
                        onUpdateCapturePrefs = viewModel::updateCapturePrefs,
                        onDeleteHabit = viewModel::deleteHabit,
                        onSetReminder = viewModel::setReminder,
                        onToggleReminder = viewModel::toggleReminder,
                        onSetRemindersMasterEnabled = viewModel::setRemindersMasterEnabled,
                        onUpdateNotificationPrefs = viewModel::updateNotificationPrefs,
                        onSetAutoLogMasterEnabled = viewModel::setAutoLogMasterEnabled,
                        onRunAutoLogNow = { viewModel.runAutoLogNow() },
                        onUpdateSleepAudioPrefs = viewModel::updateSleepAudioPrefs,
                        onStartSleepAudio = {
                            val reason = viewModel.startSleepAudioRecording()
                            if (reason != null) {
                                Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Sleep audio starting…", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onStopSleepAudio = viewModel::stopSleepAudioRecording,
                        onAlignRemindersToSchedule = viewModel::alignRemindersToSchedule,
                        onArchiveGroup = { viewModel.deleteGroup(it) },
                        onExportCsv = { exportLauncher.launch("steady_backup_${java.time.LocalDate.now()}.json") },
                        onImportCsv = { importLauncher.launch(arrayOf("application/json", "application/*", "*/*")) },
                        onUpdateHabit = viewModel::updateHabit,
                        onUpdateGroup = viewModel::updateGroup,
                        onUnarchiveGroup = { viewModel.unarchiveGroup(it) },
                        onUnarchiveHabit = { viewModel.unarchiveHabit(it) },
                        onReorderHabit = { id, dir, gid -> viewModel.reorderHabit(id, dir, gid) },
                        onAddHabitToGroup = viewModel::addHabitToGroup,
                        onRemoveHabitFromGroup = viewModel::removeHabitFromGroup,
                        onMoveHabitToGroup = viewModel::moveHabitToGroup,
                        onApplySchedulePreset = viewModel::applySchedulePreset,
                        onSetActiveSchedule = viewModel::setActiveSchedule,
                        onUpdateScheduleBlocks = viewModel::updateScheduleBlocks,
                        onAddTag = viewModel::addTag,
                        onApplySleepSchedule = { sleep -> viewModel.applySleepAnchoredSchedule(sleep) },
                        onSaveRoutine = viewModel::saveRoutine,
                        onArchiveRoutine = viewModel::archiveRoutine,
                        onLoadBlueprintRoutines = viewModel::loadBlueprintRoutines,
                        onStartRoutine = { rt -> activeWorkoutRoutine = rt },
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
                                4 -> selectedTab = 1   // Path
                                5 -> selectedTab = 2   // History
                                6 -> selectedTab = 3   // Manage
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
                            6 -> selectedTab = 3
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
        // Preview colors from shared catalogs
        val currentAccent = Color(com.steady.habittracker.data.accentColorArgb(appData.colorScheme))
        val previewBgOpt = com.steady.habittracker.data.backgroundModeOption(appData.backgroundMode)
        val previewBg = Color(previewBgOpt.backgroundArgb)
        val previewSurface = Color(previewBgOpt.surfaceArgb)
        val previewOn = if (previewBgOpt.isLight) Color(0xFF0F172A) else Color(0xFFE2E8F0)

        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Settings") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Reminders live in Manage → Reminders (times follow your Daily Planner).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Theme packs",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    Text(
                        "Popular Linux rice palettes — one tap sets background + accent.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    val packs = com.steady.habittracker.data.themePacks()
                    packs.chunked(2).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { pack ->
                                val bg = com.steady.habittracker.data.backgroundModeOption(pack.backgroundId)
                                val accent = Color(com.steady.habittracker.data.accentColorArgb(pack.accentId))
                                val selected = appData.backgroundMode == pack.backgroundId &&
                                    (appData.colorScheme == pack.accentId ||
                                        (!com.steady.habittracker.data.isCustomAccentScheme(appData.colorScheme) &&
                                            com.steady.habittracker.data.accentColorArgb(appData.colorScheme) ==
                                            com.steady.habittracker.data.accentColorArgb(pack.accentId)))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(bg.surfaceArgb))
                                        .border(
                                            if (selected) BorderStroke(2.dp, accent)
                                            else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            viewModel.setBackgroundMode(pack.backgroundId)
                                            viewModel.setColorScheme(pack.accentId)
                                        }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .height(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(bg.backgroundArgb))
                                        )
                                        Box(
                                            Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        pack.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (bg.isLight) Color(0xFF0F172A) else Color(0xFFE2E8F0)
                                    )
                                    Text(
                                        pack.blurb,
                                        fontSize = 10.sp,
                                        color = if (bg.isLight) Color(0xFF64748B) else Color(0xFF94A3B8),
                                        maxLines = 1
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Accent only",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    val schemes = com.steady.habittracker.data.accentSchemes()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        schemes.take(5).forEach { opt ->
                            val isSel = appData.colorScheme == opt.id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(opt.colorArgb))
                                    .border(
                                        if (isSel) BorderStroke(2.dp, Color.White)
                                        else BorderStroke(0.dp, Color.Transparent),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.setColorScheme(opt.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSel) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        schemes.drop(5).forEach { opt ->
                            val isSel = appData.colorScheme == opt.id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(opt.colorArgb))
                                    .border(
                                        if (isSel) BorderStroke(2.dp, Color.White)
                                        else BorderStroke(0.dp, Color.Transparent),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.setColorScheme(opt.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSel) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        repeat((5 - schemes.drop(5).size).coerceAtLeast(0)) {
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    AccentHuePicker(
                        currentSchemeId = appData.colorScheme,
                        currentArgb = com.steady.habittracker.data.accentColorArgb(appData.colorScheme),
                        onCustomColor = { viewModel.setColorScheme(it) }
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("Live Preview", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
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
                    Text(
                        "Instant updates. OLED black saves battery on AMOLED. Cards, text, progress & widget follow background + accent.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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
                    Text("Guided tour walks the screens. Welcome guide is the first-run summary.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        } // end when else (main shell: Scaffold + dialogs)
    } // end when (workout / wizard / shell)
    } // end MaterialTheme
    } // end else (onboarded)
}

// Accent resolver (used for dynamic theme + progress). Matches resolveThemeColors / accentSchemes().
private fun getAccentColor(scheme: String): Color =
    Color(com.steady.habittracker.data.accentColorArgb(scheme))

// UI composables have been extracted to ui/ package
// (keeps MainActivity small and focused)
