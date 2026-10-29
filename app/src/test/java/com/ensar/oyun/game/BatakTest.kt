package com.ensar.oyun.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatakTest {

    // kart numarası: suit * 13 + rank (0 = 2 ... 12 = As)
    private fun c(suit: Int, rank: Int) = suit * 13 + rank

    @Test
    fun kozEliAlir() {
        // koz maça(0). Kupa As, Kupa K, Maça 2, Kupa Q -> koz atan 2. koltuk kazanır
        val trick = listOf(c(1, 12), c(1, 11), c(0, 0), c(1, 10))
        assertEquals(2, BatakLogic.currentWinner(trick, 0, 0))
    }

    @Test
    fun ayniRenkteYukseltmekZorunlu() {
        val hand = listOf(c(1, 0), c(1, 12))
        val trick = listOf(c(1, 11), -1, -1, -1)
        assertEquals(listOf(c(1, 12)), BatakLogic.legalCards(hand, trick, 0, 0))
    }

    @Test
    fun yukseltemezseAyniRenkHerhangi() {
        val hand = listOf(c(1, 0), c(1, 3), c(2, 5))
        val trick = listOf(c(1, 11), -1, -1, -1)
        assertEquals(listOf(c(1, 0), c(1, 3)), BatakLogic.legalCards(hand, trick, 0, 0))
    }

    @Test
    fun rengiYoksaKozZorunlu() {
        val hand = listOf(c(0, 4), c(2, 5), c(3, 9))
        val trick = listOf(c(1, 11), -1, -1, -1)
        assertEquals(listOf(c(0, 4)), BatakLogic.legalCards(hand, trick, 0, 0))
    }

    @Test
    fun ihaleSirasiVeZorunluBes() {
        var s = BatakState(dealer = 0, turn = 1)
        // koltuk 1, 2, 3 pas der
        for (seat in listOf(1, 2, 3)) {
            assertTrue(BatakLogic.canBid(s, seat, 0))
            s = BatakLogic.applyBid(s, seat, 0)
        }
        // dağıtıcı (0) pas diyemez
        assertEquals(false, BatakLogic.canBid(s, 0, 0))
        assertTrue(BatakLogic.canBid(s, 0, 5))
        s = BatakLogic.applyBid(s, 0, 5)
        assertEquals(BatakPhase.TRUMP, s.phase)
        assertEquals(0, s.highSeat)
    }

    @Test
    fun puanlama() {
        val s = BatakState(
            phase = BatakPhase.PLAY, highSeat = 2, highBid = 6,
            tricksWon = listOf(3, 2, 6, 2),
        )
        val d = BatakLogic.finishHand(s)
        assertEquals(listOf(3, 2, 6, 2), d.lastDelta)
        val fail = BatakLogic.finishHand(s.copy(tricksWon = listOf(4, 3, 4, 2)))
        assertEquals(listOf(4, 3, -6, 2), fail.lastDelta)
    }

    @Test
    fun dagitim() {
        val h = BatakLogic.deal()
        assertEquals(4, h.size)
        assertTrue(h.all { it.size == 13 })
        assertEquals(52, h.flatten().toSet().size)
    }
}
