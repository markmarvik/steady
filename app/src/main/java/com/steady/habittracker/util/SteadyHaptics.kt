package com.steady.habittracker.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * High-quality, short haptic patterns for Steady (habits, zoom, rewards).
 * Prefers modern [VibrationEffect] compositions; falls back to View haptics.
 */
object SteadyHaptics {

    enum class Kind {
        /** Soft tick — density step, light UI. */
        TICK,
        /** Confirm a habit complete / positive log. */
        SUCCESS,
        /** Slightly richer multi-pulse for streaks / full clear. */
        REWARD,
        /** Soft warning — skip, over-limit. */
        WARN,
        /** Grid zoom step (larger / denser). */
        ZOOM
    }

    fun perform(context: Context, kind: Kind) {
        val vibrator = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (kind) {
                Kind.TICK, Kind.ZOOM ->
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                Kind.SUCCESS ->
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                Kind.REWARD ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    } else {
                        VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                Kind.WARN ->
                    VibrationEffect.createOneShot(18, 80)
            }
            try {
                vibrator.vibrate(effect)
                return
            } catch (_: Exception) {
                // fall through
            }
        }
        // Compose richer patterns when predefined APIs are weak
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val (timings, amps) = when (kind) {
                Kind.TICK, Kind.ZOOM -> longArrayOf(0, 12) to intArrayOf(0, 90)
                Kind.SUCCESS -> longArrayOf(0, 18, 30, 22) to intArrayOf(0, 140, 0, 180)
                Kind.REWARD -> longArrayOf(0, 16, 40, 20, 50, 28) to intArrayOf(0, 120, 0, 160, 0, 220)
                Kind.WARN -> longArrayOf(0, 22) to intArrayOf(0, 70)
            }
            try {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
                return
            } catch (_: Exception) { }
        }
        @Suppress("DEPRECATION")
        vibrator.vibrate(when (kind) {
            Kind.TICK, Kind.ZOOM -> 12L
            Kind.SUCCESS -> 24L
            Kind.REWARD -> 40L
            Kind.WARN -> 18L
        })
    }

    fun perform(view: View, kind: Kind) {
        val constant = when (kind) {
            Kind.TICK, Kind.ZOOM -> HapticFeedbackConstants.CLOCK_TICK
            Kind.SUCCESS -> if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
            Kind.REWARD -> if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            Kind.WARN -> if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        }
        val handled = view.performHapticFeedback(constant)
        if (!handled) {
            perform(view.context, kind)
        }
    }

    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }?.takeIf { it.hasVibrator() }
    }
}

/** Remember a simple haptic lambda bound to the current Compose view. */
@Composable
fun rememberSteadyHaptics(): (SteadyHaptics.Kind) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { kind -> SteadyHaptics.perform(view, kind) }
    }
}
