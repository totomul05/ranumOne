package com.ranum.one

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

val Green = Color(0xFF1B5E20)
val GreenMid = Color(0xFF2E7D32)
val GreenSoft = Color(0xFFE8F5E9)
val Gold = Color(0xFFFFC107)
val GoldSoft = Color(0xFFFFF8E1)
val GoldInk = Color(0xFF3E2C00)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AppDb.get(this).dao()
        Stamp.see(prefs(this).getLong("seen", 0L))
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Green, onPrimary = Color.White,
                    primaryContainer = GreenSoft, onPrimaryContainer = Green,
                    secondary = Gold, onSecondary = GoldInk,
                    secondaryContainer = Color(0xFFFFE082), onSecondaryContainer = GoldInk,
                    background = Color(0xFFFFFDF7), surface = Color(0xFFFFFDF7),
                    surfaceContainer = GoldSoft, surfaceContainerHighest = GreenSoft
                )
            ) { Gate(dao) }
        }
    }
}

// ---------- Komponen kecil ----------

@Composable
fun Gate(dao: AppDao) {
    val ctx = LocalContext.current
    var locked by remember { mutableStateOf(isLockEnabled(ctx)) }
    DisposableEffect(Unit) {
        var stopAt = 0L
        val lc = (ctx as ComponentActivity).lifecycle
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) stopAt = System.currentTimeMillis()
            if (e == Lifecycle.Event.ON_START && stopAt > 0L && isLockEnabled(ctx) &&
                System.currentTimeMillis() - stopAt > 60_000L
            ) {
                locked = true
            }
        }
        lc.addObserver(obs)
        onDispose { lc.removeObserver(obs) }
    }
    Box(Modifier.fillMaxSize()) {
        App(dao)
        if (locked) LockScreen { locked = false }
    }
}

@Composable
fun Confirm(title: String, text: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onYes) { Text(yes, color = RedInk) } },
        dismissButton = { TextButton(onNo) { Text("Batal") } }
    )
}

@Composable
fun Line(l: String, v: String, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(l, Modifier.weight(1f))
        Text(v, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

@Composable
fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = GreenSoft)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Green)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
fun Field(v: String, label: String, num: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = v,
        onValueChange = { onChange(if (num) it.filter(Char::isDigit) else it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (num) KeyboardType.Number else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    )
}

@Composable
fun pickDate(cur: LocalDate, onPick: (LocalDate) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    return {
        DatePickerDialog(
            ctx,
            { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) },
            cur.year, cur.monthValue - 1, cur.dayOfMonth
        ).show()
    }
}

fun monthLabel(d: LocalDate): String =
    d.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("id", "ID")) + " " + d.year

fun dailyFields(c: Daily?): List<String> = listOf(
    c?.o1.z(), c?.o2.z(), c?.fuel.z(),
    c?.income.z(), c?.incomeOther.z(), c?.noteIn ?: "",
    c?.mandatory.z(), c?.otherExp.z(), c?.noteExp ?: "",
    c?.bop.z()
)

fun Number?.z(): String = if (this == null || this.toLong() == 0L) "" else this.toString()

// ---------- Aplikasi ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(dao: AppDao) {
    val days by dao.dailies().collectAsState(initial = emptyList<Daily>())
    val assets by dao.assets().collectAsState(initial = emptyList<Asset>())
    val priors by dao.priors().collectAsState(initial = emptyList<Prior>())
    val sets by dao.settings().collectAsState(initial = emptyList<Setting>())
    val gold = sets.firstOrNull { it.k == "gold" }?.v?.toLongOrNull() ?: 0L
    val other = sets.firstOrNull { it.k == "other" }?.v?.toLongOrNull() ?: 0L
    val led = remember(days, assets, priors, gold, other) { Ledger(days, assets, priors, gold, other) }
    LaunchedEffect(days, assets, priors, sets) {
        days.forEach { Stamp.see(it.updated) }
        assets.forEach { Stamp.see(it.updated) }
        priors.forEach { Stamp.see(it.updated) }
        sets.forEach { Stamp.see(it.updated) }
    }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var settings by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf("") }
    // 0 abu-abu: belum terhubung, 1 kuning: sedang/menunggu, 2 hijau: tersinkron, 3 merah: gagal
    var syncState by remember { mutableIntStateOf(if (prefs(ctx).getString("uri", null) != null) 1 else 0) }
    var conflict by remember { mutableStateOf<SyncResult.Conflicts?>(null) }
    var exportMonth by remember { mutableStateOf<LocalDate?>(null) }

    val xlsx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { u ->
        val m = exportMonth
        if (u != null && m != null) {
            scope.launch {
                val msg = exportMonthXlsx(ctx, led, m, u)
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
    val onExport: (LocalDate) -> Unit = { m ->
        exportMonth = m
        xlsx.launch("ranumOne-%d-%02d.xlsx".format(m.year, m.monthValue))
    }

    var pendingPrint by remember { mutableStateOf<ByteArray?>(null) }
    fun doPrint(b: ByteArray) {
        scope.launch {
            val msg = printerSend(ctx, b)
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
    val btPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val b = pendingPrint
        pendingPrint = null
        if (ok && b != null) {
            doPrint(b)
        } else if (!ok) {
            Toast.makeText(ctx, "Izin Bluetooth diperlukan untuk mencetak.", Toast.LENGTH_LONG).show()
        }
    }
    val onPrint: (ByteArray) -> Unit = { b ->
        if (btGranted(ctx)) {
            doPrint(b)
        } else {
            pendingPrint = b
            btPerm.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    var busy by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(true) }

    fun syncCore(uri: Uri, policy: Policy?, write: Boolean, silent: Boolean) {
        if (busy && policy == null) {
            if (!silent) syncMsg = "Sedang menyinkronkan, coba lagi sebentar."
            return
        }
        busy = true
        if (!silent) refreshing = true
        scope.launch {
            try {
                if (!silent) { syncMsg = "Menyinkronkan..."; syncState = 1 }
                when (val r = doSync(ctx, dao, uri, policy, write)) {
                    is SyncResult.Done -> { syncMsg = r.msg; conflict = null; syncState = 2 }
                    is SyncResult.Fail -> { syncMsg = r.msg; syncState = 3 }
                    is SyncResult.Conflicts -> { conflict = r; syncMsg = "Ada konflik data, pilih versi yang dipakai."; syncState = 1 }
                }
            } finally {
                busy = false
                refreshing = false
            }
        }
    }
    val runSync: (Uri, Policy?, Boolean) -> Unit = { uri, policy, write -> syncCore(uri, policy, write, false) }

    // Sinkron otomatis hanya berjalan saat aplikasi terbuka di layar
    DisposableEffect(Unit) {
        val lc = (ctx as ComponentActivity).lifecycle
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) foreground = true
            if (e == Lifecycle.Event.ON_STOP) foreground = false
        }
        lc.addObserver(obs)
        onDispose { lc.removeObserver(obs) }
    }
    LaunchedEffect(foreground) {
        while (foreground) {
            val u = prefs(ctx).getString("uri", null)
            if (u == null) syncState = 0
            if (u != null && !busy && conflict == null) syncCore(Uri.parse(u), null, true, true)
            delay(10_000)
        }
    }

    val names = listOf("Dashboard", "Input", "Riwayat", "Aset", "Laporan")
    val marks = listOf("📊", "✏️", "🕘", "🪙", "📑")

    Scaffold(bottomBar = {
        NavigationBar {
            names.forEachIndexed { i, n ->
                NavigationBarItem(
                    selected = tab == i && !settings,
                    onClick = { tab = i; settings = false },
                    icon = { Text(marks[i]) },
                    label = { Text(n, maxLines = 1) }
                )
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    val u = prefs(ctx).getString("uri", null)
                    if (u != null) {
                        syncCore(Uri.parse(u), null, true, false)
                    } else {
                        Toast.makeText(ctx, "Belum terhubung ke file sinkron. Atur di Pengaturan.", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
            if (settings) {
                SettingsScreen(led, dao, scope, syncMsg, runSync, onPrint) { settings = false }
            } else {
                when (tab) {
                    0 -> Dashboard(
                        led, syncState,
                        onSync = {
                            val u = prefs(ctx).getString("uri", null)
                            if (u != null) runSync(Uri.parse(u), null, true) else settings = true
                        },
                        onSettings = { settings = true },
                        checks = sets.filter { it.k.startsWith("cek:") },
                        onCheck = { calc, real ->
                            scope.launch {
                                val ts = System.currentTimeMillis()
                                dao.putSetting(Setting("cek:$ts", "$calc;$real", Stamp.next()))
                            }
                        }
                    )
                    1 -> InputScreen(led, dao, scope, date) { date = it }
                    2 -> History(led) { date = it; tab = 1 }
                    3 -> Assets(led, dao, scope)
                    else -> Reports(led, onExport, onPrint)
                }
            }
            }
        }
    }

    conflict?.let { c ->
        AlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text("Konflik data (${c.lines.size})") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Data berikut diubah di dua perangkat sejak sinkron terakhir. Pilih versi yang dipakai untuk semuanya:")
                    Spacer(Modifier.height(8.dp))
                    c.lines.take(10).forEach { Text(it); Spacer(Modifier.height(6.dp)) }
                    if (c.lines.size > 10) Text("...dan ${c.lines.size - 10} lainnya")
                }
            },
            confirmButton = { TextButton({ runSync(c.uri, Policy.LOCAL, c.write) }) { Text("Versi HP") } },
            dismissButton = {
                Row {
                    TextButton({ runSync(c.uri, Policy.REMOTE, c.write) }) { Text("Versi OneDrive") }
                    TextButton({ conflict = null }) { Text("Batal") }
                }
            }
        )
    }
}

// ---------- Dashboard ----------

@Composable
fun StatCard(title: String, value: String, bg: Color, fg: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = fg)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}

@Composable
fun Dashboard(
    led: Ledger, syncState: Int, onSync: () -> Unit, onSettings: () -> Unit,
    checks: List<Setting>, onCheck: (Long, Long) -> Unit
) {
    var showCheck by remember { mutableStateOf(false) }
    var m by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    val saving = led.savingBefore(LocalDate.MAX)
    val nl = led.assets.sumOf { led.value(it) }
    val tin = led.days.sumOf { led.inc(it) }
    val tout = led.days.sumOf { led.exp(it) }
    val p = led.period("", m, m.plusMonths(1))
    val ratio = if (p.expense == 0L) 0.0 else p.income.toDouble() / p.expense
    val mx = maxOf(p.income, p.expense, 1L).toFloat()
    val dot = when (syncState) {
        1 -> Color(0xFFFFC107)
        2 -> Color(0xFF43A047)
        3 -> Color(0xFFE53935)
        else -> Color(0xFF9E9E9E)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("ranumOne", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Green)
                Text("One Roof, One financial Journey", style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.size(12.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
            Button(onSync, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = GoldInk)) {
                Text("🔄 Sinkron")
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Green)) {
            Column(Modifier.padding(16.dp)) {
                Text("TOTAL NET WORTH", style = MaterialTheme.typography.labelLarge, color = Gold)
                Text(rp(saving + nl), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Saving likuid + aset non-liquid", style = MaterialTheme.typography.bodySmall, color = GreenSoft)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Saving (Likuid)", rp(saving), GreenSoft, Green, Modifier.weight(1f))
            StatCard("Non-Liquid Asset", rp(nl), GoldSoft, GoldInk, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Total Pemasukan", rp(tin), GreenSoft, Green, Modifier.weight(1f))
            StatCard("Total Pengeluaran", rp(tout), Color(0xFFFFF3E0), Color(0xFF8D4B00), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Panel("Indikator bulan") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton({ m = m.minusMonths(1) }) { Text("◀") }
                Text(monthLabel(m), fontWeight = FontWeight.Bold)
                OutlinedButton({ m = m.plusMonths(1) }) { Text("▶") }
            }
            Line("Pemasukan bulan ini", rp(p.income))
            LinearProgressIndicator(
                progress = { p.income.toFloat() / mx },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = GreenMid, trackColor = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Line("Pengeluaran bulan ini", rp(p.expense))
            LinearProgressIndicator(
                progress = { p.expense.toFloat() / mx },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Gold, trackColor = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Line("Selisih operasional", rp(p.diff), true)
            Line("Persentase saving dari pemasukan", "%.1f%%".format(p.pct))
            LinearProgressIndicator(
                progress = { (p.pct / 100.0).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Gold, trackColor = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Line("Rasio pemasukan : pengeluaran", "%.2f x".format(ratio))
            Line("Dana pindah ke aset baru", rp(p.newAsset))
        }
        OutlinedButton({ showCheck = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cek kas") }
        OutlinedButton(onSettings, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Pengaturan & saldo awal") }
        if (showCheck) CashCheckDialog(saving, checks, onCheck) { showCheck = false }
        Spacer(Modifier.height(8.dp))
        Text(
            "Pembelian aset tidak mengurangi net worth: hanya memindahkan dana dari saving ke non-liquid.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ---------- Input Harian ----------

@Composable
fun InputScreen(led: Ledger, dao: AppDao, scope: CoroutineScope, date: LocalDate, onDate: (LocalDate) -> Unit) {
    val cur = led.days.firstOrNull { it.date == date.toEpochDay() }
    val f = remember(date) { mutableStateListOf<String>().apply { addAll(dailyFields(cur)) } }
    var dirty by remember(date) { mutableStateOf(false) }
    var base by remember(date) { mutableStateOf(cur) }
    LaunchedEffect(date, cur) {
        if (!dirty) {
            base = cur
            val n = dailyFields(cur)
            for (i in n.indices) f[i] = n[i]
        }
    }
    var msg by remember(date) { mutableStateOf("") }
    val pick = pickDate(date, onDate)
    val labels = listOf(
        "TRX Outlet 1 (orang)", "TRX Outlet 2 (orang)", "TRX Fuel (orang)",
        "Pemasukan (Rp)", "Pemasukan Lain (Rp)", "Keterangan Pemasukan Lain",
        "Pengeluaran Wajib (Rp)", "Pengeluaran Lain (Rp)", "Keterangan Pengeluaran Lain",
        "BOP Toko (Rp)"
    )
    val inc = f[3].num() + f[4].num()
    val exp = f[6].num() + f[7].num() + f[9].num()
    var confirmSave by remember { mutableStateOf(false) }
    var askDel by remember { mutableStateOf(false) }

    fun save() {
        scope.launch {
            dao.putDaily(
                Daily(
                    date.toEpochDay(),
                    f[0].num().toInt(), f[1].num().toInt(), f[2].num().toInt(),
                    f[3].num(), f[4].num(), f[5],
                    f[6].num(), f[7].num(), f[8],
                    f[9].num(), Stamp.next(), false
                )
            )
            dirty = false
            msg = "Tersimpan ✓"
        }
    }

    fun warnings(): List<String> {
        val w = ArrayList<String>()
        if (inc == 0L && exp == 0L && f[0].num() + f[1].num() + f[2].num() == 0L) {
            w.add("Semua nilai masih 0.")
        }
        if (inc >= 1_000_000_000L || exp >= 1_000_000_000L) {
            w.add("Ada angka lebih dari Rp1 miliar. Cek jumlah nol.")
        }
        val e = date.toEpochDay()
        val recent = led.days.filter { it.date < e && it.date >= e - 30 }
        if (recent.size >= 5) {
            val ai = recent.sumOf { led.inc(it) } / recent.size
            val ae = recent.sumOf { led.exp(it) } / recent.size
            if (ai > 0L && inc > ai * 5) w.add("Pemasukan ${rp(inc)} lebih dari 5x rata-rata 30 hari terakhir (${rp(ai)}).")
            if (ae > 0L && exp > ae * 5) w.add("Pengeluaran ${rp(exp)} lebih dari 5x rata-rata 30 hari terakhir (${rp(ae)}).")
        }
        if (f[4].num() > 0L && f[5].isBlank()) w.add("Pemasukan Lain terisi tetapi keterangan kosong.")
        if (f[7].num() > 0L && f[8].isBlank()) w.add("Pengeluaran Lain terisi tetapi keterangan kosong.")
        if (date.isAfter(LocalDate.now())) w.add("Tanggal ini di masa depan.")
        return w
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton({ onDate(date.minusDays(1)) }) { Text("◀") }
            TextButton(pick) { Text(date.toString(), fontWeight = FontWeight.Bold) }
            OutlinedButton({ onDate(date.plusDays(1)) }) { Text("▶") }
        }
        TextButton({ onDate(LocalDate.now()) }) { Text("Ke hari ini") }
        labels.forEachIndexed { i, l ->
            Field(f[i], l, num = !(i == 5 || i == 8)) { f[i] = it; dirty = true }
        }
        if (dirty && cur != base) {
            Text("⚠ Data tanggal ini baru diubah dari perangkat lain. Menyimpan akan menimpanya.")
        }
        Panel("Hitungan otomatis") {
            Line("Total Transaksi", "${f[0].num() + f[1].num() + f[2].num()} orang")
            Line("Total Pemasukan", rp(inc))
            Line("Total Pengeluaran", rp(exp))
            Line("Perubahan Saving Harian", rp(inc - exp), true)
        }
        Button(
            onClick = { confirmSave = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Simpan") }
        if (msg.isNotEmpty()) Text(msg)
        if (cur != null) {
            TextButton({ askDel = true }) { Text("Hapus data tanggal ini") }
        }
    }

    if (confirmSave) {
        val warns = warnings()
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Periksa sebelum menyimpan") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(date.toString(), fontWeight = FontWeight.Bold)
                    Line("Transaksi", "${f[0].num() + f[1].num() + f[2].num()} orang")
                    Line("Pemasukan", rp(inc))
                    Line("Pengeluaran", rp(exp))
                    Line("Perubahan saving", rp(inc - exp), true)
                    if (warns.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        warns.forEach {
                            Text("⚠ $it", color = Color(0xFF8D4B00), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({ confirmSave = false; save() }) {
                    Text(if (warns.isEmpty()) "Simpan" else "Simpan tetap")
                }
            },
            dismissButton = { TextButton({ confirmSave = false }) { Text("Periksa lagi") } }
        )
    }
    if (askDel && cur != null) {
        Confirm(
            "Hapus data?",
            "Data tanggal $date akan dihapus (pemasukan ${rp(led.inc(cur))}, pengeluaran ${rp(led.exp(cur))}).",
            "Hapus",
            { askDel = false; scope.launch { dao.putDaily(cur.copy(deleted = true, updated = Stamp.next())) } },
            { askDel = false }
        )
    }
}

// ---------- Riwayat ----------

@Composable
fun History(led: Ledger, onOpen: (LocalDate) -> Unit) {
    if (led.days.isEmpty()) {
        Text("Belum ada data harian.", Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(led.days.reversed()) { d ->
            Column(
                Modifier.fillMaxWidth()
                    .clickable { onOpen(LocalDate.ofEpochDay(d.date)) }
                    .padding(12.dp)
            ) {
                Text(LocalDate.ofEpochDay(d.date).toString(), fontWeight = FontWeight.Bold)
                Text("Masuk ${rp(led.inc(d))} · Keluar ${rp(led.exp(d))}")
                Text("Saving ${rp(led.inc(d) - led.exp(d))} · Transaksi ${d.o1 + d.o2 + d.fuel} orang")
            }
            HorizontalDivider()
        }
    }
}

// ---------- Non-Liquid Asset ----------

val RedInk = Color(0xFFC62828)
val RedSoft = Color(0xFFFFEBEE)

fun signed(v: Long): String = (if (v > 0) "+" else "") + rp(v)

// Untung/rugi aset emas = nilai buyback sekarang - harga beli. Null jika bukan aset emas atau harga emas belum diisi.
fun goldPl(led: Ledger, a: Asset): Long? = if (a.gold && led.goldPrice > 0) led.value(a) - a.price else null

@Composable
fun LineC(l: String, v: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(l, Modifier.weight(1f), color = color)
        Text(v, color = color)
    }
}

@Composable
fun Assets(led: Ledger, dao: AppDao, scope: CoroutineScope) {
    var gp by remember(led.goldPrice) { mutableStateOf(led.goldPrice.toString()) }
    var edit by remember { mutableStateOf<Asset?>(null) }
    var filter by remember { mutableIntStateOf(0) }
    var delAsset by remember { mutableStateOf<Asset?>(null) }
    var askGold by remember { mutableStateOf(false) }
    val ctxA = LocalContext.current

    // Spread emas: harga beli semua aset emas dibandingkan dengan nilai buyback hari ini
    val golds = led.assets.filter { it.gold }
    val modal = golds.sumOf { it.price }
    val nowVal = golds.sumOf { led.value(it) }
    val grams = golds.sumOf { it.weight }
    val ready = led.goldPrice > 0 && modal > 0
    val diff = nowVal - modal
    val pct = if (modal > 0) diff * 100.0 / modal else 0.0
    val sumBg = if (!ready) GoldSoft else if (diff >= 0) GreenSoft else RedSoft
    val sumFg = if (!ready) GoldInk else if (diff >= 0) Green else RedInk
    val nUntung = led.assets.count { (goldPl(led, it) ?: 0L) > 0L }
    val nRugi = led.assets.count { (goldPl(led, it) ?: 0L) < 0L }
    val items = led.assets.filter { a ->
        val pl = goldPl(led, a)
        when (filter) {
            1 -> pl != null && pl > 0L
            2 -> pl != null && pl < 0L
            else -> true
        }
    }.sortedByDescending { a ->
        val pl = goldPl(led, a)
        if (pl == null) -1e9 else pl * 100.0 / maxOf(a.price, 1L)
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
        Panel("Total Non-Liquid: ${rp(led.assets.sumOf { led.value(it) })}") {
            Field(gp, "Harga buyback emas /gram (Rp)") { gp = it }
            Button({
                val nv = gp.num()
                val ov = led.goldPrice
                if (nv <= 0L) {
                    Toast.makeText(ctxA, "Isi harga emas lebih dari 0.", Toast.LENGTH_LONG).show()
                } else if (ov > 0L && Math.abs(nv - ov) * 100.0 / ov > 15.0) {
                    askGold = true
                } else {
                    scope.launch { dao.putSetting(Setting("gold", nv.toString(), Stamp.next())) }
                }
            }) {
                Text("Simpan harga emas")
            }
        }
        Card(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = sumBg)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("SPREAD EMAS TOTAL", style = MaterialTheme.typography.labelLarge, color = sumFg)
                if (!ready) {
                    Text("Isi harga emas hari ini dan tambahkan aset emas untuk melihat untung atau rugi.", color = sumFg)
                } else {
                    Text(signed(diff), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = sumFg)
                    Text(
                        "%+.1f%%".format(pct) + " · " + (if (diff >= 0) "UNTUNG" else "RUGI"),
                        fontWeight = FontWeight.Bold, color = sumFg
                    )
                    Spacer(Modifier.height(8.dp))
                    LineC("Total harga beli (modal)", rp(modal), sumFg)
                    LineC("Nilai sekarang (buyback)", rp(nowVal), sumFg)
                    LineC("Berat emas total", "${"%.2f".format(grams)} gr", sumFg)
                    LineC("Harga emas hari ini /gr", rp(led.goldPrice), sumFg)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Dihitung dari harga buyback per gram, jadi sudah memperhitungkan selisih beli-jual toko. Aset non-emas dinilai dari harga beli dan tidak ikut dihitung.",
                    style = MaterialTheme.typography.bodySmall, color = sumFg
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Semua (${led.assets.size})", "Untung ($nUntung)", "Rugi ($nRugi)").forEachIndexed { i, t ->
                if (filter == i) {
                    Button({ filter = i }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(t, maxLines = 1) }
                } else {
                    OutlinedButton({ filter = i }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(t, maxLines = 1) }
                }
            }
        }
        if (items.isEmpty()) Text("Tidak ada aset pada filter ini.", Modifier.padding(8.dp))
        items.forEach { a ->
            val pl = goldPl(led, a)
            val itemPct = if (pl != null && a.price > 0) pl * 100.0 / a.price else 0.0
            val tint = if (pl == null) Color(0xFFEEEEEE) else if (pl >= 0) GreenSoft else RedSoft
            val ink = if (pl == null) Color(0xFF616161) else if (pl >= 0) Green else RedInk
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { edit = a },
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${a.name} (${a.type})", fontWeight = FontWeight.Bold)
                        if (a.gold && a.weight > 0) {
                            Text(
                                "${a.weight} gr · beli ${rp(a.price)} (${rp(Math.round(a.price / a.weight))}/gr)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text("Beli ${rp(a.price)} · dinilai dari harga beli", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Nilai sekarang ${rp(led.value(a))} · ${if (a.baru) "Aset Baru" else "Aset Lama"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Box(Modifier.background(tint, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            if (pl == null) {
                                Text("—", color = ink)
                            } else {
                                Text(signed(pl), fontWeight = FontWeight.Bold, color = ink, style = MaterialTheme.typography.bodySmall)
                                Text("%+.1f%%".format(itemPct), color = ink, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(88.dp))
    }
    ExtendedFloatingActionButton(
        onClick = {
            edit = Asset(
                uid = "", name = "", type = "Gold Bar", weight = 0.0, price = 0,
                date = LocalDate.now().toEpochDay(), baru = false, gold = true
            )
        },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        containerColor = Gold,
        contentColor = GoldInk
    ) { Text("+ Tambah aset", fontWeight = FontWeight.Bold) }
    }

    edit?.let { a ->
        AssetDialog(
            a,
            onSave = { x ->
                scope.launch {
                    val y = if (x.uid.isEmpty()) x.copy(uid = UUID.randomUUID().toString()) else x
                    dao.putAsset(y.copy(updated = Stamp.next()))
                }
                edit = null
            },
            onDelete = { delAsset = a; edit = null },
            onDismiss = { edit = null }
        )
    }
    delAsset?.let { a ->
        Confirm(
            "Hapus aset?",
            "${a.name} (${rp(a.price)}) akan dihapus dari daftar aset.",
            "Hapus",
            { delAsset = null; scope.launch { dao.putAsset(a.copy(deleted = true, updated = Stamp.next())) } },
            { delAsset = null }
        )
    }
    if (askGold) {
        Confirm(
            "Harga emas berubah besar",
            "Harga baru ${rp(gp.num())}/gr berbeda lebih dari 15% dari harga sebelumnya ${rp(led.goldPrice)}/gr. Lanjutkan menyimpan?",
            "Simpan",
            { askGold = false; scope.launch { dao.putSetting(Setting("gold", gp.num().toString(), Stamp.next())) } },
            { askGold = false }
        )
    }
}

@Composable
fun AssetDialog(a: Asset, onSave: (Asset) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(a.name) }
    var type by remember { mutableStateOf(a.type) }
    var w by remember { mutableStateOf(if (a.weight == 0.0) "" else a.weight.toString()) }
    var price by remember { mutableStateOf(if (a.price == 0L) "" else a.price.toString()) }
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(a.date)) }
    var baru by remember { mutableStateOf(a.baru) }
    var gold by remember { mutableStateOf(a.gold) }
    val pick = pickDate(date) { date = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (a.uid.isEmpty()) "Tambah aset" else "Ubah aset") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Field(name, "Nama aset", false) { name = it }
                Field(type, "Jenis aset (Gold Bar, Ring, ...)", false) { type = it }
                OutlinedTextField(
                    value = w,
                    onValueChange = { s -> w = s.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Berat (gram)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
                Field(price, "Harga beli (Rp)") { price = it }
                OutlinedButton(pick) { Text("Tanggal: $date") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(baru, { baru = it })
                    Text("Aset Baru (dibeli dari saving)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(gold, { gold = it })
                    Text("Dinilai dari harga emas (berat × harga/gram)")
                }
            }
        },
        confirmButton = {
            TextButton({
                onSave(
                    a.copy(
                        name = name, type = type,
                        weight = w.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        price = price.num(), date = date.toEpochDay(),
                        baru = baru, gold = gold
                    )
                )
            }) { Text("Simpan") }
        },
        dismissButton = {
            Row {
                if (a.uid.isNotEmpty()) TextButton(onDelete) { Text("Hapus") }
                TextButton(onDismiss) { Text("Batal") }
            }
        }
    )
}

// ---------- Laporan ----------

@Composable
fun Reports(led: Ledger, onExport: (LocalDate) -> Unit, onPrint: (ByteArray) -> Unit) {
    var mode by remember { mutableIntStateOf(1) }
    var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var menu by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf(LocalDate.now().minusDays(6)) }
    var to by remember { mutableStateOf(LocalDate.now()) }
    var pf by remember { mutableIntStateOf(0) }
    val pickFrom = pickDate(from) { from = it }
    val pickTo = pickDate(to) { to = it }
    val yearly = mode == 2
    val year = month.year

    Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Harian", "Bulanan", "Tahunan").forEachIndexed { i, t ->
                if (mode == i) {
                    Button({ mode = i }, Modifier.weight(1f)) { Text(t, maxLines = 1) }
                } else {
                    OutlinedButton({ mode = i }, Modifier.weight(1f)) { Text(t, maxLines = 1) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (mode == 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(pickFrom, Modifier.weight(1f)) { Text("Dari: $from", maxLines = 1) }
                OutlinedButton(pickTo, Modifier.weight(1f)) { Text("Sampai: $to", maxLines = 1) }
            }
            val n = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
            val valid = n in 1L..30L
            if (!valid) {
                Text(
                    if (n < 1L) "Tanggal akhir harus sama atau setelah tanggal awal." else "Rentang maksimal 30 hari (sekarang $n hari).",
                    color = RedInk, modifier = Modifier.padding(top = 6.dp)
                )
            }
            val rows = led.days.filter { it.date >= from.toEpochDay() && it.date <= to.toEpochDay() }
            Panel("Rincian harian") {
                if (rows.isEmpty()) {
                    Text("Tidak ada data pada rentang ini.")
                } else {
                    Cols("Tgl", "Masuk", "Keluar", "Selisih", true)
                    rows.forEach { d ->
                        val dt = LocalDate.ofEpochDay(d.date)
                        Cols(
                            "%02d/%02d".format(dt.dayOfMonth, dt.monthValue),
                            plain(led.inc(d)), plain(led.exp(d)), plain(led.inc(d) - led.exp(d))
                        )
                    }
                    HorizontalDivider()
                    val tin = rows.sumOf { led.inc(it) }
                    val tout = rows.sumOf { led.exp(it) }
                    Cols("Total", plain(tin), plain(tout), plain(tin - tout), true)
                }
            }
            PrintFilterRow(pf) { pf = it }
            Button(
                { onPrint(receiptDaily(led, from, to, pf)) },
                Modifier.fillMaxWidth(),
                enabled = valid
            ) { Text("🖨 Cetak laporan harian") }
        } else {
            val p = if (yearly) {
                led.period("Tahun $year", LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1))
            } else {
                led.period(monthLabel(month), month, month.plusMonths(1))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton({ month = month.minusYears(1) }) { Text("◀") }
                Text("Tahun $year", fontWeight = FontWeight.Bold)
                OutlinedButton({ month = month.plusYears(1) }) { Text("▶") }
            }
            if (!yearly) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton({ month = month.minusMonths(1) }) { Text("◀") }
                    Box {
                        TextButton({ menu = true }) {
                            Text(monthLabel(month).substringBefore(" ") + " ▾", fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            (1..12).forEach { mth ->
                                DropdownMenuItem(
                                    text = { Text(monthLabel(LocalDate.of(year, mth, 1)).substringBefore(" ")) },
                                    onClick = { month = LocalDate.of(year, mth, 1); menu = false }
                                )
                            }
                        }
                    }
                    OutlinedButton({ month = month.plusMonths(1) }) { Text("▶") }
                }
            }
            TextButton({ month = LocalDate.now().withDayOfMonth(1) }) {
                Text(if (yearly) "Ke tahun ini" else "Ke bulan ini")
            }
            Panel(p.label) {
                Line("Total Transaksi", "${p.trx} orang")
                Line("Pemasukan", rp(p.income))
                Line("Pengeluaran", rp(p.expense))
                Line("Selisih Operasional", rp(p.diff))
                Line("Pembelian Aset Baru", rp(p.newAsset))
                Line("Perubahan Saving", rp(p.savingChange))
                Line("Saving Akhir", rp(p.savingEnd))
                Line("Non-Liquid Akhir", rp(p.nonLiquidEnd))
                Line("Net Worth Akhir", rp(p.netWorth), true)
                Line("% Saving dari Pemasukan", "%.1f%%".format(p.pct))
                if (!yearly) {
                    OutlinedButton({ onExport(month) }, Modifier.fillMaxWidth()) {
                        Text("⬇ Unduh Excel bulan ini")
                    }
                }
            }
            PrintFilterRow(pf) { pf = it }
            Button(
                { onPrint(if (yearly) receiptYear(led, year, pf) else receiptMonth(led, month, pf)) },
                Modifier.fillMaxWidth()
            ) { Text(if (yearly) "🖨 Cetak laporan tahunan" else "🖨 Cetak laporan bulanan") }
            if (yearly) {
                val rows = (1..12).map {
                    val s = LocalDate.of(year, it, 1)
                    s to led.period("", s, s.plusMonths(1))
                }
                Panel("Rincian per bulan $year") {
                    Cols("Bulan", "Pemasukan", "Pengeluaran", "Selisih", true)
                    rows.forEach { (s, q) ->
                        Cols(
                            monthLabel(s).substringBefore(" ").take(3),
                            plain(q.income), plain(q.expense), plain(q.diff)
                        )
                    }
                    HorizontalDivider()
                    Cols(
                        "Total",
                        plain(rows.sumOf { it.second.income }),
                        plain(rows.sumOf { it.second.expense }),
                        plain(rows.sumOf { it.second.diff }),
                        true
                    )
                }
            }
        }
    }
}

@Composable
fun PrintFilterRow(sel: Int, onSel: (Int) -> Unit) {
    Text(
        "Isi cetakan", style = MaterialTheme.typography.labelLarge, color = Green,
        modifier = Modifier.padding(top = 8.dp)
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Semua", "Pemasukan", "Pengeluaran").forEachIndexed { i, t ->
            if (sel == i) {
                Button({ onSel(i) }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Text(t, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                OutlinedButton({ onSel(i) }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Text(t, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun CashCheckDialog(calc: Long, checks: List<Setting>, onSave: (Long, Long) -> Unit, onClose: () -> Unit) {
    var real by remember { mutableStateOf("") }
    val r = real.num()
    val diff = r - calc
    val hist = checks
        .filter { it.k.startsWith("cek:") }
        .sortedByDescending { it.k.removePrefix("cek:").toLongOrNull() ?: 0L }
        .take(5)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Cek kas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Line("Saldo saving di aplikasi", rp(calc), true)
                Field(real, "Uang sebenarnya (Rp)") { real = it }
                if (real.isNotEmpty()) {
                    val msg = when {
                        diff == 0L -> "Cocok. Pembukuan sesuai uang sebenarnya."
                        diff > 0L -> "Uang sebenarnya LEBIH ${rp(diff)}. Kemungkinan ada pemasukan yang belum dicatat."
                        else -> "Uang sebenarnya KURANG ${rp(-diff)}. Kemungkinan ada pengeluaran yang belum dicatat."
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(msg, color = if (diff == 0L) Green else RedInk, fontWeight = FontWeight.Bold)
                }
                if (hist.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Riwayat cek terakhir", style = MaterialTheme.typography.labelLarge, color = Green)
                    val fmt = java.text.SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID"))
                    hist.forEach { h ->
                        val ts = h.k.removePrefix("cek:").toLongOrNull() ?: 0L
                        val p = h.v.split(";")
                        val d = (p.getOrNull(1)?.toLongOrNull() ?: 0L) - (p.getOrNull(0)?.toLongOrNull() ?: 0L)
                        Line(fmt.format(java.util.Date(ts)), if (d == 0L) "Cocok" else signed(d))
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                if (real.isNotEmpty()) {
                    onSave(calc, r)
                    onClose()
                }
            }) { Text("Simpan hasil cek") }
        },
        dismissButton = { TextButton(onClose) { Text("Tutup") } }
    )
}

fun plain(v: Long): String = rp(v).replace("Rp", "")

@Composable
fun Cols(a: String, b: String, c: String, d: String, bold: Boolean = false) {
    val st = MaterialTheme.typography.bodySmall
    val w = if (bold) FontWeight.Bold else null
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(a, modifier = Modifier.weight(0.8f), style = st, fontWeight = w)
        Text(b, modifier = Modifier.weight(1.2f), style = st, fontWeight = w, textAlign = TextAlign.End)
        Text(c, modifier = Modifier.weight(1.2f), style = st, fontWeight = w, textAlign = TextAlign.End)
        Text(d, modifier = Modifier.weight(1.2f), style = st, fontWeight = w, textAlign = TextAlign.End)
    }
}

// ---------- Pengaturan ----------

@Composable
fun SettingsScreen(
    led: Ledger, dao: AppDao, scope: CoroutineScope,
    syncMsg: String, runSync: (Uri, Policy?, Boolean) -> Unit, onPrint: (ByteArray) -> Unit, onBack: () -> Unit
) {
    var other by remember(led.otherSaving) { mutableStateOf(led.otherSaving.toString()) }
    var note by remember { mutableStateOf("") }
    var pd by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }
    var delPrior by remember { mutableStateOf<Prior?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
        OutlinedButton(onBack) { Text("◀ Kembali") }
        SyncPanel(dao, scope, syncMsg, runSync)
        PrinterPanel(onPrint)
        LockPanel()
        Panel("Saldo awal saving: ${rp(led.startSaving)}") {
            Field(other, "Saldo awal saving lain (di luar usaha)") { other = it }
            Button({ scope.launch { dao.putSetting(Setting("other", other.num().toString(), Stamp.next())) } }) {
                Text("Simpan")
            }
        }
        Panel("Sisa hasil usaha sebelumnya") {
            led.priors.forEach { p ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${p.note} · ${p.date}\n${rp(p.amount)}", Modifier.weight(1f))
                    TextButton({ delPrior = p }) { Text("Hapus") }
                }
            }
            Field(note, "Keterangan", false) { note = it }
            Field(pd, "Tanggal / periode", false) { pd = it }
            Field(amt, "Jumlah (Rp)") { amt = it }
            Button({
                if (amt.num() > 0L) {
                    scope.launch { dao.putPrior(Prior(UUID.randomUUID().toString(), note, pd, amt.num(), Stamp.next())) }
                    note = ""; pd = ""; amt = ""
                }
            }) { Text("Tambah") }
        }
    }
    delPrior?.let { p ->
        Confirm(
            "Hapus sisa hasil usaha?",
            "${p.note} (${rp(p.amount)}) akan dihapus dan saldo awal saving ikut berkurang.",
            "Hapus",
            { delPrior = null; scope.launch { dao.putPrior(p.copy(deleted = true, updated = Stamp.next())) } },
            { delPrior = null }
        )
    }
}

// ---------- Sinkronisasi OneDrive & cadangan ----------

@Composable
fun SyncPanel(dao: AppDao, scope: CoroutineScope, msg: String, runSync: (Uri, Policy?, Boolean) -> Unit) {
    val ctx = LocalContext.current
    var linked by remember { mutableStateOf(prefs(ctx).getString("uri", null)) }
    var info by remember { mutableStateOf("") }

    fun link(u: Uri?) {
        if (u == null) return
        try {
            ctx.contentResolver.takePersistableUriPermission(
                u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
        }
        prefs(ctx).edit().putString("uri", u.toString()).putLong("last", 0L).apply()
        linked = u.toString()
        runSync(u, null, true)
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { link(it) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { link(it) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) scope.launch { info = exportTo(ctx, dao, u) }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) runSync(u, null, false)
    }

    Panel("Sinkronisasi OneDrive") {
        Text(if (linked != null) "File data sudah terhubung. Sinkron otomatis tiap 10 detik saat aplikasi terbuka." else "Belum terhubung ke file OneDrive/Drive.")
        if (msg.isNotEmpty()) Text(msg)
        if (info.isNotEmpty()) Text(info)
        Spacer(Modifier.height(6.dp))
        if (linked != null) {
            Button({ runSync(Uri.parse(linked!!), null, true) }, Modifier.fillMaxWidth()) { Text("Sinkronkan sekarang") }
            TextButton({ prefs(ctx).edit().remove("uri").apply(); linked = null }) { Text("Putuskan dari file") }
        } else {
            Button({ create.launch("ranumOne-data.json") }, Modifier.fillMaxWidth()) { Text("Buat file baru di OneDrive") }
            OutlinedButton({ open.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Pilih file yang sudah ada") }
        }
        Spacer(Modifier.height(6.dp))
        Text("Cadangan manual", fontWeight = FontWeight.Bold)
        OutlinedButton({ backup.launch("ranumOne-cadangan.json") }, Modifier.fillMaxWidth()) { Text("Simpan salinan cadangan") }
        OutlinedButton({ restore.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Pulihkan dari file cadangan") }
    }
}

// ---------- Printer Bluetooth ----------

@Composable
fun PrinterPanel(onPrint: (ByteArray) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(prefs(ctx).getString("printer_name", null)) }
    var addr by remember { mutableStateOf(prefs(ctx).getString("printer_mac", null)) }
    var list by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            list = bondedPrinters(ctx)
        } else {
            Toast.makeText(ctx, "Izin Bluetooth diperlukan untuk memilih printer.", Toast.LENGTH_LONG).show()
        }
    }

    Panel("Printer Bluetooth (struk 57 mm)") {
        Text(if (addr != null) "Printer: ${name ?: "Tanpa nama"} ($addr)" else "Belum ada printer dipilih.")
        Text(
            "Pasangkan (pair) printer lebih dulu lewat pengaturan Bluetooth HP, lalu pilih di sini.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(6.dp))
        Button(
            {
                if (btGranted(ctx)) list = bondedPrinters(ctx) else perm.launch(Manifest.permission.BLUETOOTH_CONNECT)
            },
            Modifier.fillMaxWidth()
        ) { Text("Pilih printer") }
        if (addr != null) {
            OutlinedButton({ onPrint(receiptTest()) }, Modifier.fillMaxWidth()) { Text("Cetak tes") }
            TextButton({
                prefs(ctx).edit().remove("printer_mac").remove("printer_name").apply()
                addr = null
                name = null
            }) { Text("Hapus pilihan printer") }
        }
    }

    list?.let { l ->
        AlertDialog(
            onDismissRequest = { list = null },
            title = { Text("Pilih printer") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (l.isEmpty()) {
                        Text("Belum ada perangkat Bluetooth yang dipasangkan. Pasangkan printer dulu di pengaturan Bluetooth HP.")
                    }
                    l.forEach { (n, a) ->
                        TextButton(
                            {
                                prefs(ctx).edit().putString("printer_mac", a).putString("printer_name", n).apply()
                                name = n
                                addr = a
                                list = null
                            },
                            Modifier.fillMaxWidth()
                        ) { Text("$n\n$a") }
                    }
                }
            },
            confirmButton = { TextButton({ list = null }) { Text("Tutup") } }
        )
    }
}
