package com.kang.colorblockjam.model

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Level source. Levels 1-6 are hand-authored tutorial boards; everything up to [MAX_LEVEL]
 * is produced deterministically by [LevelGenerator] from the level number.
 */
object Levels {
    const val MAX_LEVEL = 700

    fun get(number: Int): Level {
        val n = number.coerceIn(1, MAX_LEVEL)
        return handcrafted(n) ?: LevelGenerator.generate(n)
    }

    private fun b(id: Int, c: BlockColor, s: Shape, x: Int, y: Int, ice: Int = 0, hidden: Int = 0, bomb: Float = -1f) =
        Block(id, c, s, x.toFloat(), y.toFloat(), ice, hidden, bomb)

    private fun g(side: Side, start: Int, len: Int, c: BlockColor, ice: Int = 0) = Gate(side, start, len, c, ice)

    private fun handcrafted(n: Int): Level? = when (n) {
        1 -> Level(1, 4, 5, 300,
            listOf(b(0, BlockColor.BLUE, Shape.O2, 2, 1), b(1, BlockColor.PURPLE, Shape.O2, 0, 2)),
            listOf(g(Side.TOP, 2, 2, BlockColor.BLUE), g(Side.BOTTOM, 0, 2, BlockColor.PURPLE)))
        2 -> Level(2, 5, 5, 300,
            listOf(b(0, BlockColor.GREEN, Shape.I3.rotated(), 1, 1), b(1, BlockColor.ORANGE, Shape.I3.rotated(), 3, 1)),
            listOf(g(Side.TOP, 2, 3, BlockColor.GREEN), g(Side.BOTTOM, 0, 3, BlockColor.ORANGE)))
        3 -> Level(3, 5, 5, 300,
            listOf(
                b(0, BlockColor.BLUE, Shape.of("##", "#.", "#."), 1, 1),
                b(1, BlockColor.RED, Shape.I2.rotated(), 2, 2),
                b(2, BlockColor.YELLOW, Shape.of(".#", ".#", "##"), 2, 2),
            ),
            listOf(
                g(Side.TOP, 0, 2, BlockColor.BLUE), g(Side.LEFT, 2, 2, BlockColor.RED),
                g(Side.RIGHT, 2, 2, BlockColor.RED), g(Side.BOTTOM, 3, 2, BlockColor.YELLOW),
            ))
        4 -> Level(4, 5, 5, 300,
            listOf(
                b(0, BlockColor.BLUE, Shape.O2, 0, 1), b(1, BlockColor.ORANGE, Shape.I2.rotated(), 2, 2),
                b(2, BlockColor.YELLOW, Shape.O2, 3, 3),
            ),
            listOf(
                g(Side.TOP, 0, 2, BlockColor.YELLOW), g(Side.LEFT, 0, 2, BlockColor.ORANGE),
                g(Side.BOTTOM, 3, 2, BlockColor.BLUE), g(Side.RIGHT, 3, 2, BlockColor.ORANGE),
            ))
        5 -> Level(5, 5, 6, 300,
            listOf(
                b(0, BlockColor.BLUE, Shape.I3.rotated(), 2, 0), b(1, BlockColor.GREEN, Shape.O2, 0, 2),
                b(2, BlockColor.PURPLE, Shape.O2, 3, 2), b(3, BlockColor.ORANGE, Shape.I3, 1, 4),
                b(4, BlockColor.RED, Shape.I3, 1, 5),
            ),
            listOf(
                g(Side.TOP, 0, 2, BlockColor.PURPLE), g(Side.TOP, 3, 2, BlockColor.GREEN),
                g(Side.LEFT, 4, 2, BlockColor.ORANGE), g(Side.RIGHT, 4, 2, BlockColor.RED),
                g(Side.BOTTOM, 1, 3, BlockColor.BLUE),
            ))
        6 -> Level(6, 6, 6, 180,
            listOf(
                b(0, BlockColor.ORANGE, Shape.O2, 0, 0), b(1, BlockColor.DARK_GREEN, Shape.O2, 2, 0),
                b(2, BlockColor.RED, Shape.O2, 4, 0), b(3, BlockColor.DARK_GREEN, Shape.O2, 0, 2),
                b(4, BlockColor.BLUE, Shape.O2, 4, 2), b(5, BlockColor.YELLOW, Shape.O2, 2, 4),
                b(6, BlockColor.PURPLE, Shape.O2, 4, 4),
            ),
            listOf(
                g(Side.LEFT, 4, 2, BlockColor.BLUE), g(Side.LEFT, 0, 2, BlockColor.DARK_GREEN, 1),
                g(Side.TOP, 2, 2, BlockColor.ORANGE, 4), g(Side.RIGHT, 0, 2, BlockColor.PURPLE, 5),
                g(Side.RIGHT, 4, 2, BlockColor.YELLOW, 3), g(Side.BOTTOM, 2, 2, BlockColor.RED, 6),
            ),
            walls = setOf(Pair(2, 2), Pair(3, 2), Pair(2, 3), Pair(3, 3)))
        else -> null
    }
}

/**
 * Deterministic procedural level generator.
 *
 * Solvability is guaranteed by reverse construction: blocks are created in the reverse of a valid
 * exit order. Each new block is spawned right inside its gate and then slid backwards into the
 * board around the blocks created before it (which, in forward play, exit *after* it and therefore
 * are still in place). Replaying the slides forwards therefore always clears the board.
 *
 * The tuning numbers (board size, block density, shape mix, gate count, time, when each gimmick first
 * appears) follow statistics measured over the original game's first 300 progression levels
 * (see kang_work/cbj_analysis/ANALYSIS.md); no level layout is copied.
 */
object LevelGenerator {

    private class Spec(
        val cols: Int, val rows: Int, val fill: Float, val shapeWeights: List<Pair<Shape, Int>>, val colors: Int,
        val cornerCuts: Int, val holeCells: Int, val maxGates: Int, val iceGates: Int, val frozenBlocks: Int,
        val hiddenBlocks: Int, val bombs: Int, val minSlides: Int, val maxSlides: Int, val hardness: Int,
    )

    private fun spec(n: Int, rng: Random): Spec {
        // Bands follow the original's measured progression: dense big boards up to ~300, then somewhat smaller
        // boards with fewer blocks but more hard levels and gimmicks (301-700: 6x7-8x8, 17-22 blocks, 6-8 gates).
        val band = when {
            n <= 30 -> 0
            n <= 100 -> 1
            n <= 200 -> 2
            n <= 300 -> 3
            n <= 450 -> 4
            else -> 5
        }
        val (cols, rows) = when (band) {
            0 -> listOf(6 to 6, 6 to 7, 7 to 7)[rng.nextInt(3)]
            1 -> listOf(7 to 7, 6 to 8, 7 to 8, 7 to 7)[rng.nextInt(4)]
            2 -> listOf(7 to 8, 8 to 8, 7 to 9, 8 to 7)[rng.nextInt(4)]
            3 -> listOf(8 to 8, 8 to 9, 7 to 10, 9 to 9)[rng.nextInt(4)]
            4 -> listOf(8 to 8, 6 to 8, 7 to 8, 8 to 7, 9 to 9)[rng.nextInt(5)]
            else -> listOf(6 to 7, 7 to 7, 6 to 8, 7 to 8, 8 to 8)[rng.nextInt(5)]
        }
        // Measured shape mix: 1x2 48%, 1x1 30%, ShortL 7%, 1x3 7%, 2x2 3%, the rest ~1% each.
        val weights = ArrayList<Pair<Shape, Int>>()
        weights += Shape.I2 to 48; weights += Shape.I1 to 30; weights += Shape.L2 to 7; weights += Shape.I3 to 7; weights += Shape.O2 to 3
        if (band >= 1) { weights += Shape.L3 to 1; weights += Shape.T3 to 1; weights += Shape.S3 to 1 }
        if (band >= 2) { weights += Shape.U3 to 1; weights += Shape.PLUS to 1 }
        // Measured share of hard/super-hard levels: ~20% up to 300, ~25-35% for 301-500, ~40% for 501-700.
        val hardPct = when { n < 20 -> 0; band <= 3 -> 20; band == 4 -> 30; else -> 40 }
        val hardness = if (rng.nextInt(100) < hardPct) 1 + rng.nextInt(2) else 0
        val late = band >= 4
        return Spec(
            cols, rows,
            fill = (if (late) 0.64f else 0.72f) + rng.nextInt(-5, 6) / 100f + (if (hardness > 0) 0.05f else 0f),
            shapeWeights = weights,
            colors = min(BlockColor.values().size, 4 + min(band, 3) + rng.nextInt(2)),
            cornerCuts = if (n < 6) 0 else if (rng.nextInt(100) < (if (band == 0) 35 else if (late) 75 else 65)) 1 + rng.nextInt(2) else 0,
            holeCells = if (n < 6) 0 else if (rng.nextInt(100) < (if (late) 55 else 40)) 1 + rng.nextInt(if (late) 5 else 4) else 0,
            maxGates = if (late) 6 + rng.nextInt(3) else 6 + band + rng.nextInt(3),
            iceGates = if (n < 6) 0 else if (late) rng.nextInt(0, 3) else rng.nextInt(1, 3 + band),
            frozenBlocks = if (n < 10) 0 else if (late) rng.nextInt(0, 2) else rng.nextInt(0, 2 + band),
            hiddenBlocks = if (n < 240) 0 else rng.nextInt(0, 3),           // original: from level 701; previewed earlier here
            bombs = if (n < 120 || rng.nextInt(100) >= (if (late) 25 else 40)) 0 else 1,
            minSlides = 1,
            maxSlides = 2 + min(band, 3),
            hardness = hardness,
        )
    }

    fun generate(n: Int): Level {
        val rng = Random(n * 7919L + 17L)
        val spec = spec(n, rng)
        for (attempt in 0 until 400) {
            val level = tryBuild(n, spec, Random(rng.nextLong()))
            if (level != null) return level
        }
        // Extremely unlikely fallback: a trivial but valid board.
        return Level(n, 5, 5, 300,
            listOf(Block(0, BlockColor.BLUE, Shape.O2, 1f, 1f)),
            listOf(Gate(Side.TOP, 1, 2, BlockColor.BLUE)))
    }

    private class Builder(val cols: Int, val rows: Int) {
        val occ = Array(rows) { BooleanArray(cols) }
        fun free(x: Int, y: Int) = x in 0 until cols && y in 0 until rows && !occ[y][x]
        fun fits(shape: Shape, x: Int, y: Int) = shape.cells.all { (cx, cy) -> free(x + cx, y + cy) }
        fun mark(shape: Shape, x: Int, y: Int, v: Boolean) { shape.cells.forEach { (cx, cy) -> occ[y + cy][x + cx] = v } }
        fun freeCount() = occ.sumOf { row -> row.count { !it } }
    }

    private fun pickShape(s: Spec, rng: Random): Shape {
        var r = rng.nextInt(s.shapeWeights.sumOf { it.second })
        for ((shape, w) in s.shapeWeights) { r -= w; if (r < 0) return shape }
        return s.shapeWeights.last().first
    }

    /** Removes board cells: cut corners and small interior holes, the way the original uses HidedGridCoords. */
    private fun carveBoard(s: Spec, bld: Builder, rng: Random): Set<Pair<Int, Int>> {
        val walls = HashSet<Pair<Int, Int>>()
        val corners = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1).shuffled(rng).take(s.cornerCuts)
        for ((cx, cy) in corners) {
            val w = 1 + rng.nextInt(min(2, s.cols / 3)); val h = 1 + rng.nextInt(min(2, s.rows / 3))
            for (x in 0 until w) for (y in 0 until h) walls.add(Pair(if (cx == 0) x else s.cols - 1 - x, if (cy == 0) y else s.rows - 1 - y))
        }
        if (s.holeCells > 0 && s.cols > 4 && s.rows > 4) {
            var x = rng.nextInt(1, s.cols - 1); var y = rng.nextInt(1, s.rows - 1)
            walls.add(Pair(x, y))
            repeat(s.holeCells - 1) {
                if (rng.nextBoolean()) x = (x + 1).coerceAtMost(s.cols - 2) else y = (y + 1).coerceAtMost(s.rows - 2)
                walls.add(Pair(x, y))
            }
        }
        walls.forEach { (x, y) -> bld.occ[y][x] = true }
        return walls
    }

    private fun tryBuild(n: Int, s: Spec, rng: Random): Level? {
        val bld = Builder(s.cols, s.rows)
        val walls = carveBoard(s, bld, rng)
        val targetCells = (bld.freeCount() * s.fill).toInt()
        val palette = BlockColor.values().toMutableList().also { it.shuffle(rng) }.take(s.colors)
        val gates = ArrayList<Gate>()
        val blocks = ArrayList<Block>()          // construction order: index 0 exits last
        val slidesPerBlock = ArrayList<List<Triple<Int, Int, Int>>>() // (dx, dy, steps) reverse slides per block
        val exitSides = ArrayList<Side>()
        val sidesUsed = HashMap<Side, Int>()
        var usedCells = 0

        while (usedCells < targetCells) {
            var placed = false
            for (tries in 0 until 80) {
                var shape = pickShape(s, rng)
                repeat(rng.nextInt(4)) { shape = shape.rotated() }
                if (rng.nextBoolean()) shape = shape.mirrored()
                if (shape.width > s.cols - 1 || shape.height > s.rows - 1) continue
                // Prefer sides that have fewer gates so exits spread around the frame.
                val side = Side.values().sortedBy { (sidesUsed[it] ?: 0) + rng.nextInt(2) }[0]
                val extent = if (side.horizontal) shape.width else shape.height
                val wallLen = if (side.horizontal) s.cols else s.rows
                // Reuse an existing gate on this side when the block fits in it; mandatory once the gate budget is spent.
                val fitting = gates.filter { it.side == side && it.length >= extent }
                val mustReuse = gates.size >= s.maxGates
                val reuse = fitting.isNotEmpty() && (mustReuse || rng.nextInt(100) < 55)
                if (mustReuse && fitting.isEmpty()) continue
                val gate: Gate
                val color: BlockColor
                val start: Int
                var created = false
                if (reuse) {
                    gate = fitting[rng.nextInt(fitting.size)]
                    color = gate.color
                    start = gate.start + rng.nextInt(gate.length - extent + 1)
                } else {
                    val len = min(min(3, wallLen), extent + rng.nextInt(2))
                    if (len < extent) continue
                    val taken = gates.filter { it.side == side }
                    // A gate may not overlap another gate on the same side, nor sit next to a removed cell (hole / cut corner).
                    val candidates = (0..wallLen - len).filter { st ->
                        taken.none { st < it.end && it.start < st + len } &&
                            (st until st + len).none { i ->
                                val cell = when (side) {
                                    Side.TOP -> Pair(i, 0); Side.BOTTOM -> Pair(i, s.rows - 1)
                                    Side.LEFT -> Pair(0, i); Side.RIGHT -> Pair(s.cols - 1, i)
                                }
                                walls.contains(cell)
                            }
                    }
                    if (candidates.isEmpty()) continue
                    val gStart = candidates[rng.nextInt(candidates.size)]
                    color = palette[rng.nextInt(palette.size)]
                    // Two gates of the same colour on one side would be legal but confusing: avoid.
                    if (taken.any { it.color == color }) continue
                    gate = Gate(side, gStart, len, color)
                    start = gStart + rng.nextInt(len - extent + 1)
                    created = true
                }
                var x: Int
                var y: Int
                when (side) {
                    Side.TOP -> { x = start; y = 0 }
                    Side.BOTTOM -> { x = start; y = s.rows - shape.height }
                    Side.LEFT -> { x = 0; y = start }
                    Side.RIGHT -> { x = s.cols - shape.width; y = start }
                }
                // The whole bounding box must be free at the entry position: a concave shape cannot leave
                // through the gate if another block sits in its notch between it and the wall.
                if (!bld.fits(shape, x, y) || !bld.fits(Shape.rect(shape.width, shape.height), x, y)) continue
                if (created) { gates.add(gate); sidesUsed[side] = (sidesUsed[side] ?: 0) + 1 }
                // Reverse slides: walk the block away from its gate through free cells.
                val slides = rng.nextInt(s.minSlides, s.maxSlides + 1)
                var dir = when (side) { Side.TOP -> 1; Side.BOTTOM -> 3; Side.LEFT -> 2; Side.RIGHT -> 0 } // 0=left 1=down 2=right 3=up
                val record = ArrayList<Triple<Int, Int, Int>>()
                for (k in 0 until slides) {
                    if (k > 0) dir = (dir + if (rng.nextBoolean()) 1 else 3) % 4
                    val (dx, dy) = when (dir) { 0 -> -1 to 0; 1 -> 0 to 1; 2 -> 1 to 0; else -> 0 to -1 }
                    val maxSteps = 1 + rng.nextInt(max(s.cols, s.rows))
                    var steps = 0
                    while (steps < maxSteps && bld.fits(shape, x + dx, y + dy)) { x += dx; y += dy; steps++ }
                    if (steps > 0) record.add(Triple(dx, dy, steps))
                }
                bld.mark(shape, x, y, true)
                usedCells += shape.cells.size
                blocks.add(Block(blocks.size, color, shape, x.toFloat(), y.toFloat()))
                slidesPerBlock.add(record)
                exitSides.add(side)
                placed = true
                break
            }
            if (!placed) break // board is as full as it can get
        }
        if (blocks.size < 3 || gates.size < 2 || usedCells < targetCells * 0.8f) return null
        // Reject boards where most blocks only need a straight push out.
        val trivial = blocks.count { b -> gates.any { g -> g.color == b.color && alignedWith(b, g, s.cols, s.rows) } }
        if (trivial > blocks.size / 2) return null

        // Position p in the forward exit order: block i is the (count-1-i)-th to exit.
        fun exitsBefore(i: Int) = blocks.size - 1 - i
        // Ice doors: a gate may be iced up to the number of exits that happen before its first user.
        val gateFirstUse = HashMap<Gate, Int>()
        for ((i, b) in blocks.withIndex()) {
            for (g in gates) if (g.color == b.color) gateFirstUse[g] = min(gateFirstUse[g] ?: Int.MAX_VALUE, exitsBefore(i))
        }
        val iceCandidates = gates.filter { (gateFirstUse[it] ?: 0) >= 1 }.shuffled(rng).take(s.iceGates)
        for (g in iceCandidates) g.iceCount = rng.nextInt(1, gateFirstUse[g]!! + 1)
        // Frozen blocks, hidden colours, bombs.
        val idx = blocks.indices.shuffled(rng)
        var cursor = 0
        repeat(s.frozenBlocks) { if (cursor < idx.size) { val i = idx[cursor++]; if (exitsBefore(i) >= 1) blocks[i].iceLayers = rng.nextInt(1, min(6, exitsBefore(i)) + 1) } }
        repeat(s.hiddenBlocks) { if (cursor < idx.size) { val i = idx[cursor++]; if (exitsBefore(i) >= 1) blocks[i].hiddenFor = rng.nextInt(1, min(4, exitsBefore(i)) + 1) } }
        repeat(s.bombs) { if (cursor < idx.size) { val i = idx[cursor++]; blocks[i].bombSeconds = 25f + 8f * exitsBefore(i) } }

        // Original: mostly 60-200 s (about two minutes); here about three minutes on average, as requested.
        var time = 100 + 3 * blocks.size + rng.nextInt(0, 41)
        if (s.hardness > 0) time = (time * 0.85f).toInt()
        time = time.coerceIn(90, 300)

        // Shuffle ids/draw order so the construction order is not visible to the player.
        val order = blocks.indices.shuffled(rng)
        val newId = IntArray(blocks.size).also { arr -> order.forEachIndexed { newIdx, oldIdx -> arr[oldIdx] = newIdx } }
        val shuffled = order.map { oldIdx -> val b = blocks[oldIdx]; Block(newId[oldIdx], b.color, b.shape, b.x, b.y, b.iceLayers, b.hiddenFor, b.bombSeconds) }
        // Forward solution: last-constructed block first, undoing its reverse slides, then pushing it out.
        val solution = ArrayList<Move>()
        for (i in blocks.indices.reversed()) {
            for ((dx, dy, steps) in slidesPerBlock[i].asReversed()) solution.add(Move(newId[i], -dx * steps, -dy * steps))
            val (ex, ey) = when (exitSides[i]) { Side.TOP -> 0 to -1; Side.BOTTOM -> 0 to 1; Side.LEFT -> -1 to 0; Side.RIGHT -> 1 to 0 }
            solution.add(Move(newId[i], ex * (blocks[i].width + 1), ey * (blocks[i].height + 1), exit = true))
        }
        return Level(n, s.cols, s.rows, time, shuffled, gates, walls, solution, s.hardness)
    }

    private fun alignedWith(b: Block, g: Gate, cols: Int, rows: Int): Boolean {
        val x = b.x.toInt(); val y = b.y.toInt()
        return when (g.side) {
            Side.TOP -> y == 0 && x >= g.start && x + b.width <= g.end
            Side.BOTTOM -> y + b.height == rows && x >= g.start && x + b.width <= g.end
            Side.LEFT -> x == 0 && y >= g.start && y + b.height <= g.end
            Side.RIGHT -> x + b.width == cols && y >= g.start && y + b.height <= g.end
        }
    }
}
