package com.ensar.oyun.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TavlaTest {

    private fun empty(): MutableList<Int> = MutableList(24) { 0 }

    @Test
    fun baslangicHamleleri() {
        val s = TavlaState(dice = listOf(3, 1))
        val m = TavlaLogic.legalMoves(s, 0)
        assertTrue(m.any { it.from == 7 && it.to == 4 && it.die == 3 })
        assertTrue(m.any { it.from == 5 && it.to == 4 && it.die == 1 })
    }

    @Test
    fun kapaliNoktayaGidilmez() {
        val s = TavlaState(dice = listOf(5, 2))
        val m = TavlaLogic.legalMoves(s, 0)
        assertFalse(m.any { it.from == 23 && it.to == 18 })
    }

    @Test
    fun bardanGiris() {
        val s = TavlaState(bar = listOf(1, 0), dice = listOf(6, 3))
        val m = TavlaLogic.legalMoves(s, 0)
        assertTrue(m.isNotEmpty())
        assertTrue(m.all { it.from == BAR && it.to == 21 })
    }

    @Test
    fun tekTasVurulur() {
        val p = empty()
        p[5] = 1
        p[3] = -1
        p[22] = 14
        p[23] = -14
        val s = TavlaState(points = p, dice = listOf(2))
        val m = TavlaLogic.legalMoves(s, 0).first { it.from == 5 && it.to == 3 }
        val n = TavlaLogic.apply(s, 0, m)
        assertEquals(1, n.points[3])
        assertEquals(1, n.bar[1])
    }

    @Test
    fun tasToplama() {
        val p = empty()
        p[0] = 2
        p[5] = 1
        val s = TavlaState(points = p, off = listOf(12, 0), dice = listOf(6))
        val m = TavlaLogic.legalMoves(s, 0)
        assertEquals(listOf(TavlaMove(5, OFF, 6)), m)
    }

    @Test
    fun sonTasCikincaKazanir() {
        val p = empty()
        p[0] = 1
        val s = TavlaState(points = p, off = listOf(14, 0), dice = listOf(1))
        val m = TavlaLogic.legalMoves(s, 0).single()
        val n = TavlaLogic.apply(s, 0, m)
        assertEquals(0, n.winner)
    }

    @Test
    fun hamleYoksaSiraGecer() {
        // Koltuk 0'ın tek taşı bar'da, giriş noktaları kapalı
        val p = empty()
        p[18] = -2
        p[19] = -2
        val s = TavlaState(points = p, bar = listOf(1, 0), turn = 0, dice = listOf(6, 5))
        val n = TavlaLogic.settle(s)
        assertEquals(1, n.turn)
        assertTrue(n.skipped)
        assertTrue(n.dice.isEmpty())
    }
}
