package com.kang.colorblockjam

import com.kang.colorblockjam.model.Level
import com.kang.colorblockjam.model.Side

/**
 * Small DFS solver used only in tests: integer grid positions, moves of one cell in four directions,
 * exits through open matching gates, ice doors counted down per exit. Frozen blocks are respected;
 * hidden colours and bombs do not affect reachability.
 */
object Solver {
    private class State(val pos: IntArray, val exited: BooleanArray, val exits: Int)

    fun solvable(level: Level, budget: Int): Boolean {
        val n = level.blocks.size
        val start = State(IntArray(n * 2) { i -> if (i % 2 == 0) level.blocks[i / 2].x.toInt() else level.blocks[i / 2].y.toInt() },
            BooleanArray(n), 0)
        val seen = HashSet<String>()
        var steps = 0
        val stack = ArrayDeque<State>()
        fun key(s: State) = s.pos.joinToString(",") + "|" + s.exited.joinToString("") { if (it) "1" else "0" }
        fun push(s: State) { if (seen.add(key(s))) stack.addLast(s) } // de-duplicate before pushing to bound memory
        push(start)
        while (stack.isNotEmpty()) {
            if (steps++ > budget) return false
            val s = stack.removeLast()
            if (s.exited.all { it }) return true
            val occ = HashSet<Pair<Int, Int>>(level.walls)
            for (i in 0 until n) if (!s.exited[i]) for ((cx, cy) in level.blocks[i].shape.cells) occ.add(Pair(s.pos[2 * i] + cx, s.pos[2 * i + 1] + cy))
            // Exits first (greedy), then slides.
            for (i in 0 until n) {
                if (s.exited[i]) continue
                val b = level.blocks[i]
                if (b.iceLayers > s.exits) continue
                val x = s.pos[2 * i]; val y = s.pos[2 * i + 1]
                for (g in level.gates) {
                    if (g.color != b.color || g.iceCount > s.exits) continue
                    val aligned = when (g.side) {
                        Side.TOP -> y == 0 && x >= g.start && x + b.width <= g.end
                        Side.BOTTOM -> y + b.height == level.rows && x >= g.start && x + b.width <= g.end
                        Side.LEFT -> x == 0 && y >= g.start && y + b.height <= g.end
                        Side.RIGHT -> x + b.width == level.cols && y >= g.start && y + b.height <= g.end
                    }
                    if (aligned) {
                        val ex = s.exited.copyOf(); ex[i] = true
                        push(State(s.pos, ex, s.exits + 1))
                    }
                }
            }
            for (i in 0 until n) {
                if (s.exited[i]) continue
                val b = level.blocks[i]
                if (b.iceLayers > s.exits) continue
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = s.pos[2 * i] + dx; val ny = s.pos[2 * i + 1] + dy
                    if (nx < 0 || ny < 0 || nx + b.width > level.cols || ny + b.height > level.rows) continue
                    val ok = b.shape.cells.all { (cx, cy) ->
                        val c = Pair(nx + cx, ny + cy)
                        !occ.contains(c) || b.shape.cells.any { (ox, oy) -> ox + s.pos[2 * i] == c.first && oy + s.pos[2 * i + 1] == c.second }
                    }
                    if (!ok) continue
                    val np = s.pos.copyOf(); np[2 * i] = nx; np[2 * i + 1] = ny
                    push(State(np, s.exited, s.exits))
                }
            }
        }
        return false
    }
}
