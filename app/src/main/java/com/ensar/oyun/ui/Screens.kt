package com.ensar.oyun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ensar.oyun.SessionViewModel
import com.ensar.oyun.lobby.Game
import com.ensar.oyun.lobby.SeatKind

@Composable
fun HomeScreen(vm: SessionViewModel, onHost: () -> Unit, onJoin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Batak & Tavla", style = MaterialTheme.typography.headlineMedium)
        Text(
            "İnternet olmadan, Wi-Fi veya Bluetooth ile yakındaki telefonlarla oyna.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = vm.playerName,
            onValueChange = { vm.playerName = it },
            label = { Text("Adın") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Oyun seç", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Game.entries.forEach { g ->
                FilterChip(
                    selected = vm.game == g,
                    onClick = { vm.game = g },
                    label = { Text(g.title) },
                )
            }
        }
        Text("${vm.game.seats} kişilik oyun (eksik oyuncuları bot tamamlar)", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onHost, modifier = Modifier.fillMaxWidth()) { Text("Oda kur") }
        OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth()) { Text("Odaya katıl") }
        vm.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun JoinScreen(vm: SessionViewModel) {
    val st by vm.net.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Odaya katıl", style = MaterialTheme.typography.headlineSmall)
        Text(st.status, style = MaterialTheme.typography.bodyMedium)
        if (st.rooms.isEmpty()) {
            Text("Henüz oda bulunamadı. Diğer telefonda \"Oda kur\"a bas, iki telefon da yakın olsun.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(st.rooms, key = { it.endpointId }) { room ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${room.name} odası", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { vm.joinRoom(room) }) { Text("Katıl") }
                    }
                }
            }
        }
        OutlinedButton(onClick = vm::leave, modifier = Modifier.fillMaxWidth()) { Text("Geri") }
    }
}

@Composable
fun LobbyScreen(vm: SessionViewModel) {
    val st by vm.net.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${vm.game.title} odası", style = MaterialTheme.typography.headlineSmall)
        if (vm.isHost) Text(st.status, style = MaterialTheme.typography.bodyMedium)

        vm.seats.forEachIndexed { i, seat ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val label = when (seat.kind) {
                        SeatKind.EMPTY -> "Koltuk ${i + 1}: boş"
                        SeatKind.HUMAN -> "Koltuk ${i + 1}: ${seat.name}" + if (i == vm.mySeat) " (sen)" else ""
                        SeatKind.BOT -> "Koltuk ${i + 1}: ${seat.name} (bot)"
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    if (vm.isHost) {
                        when (seat.kind) {
                            SeatKind.EMPTY -> TextButton(onClick = { vm.addBot(i) }) { Text("Bot ekle") }
                            SeatKind.BOT -> TextButton(onClick = { vm.clearSeat(i) }) { Text("Çıkar") }
                            SeatKind.HUMAN -> if (i != 0) TextButton(onClick = { vm.clearSeat(i) }) { Text("At") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (vm.isHost) {
            Text("Boş kalan koltukları bot doldurur.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = vm::startGame, modifier = Modifier.fillMaxWidth()) { Text("Oyunu başlat") }
        } else {
            Text("Kurucunun oyunu başlatması bekleniyor…")
        }
        OutlinedButton(onClick = vm::leave, modifier = Modifier.fillMaxWidth()) { Text("Odadan çık") }
    }
}

/**
 * Geçici oyun ekranı: Tavla ve Batak ekranları sonraki aşamalarda buraya gelecek.
 * Şimdilik iki telefonun gerçekten internetsiz konuştuğunu denemek için mini sohbet var.
 */
@Composable
fun GameScreen(vm: SessionViewModel) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${vm.game.title} başladı", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Oyun ekranı sonraki aşamada gelecek. Şimdilik bağlantıyı bu kutudan dene.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Oyuncular: " + vm.seats.joinToString { it.name.ifEmpty { "-" } })

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(vm.chat.toList()) { line -> Text(line, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Mesaj") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { vm.sendChat(text); text = "" }) { Text("Gönder") }
        }
        OutlinedButton(onClick = vm::leave, modifier = Modifier.fillMaxWidth()) { Text("Oyundan çık") }
    }
}
