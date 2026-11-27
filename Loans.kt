package com.ranum.one

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

// Hutang ke saving: uang yang dipinjam dari saving likuid dan akan dikembalikan.
// Saat dipinjam, saving berkurang tetapi net worth tetap (jadi "hutang ke saving").
// Saat dikembalikan, hutang berkurang dan saving bertambah.
// Disimpan sebagai baris Setting ("loan:<id>" dan "pay:<id>") sehingga ikut tersinkron tanpa migrasi database.

class Loan(val uid: String, val date: Long, val amount: Long, val note: String)
class LoanPay(val uid: String, val loanUid: String, val date: Long, val amount: Long, val note: String)

fun parseLoans(sets: List<Setting>): Pair<List<Loan>, List<LoanPay>> {
    val loans = ArrayList<Loan>()
    val pays = ArrayList<LoanPay>()
    for (s in sets) {
        if (s.v.isBlank()) continue
        try {
            if (s.k.startsWith("loan:")) {
                val o = JSONObject(s.v)
                loans.add(Loan(s.k.removePrefix("loan:"), o.getLong("d"), o.getLong("a"), o.optString("n")))
            } else if (s.k.startsWith("pay:")) {
                val o = JSONObject(s.v)
                pays.add(LoanPay(s.k.removePrefix("pay:"), o.getString("l"), o.getLong("d"), o.getLong("a"), o.optString("n")))
            }
        } catch (e: Exception) {
        }
    }
    val ids = loans.map { it.uid }.toSet()
    return loans.sortedBy { it.date } to pays.filter { it.loanUid in ids }.sortedBy { it.date }
}

fun saveLoan(dao: AppDao, scope: CoroutineScope, date: LocalDate, amount: Long, note: String) {
    scope.launch {
        val v = JSONObject().put("d", date.toEpochDay()).put("a", amount).put("n", note).toString()
        dao.putSetting(Setting("loan:" + UUID.randomUUID().toString(), v, Stamp.next()))
    }
}

fun savePay(dao: AppDao, scope: CoroutineScope, loanUid: String, date: LocalDate, amount: Long, note: String) {
    scope.launch {
        val v = JSONObject().put("l", loanUid).put("d", date.toEpochDay()).put("a", amount).put("n", note).toString()
        dao.putSetting(Setting("pay:" + UUID.randomUUID().toString(), v, Stamp.next()))
    }
}

// Menghapus = mengosongkan isinya (tetap tercatat agar penghapusan ikut tersinkron)
fun clearKey(dao: AppDao, scope: CoroutineScope, key: String) {
    scope.launch { dao.putSetting(Setting(key, "", Stamp.next())) }
}

@Composable
fun MoneyDialog(
    title: String, confirm: String, startAmount: Long, maxAmount: Long?,
    onSave: (LocalDate, Long, String) -> Unit, onDismiss: () -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var amt by remember { mutableStateOf(if (startAmount > 0L) startAmount.toString() else "") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val pick = pickDate(date) { date = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(pick, Modifier.fillMaxWidth()) { Text("Tanggal: $date") }
                Field(amt, "Jumlah (Rp)") { amt = it; err = "" }
                Field(note, "Keterangan (opsional)", false) { note = it }
                if (maxAmount != null) {
                    Text("Sisa pinjaman " + rp(maxAmount), style = MaterialTheme.typography.bodySmall)
                }
                if (err.isNotEmpty()) {
                    Text(err, color = RedInk, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton({
                val a = amt.num()
                if (a <= 0L) {
                    err = "Isi jumlah lebih dari 0."
                } else if (maxAmount != null && a > maxAmount) {
                    err = "Melebihi sisa pinjaman (" + rp(maxAmount) + ")."
                } else {
                    onSave(date, a, note.trim())
                }
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun LoanCard(
    l: Loan, led: Ledger, expanded: Boolean,
    onToggle: () -> Unit, onPay: () -> Unit, onDelete: () -> Unit, onDeletePay: (LoanPay) -> Unit
) {
    val paid = led.pays.filter { it.loanUid == l.uid }
    val sisa = l.amount - paid.sumOf { it.amount }
    val prog = if (l.amount > 0L) ((l.amount - sisa).toFloat() / l.amount.toFloat()).coerceIn(0f, 1f) else 0f

    Panel("") {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (l.note.isBlank()) "Pinjaman" else l.note, fontWeight = FontWeight.Bold)
                Text(LocalDate.ofEpochDay(l.date).toString(), style = MaterialTheme.typography.bodySmall, color = Pal.cur.mu)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(rp(sisa), fontWeight = FontWeight.Bold, color = if (sisa > 0L) GoldInk else Green)
                Text(if (sisa > 0L) "belum lunas" else "lunas", style = MaterialTheme.typography.labelSmall, color = Pal.cur.mu)
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { prog },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = GreenMid, trackColor = GreenSoft
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Dipinjam " + rp(l.amount), style = MaterialTheme.typography.bodySmall)
            Text("Kembali " + rp(l.amount - sisa), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sisa > 0L) {
                Button(onPay) { Text("Bayar") }
            }
            TextButton(onToggle) { Text(if (expanded) "Tutup riwayat" else "Riwayat bayar (" + paid.size + ")") }
            TextButton(onDelete) { Text("Hapus", color = RedInk) }
        }
        if (expanded) {
            if (paid.isEmpty()) {
                Text("Belum ada pembayaran.", style = MaterialTheme.typography.bodySmall)
            }
            paid.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rp(p.amount) + " · " + LocalDate.ofEpochDay(p.date), style = MaterialTheme.typography.bodySmall)
                        if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.labelSmall, color = Pal.cur.mu)
                    }
                    TextButton({ onDeletePay(p) }) { Text("Hapus", color = RedInk) }
                }
            }
        }
    }
}

@Composable
fun LoansScreen(led: Ledger, dao: AppDao, scope: CoroutineScope) {
    val saving = led.savingBefore(LocalDate.MAX)
    val owed = led.outstandingBefore(LocalDate.MAX)
    val totalOut = led.loans.sumOf { it.amount }
    val totalPaid = led.pays.sumOf { it.amount }
    var newLoan by remember { mutableStateOf(false) }
    var payFor by remember { mutableStateOf<Loan?>(null) }
    var delLoan by remember { mutableStateOf<Loan?>(null) }
    var delPay by remember { mutableStateOf<LoanPay?>(null) }
    var warn by remember { mutableStateOf<Triple<LocalDate, Long, String>?>(null) }
    var showDone by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }

    Panel("Ringkasan") {
        Line("Belum lunas", rp(owed), true)
        Line("Total dipinjam", rp(totalOut))
        Line("Sudah dikembalikan", rp(totalPaid))
        Spacer(Modifier.height(4.dp))
        Text(
            "Uang yang dipinjam dari saving dicatat di sini. Saving berkurang, tetapi net worth tidak berubah karena masih menjadi hutang yang akan dikembalikan.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Button({ newLoan = true }, Modifier.fillMaxWidth()) { Text("Catat pinjaman baru") }
    }

    val active = led.loans.filter { led.remaining(it) > 0L }.sortedByDescending { it.date }
    val done = led.loans.filter { led.remaining(it) <= 0L }.sortedByDescending { it.date }
    if (active.isEmpty()) {
        Text("Tidak ada pinjaman yang belum lunas.", Modifier.padding(8.dp))
    }
    active.forEach { l ->
        LoanCard(
            l, led, open == l.uid,
            { open = if (open == l.uid) null else l.uid },
            { payFor = l }, { delLoan = l }, { delPay = it }
        )
    }
    if (done.isNotEmpty()) {
        TextButton({ showDone = !showDone }) {
            Text(if (showDone) "Sembunyikan yang lunas" else "Lihat yang lunas (" + done.size + ")")
        }
        if (showDone) {
            done.forEach { l ->
                LoanCard(
                    l, led, open == l.uid,
                    { open = if (open == l.uid) null else l.uid },
                    { payFor = l }, { delLoan = l }, { delPay = it }
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    if (newLoan) {
        MoneyDialog(
            "Catat pinjaman dari saving", "Simpan", 0L, null,
            onSave = { d, a, n ->
                newLoan = false
                if (a > saving) {
                    warn = Triple(d, a, n)
                } else {
                    saveLoan(dao, scope, d, a, n)
                }
            },
            onDismiss = { newLoan = false }
        )
    }
    warn?.let { w ->
        Confirm(
            "Melebihi saldo saving",
            "Jumlah " + rp(w.second) + " lebih besar dari saldo saving " + rp(saving) + ". Saving akan menjadi negatif. Tetap catat?",
            "Tetap catat",
            { warn = null; saveLoan(dao, scope, w.first, w.second, w.third) },
            { warn = null }
        )
    }
    payFor?.let { l ->
        val sisa = led.remaining(l)
        MoneyDialog(
            "Bayar pinjaman", "Simpan pembayaran", sisa, sisa,
            onSave = { d, a, n ->
                payFor = null
                savePay(dao, scope, l.uid, d, a, n)
            },
            onDismiss = { payFor = null }
        )
    }
    delLoan?.let { l ->
        val n = led.pays.count { it.loanUid == l.uid }
        Confirm(
            "Hapus pinjaman?",
            "Pinjaman " + rp(l.amount) + " beserta " + n + " pembayarannya akan dihapus. Saldo saving ikut disesuaikan.",
            "Hapus",
            {
                delLoan = null
                clearKey(dao, scope, "loan:" + l.uid)
                led.pays.filter { it.loanUid == l.uid }.forEach { clearKey(dao, scope, "pay:" + it.uid) }
            },
            { delLoan = null }
        )
    }
    delPay?.let { p ->
        Confirm(
            "Hapus pembayaran?",
            "Pembayaran " + rp(p.amount) + " akan dihapus. Saldo saving berkurang dan sisa pinjaman bertambah.",
            "Hapus",
            { delPay = null; clearKey(dao, scope, "pay:" + p.uid) },
            { delPay = null }
        )
    }
}
