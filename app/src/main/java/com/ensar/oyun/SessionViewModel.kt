package com.ensar.oyun

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ensar.oyun.game.XoxLogic
import com.ensar.oyun.game.XoxState
import com.ensar.oyun.lobby.Game
import com.ensar.oyun.lobby.Seat
import com.ensar.oyun.lobby.SeatKind
import com.ensar.oyun.lobby.Wire
import com.ensar.oyun.net.NearbyNet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class Screen { HOME, JOIN, LOBBY, GAME }

/**
 * Oturumun tek sahibi. Kurucu telefon "hakem"dir: koltukları o yönetir,
 * oyun başlayınca (sonraki aşamada) kart dağıtımı ve zar da onda olacak.
 */
class SessionViewModel(app: Application) : AndroidViewModel(app) {

    val net = NearbyNet(app)

    var screen by mutableStateOf(Screen.HOME)
        private set
    var playerName by mutableStateOf("")
    var game by mutableStateOf(Game.TAVLA)
    var seats by mutableStateOf(emptyList<Seat>())
        private set
    var mySeat by mutableIntStateOf(0)
        private set
    var isHost by mutableStateOf(false)
        private set
    var started by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)

    /** XOX tahtası. Tek gerçek kopya kurucuda; diğer telefonlar her hamleden sonra kopyasını alır. */
    var xox by mutableStateOf(XoxState())
        private set

    /** Bağlantı testi için basit sohbet kutusu (oyun ekranları gelince kalkacak). */
    val chat = mutableStateListOf<String>()

    private val name: String get() = playerName.trim().ifEmpty { "Oyuncu" }

    init {
        viewModelScope.launch { net.events.collect { handle(it) } }
    }

    // ------------------------------------------------------------ Kullanıcı eylemleri

    fun hostRoom() {
        resetSession()
        isHost = true
        notice = null
        val list = MutableList(game.seats) { Seat() }
        list[0] = Seat(SeatKind.HUMAN, name)
        seats = list
        mySeat = 0
        net.startHost(name)
        screen = Screen.LOBBY
    }

    fun searchRooms() {
        resetSession()
        notice = null
        net.startDiscovery(name)
        screen = Screen.JOIN
    }

    fun joinRoom(room: NearbyNet.Room) = net.join(room)

    fun addBot(i: Int) {
        if (!isHost || seats.getOrNull(i)?.kind != SeatKind.EMPTY) return
        setSeat(i, Seat(SeatKind.BOT, "Bot ${i + 1}"))
        broadcastLobby()
    }

    fun clearSeat(i: Int) {
        if (!isHost || i == 0) return
        val seat = seats.getOrNull(i) ?: return
        seat.endpointId?.let { net.kick(it) }
        setSeat(i, Seat())
        broadcastLobby()
    }

    fun startGame() {
        if (!isHost) return
        // Boş koltukları botlar doldurur.
        seats = seats.mapIndexed { i, s ->
            if (s.kind == SeatKind.EMPTY) Seat(SeatKind.BOT, "Bot ${i + 1}") else s
        }
        started = true
        broadcastLobby()
        net.broadcast(Wire.start())
        screen = Screen.GAME
        if (game == Game.XOX) {
            xox = XoxState()
            net.broadcast(Wire.xoxState(xox))
            maybeBotMove()
        }
    }

    // ------------------------------------------------------------ XOX

    /** Bir kareye dokunuldu. Kurucuysa doğrudan uygula, değilse kurucuya yolla. */
    fun xoxTap(cell: Int) {
        if (!started || xox.result.isNotEmpty() || xox.turn != mySeat) return
        if (isHost) applyXox(mySeat, cell) else net.sendToHost(Wire.xoxMove(cell))
    }

    /** Sadece kurucu: yeni tur başlat, başlayan taraf her turda değişir. */
    fun xoxRematch() {
        if (!isHost || game != Game.XOX) return
        val round = xox.round + 1
        xox = XoxState(turn = round % 2, round = round)
        net.broadcast(Wire.xoxState(xox))
        maybeBotMove()
    }

    private fun applyXox(seat: Int, cell: Int) {
        val next = XoxLogic.apply(xox, seat, cell) ?: return
        xox = next
        net.broadcast(Wire.xoxState(next))
        maybeBotMove()
    }

    private fun maybeBotMove() {
        if (!isHost || game != Game.XOX || !started) return
        val before = xox
        if (before.result.isNotEmpty() || seats.getOrNull(before.turn)?.kind != SeatKind.BOT) return
        viewModelScope.launch {
            delay(700)
            val now = xox
            val unchanged = now.board == before.board && now.turn == before.turn
            if (isHost && unchanged && now.result.isEmpty() &&
                seats.getOrNull(now.turn)?.kind == SeatKind.BOT
            ) {
                val cell = XoxLogic.botMove(now.board, now.turn)
                if (cell >= 0) applyXox(now.turn, cell)
            }
        }
    }

    fun sendChat(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        chat.add("$name: $t")
        val msg = Wire.chat(name, t)
        if (isHost) net.broadcast(msg) else net.sendToHost(msg)
    }

    fun leave() {
        resetSession()
        screen = Screen.HOME
    }

    // ------------------------------------------------------------ Ağ olayları

    private fun handle(e: NearbyNet.Event) {
        when (e) {
            is NearbyNet.Event.PeerJoined -> if (isHost) onPeerJoined(e.id, e.name)
            is NearbyNet.Event.PeerLeft -> if (isHost) onPeerLeft(e.id)
            is NearbyNet.Event.LostHost -> if (!isHost && screen != Screen.HOME) {
                resetSession()
                notice = "Odayla bağlantı koptu."
                screen = Screen.HOME
            }
            is NearbyNet.Event.Received -> onReceived(e.from, e.text)
        }
    }

    private fun onPeerJoined(id: String, peerName: String) {
        val i = seats.indexOfFirst { it.kind == SeatKind.EMPTY }
        if (i < 0 || started) {
            net.kick(id) // oda dolu ya da oyun başlamış
            return
        }
        setSeat(i, Seat(SeatKind.HUMAN, peerName, id))
        broadcastLobby()
    }

    private fun onPeerLeft(id: String) {
        val i = seats.indexOfFirst { it.endpointId == id }
        if (i < 0) return
        // Oyun başladıysa giden oyuncunun yerini bot alır, oyun bozulmaz.
        setSeat(i, if (started) Seat(SeatKind.BOT, "Bot ${i + 1}") else Seat())
        broadcastLobby()
        maybeBotMove()
    }

    private fun onReceived(from: String, text: String) {
        val j = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (j.optString("t")) {
            Wire.LOBBY -> if (!isHost) {
                game = Game.valueOf(j.getString("game"))
                seats = Wire.parseSeats(j.getJSONArray("seats"))
                mySeat = j.getInt("you")
                started = j.getBoolean("started")
                if (screen == Screen.JOIN) screen = Screen.LOBBY
            }
            Wire.START -> if (!isHost) {
                started = true
                screen = Screen.GAME
            }
            Wire.XOX_STATE -> if (!isHost) xox = Wire.parseXox(j)
            Wire.XOX_MOVE -> if (isHost) {
                val seat = seats.indexOfFirst { it.endpointId == from }
                if (seat >= 0) applyXox(seat, j.optInt("cell", -1))
            }
            Wire.CHAT -> {
                chat.add("${j.optString("from")}: ${j.optString("text")}")
                if (isHost) net.broadcast(text, except = from)
            }
        }
    }

    // ------------------------------------------------------------ Yardımcılar

    private fun setSeat(i: Int, seat: Seat) {
        seats = seats.toMutableList().also { it[i] = seat }
    }

    private fun broadcastLobby() {
        seats.forEachIndexed { i, s ->
            val id = s.endpointId
            if (s.kind == SeatKind.HUMAN && id != null) {
                net.send(id, Wire.lobby(game, seats, i, started))
            }
        }
    }

    private fun resetSession() {
        net.stop()
        seats = emptyList()
        mySeat = 0
        started = false
        isHost = false
        xox = XoxState()
        chat.clear()
    }

    override fun onCleared() {
        net.stop()
    }
}
