package com.ensar.oyun.lobby

import com.ensar.oyun.game.BatakState
import com.ensar.oyun.game.TavlaState
import com.ensar.oyun.game.XoxState
import org.json.JSONArray
import org.json.JSONObject

enum class Game(val title: String, val seats: Int) {
    XOX("XOX", 2),
    TAVLA("Tavla", 2),
    BATAK("Batak", 4),
}

enum class SeatKind { EMPTY, HUMAN, BOT }

/** endpointId sadece kurucu telefonda dolu; ağa gönderilmez. */
data class Seat(
    val kind: SeatKind = SeatKind.EMPTY,
    val name: String = "",
    val endpointId: String? = null,
)

/** Telefonlar arası taşınan mesajların JSON biçimi. */
object Wire {
    const val LOBBY = "LOBBY"
    const val START = "START"
    const val CHAT = "CHAT"
    const val XOX_STATE = "XOX_STATE"
    const val XOX_MOVE = "XOX_MOVE"

    fun xoxState(s: XoxState): String =
        JSONObject().put("t", XOX_STATE).put("board", s.board).put("turn", s.turn)
            .put("result", s.result).put("round", s.round).toString()

    fun xoxMove(cell: Int): String =
        JSONObject().put("t", XOX_MOVE).put("cell", cell).toString()

    fun parseXox(j: JSONObject): XoxState =
        XoxState(j.getString("board"), j.getInt("turn"), j.getString("result"), j.getInt("round"))

    // ---------------------------------------------------------------- Tavla

    const val TAVLA_STATE = "TAVLA_STATE"
    const val TAVLA_ROLL = "TAVLA_ROLL"
    const val TAVLA_MOVE = "TAVLA_MOVE"

    private fun ints(a: JSONArray): List<Int> = List(a.length()) { a.getInt(it) }

    fun tavlaState(s: TavlaState): String =
        JSONObject().put("t", TAVLA_STATE)
            .put("p", JSONArray(s.points)).put("bar", JSONArray(s.bar)).put("off", JSONArray(s.off))
            .put("turn", s.turn).put("dice", JSONArray(s.dice)).put("rolled", JSONArray(s.rolled))
            .put("w", s.winner).put("round", s.round).put("skip", s.skipped)
            .toString()

    fun parseTavla(j: JSONObject): TavlaState =
        TavlaState(
            points = ints(j.getJSONArray("p")),
            bar = ints(j.getJSONArray("bar")),
            off = ints(j.getJSONArray("off")),
            turn = j.getInt("turn"),
            dice = ints(j.getJSONArray("dice")),
            rolled = ints(j.getJSONArray("rolled")),
            winner = j.getInt("w"),
            round = j.getInt("round"),
            skipped = j.getBoolean("skip"),
        )

    fun tavlaRoll(): String = JSONObject().put("t", TAVLA_ROLL).toString()

    fun tavlaMove(from: Int, to: Int): String =
        JSONObject().put("t", TAVLA_MOVE).put("from", from).put("to", to).toString()

    // ---------------------------------------------------------------- Batak

    const val BATAK_STATE = "BATAK_STATE"
    const val BATAK_HAND = "BATAK_HAND"
    const val BATAK_BID = "BATAK_BID"
    const val BATAK_TRUMP = "BATAK_TRUMP"
    const val BATAK_PLAY = "BATAK_PLAY"

    fun batakState(s: BatakState): String =
        JSONObject().put("t", BATAK_STATE)
            .put("phase", s.phase).put("dealer", s.dealer).put("turn", s.turn)
            .put("bids", JSONArray(s.bids)).put("highBid", s.highBid).put("highSeat", s.highSeat)
            .put("trump", s.trump).put("trick", JSONArray(s.trick)).put("leader", s.leader)
            .put("won", JSONArray(s.tricksWon)).put("scores", JSONArray(s.scores))
            .put("counts", JSONArray(s.handCounts)).put("resolving", s.resolving)
            .put("delta", JSONArray(s.lastDelta))
            .toString()

    fun parseBatak(j: JSONObject): BatakState =
        BatakState(
            phase = j.getInt("phase"),
            dealer = j.getInt("dealer"),
            turn = j.getInt("turn"),
            bids = ints(j.getJSONArray("bids")),
            highBid = j.getInt("highBid"),
            highSeat = j.getInt("highSeat"),
            trump = j.getInt("trump"),
            trick = ints(j.getJSONArray("trick")),
            leader = j.getInt("leader"),
            tricksWon = ints(j.getJSONArray("won")),
            scores = ints(j.getJSONArray("scores")),
            handCounts = ints(j.getJSONArray("counts")),
            resolving = j.getBoolean("resolving"),
            lastDelta = ints(j.getJSONArray("delta")),
        )

    fun batakHand(cards: List<Int>): String =
        JSONObject().put("t", BATAK_HAND).put("cards", JSONArray(cards)).toString()

    fun parseHand(j: JSONObject): List<Int> = ints(j.getJSONArray("cards"))

    fun batakBid(v: Int): String = JSONObject().put("t", BATAK_BID).put("v", v).toString()

    fun batakTrump(suit: Int): String = JSONObject().put("t", BATAK_TRUMP).put("suit", suit).toString()

    fun batakPlay(card: Int): String = JSONObject().put("t", BATAK_PLAY).put("card", card).toString()

    // ---------------------------------------------------------------- Lobi

    fun lobby(game: Game, seats: List<Seat>, you: Int, started: Boolean): String {
        val arr = JSONArray()
        seats.forEach { s ->
            arr.put(JSONObject().put("k", s.kind.name).put("n", s.name))
        }
        return JSONObject()
            .put("t", LOBBY)
            .put("game", game.name)
            .put("seats", arr)
            .put("you", you)
            .put("started", started)
            .toString()
    }

    fun start(): String = JSONObject().put("t", START).toString()

    fun chat(from: String, text: String): String =
        JSONObject().put("t", CHAT).put("from", from).put("text", text).toString()

    fun parseSeats(arr: JSONArray): List<Seat> =
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Seat(SeatKind.valueOf(o.getString("k")), o.optString("n"))
        }
}
