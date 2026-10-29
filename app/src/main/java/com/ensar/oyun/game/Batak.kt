package com.ensar.oyun.game

/** Kart: 0..51. suit = c / 13 (0 maça, 1 kupa, 2 karo, 3 sinek), rank = c % 13 (0 = 2 ... 12 = As). */
object Cards {
    val SUIT_SYMBOLS = listOf("♠", "♥", "♦", "♣")
    val SUIT_NAMES = listOf("Maça", "Kupa", "Karo", "Sinek")
    private val RANK_LABELS = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A")

    fun suit(c: Int): Int = c / 13
    fun rank(c: Int): Int = c % 13
    fun label(c: Int): String = RANK_LABELS[rank(c)] + SUIT_SYMBOLS[suit(c)]
    fun isRed(c: Int): Boolean = suit(c) == 1 || suit(c) == 2
    fun sortHand(h: List<Int>): List<Int> = h.sortedWith(compareBy({ suit(it) }, { -rank(it) }))
}

object BatakPhase {
    const val BID = 0
    const val TRUMP = 1
    const val PLAY = 2
    const val DONE = 3
}

/**
 * Herkesin görebildiği Batak durumu. Eller (kartlar) burada YOK, sadece kurucu telefonda durur
 * ve her oyuncuya yalnızca kendi eli gönderilir.
 */
data class BatakState(
    val phase: Int = BatakPhase.BID,
    val dealer: Int = 0,
    /** Sırası gelen koltuk (ihale ve oynama sırasında). */
    val turn: Int = 1,
    /** İhale: -1 henüz konuşmadı, 0 pas, 5..13 teklif. */
    val bids: List<Int> = listOf(-1, -1, -1, -1),
    val highBid: Int = 0,
    val highSeat: Int = -1,
    val trump: Int = -1,
    /** Masadaki el: koltuk başına oynanan kart, -1 = henüz yok. */
    val trick: List<Int> = listOf(-1, -1, -1, -1),
    /** Bu eli açan koltuk. */
    val leader: Int = 1,
    val tricksWon: List<Int> = listOf(0, 0, 0, 0),
    val scores: List<Int> = listOf(0, 0, 0, 0),
    val handCounts: List<Int> = listOf(13, 13, 13, 13),
    /** Dördüncü kart oynandı, el kısa süre masada görünüp toplanacak. */
    val resolving: Boolean = false,
    /** Biten elin puan değişimi (özet için). */
    val lastDelta: List<Int> = listOf(0, 0, 0, 0),
)

object BatakLogic {

    fun deal(): List<List<Int>> {
        val deck = (0 until 52).shuffled()
        return List(4) { seat -> deck.subList(seat * 13, seat * 13 + 13).toList() }
    }

    fun newHand(prev: BatakState, dealer: Int): BatakState {
        val first = (dealer + 1) % 4
        return BatakState(dealer = dealer, turn = first, leader = first, scores = prev.scores)
    }

    // ------------------------------------------------------------ İhale

    fun isForcedBid(s: BatakState, seat: Int): Boolean = seat == s.dealer && s.highSeat < 0

    fun canBid(s: BatakState, seat: Int, v: Int): Boolean {
        if (s.phase != BatakPhase.BID || s.turn != seat || s.bids[seat] >= 0) return false
        if (v == 0) return !isForcedBid(s, seat)
        return v in 5..13 && v > s.highBid
    }

    fun applyBid(s: BatakState, seat: Int, v: Int): BatakState {
        val bids = s.bids.toMutableList().also { it[seat] = v }
        val highBid = if (v > 0) v else s.highBid
        val highSeat = if (v > 0) seat else s.highSeat
        val spoken = bids.count { it >= 0 }
        return if (spoken == 4) {
            s.copy(bids = bids, highBid = highBid, highSeat = highSeat, phase = BatakPhase.TRUMP, turn = highSeat)
        } else {
            s.copy(bids = bids, highBid = highBid, highSeat = highSeat, turn = (seat + 1) % 4)
        }
    }

    // ------------------------------------------------------------ Koz ve oynama

    fun applyTrump(s: BatakState, suit: Int): BatakState =
        s.copy(
            phase = BatakPhase.PLAY,
            trump = suit,
            leader = s.highSeat,
            turn = s.highSeat,
            trick = listOf(-1, -1, -1, -1),
        )

    /** Şu ana kadar oynanan kartlar arasında eli alan koltuk. */
    fun currentWinner(trick: List<Int>, trump: Int, leader: Int): Int {
        var best = leader
        for (i in 1..3) {
            val seat = (leader + i) % 4
            val c = trick[seat]
            if (c < 0) continue
            val b = trick[best]
            val cs = Cards.suit(c)
            val bs = Cards.suit(b)
            val beats = when {
                cs == bs -> Cards.rank(c) > Cards.rank(b)
                cs == trump -> true
                else -> false
            }
            if (beats) best = seat
        }
        return best
    }

    /**
     * Oynanabilecek kartlar: aynı renk zorunlu ve mümkünse yükseltmek zorunlu; rengi yoksa
     * koz atmak zorunlu (masada koz varsa mümkünse üstüne koz); yoksa serbest.
     */
    fun legalCards(hand: List<Int>, trick: List<Int>, trump: Int, leader: Int): List<Int> {
        if (trick[leader] < 0) return hand
        val led = Cards.suit(trick[leader])
        val w = trick[currentWinner(trick, trump, leader)]
        val follow = hand.filter { Cards.suit(it) == led }
        if (follow.isNotEmpty()) {
            if (Cards.suit(w) == led) {
                val higher = follow.filter { Cards.rank(it) > Cards.rank(w) }
                return if (higher.isNotEmpty()) higher else follow
            }
            return follow
        }
        val tr = hand.filter { Cards.suit(it) == trump }
        if (tr.isNotEmpty()) {
            if (Cards.suit(w) == trump) {
                val higher = tr.filter { Cards.rank(it) > Cards.rank(w) }
                return if (higher.isNotEmpty()) higher else hand
            }
            return tr
        }
        return hand
    }

    fun playCard(s: BatakState, seat: Int, card: Int): BatakState {
        val trick = s.trick.toMutableList().also { it[seat] = card }
        val counts = s.handCounts.toMutableList().also { it[seat]-- }
        return if (trick.all { it >= 0 }) {
            s.copy(
                trick = trick,
                handCounts = counts,
                resolving = true,
                turn = currentWinner(trick, s.trump, s.leader),
            )
        } else {
            s.copy(trick = trick, handCounts = counts, turn = (seat + 1) % 4)
        }
    }

    /** Dolu eli topla: kazanana yaz; el bittiyse puanla. */
    fun resolveTrick(s: BatakState): BatakState {
        val winner = s.turn
        val won = s.tricksWon.toMutableList().also { it[winner]++ }
        if (s.handCounts.all { it == 0 }) return finishHand(s.copy(tricksWon = won, resolving = false))
        return s.copy(
            tricksWon = won,
            trick = listOf(-1, -1, -1, -1),
            leader = winner,
            turn = winner,
            resolving = false,
        )
    }

    /** İhaleyi alan: yaptıysa +teklif, yapamadıysa -teklif. Diğerleri aldıkları el kadar puan alır. */
    fun finishHand(s: BatakState): BatakState {
        val delta = List(4) { seat ->
            val won = s.tricksWon[seat]
            if (seat == s.highSeat) (if (won >= s.highBid) s.highBid else -s.highBid) else won
        }
        return s.copy(
            phase = BatakPhase.DONE,
            scores = List(4) { s.scores[it] + delta[it] },
            lastDelta = delta,
        )
    }

    // ------------------------------------------------------------ Bot

    private fun estimateTricks(hand: List<Int>): Int {
        var est = 0.0
        for (suit in 0..3) {
            val cs = hand.filter { Cards.suit(it) == suit }.map { Cards.rank(it) }
            if (12 in cs) est += 1.0
            if (11 in cs) est += if (cs.size >= 2) 0.8 else 0.3
            if (10 in cs) est += if (cs.size >= 3) 0.4 else 0.1
            if (cs.size > 4) est += (cs.size - 4) * 0.7
        }
        return est.toInt()
    }

    fun botBid(s: BatakState, hand: List<Int>, seat: Int): Int {
        val min = maxOf(5, s.highBid + 1)
        if (estimateTricks(hand) >= min) return min
        return if (isForcedBid(s, seat)) 5 else 0
    }

    fun botTrump(hand: List<Int>): Int =
        (0..3).maxByOrNull { suit ->
            val cs = hand.filter { Cards.suit(it) == suit }.map { Cards.rank(it) }
            cs.size * 3 + (if (12 in cs) 2 else 0) + (if (11 in cs) 1 else 0)
        } ?: 0

    fun botCard(s: BatakState, hand: List<Int>, seat: Int): Int {
        val legal = legalCards(hand, s.trick, s.trump, s.leader)
        if (legal.size == 1) return legal[0]
        if (s.trick[s.leader] < 0) {
            val strong = legal.filter { Cards.suit(it) != s.trump && Cards.rank(it) >= 11 }
            if (strong.isNotEmpty()) return strong.maxBy { Cards.rank(it) }
            val plain = legal.filter { Cards.suit(it) != s.trump }
            return (plain.ifEmpty { legal }).minBy { Cards.rank(it) }
        }
        val winners = legal.filter { c ->
            val t = s.trick.toMutableList().also { it[seat] = c }
            currentWinner(t, s.trump, s.leader) == seat
        }
        if (winners.isNotEmpty()) return winners.minBy { Cards.rank(it) }
        return legal.minBy { (if (Cards.suit(it) == s.trump) 100 else 0) + Cards.rank(it) }
    }
}
