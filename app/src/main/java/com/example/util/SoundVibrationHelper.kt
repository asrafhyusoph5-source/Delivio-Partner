package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object SoundVibrationHelper {

    fun playNewOrderAlert(context: Context, soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        if (soundEnabled) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 750)
            } catch (e: Exception) {
                // Fallback silently
            }
        }

        if (vibrationEnabled) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                vibrator?.let { v ->
                    if (v.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val pattern = longArrayOf(0, 300, 150, 300, 150, 450)
                            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                            v.vibrate(effect)
                        } else {
                            @Suppress("DEPRECATION")
                            v.vibrate(longArrayOf(0, 300, 150, 300, 150, 450), -1)
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback silently
            }
        }
    }

    fun playClickHaptic(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(30)
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
