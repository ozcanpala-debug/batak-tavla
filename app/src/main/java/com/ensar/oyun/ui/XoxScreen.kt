package com.ensar.oyun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ensar.oyun.SessionViewModel
import com.ensar.oyun.game.XoxLogic

@Composable
fun XoxScreen(vm: SessionViewModel) {
    val s = vm.xox
    fun nameOf(seat: Int) = vm.seats.getOrNull(seat)?.name?.ifEmpty { "?" } ?: "?"

    val status = when {
        s.result == "DRAW" -> "Berabere"
        s.result.isNotEmpty() -> {
            val winner = if (s.result == "X") 0 else 1
            if (winner == vm.mySeat) "Kazandın!" else "${nameOf(winner)} kazandı"
        }
        s.turn == vm.mySeat -> "Sıra sende"
        else -> "Sıra: ${nameOf(s.turn)}"
    }
    val winLine = XoxLogic.winningLine(s.board) ?: emptyList()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("XOX", style = MaterialTheme.typography.headlineSmall)
        Text("X: ${nameOf(0)}   O: ${nameOf(1)}", style = MaterialTheme.typography.bodyLarge)
        Text("Sen: ${XoxLogic.symbol(vm.mySeat)}", style = MaterialTheme.typography.bodyMedium)
        Text(status, style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0..2) {
                        val i = r * 3 + c
                        val ch = s.board[i]
                        val canTap = s.result.isEmpty() && s.turn == vm.mySeat && ch == '.'
                        val bg = if (i in winLine) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(bg, RoundedCornerShape(12.dp))
                                .clickable(enabled = canTap) { vm.xoxTap(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ch != '.') Text(ch.toString(), fontSize = 52.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (s.result.isNotEmpty()) {
            if (vm.isHost) {
                Button(onClick = vm::xoxRematch, modifier = Modifier.fillMaxWidth()) { Text("Yeniden oyna") }
            } else {
                Text("Kurucunun yeni turu başlatması bekleniyor…")
            }
        }
        OutlinedButton(onClick = vm::leave, modifier = Modifier.fillMaxWidth()) { Text("Oyundan çık") }
    }
}
