package com.steady.habittracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 * Simple one-time onboarding for new users.
 * Explains core concepts then marks onboarded.
 */
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    // Explicit high-contrast colors so welcome is readable even if theme defaults fail
    val title = Color(0xFFF1F5F9)
    val body = Color(0xFFE2E8F0)
    val muted = Color(0xFF94A3B8)
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            "Welcome to Steady",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = title,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Evidence-based habits. Simple daily tracking.",
            fontSize = 14.sp,
            color = muted,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        val features = listOf(
            "😴 Sleep first — set bed & wake; Morning and Bedtime anchor to them.",
            "📅 Timeline groups = when (Morning, Focus, Bedtime, Sleep).",
            "🏷 Tags = what it is (Supplements, Movement…) for History — move freely.",
            "✅ Tap to log. Checkboxes, counters, minutes, scales, notes.",
            "📈 Streak + tag completion trends.",
            "🧩 Widget shows today’s full list; NOW highlights the current block."
        )
        features.forEach { f ->
            Text(
                f,
                color = body,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "We've started you with a proven set of high-ROI habits.\nCustomize freely in the Manage tab.",
            color = muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onComplete,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start tracking", color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Tip: Add the Steady widget to your home screen for 1-tap logging.",
            color = muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "Later: use the ⓘ icon (next to gear) to re-run the interactive tour anytime.",
            color = muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}
