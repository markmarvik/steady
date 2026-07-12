package com.steady.habittracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Interactive guided tour / "run onboarding" experience.
 * Steps the user through the app UI by explaining sections and switching tabs.
 */
data class TourStep(
    val title: String,
    val body: String,
    val hint: String? = null
)

val TOUR_STEPS = listOf(
    TourStep(
        "Welcome to Steady",
        "Steady is a simple, evidence-based habit tracker. This quick tour will show you the main areas so you can get the most out of it.\n\nYou can run this tour any time from the ? button."
    ),
    TourStep(
        "Header & Progress",
        "Top shows today's date and your current streak (🔥).\n\nThe big progress card shows today's completion percentage and a compact 7-day trend (dots).\n\nTap the progress card to open a detailed view with a large ring and weekly % per group."
    ),
    TourStep(
        "Tabs: Today / Path / History / Manage",
        "Four tabs:\n• Today — daily habits & workouts\n• Path — long-term vision, Dreamline goals, alignment\n• History — heatmaps & trends\n• Manage — Habits, Groups, Planner"
    ),
    TourStep(
        "Today Tab — Your daily actions",
        "Habits are grouped by time of day (Morning, Focus/Work, Evening, Mindset...).\n\n• Tap a checkbox habit to toggle complete\n• Tap Log (or the row) for counters, durations, scales, notes\n• Long-press any row to skip\n• Use +Capture for quick ideas/inbox\n• Use +Log for ad-hoc metrics (weight, etc.)\n\nOnly pending (not-yet-done) items are shown — keeps focus."
    ),
    TourStep(
        "Path Tab — Vision & orientation",
        "Run the Dreamline wizard (Having / Being / Doing for 6 & 12 months). Path shows your goals, progress, confidence, “Am I on path?” check-ins, and identity mindset prompts."
    ),
    TourStep(
        "History Tab — Trends & reflection",
        "Anki-style heatmap, charts, and workout sessions.\n\nHigher bars = better days. Use +Log on Today for past metrics."
    ),
    TourStep(
        "Manage Tab — Full control",
        "Manage has three areas:\n• Habits — create, edit, archive habits; tags & exercise routines\n• Groups — timeline groups; attach existing habits; order & move\n• Planner — sleep + 24h schedule, reminders, backup"
    ),
    TourStep(
        "Settings (gear icon)",
        "Tap the gear any time to switch:\n• Background: Dark / AMOLED (pure black) / Light\n• Accent color: Green, Blue, Orange, Purple, Slate, Teal, Red\n\nReminders are under Manage. Theme changes apply instantly including the widget."
    ),
    TourStep(
        "Widget & Reminders",
        "Add the Steady widget to your home screen — it shows exactly the right group for right now + what you missed + what's next.\n\nReminders are in Manage → Planner. Times align to your Daily Planner (wake, wind-down, blocks). Grant notification + exact alarm permissions for best results."
    ),
    TourStep(
        "You're ready!",
        "A starter set of high-ROI habits is already loaded. Customize freely in Manage.\n\nLog daily in Today. Check History for motivation. Export your data regularly.\n\nLong-press rows to skip when needed. Use notes on mindset habits — they become powerful over time.\n\nEnjoy building consistency with Steady!"
    )
)

@Composable
fun TourCoach(
    stepIndex: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val step = TOUR_STEPS.getOrNull(stepIndex) ?: return
    val isFirst = stepIndex == 0
    val isLast = stepIndex == TOUR_STEPS.lastIndex

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header — X always closes immediately (no need to finish all steps)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Tour",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${stepIndex + 1} / ${TOUR_STEPS.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEnd) {
                    Text("Skip", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                IconButton(onClick = onEnd, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close tour",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                step.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                step.body,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            if (!step.hint.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    step.hint,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }

            Spacer(Modifier.height(16.dp))

            // Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    enabled = !isFirst,
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Back")
                }

                TextButton(onClick = onEnd) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }

                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(if (isLast) "Finish" else "Next")
                }
            }
        }
    }
}

/**
 * Small header indicator shown while tour is active.
 */
@Composable
fun TourHeaderIndicator(
    stepIndex: Int,
    onEnd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Guided Tour  •  ${stepIndex + 1}/${TOUR_STEPS.size}",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onEnd, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
            Text("End", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
    }
}
