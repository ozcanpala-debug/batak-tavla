package com.ensar.oyun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ensar.oyun.SessionViewModel
import com.ensar.oyun.game.BatakLogic
import com.ensar.oyun.game.BatakPhase
import com.ensar.oyun.game.Cards

@Composable
fun BatakScreen(vm: SessionViewModel) {
    val s = vm.batak
    val me = vm.mySeat
    val hand = vm.myHand

    fun nameOf(seat: Int) = vm.seats.getOrNull(seat)?.name?.ifEmpty { "?" } ?: "?"

    val myPlayTurn = s.phase == BatakPhase.PLAY && s.turn == me && !s.resolving
    val legal = remember(s, hand) {
        if (myPlayTurn && hand.isNotEmpty()) BatakLogic.legalCards(hand, s.trick, s.trump, s.leader) else emptyList()
    }

    val status = when (s.phase) {
        BatakPhase.BID ->
            if (s.turn == me) "İhale sırası sende" else "İhale: ${nameOf(s.turn)} konuşuyor"
        BatakPhase.TRUMP ->
            if (s.highSeat == me) "İhaleyi sen aldın (${s.highBid}). Kozu seç" else "${nameOf(s.highSeat)} koz seçiyor"
        BatakPhase.PLAY -> when {
            s.resolving -> "${nameOf(s.turn)} eli aldı"
            s.turn == me -> "Sıra sende: kart at"
            else -> "Sıra: ${nameOf(s.turn)}"
        }
        else -> "El bitti"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Skor tablosu
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Oyuncu", Modifier.weight(2.2f), style = MaterialTheme.typography.labelMedium)
                    Text("İhale", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("El", Modifier.weight(0.7f), style = MaterialTheme.typography.labelMedium)
                    Text("Skor", Modifier.weight(0.9f), style = MaterialTheme.typography.labelMedium)
                }
                for (seat in 0..3) {
                    val bidText = when {
                        s.phase == BatakPhase.BID -> when (s.bids[seat]) {
                            -1 -> "…"
                            0 -> "pas"
                            else -> s.bids[seat].toString()
                        }
                        seat == s.highSeat -> "★ ${s.highBid}"
                        else -> "-"
                    }
                    val bold = if (s.turn == seat && s.phase != BatakPhase.DONE) FontWeight.Bold else FontWeight.Normal
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            nameOf(seat) + if (seat == me) " (sen)" else "",
                            Modifier.weight(2.2f), fontWeight = bold,
                        )
                        Text(bidText, Modifier.weight(1f), fontWeight = bold)
                        Text(s.tricksWon[seat].toString(), Modifier.weight(0.7f), fontWeight = bold)
                        Text(s.scores[seat].toString(), Modifier.weight(0.9f), fontWeight = bold)
                    }
                }
            }
        }

        Text(status, style = MaterialTheme.typography.titleMedium)
        if (s.trump >= 0 && s.phase != BatakPhase.BID && s.phase != BatakPhase.TRUMP) {
            Text(
                "Koz: ${Cards.SUIT_SYMBOLS[s.trump]} ${Cards.SUIT_NAMES[s.trump]}  ·  İhale: ${nameOf(s.highSeat)} (${s.highBid})",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (s.phase == BatakPhase.BID && s.highSeat >= 0) {
            Text("En yüksek teklif: ${s.highBid} (${nameOf(s.highSeat)})", style = MaterialTheme.typography.bodyMedium)
        }

        // Masa
        if (s.phase == BatakPhase.PLAY || s.phase == BatakPhase.DONE) {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                for (seat in 0..3) {
                    val rel = (seat - me + 4) % 4
                    val align = when (rel) {
                        0 -> Alignment.BottomCenter
                        1 -> Alignment.CenterStart
                        2 -> Alignment.TopCenter
                        else -> Alignment.CenterEnd
                    }
                    Column(Modifier.align(align), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            nameOf(seat) + " (${s.handCounts[seat]})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (s.turn == seat && s.phase == BatakPhase.PLAY) FontWeight.Bold else FontWeight.Normal,
                        )
                        val c = s.trick[seat]
                        if (c >= 0) {
                            CardChip(c)
                        } else {
                            Box(Modifier.size(width = 42.dp, height = 60.dp))
                        }
                    }
                }
            }
        }

        // İhale paneli
        if (s.phase == BatakPhase.BID && s.turn == me) {
            val forced = BatakLogic.isForcedBid(s, me)
            Text("Kaç el alırsın? (en az 5)", style = MaterialTheme.typography.bodyMedium)
            if (forced) Text("Herkes pas dedi, en az 5 demelisin.", style = MaterialTheme.typography.bodySmall)
            val options = (maxOf(5, s.highBid + 1)..13).toList()
            options.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { v ->
                        Button(
                            onClick = { vm.batakBid(v) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text(v.toString()) }
                    }
                }
            }
            OutlinedButton(
                onClick = { vm.batakBid(0) },
                enabled = !forced,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pas") }
        }

        // Koz paneli
        if (s.phase == BatakPhase.TRUMP && s.highSeat == me) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (suit in 0..3) {
                    Button(
                        onClick = { vm.batakChooseTrump(suit) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text(Cards.SUIT_SYMBOLS[suit] + " " + Cards.SUIT_NAMES[suit], fontSize = 13.sp) }
                }
            }
        }

        // El bitti özeti
        if (s.phase == BatakPhase.DONE) {
            val made = s.tricksWon[s.highSeat] >= s.highBid
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (made) "${nameOf(s.highSeat)} ihaleyi tuttu (${s.highBid})"
                        else "${nameOf(s.highSeat)} ihaleyi yapamadı (${s.highBid})",
                        fontWeight = FontWeight.Bold,
                    )
                    for (seat in 0..3) {
                        val d = s.lastDelta[seat]
                        Text("${nameOf(seat)}: ${if (d >= 0) "+" else ""}$d  (toplam ${s.scores[seat]})")
                    }
                }
            }
            if (vm.isHost) {
                Button(onClick = vm::batakNextHand, modifier = Modifier.fillMaxWidth()) { Text("Yeni el") }
            } else {
                Text("Kurucunun yeni eli dağıtması bekleniyor…")
            }
        }

        // Elim
        if (hand.isNotEmpty()) {
            Text("Elin", style = MaterialTheme.typography.labelMedium)
            hand.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { c ->
                        val ok = c in legal
                        CardChip(
                            c,
                            enabled = !myPlayTurn || ok,
                            highlight = myPlayTurn && ok,
                            onClick = if (myPlayTurn && ok) ({ vm.batakPlay(c) }) else null,
                        )
                    }
                }
            }
        }

        OutlinedButton(onClick = vm::leave, modifier = Modifier.fillMaxWidth()) { Text("Oyundan çık") }
    }
}

@Composable
private fun CardChip(
    card: Int,
    enabled: Boolean = true,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val base = Modifier
        .size(width = 42.dp, height = 60.dp)
        .alpha(if (enabled) 1f else 0.4f)
        .background(Color.White, shape)
        .border(if (highlight) 3.dp else 1.dp, if (highlight) Color(0xFF2E7D32) else Color.Gray, shape)
    Box(
        if (onClick != null) base.clickable { onClick() } else base,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Cards.label(card),
            color = if (Cards.isRed(card)) Color(0xFFC62828) else Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
