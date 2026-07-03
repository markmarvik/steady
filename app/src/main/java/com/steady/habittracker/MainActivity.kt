package com.steady.habittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var repository: HabitRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = HabitRepository(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF22C55E),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E2937)
                )
            ) {
                SteadyApp(repository)
            }
        }
    }
}

@Composable
fun SteadyApp(repository: HabitRepository) {
    val scope = rememberCoroutineScope()
    var appData by remember { mutableStateOf(AppData()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Today, 1 = History
    var showAddDialog by remember { mutableStateOf(false) }

    // Load data
    LaunchedEffect(Unit) {
        repository.appDataFlow.collect { data ->
            appData = data
        }
    }

    val today = remember { getTodayString() }
    val todayCompletions = appData.completions[today] ?: emptyList()
    val completionRate = if (appData.habits.isNotEmpty()) {
        todayCompletions.size.toFloat() / appData.habits.size
    } else 0f

    Scaffold(
        containerColor = Color(0xFF0F172A),
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFF22C55E)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add habit")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Steady",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()),
                    color = Color(0xFF64748B),
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            // Progress Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2937)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "TODAY'S PROGRESS",
                        fontSize = 12.sp,
                        color = Color(0xFF22C55E),
                        letterSpacing = 1.sp
                    )
                    
                    Spacer(Modifier.height(12.dp))

                    // Progress Ring
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        Canvas(modifier = Modifier.size(140.dp)) {
                            val strokeWidth = 14.dp.toPx()
                            drawArc(
                                color = Color(0xFF334155),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                size = Size(size.width, size.height)
                            )
                            drawArc(
                                color = Color(0xFF22C55E),
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
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "${todayCompletions.size}/${appData.habits.size}",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E2937), RoundedCornerShape(50))
                    .padding(4.dp)
            ) {
                TabButton("Today", selectedTab == 0) { selectedTab = 0 }
                TabButton("History", selectedTab == 1) { selectedTab = 1 }
            }

            Spacer(Modifier.height(16.dp))

            when (selectedTab) {
                0 -> TodayScreen(
                    appData = appData,
                    todayCompletions = todayCompletions,
                    onToggleHabit = { habitId ->
                        scope.launch {
                            val newCompletions = appData.completions.toMutableMap()
                            val list = newCompletions.getOrPut(today) { mutableListOf() } as MutableList
                            
                            if (list.contains(habitId)) list.remove(habitId) else list.add(habitId)
                            
                            val newData = appData.copy(completions = newCompletions)
                            repository.saveData(newData)
                        }
                    },
                    onDeleteHabit = { habitId ->
                        scope.launch {
                            val newHabits = appData.habits.filter { it.id != habitId }
                            val newCompletions = appData.completions.mapValues { (_, list) ->
                                list.filter { it != habitId }
                            }
                            repository.saveData(appData.copy(habits = newHabits, completions = newCompletions))
                        }
                    }
                )
                1 -> HistoryScreen(appData)
            }
        }
    }

    if (showAddDialog) {
        AddHabitDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, why ->
                scope.launch {
                    val newHabit = Habit(
                        id = "custom_${System.currentTimeMillis()}",
                        name = name,
                        why = why
                    )
                    val newData = appData.copy(habits = appData.habits + newHabit)
                    repository.saveData(newData)
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color(0xFF22C55E) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.Black else Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun TodayScreen(
    appData: AppData,
    todayCompletions: List<String>,
    onToggleHabit: (String) -> Unit,
    onDeleteHabit: (String) -> Unit
) {
    if (appData.habits.isEmpty()) {
        Text("No habits yet. Add some!", color = Color.Gray)
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(appData.habits) { habit ->
            val isCompleted = todayCompletions.contains(habit.id)
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleHabit(habit.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isCompleted) Color(0xFF166534) else Color(0xFF1E2937)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (isCompleted) Color(0xFF22C55E) else Color(0xFF475569),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column(Modifier.weight(1f)) {
                        Text(
                            habit.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (habit.why.isNotBlank()) {
                            Text(
                                habit.why,
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    
                    IconButton(onClick = { onDeleteHabit(habit.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(appData: AppData) {
    val calendar = Calendar.getInstance()
    val history = mutableListOf<Pair<String, Float>>()
    
    repeat(14) { i ->
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val completed = appData.completions[date]?.size ?: 0
        val total = appData.habits.size.coerceAtLeast(1)
        val rate = completed.toFloat() / total
        history.add(date to rate)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }
    history.reverse()

    Text(
        "Last 14 Days",
        color = Color(0xFF22C55E),
        fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    LazyColumn {
        items(history) { (date, rate) ->
            val color = when {
                rate >= 0.85 -> Color(0xFF22C55E)
                rate >= 0.5 -> Color(0xFF4ADE80)
                rate > 0 -> Color(0xFF166534)
                else -> Color(0xFF334155)
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    date,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    modifier = Modifier.width(100.dp)
                )
                
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
                
                Text(
                    "${(rate * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.width(50.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

@Composable
fun AddHabitDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, why: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Habit") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = why,
                    onValueChange = { why = it },
                    label = { Text("Why it matters (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(name, why)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun getTodayString(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}