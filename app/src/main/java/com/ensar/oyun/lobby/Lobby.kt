package com.ensar.oyun.lobby

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
