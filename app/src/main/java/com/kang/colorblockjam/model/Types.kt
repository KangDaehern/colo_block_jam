package com.kang.colorblockjam.model

/** Block / gate colours. The ARGB values are our own palette, not taken from any other game. */
enum class BlockColor(val argb: Int, val dark: Int) {
    RED(0xFFE53935.toInt(), 0xFFB71C1C.toInt()),
    BLUE(0xFF1E88E5.toInt(), 0xFF0D47A1.toInt()),
    GREEN(0xFF43A047.toInt(), 0xFF1B5E20.toInt()),
    YELLOW(0xFFFDD835.toInt(), 0xFFF9A825.toInt()),
    ORANGE(0xFFFB8C00.toInt(), 0xFFE65100.toInt()),
    PURPLE(0xFF8E24AA.toInt(), 0xFF4A148C.toInt()),
    CYAN(0xFF00ACC1.toInt(), 0xFF006064.toInt()),
    PINK(0xFFEC407A.toInt(), 0xFF880E4F.toInt()),
    DARK_GREEN(0xFF1B5E20.toInt(), 0xFF052E0A.toInt()),
    BROWN(0xFF8D6E63.toInt(), 0xFF4E342E.toInt());
}

/** Which wall of the board a gate sits on. */
enum class Side { TOP, BOTTOM, LEFT, RIGHT;
    val horizontal: Boolean get() = this == TOP || this == BOTTOM
}

/** A polyomino given as relative cell offsets; the bounding box starts at (0,0). */
class Shape(val cells: List<Pair<Int, Int>>) {
    val width: Int = cells.maxOf { it.first } + 1
    val height: Int = cells.maxOf { it.second } + 1

    fun contains(cx: Int, cy: Int): Boolean = cells.any { it.first == cx && it.second == cy }

    /** Rotated 90 degrees clockwise and re-normalised to (0,0). */
    fun rotated(): Shape {
        val h = height
        return Shape(cells.map { (x, y) -> Pair(h - 1 - y, x) }.sortedWith(compareBy({ it.second }, { it.first })))
    }

    /** Mirrored horizontally and re-normalised. */
    fun mirrored(): Shape {
        val w = width
        return Shape(cells.map { (x, y) -> Pair(w - 1 - x, y) }.sortedWith(compareBy({ it.second }, { it.first })))
    }

    override fun equals(other: Any?): Boolean = other is Shape && other.cells.toSet() == cells.toSet()
    override fun hashCode(): Int = cells.toSet().hashCode()

    companion object {
        fun rect(w: Int, h: Int): Shape = Shape((0 until h).flatMap { y -> (0 until w).map { x -> Pair(x, y) } })

        /** Parses rows of '#' and '.' into a shape, e.g. listOf("#.", "#.", "##"). */
        fun of(vararg rows: String): Shape {
            val cells = ArrayList<Pair<Int, Int>>()
            rows.forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c == '#') cells.add(Pair(x, y)) } }
            return Shape(cells)
        }

        val I1 = rect(1, 1)
        val I2 = rect(2, 1)
        val I3 = rect(3, 1)
        val I4 = rect(4, 1)
        val O2 = rect(2, 2)
        val R23 = rect(3, 2)
        val L3 = of("#.", "#.", "##")      // 2 wide, 3 tall L
        val L2 = of("#.", "##")            // small corner
        val T3 = of("###", ".#.")
        val S3 = of(".##", "##.")
        val PLUS = of(".#.", "###", ".#.")
        val U3 = of("#.#", "###")
    }
}

/** A block on the board. Position is in cell units; fractional while being dragged. */
class Block(
    val id: Int,
    val color: BlockColor,
    val shape: Shape,
    var x: Float,
    var y: Float,
    /** Frozen in ice: cannot be moved until this many other blocks have exited. */
    var iceLayers: Int = 0,
    /** Colour is hidden (drawn grey with '?') until this many blocks have exited. */
    var hiddenFor: Int = 0,
    /** Timer bomb: remaining seconds before the level fails. Negative = no bomb. */
    var bombSeconds: Float = -1f,
) {
    val width: Int get() = shape.width
    val height: Int get() = shape.height
    var exited: Boolean = false

    val frozen: Boolean get() = iceLayers > 0
    val hidden: Boolean get() = hiddenFor > 0

    fun copy(): Block = Block(id, color, shape, x, y, iceLayers, hiddenFor, bombSeconds).also { it.exited = exited }
}

/** A coloured exit on one wall. [start] is the first covered row/column, [length] how many it covers. */
class Gate(
    val side: Side,
    val start: Int,
    val length: Int,
    val color: BlockColor,
    /** Ice door: opens (and reveals its colour) after this many blocks have exited anywhere. */
    var iceCount: Int = 0,
) {
    val end: Int get() = start + length
    val open: Boolean get() = iceCount <= 0
    fun copy(): Gate = Gate(side, start, length, color, iceCount)
}

/** One step of a known solution: slide block [blockId] by ([dx],[dy]) cells; [exit] marks the final push through the gate. */
class Move(val blockId: Int, val dx: Int, val dy: Int, val exit: Boolean = false)

/** Immutable level definition. */
class Level(
    val number: Int,
    val cols: Int,
    val rows: Int,
    val timeSeconds: Int,
    val blocks: List<Block>,
    val gates: List<Gate>,
    /** Static obstacle cells. */
    val walls: Set<Pair<Int, Int>> = emptySet(),
    /** Solution recorded by the generator (empty for hand-made levels). Used by tests and could drive a hint feature. */
    val solution: List<Move> = emptyList(),
    /** 0 = normal, 1 = hard, 2 = super hard (shown as a badge; the original flags ~20% of levels this way). */
    val hardness: Int = 0,
) {
    /** Deep copy so that a running game can mutate its own blocks and gates. */
    fun freshBlocks(): MutableList<Block> = blocks.map { it.copy() }.toMutableList()
    fun freshGates(): MutableList<Gate> = gates.map { it.copy() }.toMutableList()
}
