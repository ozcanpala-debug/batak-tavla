package com.ensar.oyun

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ensar.oyun.game.BatakLogic
import com.ensar.oyun.game.BatakPhase
import com.ensar.oyun.game.BatakState
import com.ensar.oyun.game.Cards
import com.ensar.oyun.game.TavlaLogic
import com.ensar.oyun.game.TavlaState
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
 * Oturumun tek sahibi. Kurucu telefon "hakem"dir: koltukları, zarı, kart dağıtımını ve
 * hamle doğrulamasını o yapar. Diğer telefonlar sadece hamle isteği yollar ve durumu alır.
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

    var xox by mutableStateOf(XoxState())
        private set
    var tavla by mutableStateOf(TavlaState())
        private set
    var batak by mutableStateOf(BatakState())
        private set

    /** Kendi Batak elim (sıralı). Rakiplerin eli hiçbir zaman telefonlara gönderilmez. */
    var myHand by mutableStateOf(emptyList<Int>())
        private set

    /** Sadece kurucuda: dört elin gerçek hali. */
    private var hands: List<MutableList<Int>> = emptyList()

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
        when (game) {
            Game.XOX -> {
                xox = XoxState()
                net.broadcast(Wire.xoxState(xox))
                maybeBotMove()
            }
            Game.TAVLA -> {
                tavla = TavlaLogic.initial(0)
                publishTavla()
            }
            Game.BATAK -> {
                batak = BatakState()
                dealBatak(0)
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

    // ------------------------------------------------------------ XOX

    fun xoxTap(cell: Int) {
        if (!started || xox.result.isNotEmpty() || xox.turn != mySeat) return
        if (isHost) applyXox(mySeat, cell) else net.sendToHost(Wire.xoxMove(cell))
    }

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

    // ------------------------------------------------------------ Tavla

    fun tavlaRoll() {
        if (!started || game != Game.TAVLA) return
        if (isHost) hostTavlaRoll(mySeat) else net.sendToHost(Wire.tavlaRoll())
    }

    fun tavlaMove(from: Int, to: Int) {
        if (!started || game != Game.TAVLA) return
        if (isHost) hostTavlaMove(mySeat, from, to) else net.sendToHost(Wire.tavlaMove(from, to))
    }

    fun tavlaRematch() {
        if (!isHost || game != Game.TAVLA) return
        tavla = TavlaLogic.initial(tavla.round + 1)
        publishTavla()
    }

    private fun hostTavlaRoll(seat: Int) {
        val s = tavla
        if (s.winner >= 0 || s.turn != seat || s.dice.isNotEmpty()) return
        tavla = TavlaLogic.settle(TavlaLogic.roll(s))
        publishTavla()
    }

    private fun hostTavlaMove(seat: Int, from: Int, to: Int) {
        val s = tavla
        if (s.turn != seat || s.winner >= 0) return
        // Aynı (from,to) için birden çok zar uyuyorsa (taş toplama) küçük zar harcanır.
        val m = TavlaLogic.legalMoves(s, seat)
            .filter { it.from == from && it.to == to }
            .minByOrNull { it.die } ?: return
        tavla = TavlaLogic.settle(TavlaLogic.apply(s, seat, m))
        publishTavla()
    }

    private fun publishTavla() {
        net.broadcast(Wire.tavlaState(tavla))
        maybeTavlaBot()
    }

    private fun maybeTavlaBot() {
        if (!isHost || game != Game.TAVLA || !started) return
        val s = tavla
        if (s.winner >= 0 || seats.getOrNull(s.turn)?.kind != SeatKind.BOT) return
        viewModelScope.launch {
            delay(900)
            val now = tavla
            if (!isHost || now != s) return@launch
            if (now.dice.isEmpty()) {
                hostTavlaRoll(now.turn)
            } else {
                val m = TavlaLogic.botMove(now, now.turn)
                tavla = if (m == null) {
                    TavlaLogic.settle(now.copy(dice = now.dice))
                } else {
                    TavlaLogic.settle(TavlaLogic.apply(now, now.turn, m))
                }
                publishTavla()
            }
        }
    }

    // ------------------------------------------------------------ Batak

    fun batakBid(v: Int) {
        if (!started || game != Game.BATAK) return
        if (isHost) hostBatakBid(mySeat, v) else net.sendToHost(Wire.batakBid(v))
    }

    fun batakChooseTrump(suit: Int) {
        if (!started || game != Game.BATAK) return
        if (isHost) hostBatakTrump(mySeat, suit) else net.sendToHost(Wire.batakTrump(suit))
    }

    fun batakPlay(card: Int) {
        if (!started || game != Game.BATAK) return
        if (isHost) hostBatakPlay(mySeat, card) else net.sendToHost(Wire.batakPlay(card))
    }

    fun batakNextHand() {
        if (!isHost || game != Game.BATAK || batak.phase != BatakPhase.DONE) return
        dealBatak((batak.dealer + 1) % 4)
    }

    private fun dealBatak(dealer: Int) {
        hands = BatakLogic.deal().map { it.toMutableList() }
        batak = BatakLogic.newHand(batak, dealer)
        publishBatak()
    }

    private fun publishBatak() {
        if (hands.size == 4) myHand = Cards.sortHand(hands[mySeat])
        net.broadcast(Wire.batakState(batak))
        if (hands.size == 4) {
            seats.forEachIndexed { i, s ->
                val id = s.endpointId
                if (s.kind == SeatKind.HUMAN && id != null) {
                    net.send(id, Wire.batakHand(Cards.sortHand(hands[i])))
                }
            }
        }
        maybeBatakBot()
    }

    private fun hostBatakBid(seat: Int, v: Int) {
        if (!BatakLogic.canBid(batak, seat, v)) return
        batak = BatakLogic.applyBid(batak, seat, v)
        publishBatak()
    }

    private fun hostBatakTrump(seat: Int, suit: Int) {
        val s = batak
        if (s.phase != BatakPhase.TRUMP || s.highSeat != seat || suit !in 0..3) return
        batak = BatakLogic.applyTrump(s, suit)
        publishBatak()
    }

    private fun hostBatakPlay(seat: Int, card: Int) {
        val s = batak
        if (s.phase != BatakPhase.PLAY || s.resolving || s.turn != seat || hands.size != 4) return
        val hand = hands[seat]
        if (card !in hand) return
        if (card !in BatakLogic.legalCards(hand, s.trick, s.trump, s.leader)) return
        hand.remove(card)
        batak = BatakLogic.playCard(s, seat, card)
        publishBatak()
        if (batak.resolving) {
            viewModelScope.launch {
                delay(1500)
                if (isHost && batak.resolving) {
                    batak = BatakLogic.resolveTrick(batak)
                    publishBatak()
                }
            }
        }
    }

    private fun maybeBatakBot() {
        if (!isHost || game != Game.BATAK || !started || hands.size != 4) return
        val s = batak
        if (s.resolving || s.phase == BatakPhase.DONE) return
        val seat = if (s.phase == BatakPhase.TRUMP) s.highSeat else s.turn
        if (seats.getOrNull(seat)?.kind != SeatKind.BOT) return
        viewModelScope.launch {
            delay(900)
            val now = batak
            if (!isHost || now != s) return@launch
            val hand = hands[seat]
            when (now.phase) {
                BatakPhase.BID -> hostBatakBid(seat, BatakLogic.botBid(now, hand, seat))
                BatakPhase.TRUMP -> hostBatakTrump(seat, BatakLogic.botTrump(hand))
                BatakPhase.PLAY -> hostBatakPlay(seat, BatakLogic.botCard(now, hand, seat))
            }
        }
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
        maybeTavlaBot()
        maybeBatakBot()
    }

    private fun onReceived(from: String, text: String) {
        val j = runCatching { JSONObject(text) }.getOrNull() ?: return
        val fromSeat = if (isHost) seats.indexOfFirst { it.endpointId == from } else -1
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
            Wire.XOX_MOVE -> if (isHost && fromSeat >= 0) applyXox(fromSeat, j.optInt("cell", -1))
            Wire.TAVLA_STATE -> if (!isHost) tavla = Wire.parseTavla(j)
            Wire.TAVLA_ROLL -> if (isHost && fromSeat >= 0) hostTavlaRoll(fromSeat)
            Wire.TAVLA_MOVE -> if (isHost && fromSeat >= 0) {
                hostTavlaMove(fromSeat, j.optInt("from", -2), j.optInt("to", -2))
            }
            Wire.BATAK_STATE -> if (!isHost) batak = Wire.parseBatak(j)
            Wire.BATAK_HAND -> if (!isHost) myHand = Wire.parseHand(j)
            Wire.BATAK_BID -> if (isHost && fromSeat >= 0) hostBatakBid(fromSeat, j.optInt("v", -1))
            Wire.BATAK_TRUMP -> if (isHost && fromSeat >= 0) hostBatakTrump(fromSeat, j.optInt("suit", -1))
            Wire.BATAK_PLAY -> if (isHost && fromSeat >= 0) hostBatakPlay(fromSeat, j.optInt("card", -1))
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
        tavla = TavlaState()
        batak = BatakState()
        myHand = emptyList()
        hands = emptyList()
        chat.clear()
    }

    override fun onCleared() {
        net.stop()
    }
}
