package com.kang.colorblockjam.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Game-state engine for one level: continuous block dragging with collision sliding,
 * gate exit detection, ice doors, frozen / hidden blocks and timer bombs.
 * No Android dependencies so it can be unit-tested on the JVM.
 */
class Board(val level: Level) {
    val cols = level.cols
    val rows = level.rows
    val blocks: MutableList<Block> = level.freshBlocks()
    val gates: MutableList<Gate> = level.freshGates()
    val walls: Set<Pair<Int, Int>> = level.walls

    var exitedCount = 0
        private set
    var timeLeft: Float = level.timeSeconds.toFloat()
        private set
    var frozenTimeLeft: Float = 0f
        private set
    var failed: Boolean = false
        private set
    var failReason: String = ""
        private set
    val won: Boolean get() = blocks.all { it.exited }

    /** Block currently being exited (animated outward), with its travel direction. */
    class ExitAnim(val block: Block, val side: Side, var progress: Float)
    val exitAnims = ArrayList<ExitAnim>()

    interface Listener {
        fun onBlockExited(block: Block, gate: Gate) {}
        fun onGateOpened(gate: Gate) {}
        fun onBlockMoved(block: Block) {}
        fun onBlockBlocked(block: Block) {}
        fun onFailed(reason: String) {}
        fun onWon() {}
    }
    var listener: Listener? = null

    companion object {
        const val EPS = 1e-3f
        /** How far (in cells) the block may be misaligned with the gate span and still slide out. */
        const val GATE_TOLERANCE = 0.2f
        /** Once a block protrudes this far through a gate, the exit completes automatically. */
        const val AUTO_EXIT_DEPTH = 0.25f
        const val EXIT_ANIM_SPEED = 6f // cells per second
    }

    // ---------------------------------------------------------------- queries

    fun activeBlocks(): List<Block> = blocks.filter { !it.exited && exitAnims.none { a -> a.block === it } }

    fun blockAt(px: Float, py: Float): Block? {
        for (b in activeBlocks().asReversed()) {
            val lx = px - b.x
            val ly = py - b.y
            if (lx < 0 || ly < 0 || lx >= b.width || ly >= b.height) continue
            if (b.shape.contains(lx.toInt(), ly.toInt())) return b
        }
        return null
    }

    fun gateFor(block: Block, side: Side): Gate? {
        val lo: Float
        val hi: Float
        if (side.horizontal) { lo = block.x; hi = block.x + block.width } else { lo = block.y; hi = block.y + block.height }
        return gates.firstOrNull { g ->
            g.side == side && g.open && g.color == block.color &&
                lo >= g.start - GATE_TOLERANCE && hi <= g.end + GATE_TOLERANCE
        }
    }

    /** True when the block may be grabbed by the player. */
    fun canGrab(block: Block): Boolean = !block.exited && !block.frozen && !failed && exitAnims.none { it.block === block }

    // ---------------------------------------------------------------- movement

    /**
     * Moves [block] by (dx, dy) as far as collisions allow, resolving one axis at a time
     * (larger component first so sliding along obstacles feels natural). Returns true if it moved.
     */
    fun drag(block: Block, dx: Float, dy: Float): Boolean {
        if (!canGrab(block)) return false
        var moved = false
        val order = if (abs(dx) >= abs(dy)) listOf(true, false) else listOf(false, true)
        for (horizontal in order) {
            val d = if (horizontal) dx else dy
            if (abs(d) < EPS) continue
            val allowed = allowedMove(block, horizontal, d)
            if (abs(allowed) > EPS) {
                if (horizontal) block.x += allowed else block.y += allowed
                moved = true
            }
            if (abs(allowed - d) > EPS) listener?.onBlockBlocked(block)
        }
        if (moved) listener?.onBlockMoved(block)
        checkAutoExit(block)
        return moved
    }

    /** Largest displacement along one axis (sign of [d]) that keeps [block] free of overlaps. */
    fun allowedMove(block: Block, horizontal: Boolean, d: Float): Float {
        if (abs(d) < EPS) return 0f
        val positive = d > 0
        var limit = abs(d)
        // Board walls, unless an open matching gate lets us through.
        val side = if (horizontal) (if (positive) Side.RIGHT else Side.LEFT) else (if (positive) Side.BOTTOM else Side.TOP)
        val gate = gateFor(block, side)
        if (gate == null) {
            val toWall = when (side) {
                Side.RIGHT -> cols - (block.x + block.width)
                Side.LEFT -> block.x
                Side.BOTTOM -> rows - (block.y + block.height)
                Side.TOP -> block.y
            }
            limit = min(limit, max(0f, toWall))
        } else {
            // Through a gate the block must stay aligned with the gate span on the other axis.
            val cross = if (horizontal) block.y else block.x
            val snapped = cross.roundToInt().toFloat()
            if (abs(cross - snapped) > GATE_TOLERANCE) {
                val toWall = when (side) {
                    Side.RIGHT -> cols - (block.x + block.width)
                    Side.LEFT -> block.x
                    Side.BOTTOM -> rows - (block.y + block.height)
                    Side.TOP -> block.y
                }
                limit = min(limit, max(0f, toWall))
            }
        }
        if (limit <= EPS) return 0f
        // Other blocks and wall cells, cell against cell.
        for ((cx, cy) in block.shape.cells) {
            val ax = block.x + cx
            val ay = block.y + cy
            for (other in activeBlocks()) {
                if (other === block) continue
                for ((ox, oy) in other.shape.cells) {
                    limit = min(limit, gapBetween(ax, ay, other.x + ox, other.y + oy, horizontal, positive))
                    if (limit <= EPS) return 0f
                }
            }
            for ((wx, wy) in walls) {
                limit = min(limit, gapBetween(ax, ay, wx.toFloat(), wy.toFloat(), horizontal, positive))
                if (limit <= EPS) return 0f
            }
        }
        return if (positive) limit else -limit
    }

    /** Free distance between unit cell A and unit cell B along the move axis (infinite when they do not line up). */
    private fun gapBetween(ax: Float, ay: Float, bx: Float, by: Float, horizontal: Boolean, positive: Boolean): Float {
        val crossOverlap = if (horizontal) (ay < by + 1 - EPS && by < ay + 1 - EPS) else (ax < bx + 1 - EPS && bx < ax + 1 - EPS)
        if (!crossOverlap) return Float.MAX_VALUE
        val a = if (horizontal) ax else ay
        val b = if (horizontal) bx else by
        val gap = if (positive) b - (a + 1) else a - (b + 1)
        return if (gap < -EPS) Float.MAX_VALUE else max(0f, gap) // already past each other -> no constraint
    }

    /** Snaps a released block to the grid. Rounding never creates overlaps because every rounded cell overlaps its fractional footprint. */
    fun release(block: Block) {
        if (block.exited || exitAnims.any { it.block === block }) return
        if (!checkAutoExit(block)) {
            block.x = block.x.roundToInt().toFloat().coerceIn(0f, (cols - block.width).toFloat())
            block.y = block.y.roundToInt().toFloat().coerceIn(0f, (rows - block.height).toFloat())
        }
    }

    /** If the block protrudes through a matching gate, start the exit animation. */
    private fun checkAutoExit(block: Block): Boolean {
        val side = when {
            block.x < -AUTO_EXIT_DEPTH -> Side.LEFT
            block.x + block.width > cols + AUTO_EXIT_DEPTH -> Side.RIGHT
            block.y < -AUTO_EXIT_DEPTH -> Side.TOP
            block.y + block.height > rows + AUTO_EXIT_DEPTH -> Side.BOTTOM
            else -> return false
        }
        val gate = gateFor(block, side) ?: return false
        // Align on the cross axis so the block leaves straight through the gate.
        if (side.horizontal) block.x = block.x.roundToInt().toFloat() else block.y = block.y.roundToInt().toFloat()
        exitAnims.add(ExitAnim(block, side, 0f))
        completeExitBookkeeping(block, gate)
        return true
    }

    private fun completeExitBookkeeping(block: Block, gate: Gate) {
        block.exited = true
        exitedCount++
        for (g in gates) {
            if (g.iceCount > 0) {
                g.iceCount--
                if (g.iceCount == 0) listener?.onGateOpened(g)
            }
        }
        for (b in blocks) {
            if (b.exited) continue
            if (b.iceLayers > 0) b.iceLayers--
            if (b.hiddenFor > 0) b.hiddenFor--
        }
        listener?.onBlockExited(block, gate)
        if (won) listener?.onWon()
    }

    // ---------------------------------------------------------------- time

    /** Advances timers; call once per frame. */
    fun tick(dt: Float) {
        if (failed || won) {
            exitAnims.forEach { it.progress += dt * EXIT_ANIM_SPEED }
            exitAnims.removeAll { it.progress >= 1f }
            return
        }
        for (a in exitAnims) {
            a.progress += dt * EXIT_ANIM_SPEED
            val step = dt * EXIT_ANIM_SPEED * 2f
            when (a.side) {
                Side.LEFT -> a.block.x -= step
                Side.RIGHT -> a.block.x += step
                Side.TOP -> a.block.y -= step
                Side.BOTTOM -> a.block.y += step
            }
        }
        exitAnims.removeAll { it.progress >= 1f }
        if (frozenTimeLeft > 0f) {
            frozenTimeLeft -= dt
            return
        }
        timeLeft -= dt
        for (b in activeBlocks()) {
            if (b.bombSeconds >= 0f) {
                b.bombSeconds -= dt
                if (b.bombSeconds <= 0f) {
                    fail("bomb")
                    return
                }
            }
        }
        if (timeLeft <= 0f) {
            timeLeft = 0f
            fail("time")
        }
    }

    private fun fail(reason: String) {
        if (failed) return
        failed = true
        failReason = reason
        listener?.onFailed(reason)
    }

    // ---------------------------------------------------------------- boosters

    /** Freeze-timer booster: pauses the countdown (and bombs) for [seconds]. */
    fun freezeTime(seconds: Float) { frozenTimeLeft += seconds }

    /** Extra time granted when continuing after a time-out. */
    fun addTime(seconds: Float) {
        timeLeft += seconds
        if (failed && failReason == "time") { failed = false; failReason = "" }
    }

    /** Hammer booster: removes a block as if it had exited (counts for ice doors). */
    fun hammer(block: Block): Boolean {
        if (block.exited || failed) return false
        val gate = gates.firstOrNull { it.color == block.color } ?: Gate(Side.TOP, 0, 1, block.color)
        block.exited = true
        exitedCount++
        for (g in gates) if (g.iceCount > 0) { g.iceCount--; if (g.iceCount == 0) listener?.onGateOpened(g) }
        for (b in blocks) if (!b.exited) { if (b.iceLayers > 0) b.iceLayers--; if (b.hiddenFor > 0) b.hiddenFor-- }
        listener?.onBlockExited(block, gate)
        if (won) listener?.onWon()
        return true
    }

    /** Debug helper: textual dump of the grid. */
    fun dump(): String {
        val sb = StringBuilder()
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val b = blockAt(x + 0.5f, y + 0.5f)
                sb.append(when {
                    walls.contains(Pair(x, y)) -> '#'
                    b != null -> ('A' + (b.id % 26))
                    else -> '.'
                })
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}
