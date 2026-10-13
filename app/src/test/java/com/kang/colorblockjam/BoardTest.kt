package com.kang.colorblockjam

import com.kang.colorblockjam.model.Block
import com.kang.colorblockjam.model.BlockColor
import com.kang.colorblockjam.model.Board
import com.kang.colorblockjam.model.Gate
import com.kang.colorblockjam.model.Level
import com.kang.colorblockjam.model.Levels
import com.kang.colorblockjam.model.Shape
import com.kang.colorblockjam.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {

    private fun twoBlockLevel() = Level(1, 4, 5, 300,
        listOf(Block(0, BlockColor.BLUE, Shape.O2, 2f, 1f), Block(1, BlockColor.PURPLE, Shape.O2, 0f, 2f)),
        listOf(Gate(Side.TOP, 2, 2, BlockColor.BLUE), Gate(Side.BOTTOM, 0, 2, BlockColor.PURPLE)))

    @Test
    fun blockStopsAtWallWithoutMatchingGate() {
        val board = Board(twoBlockLevel())
        val purple = board.blocks[1]
        board.drag(purple, 0f, -5f) // up: no purple gate on top
        assertEquals(0f, purple.y, 1e-4f)
        board.drag(purple, -5f, 0f)
        assertEquals(0f, purple.x, 1e-4f)
    }

    @Test
    fun blockSlidesAroundOtherBlock() {
        val board = Board(twoBlockLevel())
        val purple = board.blocks[1]
        // Purple (0..2, 2..4) moving right is stopped by blue (2..4, 1..3) because rows overlap.
        board.drag(purple, 5f, 0f)
        assertEquals(0f, purple.x, 1e-4f)
        // Move it down one row first; now rows 3..5 do not overlap blue's 1..3 -> it can slide right.
        board.drag(purple, 0f, 1f)
        board.drag(purple, 5f, 0f)
        assertEquals(2f, purple.x, 1e-4f)
    }

    @Test
    fun blockExitsThroughMatchingGate() {
        val board = Board(twoBlockLevel())
        val blue = board.blocks[0]
        board.drag(blue, 0f, -3f)
        assertTrue(blue.exited)
        assertEquals(1, board.exitedCount)
        assertFalse(board.won)
        val purple = board.blocks[1]
        board.drag(purple, 0f, 5f)
        assertTrue(purple.exited)
        assertTrue(board.won)
    }

    @Test
    fun misalignedBlockDoesNotExit() {
        val level = Level(1, 4, 5, 300,
            listOf(Block(0, BlockColor.BLUE, Shape.O2, 1f, 1f)),
            listOf(Gate(Side.TOP, 2, 2, BlockColor.BLUE)))
        val board = Board(level)
        val blue = board.blocks[0]
        board.drag(blue, 0f, -3f) // x=1 but the gate covers columns 2..4
        assertFalse(blue.exited)
        assertEquals(0f, blue.y, 1e-4f)
        board.drag(blue, 1f, 0f)
        board.drag(blue, 0f, -3f)
        assertTrue(blue.exited)
    }

    @Test
    fun iceDoorOpensAfterExits() {
        val level = Level(1, 4, 5, 300,
            listOf(Block(0, BlockColor.BLUE, Shape.O2, 2f, 1f), Block(1, BlockColor.PURPLE, Shape.O2, 0f, 2f)),
            listOf(Gate(Side.TOP, 2, 2, BlockColor.BLUE), Gate(Side.BOTTOM, 0, 2, BlockColor.PURPLE, iceCount = 1)))
        val board = Board(level)
        val purple = board.blocks[1]
        board.drag(purple, 0f, 5f)
        assertFalse(purple.exited)
        board.drag(board.blocks[0], 0f, -3f)
        assertTrue(board.gates[1].open)
        board.drag(purple, 0f, 5f)
        assertTrue(purple.exited)
    }

    @Test
    fun timerFailsAndFreezeHolds() {
        val board = Board(Level(1, 4, 4, 2, listOf(Block(0, BlockColor.RED, Shape.I1, 0f, 0f)), listOf(Gate(Side.TOP, 0, 1, BlockColor.RED))))
        board.freezeTime(1f)
        board.tick(0.9f)
        assertEquals(2f, board.timeLeft, 1e-4f)
        board.tick(0.2f)
        board.tick(2.5f)
        assertTrue(board.failed)
        assertEquals("time", board.failReason)
    }

    @Test
    fun allLevelsAreWellFormed() {
        for (n in 1..Levels.MAX_LEVEL) {
            val level = Levels.get(n)
            val occupied = HashSet<Pair<Int, Int>>()
            for (b in level.blocks) {
                for ((cx, cy) in b.shape.cells) {
                    val cell = Pair(b.x.toInt() + cx, b.y.toInt() + cy)
                    assertTrue("level $n block ${b.id} outside board", cell.first in 0 until level.cols && cell.second in 0 until level.rows)
                    assertTrue("level $n overlap at $cell", occupied.add(cell))
                    assertFalse("level $n block on wall $cell", level.walls.contains(cell))
                }
                assertTrue("level $n block ${b.id} has no gate", level.gates.any { it.color == b.color })
            }
            for (g in level.gates) {
                val len = if (g.side.horizontal) level.cols else level.rows
                assertTrue("level $n gate off wall", g.start >= 0 && g.end <= len)
            }
            assertTrue("level $n too few blocks", level.blocks.size >= 2)
            assertTrue("level $n time", level.timeSeconds in 60..300)
        }
    }

    @Test
    fun generatedLevelsAreSolvableByReplayingRecordedSolution() {
        // Every generated level carries the solution implied by its reverse construction; replaying it
        // through the real engine (collisions, gates, ice doors, frozen blocks) must clear the board.
        for (n in 7..Levels.MAX_LEVEL) {
            val level = Levels.get(n)
            assertTrue("level $n has no recorded solution", level.solution.isNotEmpty())
            val board = Board(level)
            for ((step, m) in level.solution.withIndex()) {
                val b = board.blocks.first { it.id == m.blockId }
                assertTrue("level $n step $step: block ${b.id} frozen", !b.frozen)
                val before = Pair(b.x, b.y)
                board.drag(b, m.dx.toFloat(), m.dy.toFloat())
                if (m.exit) {
                    assertTrue("level $n step $step: block ${b.id} did not exit (at ${b.x},${b.y})", b.exited)
                } else {
                    assertEquals("level $n step $step: block ${b.id} x", before.first + m.dx, b.x, 1e-3f)
                    assertEquals("level $n step $step: block ${b.id} y", before.second + m.dy, b.y, 1e-3f)
                }
            }
            assertTrue("level $n not cleared", board.won)
        }
    }

    @Test
    fun smallGeneratedLevelsAreSolvableByBruteForce() {
        // Independent cross-check with a tiny DFS solver on boards small enough to search.
        for (n in 7..40) {
            val level = Levels.get(n)
            if (level.blocks.size > 7) continue
            assertTrue("level $n not solvable", Solver.solvable(level, 200_000))
        }
    }
}
