package com.kang.colorblockjam

import com.kang.colorblockjam.model.Levels
import org.junit.Test

/** Diagnostic: aggregate statistics of the generated levels, for comparison with the measured original-game numbers. */
class GeneratorStatsTest {
    @Test
    fun stats() {
        val bands = listOf(1..30, 31..100, 101..200, 201..300, 301..450, 451..700)
        for (band in bands) {
            var blocks = 0; var gates = 0; var time = 0; var walls = 0; var cells = 0; var area = 0; var hard = 0; var ice = 0; var frozen = 0; var bombs = 0; var hidden = 0
            val shapes = HashMap<String, Int>(); val grids = HashMap<String, Int>()
            for (n in band) {
                val l = Levels.get(n)
                blocks += l.blocks.size; gates += l.gates.size; time += l.timeSeconds; walls += l.walls.size
                cells += l.blocks.sumOf { it.shape.cells.size }; area += l.cols * l.rows - l.walls.size
                if (l.hardness > 0) hard++
                ice += l.gates.count { it.iceCount > 0 }; frozen += l.blocks.count { it.iceLayers > 0 }
                bombs += l.blocks.count { it.bombSeconds >= 0f }; hidden += l.blocks.count { it.hiddenFor > 0 }
                for (b in l.blocks) { val k = "${b.shape.cells.size}c"; shapes[k] = (shapes[k] ?: 0) + 1 }
                val g = "${l.cols}x${l.rows}"; grids[g] = (grids[g] ?: 0) + 1
            }
            val cnt = band.count()
            println("levels ${band.first}-${band.last}: blocks/level=${"%.1f".format(blocks.toFloat() / cnt)} gates/level=${"%.1f".format(gates.toFloat() / cnt)} " +
                "fill=${"%.0f".format(100f * cells / area)}% time=${time / cnt}s walls/level=${"%.1f".format(walls.toFloat() / cnt)} hard=$hard " +
                "iceGates=${"%.1f".format(ice.toFloat() / cnt)} frozen=${"%.1f".format(frozen.toFloat() / cnt)} bombs=$bombs hidden=$hidden")
            println("   shapes by cell count: ${shapes.toSortedMap()}  grids: ${grids.toSortedMap()}")
        }
    }
}
