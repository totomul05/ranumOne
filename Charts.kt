package com.ranum.one

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

val SaveLine = Color(0xFF37474F)
private const val DAY_MS = 86_400_000L

// Menyimpan harga emas saat ini sekaligus mencatatnya di riwayat (kunci "gold:<waktu>")
fun saveGoldPrice(dao: AppDao, scope: CoroutineScope, price: Long) {
    scope.launch {
        val ts = System.currentTimeMillis()
        dao.putSetting(Setting("gold", price.toString(), Stamp.next()))
        dao.putSetting(Setting("gold:$ts", price.toString(), Stamp.next()))
    }
}

private fun xAt(t: Long, t0: Long, t1: Long, w: Float): Float =
    if (t1 == t0) w / 2f else 8f + (t - t0).toFloat() / (t1 - t0).toFloat() * (w - 16f)

private fun nearestIdx(pts: List<Pair<Long, Long>>, x: Float, w: Float): Int {
    val t0 = pts.first().first
    val t1 = pts.last().first
    var best = 0
    var bd = Float.MAX_VALUE
    pts.forEachIndexed { i, p ->
        val d = abs(xAt(p.first, t0, t1, w) - x)
        if (d < bd) {
            bd = d
            best = i
        }
    }
    return best
}

@Composable
fun GoldHistoryPanel(hist: List<Pair<Long, Long>>, alwaysOpen: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    var period by remember { mutableIntStateOf(1) }
    var sel by remember(period) { mutableStateOf<Int?>(null) }
    val fmtFull = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")) }
    val fmtShort = remember { SimpleDateFormat("dd MMM yy", Locale("id", "ID")) }
    val now = System.currentTimeMillis()
    val from = when (period) {
        0 -> now - 7 * DAY_MS
        1 -> now - 30 * DAY_MS
        2 -> now - 365 * DAY_MS
        else -> Long.MIN_VALUE
    }
    val pts = hist.filter { it.first >= from }

    Panel("Riwayat harga emas") {
        Text(
            if (hist.isEmpty()) {
                "Belum ada riwayat. Harga yang Anda simpan mulai sekarang akan tercatat di sini."
            } else {
                "${hist.size} harga tercatat. Terakhir ${fmtFull.format(Date(hist.last().first))}."
            },
            style = MaterialTheme.typography.bodySmall
        )
        if (!alwaysOpen) {
            Spacer(Modifier.height(6.dp))
            Button({ open = !open }, Modifier.fillMaxWidth()) {
                Text(if (open) "Sembunyikan grafik" else "Lihat grafik harga emas")
            }
        }
        if (open || alwaysOpen) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("7 hari", "30 hari", "1 tahun", "Semua").forEachIndexed { i, t ->
                    if (period == i) {
                        Button({ period = i }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                            Text(t, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        OutlinedButton({ period = i }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                            Text(t, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (pts.size < 2) {
                Text(
                    if (pts.isEmpty()) {
                        "Tidak ada harga pada periode ini."
                    } else {
                        "Baru ada 1 harga pada periode ini (${rp(pts[0].second)}). Grafik butuh minimal 2 harga."
                    }
                )
            } else {
                val lo = pts.minOf { it.second }
                val hi = pts.maxOf { it.second }
                val t0 = pts.first().first
                val t1 = pts.last().first
                Row(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.height(160.dp).padding(end = 6.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(plain(hi), style = MaterialTheme.typography.labelSmall)
                        Text(plain(lo), style = MaterialTheme.typography.labelSmall)
                    }
                    Canvas(
                        Modifier.weight(1f).height(160.dp)
                            .pointerInput(pts) {
                                detectTapGestures { o -> sel = nearestIdx(pts, o.x, size.width.toFloat()) }
                            }
                            .pointerInput(pts) {
                                detectDragGestures(
                                    onDragStart = { o -> sel = nearestIdx(pts, o.x, size.width.toFloat()) },
                                    onDrag = { c, _ -> sel = nearestIdx(pts, c.position.x, size.width.toFloat()) }
                                )
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        val rng = (hi - lo).toFloat().coerceAtLeast(1f)
                        fun yAt(v: Long): Float = h - 8f - (v - lo).toFloat() / rng * (h - 16f)
                        for (g in 0..2) {
                            val gy = 8f + g * (h - 16f) / 2f
                            drawLine(Color(0xFFDDDDDD), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                        }
                        for (i in 0 until pts.size - 1) {
                            drawLine(
                                Green,
                                Offset(xAt(pts[i].first, t0, t1, w), yAt(pts[i].second)),
                                Offset(xAt(pts[i + 1].first, t0, t1, w), yAt(pts[i + 1].second)),
                                strokeWidth = 5f, cap = StrokeCap.Round
                            )
                        }
                        if (pts.size <= 120) {
                            pts.forEach {
                                drawCircle(Gold, radius = 5f, center = Offset(xAt(it.first, t0, t1, w), yAt(it.second)))
                            }
                        }
                        val si = sel
                        if (si != null && si in pts.indices) {
                            val sx = xAt(pts[si].first, t0, t1, w)
                            drawLine(Color(0xFF9E9E9E), Offset(sx, 0f), Offset(sx, h), strokeWidth = 2f)
                            drawCircle(Green, radius = 9f, center = Offset(sx, yAt(pts[si].second)))
                            drawCircle(Gold, radius = 5f, center = Offset(sx, yAt(pts[si].second)))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtShort.format(Date(t0)), style = MaterialTheme.typography.labelSmall)
                    Text(fmtShort.format(Date(t1)), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(6.dp))
                val si = sel
                val chosen = if (si != null) pts.getOrNull(si) else null
                if (chosen != null) {
                    Text(
                        "${fmtFull.format(Date(chosen.first))} · ${rp(chosen.second)}",
                        fontWeight = FontWeight.Bold, color = Green
                    )
                } else {
                    Text("Sentuh atau geser grafik untuk melihat harga pada titik tertentu.", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                val first = pts.first().second
                val last = pts.last().second
                val ch = last - first
                val pct = if (first > 0L) ch * 100.0 / first else 0.0
                val up = ch >= 0L
                val top = pts.maxByOrNull { it.second }
                val bot = pts.minByOrNull { it.second }
                Line("Harga terakhir", rp(last), true)
                LineC("Perubahan periode", signed(ch) + " (" + "%+.1f%%".format(pct) + ")", if (up) Green else RedInk)
                if (top != null) Line("Tertinggi", rp(top.second) + " · " + fmtShort.format(Date(top.first)))
                if (bot != null) Line("Terendah", rp(bot.second) + " · " + fmtShort.format(Date(bot.first)))
            }
        }
    }
}

@Composable
fun LegendDot(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(c))
        Spacer(Modifier.width(4.dp))
        Text(t, style = MaterialTheme.typography.labelSmall)
    }
}

// Batang pemasukan dan pengeluaran per bulan, plus garis saving (selisih) bulanan
@Composable
fun TrendChart(inc: List<Long>, exp: List<Long>, diff: List<Long>, labels: List<String>) {
    val n = inc.size
    val ymax = maxOf(inc.maxOrNull() ?: 0L, exp.maxOrNull() ?: 0L, diff.maxOrNull() ?: 0L, 1L).toFloat()
    val ymin = minOf(0L, diff.minOrNull() ?: 0L).toFloat()
    Column {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val w = size.width
            val h = size.height
            val slot = w / n
            fun y(v: Long): Float = h - (v - ymin) / (ymax - ymin) * h
            val y0 = y(0L)
            drawLine(Color(0xFFBDBDBD), Offset(0f, y0), Offset(w, y0), strokeWidth = 2f)
            val bw = slot * 0.28f
            for (i in 0 until n) {
                val x0 = i * slot + slot * 0.12f
                drawRect(GreenMid, topLeft = Offset(x0, y(inc[i])), size = Size(bw, y0 - y(inc[i])))
                drawRect(Gold, topLeft = Offset(x0 + bw + slot * 0.04f, y(exp[i])), size = Size(bw, y0 - y(exp[i])))
            }
            for (i in 0 until n - 1) {
                drawLine(
                    SaveLine,
                    Offset(i * slot + slot / 2f, y(diff[i])),
                    Offset((i + 1) * slot + slot / 2f, y(diff[i + 1])),
                    strokeWidth = 4f, cap = StrokeCap.Round
                )
            }
            for (i in 0 until n) {
                drawCircle(SaveLine, radius = 5f, center = Offset(i * slot + slot / 2f, y(diff[i])))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(GreenMid, "Pemasukan")
            LegendDot(Gold, "Pengeluaran")
            LegendDot(SaveLine, "Saving")
        }
    }
}

@Composable
fun TargetDialog(current: Long, onSave: (Long) -> Unit, onClose: () -> Unit) {
    var v by remember { mutableStateOf(if (current > 0L) current.toString() else "") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Target tabungan bulanan") },
        text = {
            Column {
                Text("Target selisih pemasukan dikurangi pengeluaran dalam satu bulan.")
                Field(v, "Target per bulan (Rp)") { v = it }
            }
        },
        confirmButton = { TextButton({ onSave(v.num()); onClose() }) { Text("Simpan") } },
        dismissButton = { TextButton(onClose) { Text("Batal") } }
    )
}
