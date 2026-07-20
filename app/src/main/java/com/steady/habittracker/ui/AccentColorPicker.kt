package com.steady.habittracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.customAccentSchemeId
import com.steady.habittracker.data.isCustomAccentScheme
import android.graphics.Color as AndroidColor

/**
 * HSV-style accent picker (#30): hue strip + saturation/value sliders.
 * Emits catalog-compatible scheme ids via [customAccentSchemeId].
 */
@Composable
fun AccentHuePicker(
    currentSchemeId: String,
    currentArgb: Int,
    onCustomColor: (schemeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hsv = remember(currentArgb) {
        val arr = FloatArray(3)
        AndroidColor.colorToHSV(currentArgb, arr)
        arr
    }
    var hue by remember(currentSchemeId) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(currentSchemeId) { mutableFloatStateOf(hsv[1].coerceIn(0.15f, 1f)) }
    var value by remember(currentSchemeId) { mutableFloatStateOf(hsv[2].coerceIn(0.35f, 1f)) }

    fun emit() {
        val argb = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))
        onCustomColor(customAccentSchemeId(argb))
    }

    val preview = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Custom accent (hue wheel)",
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            if (isCustomAccentScheme(currentSchemeId)) "Using custom color"
            else "Drag hue or sliders to set a custom accent",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterHorizontally)
                .clip(CircleShape)
                .background(preview)
                .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
        )
        Spacer(Modifier.height(10.dp))
        // Hue strip
        val hueColors = remember {
            (0..12).map { i ->
                Color(AndroidColor.HSVToColor(floatArrayOf(i * 30f, 1f, 1f)))
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        hue = (offset.x / size.width * 360f).coerceIn(0f, 359.9f)
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        hue = (change.position.x / size.width * 360f).coerceIn(0f, 359.9f)
                        emit()
                    }
                }
        ) {
            drawRoundRect(
                brush = Brush.horizontalGradient(hueColors),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            val x = (hue / 360f) * size.width
            drawCircle(
                color = Color.White,
                radius = 10.dp.toPx(),
                center = Offset(x, size.height / 2f),
                style = Stroke(width = 3.dp.toPx())
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Saturation", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = sat,
            onValueChange = { sat = it; emit() },
            valueRange = 0.15f..1f
        )
        Text("Brightness", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value,
            onValueChange = { value = it; emit() },
            valueRange = 0.35f..1f
        )
        Text(
            "#%06X".format(preview.toArgb() and 0xFFFFFF),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
