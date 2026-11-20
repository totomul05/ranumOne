package com.ranum.one

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            ) { App(dao) }
        }
    }
}

// ---------- Komponen kecil ----------

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

    var busy by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(true) }

    fun syncCore(uri: Uri, policy: Policy?, write: Boolean, silent: Boolean) {
        if (busy && policy == null) {
            if (!silent) syncMsg = "Sedang menyinkronkan, coba lagi sebentar."
            return
        }
        busy = true
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
            if (settings) {
                SettingsScreen(led, dao, scope, syncMsg, runSync) { settings = false }
            } else {
                when (tab) {
                    0 -> Dashboard(
                        led, syncState,
                        onSync = {
                            val u = prefs(ctx).getString("uri", null)
                            if (u != null) runSync(Uri.parse(u), null, true) else settings = true
                        },
                        onSettings = { settings = true }
                    )
                    1 -> InputScreen(led, dao, scope, date) { date = it }
                    2 -> History(led) { date = it; tab = 1 }
                    3 -> Assets(led, dao, scope)
                    else -> Reports(led, onExport)
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
fun Dashboard(led: Ledger, syncState: Int, onSync: () -> Unit, onSettings: () -> Unit) {
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
        OutlinedButton(onSettings, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Pengaturan & saldo awal") }
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
            onClick = {
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
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Simpan") }
        if (msg.isNotEmpty()) Text(msg)
        if (cur != null) {
            TextButton({ scope.launch { dao.putDaily(cur.copy(deleted = true, updated = Stamp.next())) } }) { Text("Hapus data tanggal ini") }
        }
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

@Composable
fun Assets(led: Ledger, dao: AppDao, scope: CoroutineScope) {
    var gp by remember(led.goldPrice) { mutableStateOf(led.goldPrice.toString()) }
    var edit by remember { mutableStateOf<Asset?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
        Panel("Total Non-Liquid: ${rp(led.assets.sumOf { led.value(it) })}") {
            Field(gp, "Harga buyback emas /gram (Rp)") { gp = it }
            Button({ scope.launch { dao.putSetting(Setting("gold", gp.num().toString(), Stamp.next())) } }) {
                Text("Simpan harga emas")
            }
        }
        led.assets.forEach { a ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { edit = a }) {
                Column(Modifier.padding(12.dp)) {
                    Text("${a.name} (${a.type})", fontWeight = FontWeight.Bold)
                    Text("${a.weight} gr · beli ${rp(a.price)} · ${LocalDate.ofEpochDay(a.date)}")
                    Text("Nilai ${rp(led.value(a))} · ${if (a.baru) "Aset Baru" else "Aset Lama"}")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                edit = Asset(
                    uid = "", name = "", type = "Gold Bar", weight = 0.0, price = 0,
                    date = LocalDate.now().toEpochDay(), baru = false, gold = true
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ Tambah aset") }
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
            onDelete = { scope.launch { dao.putAsset(a.copy(deleted = true, updated = Stamp.next())) }; edit = null },
            onDismiss = { edit = null }
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
fun Reports(led: Ledger, onExport: (LocalDate) -> Unit) {
    var yearly by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var menu by remember { mutableStateOf(false) }
    val year = month.year
    val p = if (yearly) {
        led.period("Tahun $year", LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1))
    } else {
        led.period(monthLabel(month), month, month.plusMonths(1))
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (yearly) OutlinedButton({ yearly = false }) { Text("Bulanan") } else Button({ yearly = false }) { Text("Bulanan") }
            if (yearly) Button({ yearly = true }) { Text("Tahunan") } else OutlinedButton({ yearly = true }) { Text("Tahunan") }
        }
        Spacer(Modifier.height(8.dp))
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
                Cols("Total", plain(rows.sumOf { it.second.income }), plain(rows.sumOf { it.second.expense }), plain(rows.sumOf { it.second.diff }), true)
            }
        }
    }
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
    syncMsg: String, runSync: (Uri, Policy?, Boolean) -> Unit, onBack: () -> Unit
) {
    var other by remember(led.otherSaving) { mutableStateOf(led.otherSaving.toString()) }
    var note by remember { mutableStateOf("") }
    var pd by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
        OutlinedButton(onBack) { Text("◀ Kembali") }
        SyncPanel(dao, scope, syncMsg, runSync)
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
                    TextButton({ scope.launch { dao.putPrior(p.copy(deleted = true, updated = Stamp.next())) } }) { Text("Hapus") }
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
