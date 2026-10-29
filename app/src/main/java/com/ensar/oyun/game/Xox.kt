package com.ensar.oyun.game

import kotlin.random.Random

/**
 * XOX durumu. Koltuk 0 = X, koltuk 1 = O.
 * board: 9 karakter, '.' boş, 'X' veya 'O' dolu.
 * turn: sırası gelen koltuk. result: "" (devam), "X", "O" veya "DRAW".
 */
data class XoxState(
    val board: String = EMPTY,
    val turn: Int = 0,
    val result: String = "",
    val round: Int = 0,
) {
    companion object {
        const val EMPTY = "........."
    }
}

object XoxLogic {

    private val lines = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6),
    )

    fun symbol(seat: Int): Char = if (seat == 0) 'X' else 'O'

    private fun other(c: Char): Char = if (c == 'X') 'O' else 'X'

    private fun put(board: String, i: Int, c: Char): String =
        board.substring(0, i) + c + board.substring(i + 1)

    fun winningLine(board: String): List<Int>? =
        lines.firstOrNull { l ->
            val c = board[l[0]]
            c != '.' && c == board[l[1]] && c == board[l[2]]
        }

    fun result(board: String): String {
        val line = winningLine(board)
        if (line != null) return board[line[0]].toString()
        return if ('.' in board) "" else "DRAW"
    }

    /** Geçerli hamleyse yeni durumu, değilse null döner. */
    fun apply(s: XoxState, seat: Int, cell: Int): XoxState? {
        if (s.result.isNotEmpty() || s.turn != seat) return null
        if (cell !in 0..8 || s.board[cell] != '.') return null
        val nb = put(s.board, cell, symbol(seat))
        val r = result(nb)
        return s.copy(board = nb, result = r, turn = if (r.isEmpty()) 1 - seat else s.turn)
    }

    /** Bot: çoğu zaman en iyi hamle, ara sıra rastgele (yenilebilsin diye). */
    fun botMove(board: String, seat: Int): Int {
        val empty = board.indices.filter { board[it] == '.' }
        if (empty.isEmpty()) return -1
        if (Random.nextFloat() < 0.2f) return empty.random()
        val me = symbol(seat)
        val scored = empty.map { it to minimax(put(board, it, me), other(me), me, 1) }
        val best = scored.maxOf { it.second }
        return scored.filter { it.second == best }.random().first
    }

    private fun minimax(board: String, toMove: Char, me: Char, depth: Int): Int {
        val r = result(board)
        if (r == "DRAW") return 0
        if (r.isNotEmpty()) return if (r[0] == me) 10 - depth else depth - 10
        val scores = board.indices.filter { board[it] == '.' }
            .map { minimax(put(board, it, toMove), other(toMove), me, depth + 1) }
        return if (toMove == me) scores.max() else scores.min()
    }
}
