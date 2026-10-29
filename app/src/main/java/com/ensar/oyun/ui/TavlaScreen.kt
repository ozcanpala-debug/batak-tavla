package com.ensar.oyun.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ensar.oyun.SessionViewModel
import com.ensar.oyun.game.BAR
import com.ensar.oyun.game.OFF
import com.ensar.oyun.game.TavlaLogic
import com.ensar.oyun.game.TavlaMove
import com.ensar.oyun.game.TavlaState
import kotlin.math.abs
import kotlin.math.min

private val BoardBrown = Color(0xFF5D4037)
private val TrayBrown = Color(0xFF3E2723)
private val TriLight = Color(0xFFD7B98E)
private val TriDark = Color(0xFF8D5A3B)
private val TriSource = Color(0xFFFFD54F)
private val TriDest = Color(0xFF66BB6A)
private val White = Color(0xFFF2EBD9)
private val Black = Color(0xFF1E1E1E)

@Composable
fun TavlaScreen(vm: SessionViewModel) {
    val s = vm.tavla
    val me = vm.mySeat
    val myTurn = s.turn == me && s.winner < 0
    val legal = remember(s, me) {
        if (myTurn && s.dice.isNotEmpty()) TavlaLogic.legalMoves(s, me) else emptyList()
    }
    var selected by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(s) { selected = null }

    fun nameOf(seat: Int) = vm.seats.getOrNull(seat)?.name?.ifEmpty { "?" } ?: "?"

    val status = when {
        s.winner >= 0 -> if (s.winner == me) "Kazandın!" else "${nameOf(s.winner)} kazandı"
        myTurn && s.dice.isEmpty() -> "Sıra sende: zarı at"
        myTurn -> "Taşını seç, sonra gideceği yere dokun"
        else -> "Sıra: ${nameOf(s.turn)}"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (seat in listOf(1 - me, me)) {
            val mine = seat == me
            Text(
                (if (seat == 0) "⚪ " else "⚫ ") + nameOf(seat) + (if (mine) " (sen)" else "") +
                    "   çıkan: ${s.off[seat]}   bar: ${s.bar[seat]}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (s.turn == seat && s.winner < 0) FontWeight.Bold else FontWeight.Normal,
            )
        }

        Text(status, style = MaterialTheme.typography.titleMedium)
        if (s.skipped) {
            Text("${nameOf(1 - s.turn)} oynayamadı (hamle yok)", style = MaterialTheme.typography.bodySmall)
        }

        DiceRow(s)

        BoardCanvas(
            s = s,
            me = me,
            legal = legal,
            selected = selected,
            onTap = { target ->
                val sel = selected
                if (target == null) {
                    selected = null
                } else if (sel == null) {
                    if (legal.any { it.from == target }) selected = target
                } else if (legal.any { it.from == sel && it.to == target }) {
                    vm.tavlaMove(sel, target)
                } else if (legal.any { it.from == target }) {
                    selected = target
                } else {
                    selected = null
                }
            },
        )

        if (s.winner < 0) {
            Button(
                onClick = vm::tavlaRoll,
                enabled = myTurn && s.dice.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Zar at") }
        } else if (vm.isHost) {
            Button(onClick = vm::tavlaRematch, modifier = Modifier.fillMaxWidth()) { Text("Yeniden oyna") }
        } else {
            Text("Kurucunun yeni turu başlatması bekleniyor…")
        }
        OutlinedButton(onClick = vm::leave, modifier = Modifier.fillMaxWidth()) { Text("Oyundan çık") }
    }
}

@Composable
private fun DiceRow(s: TavlaState) {
    val remaining = s.dice
    val shown = if (remaining.isNotEmpty()) remaining else s.rolled
    val dim = remaining.isEmpty()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (shown.isEmpty()) {
            Text("Zar henüz atılmadı", style = MaterialTheme.typography.bodySmall)
        } else {
            shown.forEach { d ->
                Box(
                    Modifier
                        .size(44.dp)
                        .alpha(if (dim) 0.4f else 1f)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .border(2.dp, Color.DarkGray, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(d.toString(), fontSize = 24.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Dokunulan yeri tahta hedefine çevirir: mutlak nokta indeksi, BAR, OFF ya da null. */
private fun hitTarget(x: Float, y: Float, w: Float, h: Float, me: Int): Int? {
    if (x < 0f || y < 0f || x > w || y > h) return null
    val cw = w / 14f
    val col = (x / cw).toInt().coerceIn(0, 13)
    return when (col) {
        6 -> BAR
        13 -> OFF
        else -> {
            val top = y < h / 2f
            val rel = if (top) (if (col < 6) 12 + col else 11 + col) else (if (col < 6) 11 - col else 12 - col)
            TavlaLogic.absOf(me, rel)
        }
    }
}

@Composable
private fun BoardCanvas(
    s: TavlaState,
    me: Int,
    legal: List<TavlaMove>,
    selected: Int?,
    onTap: (Int?) -> Unit,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(14f / 15f)
            .pointerInput(s, legal, selected) {
                detectTapGestures { off ->
                    onTap(hitTarget(off.x, off.y, size.width.toFloat(), size.height.toFloat(), me))
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val cw = w / 14f
        val r = cw * 0.44f

        drawRect(BoardBrown)
        drawRect(TrayBrown, topLeft = Offset(6 * cw, 0f), size = Size(cw, h))
        drawRect(TrayBrown, topLeft = Offset(13 * cw, 0f), size = Size(cw, h))

        // Noktalar
        for (col in 0..12) {
            if (col == 6) continue
            for (top in listOf(true, false)) {
                val rel = if (top) (if (col < 6) 12 + col else 11 + col) else (if (col < 6) 11 - col else 12 - col)
                val ab = TavlaLogic.absOf(me, rel)
                val x0 = col * cw
                val dark = (col + (if (top) 0 else 1)) % 2 == 0
                val isSelected = selected == ab
                val isDest = selected != null && legal.any { it.from == selected && it.to == ab }
                val color = when {
                    isSelected -> TriSource
                    isDest -> TriDest
                    dark -> TriDark
                    else -> TriLight
                }
                val baseY = if (top) 0f else h
                val tipY = if (top) h * 0.46f else h * 0.54f
                val path = Path().apply {
                    moveTo(x0, baseY)
                    lineTo(x0 + cw, baseY)
                    lineTo(x0 + cw / 2f, tipY)
                    close()
                }
                drawPath(path, color)

                val v = s.points[ab]
                val n = abs(v)
                if (n > 0) {
                    val owner = if (v > 0) 0 else 1
                    val shown = min(n, 5)
                    for (i in 0 until shown) {
                        val cy = if (top) r + i * 2f * r else h - r - i * 2f * r
                        checker(x0 + cw / 2f, cy, r, owner)
                    }
                    if (n > 5) {
                        val cy = if (top) r + 4 * 2f * r else h - r - 4 * 2f * r
                        label(n.toString(), x0 + cw / 2f, cy, if (owner == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), r * 1.1f)
                    }
                    if (selected == null && legal.any { it.from == ab }) {
                        val topIdx = shown - 1
                        val cy = if (top) r + topIdx * 2f * r else h - r - topIdx * 2f * r
                        drawCircle(
                            color = TriDest,
                            radius = r * 1.1f,
                            center = Offset(x0 + cw / 2f, cy),
                            style = Stroke(width = r * 0.18f),
                        )
                    }
                }
            }
        }

        // Bar (orta sütun): rakip üst yarıda, benimkiler alt yarıda
        val opp = 1 - me
        val barX = 6 * cw + cw / 2f
        val myBarSelected = selected == BAR
        if (myBarSelected) drawRect(TriSource.copy(alpha = 0.35f), Offset(6 * cw, h / 2f), Size(cw, h / 2f))
        for (i in 0 until min(s.bar[opp], 5)) {
            checker(barX, h / 2f - r - i * 2f * r, r, opp)
        }
        for (i in 0 until min(s.bar[me], 5)) {
            checker(barX, h / 2f + r + i * 2f * r, r, me)
        }
        if (selected == null && legal.any { it.from == BAR }) {
            drawCircle(
                color = TriDest,
                radius = r * 1.1f,
                center = Offset(barX, h / 2f + r),
                style = Stroke(width = r * 0.18f),
            )
        }

        // Taş toplama alanı (sağ sütun)
        val offX = 13 * cw + cw / 2f
        val canOff = selected != null && legal.any { it.from == selected && it.to == OFF }
        if (canOff) drawRect(TriDest.copy(alpha = 0.5f), Offset(13 * cw, h / 2f), Size(cw, h / 2f))
        label("${s.off[opp]}", offX, h * 0.25f, 0xFFFFFFFF.toInt(), cw * 0.5f)
        label("${s.off[me]}", offX, h * 0.75f, 0xFFFFFFFF.toInt(), cw * 0.5f)
        label("çıkar", offX, h * 0.9f, 0xFFBCAAA4.toInt(), cw * 0.28f)
    }
}

private fun DrawScope.checker(cx: Float, cy: Float, r: Float, seat: Int) {
    drawCircle(color = if (seat == 0) White else Black, radius = r, center = Offset(cx, cy))
    drawCircle(
        color = if (seat == 0) Color(0xFF8D6E63) else Color(0xFF9E9E9E),
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.12f),
    )
}

private fun DrawScope.label(text: String, cx: Float, cy: Float, argb: Int, sizePx: Float) {
    val p = android.graphics.Paint().apply {
        color = argb
        textSize = sizePx
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, cx, cy + sizePx / 3f, p)
}
