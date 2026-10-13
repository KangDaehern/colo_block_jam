package com.kang.colorblockjam

import com.kang.colorblockjam.model.Board
import com.kang.colorblockjam.model.Levels
import org.junit.Test

/** Diagnostic: prints a level and replays its solution step by step (run with `--tests '*DumpLevelTest*' -i`). */
class DumpLevelTest {
    @Test
    fun dump() {
        val n = (System.getProperty("dumpLevel") ?: "13").toInt()
        val level = Levels.get(n)
        println("level $n ${level.cols}x${level.rows} time=${level.timeSeconds} walls=${level.walls}")
        for (g in level.gates) println("  gate ${g.side} start=${g.start} len=${g.length} ${g.color} ice=${g.iceCount}")
        for (b in level.blocks) println("  block ${b.id} ${b.color} ${b.width}x${b.height} cells=${b.shape.cells} at (${b.x},${b.y}) ice=${b.iceLayers} hidden=${b.hiddenFor} bomb=${b.bombSeconds}")
        val board = Board(level)
        println(board.dump())
        for ((i, m) in level.solution.withIndex()) {
            val b = board.blocks.first { it.id == m.blockId }
            val before = "(${b.x},${b.y})"
            board.drag(b, m.dx.toFloat(), m.dy.toFloat())
            println("step $i block ${b.id} move (${m.dx},${m.dy}) exit=${m.exit}: $before -> (${b.x},${b.y}) exited=${b.exited}")
        }
        println(board.dump())
    }
}
