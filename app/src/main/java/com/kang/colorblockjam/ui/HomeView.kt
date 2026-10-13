package com.kang.colorblockjam.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.kang.colorblockjam.Progress
import com.kang.colorblockjam.model.Levels
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Home screen: title, big "play next level" button and a scrollable grid of all levels. */
class HomeView(context: Context, private val progress: Progress, private val onPlay: (Int) -> Unit) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val playRect = RectF()
    private var gridTop = 0f
    private var scroll = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var moved = false
    private val perRow = 6

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        playRect.set(w * 0.2f, h * 0.2f, w * 0.8f, h * 0.29f)
        gridTop = h * 0.34f
    }

    /** The level the big button continues: the one last played, never beyond what is unlocked. */
    private fun resumeLevel() = progress.lastPlayed.coerceIn(1, progress.highestUnlocked.coerceAtMost(Levels.MAX_LEVEL))

    private fun cellSize() = width / perRow.toFloat()
    private fun maxScroll() = max(0f, ((Levels.MAX_LEVEL + perRow - 1) / perRow) * cellSize() - (height - gridTop))

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.y; downScroll = scroll; moved = false }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.y - downY) > 12f) moved = true
                if (e.y > gridTop || moved) {
                    scroll = (downScroll + (downY - e.y)).coerceIn(0f, maxScroll())
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (moved) return true
                if (playRect.contains(e.x, e.y)) { onPlay(resumeLevel()); return true }
                if (e.y >= gridTop) {
                    val cs = cellSize()
                    val col = (e.x / cs).toInt()
                    val row = ((e.y - gridTop + scroll) / cs).toInt()
                    val n = row * perRow + col + 1
                    if (n in 1..Levels.MAX_LEVEL) {
                        if (n <= progress.highestUnlocked) onPlay(n)
                    }
                }
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFFD6E4F7.toInt())
        textPaint.color = 0xFF1565C0.toInt()
        textPaint.textSize = height * 0.05f
        c.drawText("COLOR BLOCK JAM", width / 2f, height * 0.1f, textPaint)
        textPaint.textSize = height * 0.02f
        textPaint.color = 0xFF37474F.toInt()
        c.drawText("Coins ${progress.coins}   ❄ ${progress.freezeCount}   ⚒ ${progress.hammerCount}   Lives ∞", width / 2f, height * 0.15f, textPaint)
        paint.color = 0xFF43A047.toInt()
        c.drawRoundRect(playRect, playRect.height() * 0.4f, playRect.height() * 0.4f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = playRect.height() * 0.4f
        c.drawText("PLAY LEVEL ${resumeLevel()}", playRect.centerX(), playRect.centerY() + playRect.height() * 0.14f, textPaint)

        c.save()
        c.clipRect(0f, gridTop, width.toFloat(), height.toFloat())
        val cs = cellSize()
        val firstRow = (scroll / cs).toInt()
        val lastRow = min((Levels.MAX_LEVEL - 1) / perRow, ((scroll + height - gridTop) / cs).toInt() + 1)
        for (row in firstRow..lastRow) {
            for (col in 0 until perRow) {
                val n = row * perRow + col + 1
                if (n > Levels.MAX_LEVEL) break
                val l = col * cs + cs * 0.08f
                val t = gridTop + row * cs - scroll + cs * 0.08f
                val r = RectF(l, t, l + cs * 0.84f, t + cs * 0.84f)
                val unlocked = n <= progress.highestUnlocked
                val done = n < progress.highestUnlocked
                paint.color = when { done -> 0xFF42A5F5.toInt(); unlocked -> 0xFFFFB300.toInt(); else -> 0xFFB0BEC5.toInt() }
                c.drawRoundRect(r, cs * 0.15f, cs * 0.15f, paint)
                textPaint.color = Color.WHITE
                textPaint.textSize = cs * 0.34f
                c.drawText(if (unlocked) "$n" else "🔒", r.centerX(), r.centerY() + cs * 0.12f, textPaint)
            }
        }
        c.restore()
    }
}
