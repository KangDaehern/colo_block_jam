package com.kang.colorblockjam

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Thin wrapper around the system vibrator with the few patterns the game uses. Tune the constants below. */
class Haptics(context: Context) {
    companion object {
        const val EXIT_MS = 70L            // block leaves through its gate (first build was 18 ms @ 160: too weak and short)
        const val EXIT_AMPLITUDE = 255     // 1..255 (maximum)
        const val WIN_MS = 90L
        const val WIN_AMPLITUDE = 255
        val FAIL_TIMINGS = longArrayOf(0, 150, 100, 150)   // off, on, off, on (ms)
        val FAIL_AMPLITUDES = intArrayOf(0, 255, 0, 255)
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** Firm buzz when a block slides out through its gate. */
    fun blockExit() = pulse(EXIT_MS, EXIT_AMPLITUDE)

    /** Two firm buzzes when the timer runs out (or a bomb goes off). */
    fun levelFailed() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(FAIL_TIMINGS, FAIL_AMPLITUDES, -1))
        } else {
            @Suppress("DEPRECATION") v.vibrate(FAIL_TIMINGS, -1)
        }
    }

    /** Confirmation when the level is cleared. */
    fun levelWon() = pulse(WIN_MS, WIN_AMPLITUDE)

    private fun pulse(ms: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(ms, amp))
        } else {
            @Suppress("DEPRECATION") v.vibrate(ms)
        }
    }
}
