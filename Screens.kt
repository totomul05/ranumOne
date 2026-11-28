package com.ranum.one

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Backup as BackupIcon
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------- Komponen bersama ----------

@Composable
fun MenuRow(
    icon: ImageVector, title: String, subtitle: String? = null,
    trailing: ImageVector? = Icons.Rounded.ChevronRight, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).background(Pal.cur.pt, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pal.cur.mu)
            }
        }
        if (trailing != null) Icon(trailing, contentDescription = null, tint = Pal.cur.mu)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = Pal.cur.mu,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp)
    )
}

@Composable
fun SubScreen(title: String, onBack: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali", tint = Green)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Green)
        }
        val body = if (scroll) {
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)
        } else {
            Modifier.weight(1f).padding(horizontal = 2.dp)
        }
        Column(body) { content() }
    }
}

// ---------- Beranda ----------

@Composable
fun Dashboard(
    led: Ledger, syncState: Int, onSync: () -> Unit,
    checks: List<Setting>, onCheck: (Long, Long) -> Unit,
    target: Long, onTarget: (Long) -> Unit, onLoans: () -> Unit
) {
    var m by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var showCheck by remember { mutableStateOf(false) }
    var showTarget by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var showTrend by remember { mutableStateOf(false) }
    val saving = led.savingBefore(LocalDate.MAX)
    val nl = led.assets.sumOf { led.value(it) }
    val owed = led.outstandingBefore(LocalDate.MAX)
    val p = led.period("", m, m.plusMonths(1))
    val golds = led.assets.filter { it.gold }
    val goldModal = golds.sumOf { it.price }
    val goldNow = golds.sumOf { led.value(it) }
    val goldReady = led.goldPrice > 0L && goldModal > 0L
    val gDiff = goldNow - goldModal
    val gPct = if (goldModal > 0L) gDiff * 100.0 / goldModal else 0.0
    val gInk = if (gDiff >= 0L) Green else RedInk
    val ratio = if (p.expense == 0L) 0.0 else p.income.toDouble() / p.expense
    val dot = when (syncState) {
        1 -> Color(0xFFFFC107)
        2 -> Color(0xFF43A047)
        3 -> Color(0xFFE53935)
        else -> Color(0xFF9E9E9E)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ranumOne", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Green)
            Box {
                IconButton(onSync) {
                    Icon(Icons.Rounded.Sync, contentDescription = "Sinkron", tint = Green)
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                        .size(10.dp).background(dot, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Green)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Net worth", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                Text(
                    rp(saving + nl + owed), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = Color.White
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Saving likuid", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                        Text(rp(saving), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Aset non-likuid", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                        Text(rp(nl), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (owed != 0L) {
                        Column(Modifier.weight(1f)) {
                            Text("Hutang ke saving", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                            Text(rp(owed), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        if (golds.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (!goldReady) Color.White else if (gDiff >= 0L) GreenSoft else RedSoft
                ),
                border = BorderStroke(0.5.dp, Pal.cur.bd)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Aset emas", style = MaterialTheme.typography.labelMedium, color = Pal.cur.mu)
                        Text(rp(goldNow), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (!goldReady) {
                            Text("Isi harga emas di tab Aset", style = MaterialTheme.typography.bodySmall, color = Pal.cur.mu)
                        } else {
                            Text(
                                if (gDiff >= 0L) "UNTUNG" else "RUGI",
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = gInk
                            )
                            Text(signed(gDiff), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = gInk)
                            Text("%+.1f%%".format(gPct), style = MaterialTheme.typography.bodySmall, color = gInk)
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton({ m = m.minusMonths(1) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Bulan sebelumnya", tint = Pal.cur.mu)
            }
            Text(monthLabel(m), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            IconButton({ m = m.plusMonths(1) }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Bulan berikutnya", tint = Pal.cur.mu)
            }
        }

        Panel("") {
            Line("Pemasukan", rp(p.income))
            Line("Pengeluaran", rp(p.expense))
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Selisih", fontWeight = FontWeight.Bold, color = Green)
                Text(rp(p.diff), fontWeight = FontWeight.Bold, color = if (p.diff >= 0L) Green else RedInk)
            }
            Spacer(Modifier.height(8.dp))
            if (target > 0L) {
                val prog = (p.diff.toFloat() / target.toFloat()).coerceIn(0f, 1f)
                val pctT = maxOf(p.diff * 100.0 / target, 0.0)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target tabungan", style = MaterialTheme.typography.bodySmall, color = Pal.cur.mu)
                    Text("%.0f%%".format(pctT), style = MaterialTheme.typography.bodySmall, color = Pal.cur.mu)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { prog },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (p.diff >= target) GreenMid else Gold,
                    trackColor = GreenSoft
                )
                TextButton({ showTarget = true }) { Text("Ubah target (" + rp(target) + ")") }
            } else {
                TextButton({ showTarget = true }) { Text("Atur target tabungan") }
            }
        }

        Panel("") {
            MenuRow(
                Icons.Rounded.Assessment, "Rincian bulan ini", null,
                if (showDetail) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown
            ) { showDetail = !showDetail }
            if (showDetail) {
                Column(Modifier.padding(start = 48.dp, bottom = 6.dp)) {
                    Line("Persentase saving", "%.1f%%".format(p.pct))
                    Line("Rasio masuk : keluar", "%.2f x".format(ratio))
                    Line("Dana pindah ke aset baru", rp(p.newAsset))
                    Line("Total pemasukan (semua)", rp(led.days.sumOf { led.inc(it) }))
                    Line("Total pengeluaran (semua)", rp(led.days.sumOf { led.exp(it) }))
                }
            }
            HorizontalDivider(color = Pal.cur.bd)
            MenuRow(
                Icons.Rounded.ShowChart, "Tren 12 bulan", null,
                if (showTrend) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown
            ) { showTrend = !showTrend }
            if (showTrend) {
                val trend = remember(led, m) {
                    (11 downTo 0).map { k ->
                        val s = m.minusMonths(k.toLong())
                        s to led.period("", s, s.plusMonths(1))
                    }
                }
                Column(Modifier.padding(bottom = 8.dp)) {
                    TrendChart(
                        trend.map { it.second.income },
                        trend.map { it.second.expense },
                        trend.map { it.second.diff },
                        trend.map { monthLabel(it.first).substringBefore(" ").take(3) }
                    )
                }
            }
            HorizontalDivider(color = Pal.cur.bd)
            MenuRow(
                Icons.Rounded.SwapHoriz, "Hutang ke saving",
                if (owed > 0L) "Belum lunas " + rp(owed) else "Belum ada pinjaman"
            ) { onLoans() }
            HorizontalDivider(color = Pal.cur.bd)
            MenuRow(Icons.Rounded.AccountBalance, "Cek kas", "Bandingkan dengan uang sebenarnya") { showCheck = true }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showCheck) CashCheckDialog(saving, checks, onCheck) { showCheck = false }
    if (showTarget) TargetDialog(target, onTarget) { showTarget = false }
}

// ---------- Pengaturan (pusat menu) ----------

@Composable
fun SettingsHub(led: Ledger, syncState: Int, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val linked = prefs(ctx).getString("uri", null) != null
    val lockText = when (lockType(ctx)) {
        "pin" -> "PIN aktif"
        "pattern" -> "Pola aktif"
        else -> "Mati"
    }
    val printer = prefs(ctx).getString("printer_name", null) ?: "Belum dipilih"
    val reminder = if (Reminder.enabled(ctx)) "Aktif, pukul 19.00" else "Mati"
    val backupText = if (Backup.enabled(ctx)) "Aktif, tiap tengah malam" else "Mati"
    val syncText = if (!linked) {
        "Belum terhubung"
    } else {
        when (syncState) {
            2 -> "Tersinkron"
            3 -> "Gagal, coba lagi"
            else -> "Menyinkronkan"
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Pengaturan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Green)

        SectionLabel("Laporan")
        Panel("") {
            MenuRow(Icons.Rounded.Assessment, "Laporan dan cetak", "Harian, bulanan, tahunan") { onOpen("laporan") }
            HorizontalDivider(color = Pal.cur.bd)
            MenuRow(Icons.Rounded.ShowChart, "Grafik harga emas", "Riwayat naik turun harga") { onOpen("emas") }
        }

        SectionLabel("Keuangan")
        Panel("") {
            MenuRow(Icons.Rounded.AccountBalanceWallet, "Saldo awal saving", rp(led.otherSaving)) { onOpen("saldo") }
            HorizontalDivider(color = Pal.cur.bd)
            MenuRow(
                Icons.Rounded.SwapHoriz, "Hutang ke saving",
                led.outstandingBefore(LocalDate.MAX).let { if (it > 0L) "Belum lunas " + rp(it) else "Belum ada pinjaman" }
            ) { onOpen("hutang") }
        }

        SectionLabel("Sistem")
        Panel("") {
            MenuRow(Icons.Rounded.Sync, "Sinkronisasi", syncText) { onOpen("sinkron") }
            HorizontalDivider(color = Pal.cur.bd)
            MenuRow(Icons.Rounded.BackupIcon, "Cadangan otomatis", backupText) { onOpen("cadangan") }
            HorizontalDivider(color = Pal.cur.bd)
            MenuRow(Icons.Rounded.Print, "Printer Bluetooth", printer) { onOpen("printer") }
            HorizontalDivider(color = Pal.cur.bd)
            MenuRow(Icons.Rounded.Lock, "Kunci aplikasi", lockText) { onOpen("kunci") }
            HorizontalDivider(color = Pal.cur.bd)
            MenuRow(Icons.Rounded.Notifications, "Pengingat harian", reminder) { onOpen("pengingat") }
            HorizontalDivider(color = Pal.cur.bd)
            MenuRow(Icons.Rounded.Palette, "Warna tampilan", Pal.cur.name) { onOpen("tema") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ThemePanel() {
    val ctx = LocalContext.current
    Panel("Warna tampilan") {
        Text("Pilih warna aplikasi. Pilihan ini berlaku di HP ini.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Palettes.forEachIndexed { i, pl ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable {
                        Pal.idx = i
                        prefs(ctx).edit().putInt("theme", i).apply()
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(28.dp).background(pl.p, CircleShape))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(14.dp).background(pl.a, CircleShape))
                Spacer(Modifier.width(14.dp))
                Text(
                    pl.name, Modifier.weight(1f),
                    fontWeight = if (Pal.idx == i) FontWeight.Bold else null
                )
                if (Pal.idx == i) Icon(Icons.Rounded.Check, contentDescription = "Dipilih", tint = Green)
            }
        }
    }
}

// ---------- Saldo awal (dilindungi PIN) ----------

@Composable
fun SaldoAwalScreen(led: Ledger, dao: AppDao, scope: CoroutineScope) {
    val ctx = LocalContext.current
    val type = lockType(ctx)
    var unlocked by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var setup by remember { mutableStateOf(false) }
    var value by remember(led.otherSaving) { mutableStateOf(led.otherSaving.toString()) }
    var confirm by remember { mutableStateOf(false) }
    var mergeAsk by remember { mutableStateOf(false) }
    val oldSum = led.priors.sumOf { it.amount }

    Panel("Saldo awal saving") {
        Text(
            "Uang likuid yang sudah ada sebelum Anda mulai memakai aplikasi ini.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(6.dp))
        Line("Saldo awal saat ini", rp(led.otherSaving), true)
        if (oldSum != 0L) Line("Sisa hasil usaha lama (belum digabung)", rp(oldSum))
    }

    if (!unlocked) {
        Panel("Pengaman") {
            if (type == null) {
                Text("Agar tidak terubah tanpa sengaja, perubahan saldo awal dilindungi PIN. Atur PIN dulu.")
                Spacer(Modifier.height(8.dp))
                Button({ setup = true }, Modifier.fillMaxWidth()) { Text("Atur PIN") }
            } else {
                SecretEntry(
                    type,
                    if (type == "pin") "Masukkan PIN untuk mengubah" else "Gambar pola untuk mengubah",
                    err,
                    onChange = { d ->
                        if (type == "pin" && !prefs(ctx).contains("lock_len") && d.length in 4..5 && checkLock(ctx, d)) {
                            unlocked = true
                            err = ""
                        }
                    }
                ) { s ->
                    if (s.isNotEmpty() && checkLock(ctx, s)) {
                        unlocked = true
                        err = ""
                    } else {
                        err = "Salah, coba lagi."
                    }
                }
            }
        }
    } else {
        Panel("Ubah saldo awal") {
            Field(value, "Saldo awal saving (Rp)") { value = it }
            Spacer(Modifier.height(6.dp))
            Button({ confirm = true }, Modifier.fillMaxWidth()) { Text("Simpan") }
        }
        if (oldSum != 0L) {
            Panel("Sisa hasil usaha lama") {
                Text(
                    "Sisa hasil usaha dan saldo awal sama-sama uang likuid. Gabungkan agar cukup satu angka. Total saving tidak berubah.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton({ mergeAsk = true }, Modifier.fillMaxWidth()) { Text("Gabungkan ke saldo awal") }
            }
        }
    }

    if (setup) {
        LockSetupDialog(
            "pin",
            { s ->
                setLock(ctx, "pin", s)
                setup = false
                unlocked = true
            },
            { setup = false }
        )
    }
    if (confirm) {
        val nv = value.num()
        Confirm(
            "Ubah saldo awal?",
            "Dari " + rp(led.otherSaving) + " menjadi " + rp(nv) + ". Total saving akan berubah " + signed(nv - led.otherSaving) + ".",
            "Ubah",
            {
                confirm = false
                scope.launch { dao.putSetting(Setting("other", nv.toString(), Stamp.next())) }
            },
            { confirm = false }
        )
    }
    if (mergeAsk) {
        Confirm(
            "Gabungkan?",
            "Saldo awal menjadi " + rp(led.otherSaving + oldSum) + " (ditambah " + rp(oldSum) + " dari sisa hasil usaha lama).",
            "Gabungkan",
            {
                mergeAsk = false
                scope.launch {
                    dao.putSetting(Setting("other", (led.otherSaving + oldSum).toString(), Stamp.next()))
                    led.priors.forEach { dao.putPrior(it.copy(deleted = true, updated = Stamp.next())) }
                }
            },
            { mergeAsk = false }
        )
    }
}
