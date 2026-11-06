package com.kazaeza.muzykalka.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Rich haptics for game feedback. Uses composition primitives on Pixels (crisp click, thud, rise),
 * falls back to predefined effects elsewhere. All calls are no-ops when disabled.
 */
class Haptics(context: Context, var enabled: Boolean = true) {
    private val vibrator: Vibrator? = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    private fun play(effect: VibrationEffect) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(effect) }
    }

    fun tick() = play(
        if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) == true) {
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f).compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        },
    )

    fun success() = play(
        if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_QUICK_RISE) == true) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.4f, 30)
                .compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        },
    )

    fun error() = play(
        if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) == true) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.6f, 60)
                .compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        },
    )

    fun celebrate() = play(
        if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_SPIN, VibrationEffect.Composition.PRIMITIVE_CLICK) == true) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.8f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 80)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 80)
                .compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        },
    )
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val context = LocalContext.current
    val h = remember { Haptics(context.applicationContext, enabled) }
    h.enabled = enabled
    return h
}
