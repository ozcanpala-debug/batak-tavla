package com.ensar.oyun.net

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * İnternetsiz telefon-telefon bağlantısı (Google Nearby Connections).
 * Wi-Fi Direct / Bluetooth seçimini kütüphane kendisi yapar.
 *
 * Topoloji: P2P_STAR. Bir telefon "kurucu" (host) olur, en fazla 3 telefon ona bağlanır.
 * Mesajlar düz metin (JSON) olarak taşınır.
 */
class NearbyNet(context: Context) {

    companion object {
        const val SERVICE_ID = "com.ensar.oyun.v1"
    }

    enum class Role { NONE, HOST, CLIENT }

    data class Room(val endpointId: String, val name: String)

    data class State(
        val role: Role = Role.NONE,
        val status: String = "",
        val rooms: List<Room> = emptyList(),
        /** Host tarafında bağlı telefonlar: endpointId -> isim */
        val peers: Map<String, String> = emptyMap(),
        /** Client tarafında kurucunun endpointId'si */
        val hostEndpoint: String? = null,
    )

    sealed interface Event {
        data class PeerJoined(val id: String, val name: String) : Event
        data class PeerLeft(val id: String) : Event
        data class Received(val from: String, val text: String) : Event
        data object LostHost : Event
    }

    private val connections: ConnectionsClient =
        Nearby.getConnectionsClient(context.applicationContext)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 256)
    val events: SharedFlow<Event> = _events

    private var myName = "Oyuncu"
    private val names = HashMap<String, String>()

    // ---------------------------------------------------------------- Host

    fun startHost(name: String) {
        stop()
        myName = name
        _state.value = State(role = Role.HOST, status = "Oda açılıyor…")
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        connections.startAdvertising(name, SERVICE_ID, lifecycle, options)
            .addOnSuccessListener {
                _state.update { it.copy(status = "Oda açık. Katılacak telefonlar bekleniyor.") }
            }
            .addOnFailureListener { e ->
                _state.update { it.copy(status = "Oda açılamadı: ${e.message}") }
            }
    }

    /** Host: bir telefonu odadan at. */
    fun kick(endpointId: String) {
        connections.disconnectFromEndpoint(endpointId)
        _state.update { it.copy(peers = it.peers - endpointId) }
    }

    // -------------------------------------------------------------- Client

    fun startDiscovery(name: String) {
        stop()
        myName = name
        _state.value = State(role = Role.CLIENT, status = "Odalar aranıyor…")
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        connections.startDiscovery(SERVICE_ID, discovery, options)
            .addOnFailureListener { e ->
                _state.update { it.copy(status = "Arama başlatılamadı: ${e.message}") }
            }
    }

    fun join(room: Room) {
        _state.update { it.copy(status = "${room.name} odasına bağlanılıyor…") }
        connections.requestConnection(myName, room.endpointId, lifecycle)
            .addOnFailureListener { e ->
                _state.update { it.copy(status = "Bağlanılamadı: ${e.message}") }
            }
    }

    // ------------------------------------------------------------- Mesajlar

    fun send(endpointId: String, text: String) {
        connections.sendPayload(endpointId, Payload.fromBytes(text.toByteArray(Charsets.UTF_8)))
    }

    /** Host: bağlı herkese gönder (isteğe bağlı bir telefon hariç). */
    fun broadcast(text: String, except: String? = null) {
        val targets = _state.value.peers.keys.filter { it != except }
        if (targets.isEmpty()) return
        connections.sendPayload(targets, Payload.fromBytes(text.toByteArray(Charsets.UTF_8)))
    }

    /** Client: kurucuya gönder. */
    fun sendToHost(text: String) {
        _state.value.hostEndpoint?.let { send(it, text) }
    }

    fun stop() {
        connections.stopAdvertising()
        connections.stopDiscovery()
        connections.stopAllEndpoints()
        names.clear()
        _state.value = State()
    }

    // ------------------------------------------------------------ Callback'ler

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            _events.tryEmit(Event.Received(endpointId, String(bytes, Charsets.UTF_8)))
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            names[endpointId] = info.endpointName
            // Bağlantıyı otomatik kabul ediyoruz; kimin odaya gireceğine kurucu uygulama kararı verir.
            connections.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val name = names[endpointId] ?: "Oyuncu"
            if (!result.status.isSuccess) {
                names.remove(endpointId)
                _state.update { it.copy(status = "Bağlantı kurulamadı.") }
                return
            }
            when (_state.value.role) {
                Role.HOST -> {
                    _state.update { it.copy(peers = it.peers + (endpointId to name)) }
                    _events.tryEmit(Event.PeerJoined(endpointId, name))
                }
                Role.CLIENT -> {
                    connections.stopDiscovery()
                    _state.update {
                        it.copy(hostEndpoint = endpointId, rooms = emptyList(), status = "Bağlandı.")
                    }
                }
                Role.NONE -> Unit
            }
        }

        override fun onDisconnected(endpointId: String) {
            names.remove(endpointId)
            when (_state.value.role) {
                Role.HOST -> {
                    val known = endpointId in _state.value.peers
                    _state.update { it.copy(peers = it.peers - endpointId) }
                    if (known) _events.tryEmit(Event.PeerLeft(endpointId))
                }
                Role.CLIENT -> {
                    if (_state.value.hostEndpoint == endpointId) {
                        _state.update { it.copy(hostEndpoint = null) }
                        _events.tryEmit(Event.LostHost)
                    }
                }
                Role.NONE -> Unit
            }
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _state.update { s ->
                if (s.rooms.any { it.endpointId == endpointId }) s
                else s.copy(rooms = s.rooms + Room(endpointId, info.endpointName))
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _state.update { s -> s.copy(rooms = s.rooms.filter { it.endpointId != endpointId }) }
        }
    }
}
