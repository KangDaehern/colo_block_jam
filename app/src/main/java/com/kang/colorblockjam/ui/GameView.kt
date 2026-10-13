package com.kang.colorblockjam.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.kang.colorblockjam.Progress
import com.kang.colorblockjam.model.Block
import com.kang.colorblockjam.model.BlockColor
import com.kang.colorblockjam.model.Board
import com.kang.colorblockjam.model.Gate
import com.kang.colorblockjam.model.Level
import com.kang.colorblockjam.model.Side
import kotlin.math.max
import kotlin.math.min

/**
 * The whole in-game screen: HUD, board, blocks, gates, boosters and the result overlays.
 * Everything is drawn directly on the Canvas; input is handled in [onTouchEvent].
 */
class GameView(
    context: Context,
    private val progress: Progress,
    private val level: Level,
    private val onHome: () -> Unit,
    private val onNext: () -> Unit,
    private val onRetry: () -> Unit,
) : View(context) {

    private var board = Board(level)
    private val haptics = com.kang.colorblockjam.Haptics(context)
    private enum class Overlay { NONE, PAUSE, WIN, FAIL }
    private var overlay = Overlay.NONE
    private var winRewarded = false
    private var hammerMode = false
    private var toast: String = ""
    private var toastUntil = 0L
    private var bannerUntil = 0L

    companion object {
        const val WIN_POPUP_EVERY = 10
        const val AUTO_NEXT_DELAY_MS = 1100L
    }
    private var shake = 0f

    // Layout (recomputed in onSizeChanged)
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private val frame = 0.45f // frame thickness in cells
    private val hudRect = RectF()
    private val restartRect = RectF()
    private val pauseRect = RectF()
    private val freezeRect = RectF()
    private val hammerRect = RectF()
    private val button1 = RectF()
    private val button2 = RectF()
    private val button3 = RectF()

    // Drag state
    private var dragging: Block? = null
    private var lastX = 0f
    private var lastY = 0f

    // Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var lastFrame = 0L
    private var running = true
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            if (lastFrame != 0L) {
                val dt = min(0.1f, (now - lastFrame) / 1e9f)
                if (overlay == Overlay.NONE) board.tick(dt)
                if (shake > 0f) shake = max(0f, shake - dt * 3f)
            }
            lastFrame = now
            invalidate()
            postOnAnimation(this)
        }
    }

    init {
        board.listener = listener()
        postOnAnimation(ticker)
    }

    private fun listener() = object : Board.Listener {
        override fun onBlockExited(block: Block, gate: Gate) { haptics.blockExit() }
        override fun onWon() {
            haptics.levelWon()
            if (!winRewarded) {
                winRewarded = true
                progress.onLevelWon(level.number)
                // A cleared level is finished: the next launch should resume at the following one.
                progress.lastPlayed = (level.number + 1).coerceAtMost(com.kang.colorblockjam.model.Levels.MAX_LEVEL)
            }
            // The "Level complete" popup only every 10th level (and at the very end); otherwise flash a banner and move on.
            if (level.number % WIN_POPUP_EVERY == 0 || level.number >= com.kang.colorblockjam.model.Levels.MAX_LEVEL) {
                overlay = Overlay.WIN
            } else {
                bannerUntil = System.currentTimeMillis() + AUTO_NEXT_DELAY_MS
                postDelayed({ if (running) onNext() }, AUTO_NEXT_DELAY_MS)
            }
        }
        override fun onFailed(reason: String) { overlay = Overlay.FAIL; haptics.levelFailed() }
        override fun onBlockBlocked(block: Block) { shake = 1f }
    }

    fun stop() { running = false }

    fun pauseGame() { if (overlay == Overlay.NONE) overlay = Overlay.PAUSE }

    private fun showToast(msg: String) { toast = msg; toastUntil = System.currentTimeMillis() + 1500 }

    // ------------------------------------------------------------------ layout

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val hudH = h * 0.14f
        val boosterH = h * 0.13f
        hudRect.set(0f, 0f, w.toFloat(), hudH)
        val availW = w - w * 0.08f
        val availH = h - hudH - boosterH - h * 0.04f
        val cellsW = level.cols + 2 * frame
        val cellsH = level.rows + 2 * frame
        cell = min(availW / cellsW, availH / cellsH)
        boardLeft = (w - level.cols * cell) / 2f
        boardTop = hudH + (availH - level.rows * cell) / 2f + h * 0.02f
        val btn = hudH * 0.42f
        restartRect.set(w - btn * 1.3f, hudH * 0.12f, w - btn * 0.3f, hudH * 0.12f + btn)
        pauseRect.set(w - btn * 1.3f, hudH * 0.12f + btn * 1.15f, w - btn * 0.3f, hudH * 0.12f + btn * 2.15f)
        val by = h - boosterH * 0.85f
        val bs = boosterH * 0.6f
        freezeRect.set(w * 0.5f - bs * 1.3f, by, w * 0.5f - bs * 0.3f, by + bs)
        hammerRect.set(w * 0.5f + bs * 0.3f, by, w * 0.5f + bs * 1.3f, by + bs)
        val bw = w * 0.6f
        val bh = h * 0.07f
        button1.set(w / 2f - bw / 2f, h * 0.52f, w / 2f + bw / 2f, h * 0.52f + bh)
        button2.set(w / 2f - bw / 2f, h * 0.61f, w / 2f + bw / 2f, h * 0.61f + bh)
        button3.set(w / 2f - bw / 2f, h * 0.70f, w / 2f + bw / 2f, h * 0.70f + bh)
    }

    private fun toCellX(px: Float) = (px - boardLeft) / cell
    private fun toCellY(py: Float) = (py - boardTop) / cell

    // ------------------------------------------------------------------ input

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (overlay != Overlay.NONE) { handleOverlayTap(e.x, e.y); return true }
                if (restartRect.contains(e.x, e.y)) { restartLevel(); return true }
                if (pauseRect.contains(e.x, e.y)) { overlay = Overlay.PAUSE; return true }
                if (freezeRect.contains(e.x, e.y)) { useFreeze(); return true }
                if (hammerRect.contains(e.x, e.y)) { toggleHammer(); return true }
                val b = board.blockAt(toCellX(e.x), toCellY(e.y)) ?: return true
                if (hammerMode) {
                    if (progress.hammerCount > 0 && board.hammer(b)) { progress.hammerCount -= 1 }
                    hammerMode = false
                    return true
                }
                if (!board.canGrab(b)) { if (b.frozen) showToast("Frozen: remove ${b.iceLayers} more block(s)"); return true }
                dragging = b
                lastX = e.x; lastY = e.y
                // Bring the dragged block to the front of the draw order.
                board.blocks.remove(b); board.blocks.add(b)
            }
            MotionEvent.ACTION_MOVE -> {
                val b = dragging ?: return true
                val dx = (e.x - lastX) / cell
                val dy = (e.y - lastY) / cell
                lastX = e.x; lastY = e.y
                board.drag(b, dx, dy)
                if (b.exited) dragging = null
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging?.let { board.release(it) }
                dragging = null
            }
        }
        return true
    }

    private fun handleOverlayTap(x: Float, y: Float) {
        when (overlay) {
            Overlay.PAUSE -> {
                if (button1.contains(x, y)) overlay = Overlay.NONE
                else if (button2.contains(x, y)) restartLevel()
                else if (button3.contains(x, y)) onHome()
            }
            Overlay.WIN -> if (button1.contains(x, y)) onNext() else if (button2.contains(x, y)) onHome()
            Overlay.FAIL -> {
                if (button1.contains(x, y)) {
                    // Continue with extra time (coins); bombs cannot be bought off.
                    if (board.failReason == "time" && progress.coins >= Progress.CONTINUE_COST) {
                        progress.coins -= Progress.CONTINUE_COST
                        board.addTime(Progress.CONTINUE_SECONDS)
                        overlay = Overlay.NONE
                    } else showToast("Not enough coins")
                } else if (button2.contains(x, y)) onRetry()
                else if (button3.contains(x, y)) onHome()
            }
            Overlay.NONE -> {}
        }
    }

    private fun restartLevel() {
        // Lives are unlimited: restarting costs nothing (see Progress.LIVES_UNLIMITED).
        board = Board(level)
        board.listener = listener()
        overlay = Overlay.NONE
        dragging = null
        hammerMode = false
    }

    private fun useFreeze() {
        if (level.number < Progress.FREEZE_UNLOCK_LEVEL) { showToast("Unlocks at level ${Progress.FREEZE_UNLOCK_LEVEL}"); return }
        if (progress.freezeCount > 0) {
            progress.freezeCount -= 1
            board.freezeTime(Progress.FREEZE_SECONDS)
        } else if (progress.coins >= Progress.FREEZE_COST) {
            progress.coins -= Progress.FREEZE_COST
            board.freezeTime(Progress.FREEZE_SECONDS)
        } else showToast("Not enough coins")
    }

    private fun toggleHammer() {
        if (level.number < Progress.HAMMER_UNLOCK_LEVEL) { showToast("Unlocks at level ${Progress.HAMMER_UNLOCK_LEVEL}"); return }
        if (progress.hammerCount == 0) {
            if (progress.coins >= Progress.HAMMER_COST) { progress.coins -= Progress.HAMMER_COST; progress.hammerCount += 1 }
            else { showToast("Not enough coins"); return }
        }
        hammerMode = !hammerMode
        if (hammerMode) showToast("Tap a block to remove it")
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFFD6E4F7.toInt())
        drawHud(c)
        drawBoard(c)
        drawBoosters(c)
        if (toastUntil > System.currentTimeMillis()) {
            textPaint.color = 0xFF37474F.toInt(); textPaint.textSize = height * 0.022f
            c.drawText(toast, width / 2f, height * 0.83f, textPaint)
        }
        if (bannerUntil > System.currentTimeMillis() && overlay == Overlay.NONE) {
            // Short "cleared" banner shown while the next level loads (no popup on ordinary levels).
            val r = RectF(width * 0.1f, height * 0.44f, width * 0.9f, height * 0.56f)
            paint.color = 0xE61E88E5.toInt()
            c.drawRoundRect(r, r.height() * 0.3f, r.height() * 0.3f, paint)
            textPaint.color = Color.WHITE; textPaint.textSize = height * 0.04f
            c.drawText("LEVEL ${level.number} CLEARED", r.centerX(), r.centerY() + height * 0.014f, textPaint)
            textPaint.textSize = height * 0.02f
            c.drawText("+${Progress.COINS_PER_LEVEL} coins", r.centerX(), r.bottom - height * 0.015f, textPaint)
        }
        when (overlay) {
            Overlay.PAUSE -> drawDialog(c, "PAUSED", null, "Resume", "Restart", "Home")
            Overlay.WIN -> drawDialog(c, "LEVEL COMPLETE", "+${Progress.COINS_PER_LEVEL} coins", "Continue", "Home", null)
            Overlay.FAIL -> drawDialog(
                c, if (board.failReason == "bomb") "BOOM!" else "OUT OF TIME",
                if (board.failReason == "time") "Lives: unlimited" else "A bomb block ran out of time",
                if (board.failReason == "time") "+${Progress.CONTINUE_SECONDS.toInt()}s  (${Progress.CONTINUE_COST} coins)" else "—",
                "Retry", "Home")
            Overlay.NONE -> {}
        }
    }

    private fun drawHud(c: Canvas) {
        val h = hudRect.height()
        // Level badge
        paint.color = 0xFF1E88E5.toInt()
        c.drawCircle(width * 0.13f, h * 0.5f, h * 0.36f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = h * 0.2f
        c.drawText("Level", width * 0.13f, h * 0.42f, textPaint)
        textPaint.textSize = h * 0.34f
        c.drawText("${level.number}", width * 0.13f, h * 0.76f, textPaint)
        if (level.hardness > 0) {
            val badge = RectF(width * 0.04f, h * 0.84f, width * 0.22f, h * 1.02f)
            paint.color = if (level.hardness == 1) 0xFFEF6C00.toInt() else 0xFFC62828.toInt()
            c.drawRoundRect(badge, badge.height() / 2f, badge.height() / 2f, paint)
            textPaint.textSize = h * 0.13f
            textPaint.color = Color.WHITE
            c.drawText(if (level.hardness == 1) "HARD" else "SUPER HARD", badge.centerX(), badge.centerY() + h * 0.045f, textPaint)
        }
        // Timer
        val timerRect = RectF(width * 0.27f, h * 0.2f, width * 0.68f, h * 0.8f)
        paint.color = 0xFF1565C0.toInt()
        c.drawRoundRect(timerRect, h * 0.2f, h * 0.2f, paint)
        val t = board.timeLeft.toInt()
        textPaint.textSize = h * 0.36f
        textPaint.color = if (board.frozenTimeLeft > 0f) 0xFF80DEEA.toInt() else if (t <= 10) 0xFFFF8A80.toInt() else Color.WHITE
        c.drawText(String.format("%02d:%02d", t / 60, t % 60), timerRect.centerX(), timerRect.centerY() + h * 0.13f, textPaint)
        if (board.frozenTimeLeft > 0f) {
            textPaint.textSize = h * 0.16f
            c.drawText("FROZEN ${board.frozenTimeLeft.toInt() + 1}s", timerRect.centerX(), timerRect.bottom + h * 0.17f, textPaint)
        }
        // Coins
        textPaint.textSize = h * 0.2f
        textPaint.color = 0xFF5D4037.toInt()
        textPaint.textAlign = Paint.Align.LEFT
        c.drawText("● ${progress.coins}", width * 0.27f, h * 0.97f, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        // Buttons
        drawIconButton(c, restartRect, 0xFFFB8C00.toInt(), "↻")
        drawIconButton(c, pauseRect, 0xFF1E88E5.toInt(), "❚❚")
    }

    private fun drawIconButton(c: Canvas, r: RectF, color: Int, glyph: String) {
        paint.color = color
        c.drawRoundRect(r, r.height() * 0.3f, r.height() * 0.3f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = r.height() * 0.55f
        c.drawText(glyph, r.centerX(), r.centerY() + r.height() * 0.2f, textPaint)
    }

    private fun drawBoard(c: Canvas) {
        val f = frame * cell
        val outer = RectF(boardLeft - f, boardTop - f, boardLeft + level.cols * cell + f, boardTop + level.rows * cell + f)
        // Frame
        paint.color = 0xFF3E4A5C.toInt()
        c.drawRoundRect(outer, f, f, paint)
        // Floor
        paint.color = 0xFF8D97A8.toInt()
        c.drawRect(boardLeft, boardTop, boardLeft + level.cols * cell, boardTop + level.rows * cell, paint)
        strokePaint.color = 0xFF7A8495.toInt()
        strokePaint.strokeWidth = max(1f, cell * 0.03f)
        for (x in 0..level.cols) c.drawLine(boardLeft + x * cell, boardTop, boardLeft + x * cell, boardTop + level.rows * cell, strokePaint)
        for (y in 0..level.rows) c.drawLine(boardLeft, boardTop + y * cell, boardLeft + level.cols * cell, boardTop + y * cell, strokePaint)
        // Removed cells (holes / cut corners) are drawn in the frame colour so they read as "outside the board".
        paint.color = 0xFF3E4A5C.toInt()
        for ((wx, wy) in board.walls) {
            val r = RectF(boardLeft + wx * cell - 1f, boardTop + wy * cell - 1f, boardLeft + (wx + 1) * cell + 1f, boardTop + (wy + 1) * cell + 1f)
            c.drawRect(r, paint)
        }
        // Gates
        for (g in board.gates) drawGate(c, g, f)
        // Blocks (exiting ones are clipped to the board + frame so they vanish into the gate)
        for (b in board.blocks) {
            if (b.exited && board.exitAnims.none { it.block === b }) continue
            val exiting = board.exitAnims.any { it.block === b }
            if (exiting) { c.save(); c.clipRect(outer) }
            drawBlock(c, b, b === dragging)
            if (exiting) c.restore()
        }
    }

    private fun drawGate(c: Canvas, g: Gate, f: Float) {
        val inset = f * 0.12f
        val r = when (g.side) {
            Side.TOP -> RectF(boardLeft + g.start * cell + inset, boardTop - f + inset, boardLeft + g.end * cell - inset, boardTop)
            Side.BOTTOM -> RectF(boardLeft + g.start * cell + inset, boardTop + level.rows * cell, boardLeft + g.end * cell - inset, boardTop + level.rows * cell + f - inset)
            Side.LEFT -> RectF(boardLeft - f + inset, boardTop + g.start * cell + inset, boardLeft, boardTop + g.end * cell - inset)
            Side.RIGHT -> RectF(boardLeft + level.cols * cell, boardTop + g.start * cell + inset, boardLeft + level.cols * cell + f - inset, boardTop + g.end * cell - inset)
        }
        if (g.open) {
            paint.color = g.color.argb
            c.drawRoundRect(r, f * 0.25f, f * 0.25f, paint)
            // Direction arrow
            paint.color = 0xAAFFFFFF.toInt()
            val cx = r.centerX(); val cy = r.centerY(); val s = f * 0.28f
            val p = Path()
            when (g.side) {
                Side.TOP -> { p.moveTo(cx, cy - s); p.lineTo(cx - s, cy + s * 0.6f); p.lineTo(cx + s, cy + s * 0.6f) }
                Side.BOTTOM -> { p.moveTo(cx, cy + s); p.lineTo(cx - s, cy - s * 0.6f); p.lineTo(cx + s, cy - s * 0.6f) }
                Side.LEFT -> { p.moveTo(cx - s, cy); p.lineTo(cx + s * 0.6f, cy - s); p.lineTo(cx + s * 0.6f, cy + s) }
                Side.RIGHT -> { p.moveTo(cx + s, cy); p.lineTo(cx - s * 0.6f, cy - s); p.lineTo(cx - s * 0.6f, cy + s) }
            }
            p.close(); c.drawPath(p, paint)
        } else {
            // Ice door: pale blue with the remaining count.
            paint.color = 0xFFB3E5FC.toInt()
            c.drawRoundRect(r, f * 0.25f, f * 0.25f, paint)
            strokePaint.color = 0xFF81D4FA.toInt(); strokePaint.strokeWidth = f * 0.08f
            c.drawRoundRect(r, f * 0.25f, f * 0.25f, strokePaint)
            textPaint.color = 0xFF0277BD.toInt(); textPaint.textSize = f * 0.8f
            c.drawText("${g.iceCount}", r.centerX(), r.centerY() + f * 0.28f, textPaint)
        }
    }

    private fun drawBlock(c: Canvas, b: Block, lifted: Boolean) {
        val color = if (b.hidden) 0xFF9E9E9E.toInt() else b.color.argb
        val dark = if (b.hidden) 0xFF616161.toInt() else b.color.dark
        val gap = cell * 0.06f
        val lift = if (lifted) cell * 0.06f else 0f
        val ox = boardLeft + b.x * cell - lift
        val oy = boardTop + b.y * cell - lift
        // One outline path for the whole polyomino (union of its cells) so there are no seams between cells.
        val slab = slabPath(b, ox, oy, gap)
        paint.pathEffect = cornerEffect
        // Shadow / side face
        paint.color = dark
        c.save(); c.translate(0f, cell * 0.1f + lift * 1.5f); c.drawPath(slab, paint); c.restore()
        // Top face
        paint.color = color
        c.drawPath(slab, paint)
        paint.pathEffect = null
        // Studs
        paint.color = dark and 0x60FFFFFF or 0x40000000
        val studR = cell * 0.17f
        for ((cx, cy) in b.shape.cells) {
            for (sx in 0..1) for (sy in 0..1) {
                val px = ox + (cx + 0.25f + sx * 0.5f) * cell
                val py = oy + (cy + 0.25f + sy * 0.5f) * cell
                paint.color = 0x33000000
                c.drawCircle(px, py + studR * 0.25f, studR, paint)
                paint.color = if (b.hidden) 0xFFBDBDBD.toInt() else lighten(color)
                c.drawCircle(px, py, studR, paint)
            }
        }
        // Decorations
        val bx = ox + b.width * cell / 2f
        val by = oy + b.height * cell / 2f
        if (b.hidden) {
            textPaint.color = Color.WHITE; textPaint.textSize = cell * 0.7f
            c.drawText("?", bx, by + cell * 0.25f, textPaint)
        }
        if (b.frozen) {
            paint.color = 0x99B3E5FC.toInt()
            for ((cx, cy) in b.shape.cells) c.drawRect(ox + cx * cell, oy + cy * cell, ox + (cx + 1) * cell, oy + (cy + 1) * cell, paint)
            textPaint.color = 0xFF01579B.toInt(); textPaint.textSize = cell * 0.6f
            c.drawText("${b.iceLayers}", bx, by + cell * 0.2f, textPaint)
        }
        if (b.bombSeconds >= 0f) {
            paint.color = 0xFF212121.toInt()
            c.drawCircle(bx, by, cell * 0.32f, paint)
            textPaint.color = if (b.bombSeconds < 10f) 0xFFFF5252.toInt() else Color.WHITE
            textPaint.textSize = cell * 0.36f
            c.drawText("${b.bombSeconds.toInt()}", bx, by + cell * 0.13f, textPaint)
        }
    }

    private var cornerEffect: CornerPathEffect? = null
    private val tmpPath = Path()
    private val cellPath = Path()

    /** Union of the block's cells, inset by [gap] on the outer edges only. */
    private fun slabPath(b: Block, ox: Float, oy: Float, gap: Float): Path {
        if (cornerEffect == null) cornerEffect = CornerPathEffect(cell * 0.16f)
        tmpPath.rewind()
        for ((cx, cy) in b.shape.cells) {
            val l = ox + cx * cell + (if (b.shape.contains(cx - 1, cy)) -0.5f else gap)
            val t = oy + cy * cell + (if (b.shape.contains(cx, cy - 1)) -0.5f else gap)
            val r = ox + (cx + 1) * cell - (if (b.shape.contains(cx + 1, cy)) -0.5f else gap)
            val btm = oy + (cy + 1) * cell - (if (b.shape.contains(cx, cy + 1)) -0.5f else gap)
            cellPath.rewind()
            cellPath.addRect(l, t, r, btm, Path.Direction.CW)
            tmpPath.op(cellPath, Path.Op.UNION)
        }
        return tmpPath
    }

    private fun lighten(argb: Int): Int {
        val r = min(255, Color.red(argb) + 40); val g = min(255, Color.green(argb) + 40); val b = min(255, Color.blue(argb) + 40)
        return Color.rgb(r, g, b)
    }

    private fun drawBoosters(c: Canvas) {
        drawBooster(c, freezeRect, "❄", "Freeze", progress.freezeCount, level.number >= Progress.FREEZE_UNLOCK_LEVEL,
            Progress.FREEZE_UNLOCK_LEVEL, false)
        drawBooster(c, hammerRect, "⚒", "Hammer", progress.hammerCount, level.number >= Progress.HAMMER_UNLOCK_LEVEL,
            Progress.HAMMER_UNLOCK_LEVEL, hammerMode)
    }

    private fun drawBooster(c: Canvas, r: RectF, glyph: String, name: String, count: Int, unlocked: Boolean, unlockLevel: Int, active: Boolean) {
        paint.color = if (!unlocked) 0xFFB0BEC5.toInt() else if (active) 0xFFFFB300.toInt() else 0xFF42A5F5.toInt()
        c.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = r.height() * 0.45f
        c.drawText(glyph, r.centerX(), r.centerY() + r.height() * 0.16f, textPaint)
        textPaint.textSize = r.height() * 0.22f
        textPaint.color = 0xFF37474F.toInt()
        c.drawText(if (unlocked) name else "Lv.$unlockLevel", r.centerX(), r.bottom + r.height() * 0.3f, textPaint)
        if (unlocked) {
            paint.color = if (count > 0) 0xFF43A047.toInt() else 0xFFFB8C00.toInt()
            val br = r.width() * 0.2f
            c.drawCircle(r.right - br * 0.5f, r.bottom - br * 0.5f, br, paint)
            textPaint.color = Color.WHITE; textPaint.textSize = br * 1.3f
            c.drawText(if (count > 0) "$count" else "+", r.right - br * 0.5f, r.bottom - br * 0.5f + br * 0.45f, textPaint)
        }
    }

    private fun drawDialog(c: Canvas, title: String, subtitle: String?, b1: String, b2: String?, b3: String?) {
        paint.color = 0x99000000.toInt()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val box = RectF(width * 0.1f, height * 0.3f, width * 0.9f, height * 0.8f)
        paint.color = 0xFF2196F3.toInt()
        c.drawRoundRect(box, width * 0.05f, width * 0.05f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = height * 0.045f
        c.drawText(title, width / 2f, height * 0.39f, textPaint)
        if (subtitle != null) {
            textPaint.textSize = height * 0.024f
            c.drawText(subtitle, width / 2f, height * 0.45f, textPaint)
        }
        drawButton(c, button1, b1, 0xFF43A047.toInt())
        if (b2 != null) drawButton(c, button2, b2, 0xFFFB8C00.toInt())
        if (b3 != null) drawButton(c, button3, b3, 0xFF78909C.toInt())
    }

    private fun drawButton(c: Canvas, r: RectF, label: String, color: Int) {
        paint.color = color
        c.drawRoundRect(r, r.height() * 0.4f, r.height() * 0.4f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = r.height() * 0.42f
        c.drawText(label, r.centerX(), r.centerY() + r.height() * 0.15f, textPaint)
    }
}
