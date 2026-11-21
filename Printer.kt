package com.ranum.one

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.UUID

// Printer thermal Bluetooth (ESC/POS), kertas 57 mm: 32 karakter per baris.

const val PW = 32
private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

fun btGranted(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

// Daftar perangkat Bluetooth yang sudah dipasangkan: pasangan (nama, alamat)
fun bondedPrinters(ctx: Context): List<Pair<String, String>> = try {
    val ad = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    ad?.bondedDevices?.map { (it.name ?: "Tanpa nama") to it.address }?.sortedBy { it.first } ?: emptyList()
} catch (e: SecurityException) {
    emptyList()
}

suspend fun printerSend(ctx: Context, data: ByteArray): String = withContext(Dispatchers.IO) {
    val mac = prefs(ctx).getString("printer_mac", null)
        ?: return@withContext "Pilih printer dulu di Pengaturan."
    val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    if (adapter == null) return@withContext "Perangkat ini tidak mendukung Bluetooth."
    if (!adapter.isEnabled) return@withContext "Bluetooth belum aktif. Nyalakan dulu."
    var sock: BluetoothSocket? = null
    try {
        val dev = adapter.getRemoteDevice(mac)
        try { adapter.cancelDiscovery() } catch (e: Exception) { }
        sock = try {
            val s = dev.createRfcommSocketToServiceRecord(SPP)
            s.connect()
            s
        } catch (e: Exception) {
            val s = dev.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType!!)
                .invoke(dev, 1) as BluetoothSocket
            s.connect()
            s
        }
        val os = sock.outputStream
        var i = 0
        while (i < data.size) {
            val n = minOf(512, data.size - i)
            os.write(data, i, n)
            os.flush()
            i += n
            Thread.sleep(30)
        }
        Thread.sleep(1200)
        "Struk dikirim ke printer."
    } catch (e: SecurityException) {
        "Izin Bluetooth belum diberikan."
    } catch (e: Exception) {
        "Gagal mencetak: ${e.message}. Pastikan printer menyala dan sudah dipasangkan."
    } finally {
        try { sock?.close() } catch (e: Exception) { }
    }
}

fun ascii(s: String): String = s.map { if (it.code in 32..126) it else '?' }.joinToString("")

class Esc {
    private val o = ByteArrayOutputStream()
    fun raw(vararg b: Int) { b.forEach { o.write(it) } }
    fun init() = raw(0x1B, 0x40)
    fun align(n: Int) = raw(0x1B, 0x61, n)
    fun bold(on: Boolean) = raw(0x1B, 0x45, if (on) 1 else 0)
    fun size(n: Int) = raw(0x1D, 0x21, n)
    fun feed(n: Int) = raw(0x1B, 0x64, n)
    fun text(s: String) {
        o.write(ascii(s).toByteArray(Charsets.US_ASCII))
        o.write(0x0A)
    }
    fun bytes(): ByteArray = o.toByteArray()
}

private fun lr(l: String, r: String): String {
    val a = ascii(l)
    val b = ascii(r)
    val gap = PW - a.length - b.length
    return if (gap >= 1) a + " ".repeat(gap) + b else a.take(maxOf(PW - b.length - 1, 0)) + " " + b
}

private fun row3(a: String, b: String, c: String): String =
    ascii(a).padEnd(5).take(5) + " " + ascii(b).padStart(12) + " " + ascii(c).padStart(12)

private fun fmtD(d: LocalDate): String =
    String.format(Locale.US, "%02d/%02d/%04d", d.dayOfMonth, d.monthValue, d.year)

private fun ddmm(epoch: Long): String {
    val d = LocalDate.ofEpochDay(epoch)
    return String.format(Locale.US, "%02d/%02d", d.dayOfMonth, d.monthValue)
}

private fun pctText(v: Double): String = String.format(Locale.US, "%.1f%%", v)

const val F_ALL = 0
const val F_IN = 1
const val F_OUT = 2

fun filterName(f: Int): String = when (f) {
    F_IN -> "Pemasukan saja"
    F_OUT -> "Pengeluaran saja"
    else -> "Semua"
}

private fun row2(a: String, b: String): String =
    ascii(a).padEnd(5).take(5) + " " + ascii(b).padStart(PW - 6)

private fun daysIn(led: Ledger, from: LocalDate, toExcl: LocalDate): List<Daily> =
    led.days.filter { it.date >= from.toEpochDay() && it.date < toExcl.toEpochDay() }

private fun rcHeader(e: Esc, title: String, sub: String, f: Int = F_ALL) {
    e.init()
    e.align(1)
    e.bold(true)
    e.size(0x11)
    e.text("ranumOne")
    e.size(0)
    e.text(title)
    e.bold(false)
    e.text(sub)
    if (f != F_ALL) e.text("Isi: " + filterName(f))
    e.text("Dicetak " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date()))
    e.align(0)
    e.text("-".repeat(PW))
}

private fun summary(e: Esc, p: Period) {
    e.text(lr("Transaksi", "${p.trx} orang"))
    e.text(lr("Pemasukan", rp(p.income)))
    e.text(lr("Pengeluaran", rp(p.expense)))
    e.bold(true); e.text(lr("Selisih", rp(p.diff))); e.bold(false)
    e.text(lr("Beli aset baru", rp(p.newAsset)))
    e.text(lr("Perubahan saving", rp(p.savingChange)))
    e.text(lr("Saving akhir", rp(p.savingEnd)))
    e.text(lr("Non-liquid akhir", rp(p.nonLiquidEnd)))
    e.bold(true); e.text(lr("Net worth akhir", rp(p.netWorth))); e.bold(false)
    e.text(lr("% saving", pctText(p.pct)))
}

// Ringkasan khusus pemasukan atau pengeluaran, lengkap dengan rinciannya
private fun flowSummary(e: Esc, led: Ledger, ds: List<Daily>, f: Int) {
    if (f == F_IN) {
        val trx = ds.sumOf { (it.o1 + it.o2 + it.fuel).toLong() }
        e.text(lr("Transaksi", "$trx orang"))
        e.text(lr("Pemasukan utama", rp(ds.sumOf { it.income })))
        e.text(lr("Pemasukan lain", rp(ds.sumOf { it.incomeOther })))
        e.bold(true); e.text(lr("Total pemasukan", rp(ds.sumOf { led.inc(it) }))); e.bold(false)
    } else {
        e.text(lr("Pengeluaran wajib", rp(ds.sumOf { it.mandatory })))
        e.text(lr("Pengeluaran lain", rp(ds.sumOf { it.otherExp })))
        e.text(lr("BOP toko", rp(ds.sumOf { it.bop })))
        e.bold(true); e.text(lr("Total pengeluaran", rp(ds.sumOf { led.exp(it) }))); e.bold(false)
    }
}

fun receiptTest(): ByteArray {
    val e = Esc()
    rcHeader(e, "TES CETAK", "Printer terhubung")
    e.text("12345678901234567890123456789012")
    e.text(lr("Kiri", "Kanan"))
    e.text("Jika semua baris di atas rapi,")
    e.text("printer siap dipakai.")
    e.feed(4)
    return e.bytes()
}

fun receiptDaily(led: Ledger, from: LocalDate, to: LocalDate, f: Int = F_ALL): ByteArray {
    val e = Esc()
    rcHeader(e, "LAPORAN HARIAN", "${fmtD(from)} - ${fmtD(to)}", f)
    val all = daysIn(led, from, to.plusDays(1))
    val rows = when (f) {
        F_IN -> all.filter { led.inc(it) > 0L }
        F_OUT -> all.filter { led.exp(it) > 0L }
        else -> all
    }
    if (rows.isEmpty()) {
        e.text("Tidak ada data pada rentang ini.")
    } else {
        when (f) {
            F_IN -> {
                e.bold(true); e.text(row2("Tgl", "Pemasukan")); e.bold(false)
                rows.forEach { d -> e.text(row2(ddmm(d.date), plain(led.inc(d)))) }
            }
            F_OUT -> {
                e.bold(true); e.text(row2("Tgl", "Pengeluaran")); e.bold(false)
                rows.forEach { d -> e.text(row2(ddmm(d.date), plain(led.exp(d)))) }
            }
            else -> {
                e.bold(true); e.text(row3("Tgl", "Masuk", "Keluar")); e.bold(false)
                rows.forEach { d -> e.text(row3(ddmm(d.date), plain(led.inc(d)), plain(led.exp(d)))) }
            }
        }
        e.text("(hari tanpa data tidak dicetak)")
    }
    e.text("-".repeat(PW))
    if (f == F_ALL) {
        val tin = rows.sumOf { led.inc(it) }
        val tout = rows.sumOf { led.exp(it) }
        e.text(lr("Total masuk", rp(tin)))
        e.text(lr("Total keluar", rp(tout)))
        e.bold(true); e.text(lr("Selisih", rp(tin - tout))); e.bold(false)
        e.text(lr("Transaksi", "${rows.sumOf { (it.o1 + it.o2 + it.fuel).toLong() }} orang"))
        e.text(lr("Hari berdata", "${rows.size}"))
        e.text(lr("Saving akhir", rp(led.savingBefore(to.plusDays(1)))))
    } else {
        flowSummary(e, led, rows, f)
        e.text(lr("Hari berdata", "${rows.size}"))
    }
    e.feed(4)
    return e.bytes()
}

fun receiptMonth(led: Ledger, month: LocalDate, f: Int = F_ALL): ByteArray {
    val e = Esc()
    rcHeader(e, "LAPORAN BULANAN", monthLabel(month), f)
    val end = month.plusMonths(1)
    if (f == F_ALL) summary(e, led.period("", month, end)) else flowSummary(e, led, daysIn(led, month, end), f)
    e.feed(4)
    return e.bytes()
}

fun receiptYear(led: Ledger, year: Int, f: Int = F_ALL): ByteArray {
    val e = Esc()
    rcHeader(e, "LAPORAN TAHUNAN", "Tahun $year", f)
    val ys = LocalDate.of(year, 1, 1)
    val ye = LocalDate.of(year + 1, 1, 1)
    if (f == F_ALL) summary(e, led.period("", ys, ye)) else flowSummary(e, led, daysIn(led, ys, ye), f)
    e.text("-".repeat(PW))
    e.bold(true)
    e.text(
        when (f) {
            F_IN -> row2("Bln", "Pemasukan")
            F_OUT -> row2("Bln", "Pengeluaran")
            else -> row3("Bln", "Masuk", "Keluar")
        }
    )
    e.bold(false)
    for (mth in 1..12) {
        val s = LocalDate.of(year, mth, 1)
        val q = led.period("", s, s.plusMonths(1))
        val bln = monthLabel(s).substringBefore(" ").take(3)
        e.text(
            when (f) {
                F_IN -> row2(bln, plain(q.income))
                F_OUT -> row2(bln, plain(q.expense))
                else -> row3(bln, plain(q.income), plain(q.expense))
            }
        )
    }
    e.feed(4)
    return e.bytes()
}
