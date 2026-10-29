package com.ensar.oyun.game

import kotlin.random.Random

/** Hamlenin başladığı yer: bardaki taş. */
const val BAR = 24

/** Hamlenin bittiği yer: tahtadan çıkarma. */
const val OFF = -1

/**
 * Taş hamlesi. from/to mutlak tahta indeksi (0..23), ya da BAR / OFF.
 * die: bu hamlede kullanılan zar.
 */
data class TavlaMove(val from: Int, val to: Int, val die: Int)

/**
 * Tavla durumu (2 oyuncu, klasik 24 noktalı tahta, 15'er taş).
 * points: mutlak indeks 0..23. Pozitif sayı = koltuk 0 (beyaz) taşı, negatif = koltuk 1 (siyah).
 * Koltuk 0 yüksek indeksten 0'a doğru gider (evi 0..5), koltuk 1 0'dan 23'e gider (evi 18..23).
 */
data class TavlaState(
    val points: List<Int> = TavlaLogic.startPoints(),
    val bar: List<Int> = listOf(0, 0),
    val off: List<Int> = listOf(0, 0),
    val turn: Int = 0,
    /** Henüz oynanmamış zarlar (çift gelirse 4 tane). */
    val dice: List<Int> = emptyList(),
    /** Bu elde atılan zarlar (sadece gösterim için). */
    val rolled: List<Int> = emptyList(),
    val winner: Int = -1,
    val round: Int = 0,
    /** Önceki oyuncu (1 - turn) kalan zarı oynayamadı. */
    val skipped: Boolean = false,
)

object TavlaLogic {

    fun startPoints(): List<Int> {
        val p = MutableList(24) { 0 }
        p[23] = 2; p[12] = 5; p[7] = 3; p[5] = 5
        p[0] = -2; p[11] = -5; p[16] = -3; p[18] = -5
        return p
    }

    fun initial(round: Int = 0): TavlaState = TavlaState(turn = round % 2, round = round)

    /** Göreli indeks <-> mutlak indeks. Koltuk 0 için aynı, koltuk 1 için 23 - x (kendi tersi). */
    fun absOf(p: Int, rel: Int): Int = if (p == 0) rel else 23 - rel

    /** p oyuncusunun göreli bakışında rel noktasındaki taş: kendi taşı pozitif, rakip taşı negatif. */
    private fun own(s: TavlaState, p: Int, rel: Int): Int {
        val v = s.points[absOf(p, rel)]
        return if (p == 0) v else -v
    }

    fun roll(s: TavlaState): TavlaState {
        val a = Random.nextInt(1, 7)
        val b = Random.nextInt(1, 7)
        return s.copy(
            dice = if (a == b) listOf(a, a, a, a) else listOf(a, b),
            rolled = listOf(a, b),
            skipped = false,
        )
    }

    /** Tek bir zar için olabilecek hamleler (zorunlu-hamle kuralı henüz uygulanmadan). */
    fun singleMoves(s: TavlaState, p: Int, die: Int): List<TavlaMove> {
        val res = ArrayList<TavlaMove>()
        if (s.bar[p] > 0) {
            val rel = 24 - die
            if (own(s, p, rel) >= -1) res += TavlaMove(BAR, absOf(p, rel), die)
            return res
        }
        val allHome = (6..23).none { own(s, p, it) > 0 }
        for (rel in 0..23) {
            if (own(s, p, rel) <= 0) continue
            val t = rel - die
            if (t >= 0) {
                if (own(s, p, t) >= -1) res += TavlaMove(absOf(p, rel), absOf(p, t), die)
            } else if (allHome) {
                val exact = t == -1
                val nothingFarther = (rel + 1..23).none { own(s, p, it) > 0 }
                if (exact || nothingFarther) res += TavlaMove(absOf(p, rel), OFF, die)
            }
        }
        return res
    }

    /** Hamleyi uygular: taşı oynar, rakip tek taşı varsa vurur, kullanılan zarı düşer. */
    fun apply(s: TavlaState, p: Int, m: TavlaMove): TavlaState {
        val pts = s.points.toMutableList()
        val bar = s.bar.toMutableList()
        val off = s.off.toMutableList()
        val sign = if (p == 0) 1 else -1
        if (m.from == BAR) bar[p]-- else pts[m.from] -= sign
        if (m.to == OFF) {
            off[p]++
        } else {
            if (pts[m.to] * sign < 0) {
                pts[m.to] = 0
                bar[1 - p]++
            }
            pts[m.to] += sign
        }
        val d = s.dice.toMutableList()
        d.remove(m.die)
        return s.copy(
            points = pts,
            bar = bar,
            off = off,
            dice = d,
            winner = if (off[p] == 15) p else s.winner,
        )
    }

    private fun maxDice(s: TavlaState, p: Int): Int {
        if (s.dice.isEmpty() || s.winner >= 0) return 0
        var best = 0
        for (d in s.dice.distinct()) {
            for (m in singleMoves(s, p, d)) {
                val ns = apply(s, p, m)
                val v = 1 + maxDice(ns, p)
                if (v > best) {
                    best = v
                    if (best == s.dice.size) return best
                }
            }
        }
        return best
    }

    /**
     * Şu an oynanabilecek hamleler. Kurallar: mümkün olduğunca çok zar oynanmalı;
     * sadece bir zar oynanabiliyorsa ve ikisi de tek başına oynanabiliyorsa büyük zar oynanmalı.
     */
    fun legalMoves(s: TavlaState, p: Int): List<TavlaMove> {
        if (s.dice.isEmpty() || s.winner >= 0) return emptyList()
        val max = maxDice(s, p)
        if (max == 0) return emptyList()
        val cands = ArrayList<TavlaMove>()
        for (d in s.dice.distinct()) {
            for (m in singleMoves(s, p, d)) {
                val ns = apply(s, p, m)
                if (1 + maxDice(ns, p) == max) cands += m
            }
        }
        if (max == 1 && s.dice.distinct().size == 2) {
            val big = s.dice.max()
            val bigs = cands.filter { it.die == big }
            if (bigs.isNotEmpty()) return bigs
        }
        return cands
    }

    /** Zar atıldıktan veya hamle yapıldıktan sonra: oynanacak hamle kalmadıysa sırayı geçir. */
    fun settle(s: TavlaState): TavlaState {
        if (s.winner >= 0) return s.copy(dice = emptyList())
        if (s.dice.isNotEmpty() && legalMoves(s, s.turn).isNotEmpty()) return s
        return s.copy(turn = 1 - s.turn, skipped = s.dice.isNotEmpty(), dice = emptyList())
    }

    /** Bot: basit puanlama. Vurmayı, taş toplamayı ve güvenli (2+ taşlı) noktaları sever. */
    fun botMove(s: TavlaState, p: Int): TavlaMove? {
        val moves = legalMoves(s, p)
        if (moves.isEmpty()) return null
        fun score(m: TavlaMove): Double {
            var sc = Random.nextDouble() * 3.0 + m.die * 0.5
            if (m.to == OFF) return sc + 20.0
            val tr = absOf(p, m.to)
            val before = own(s, p, tr)
            if (before == -1) sc += 30.0
            if (before >= 1) sc += 8.0
            if (before == 0) sc -= if (tr >= 6) 4.0 else 1.0
            if (m.from != BAR) {
                val fr = absOf(p, m.from)
                if (own(s, p, fr) == 2) sc -= 5.0
            }
            return sc
        }
        return moves.maxByOrNull { score(it) }
    }
}
