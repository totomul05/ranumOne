package com.ranum.one

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Membuat file .xlsx asli tanpa library tambahan (format OOXML dikemas dalam zip).

class C(val v: Any?, val s: Int = 0)

private val CT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""

private val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

private val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Ringkasan" sheetId="1" r:id="rId1"/><sheet name="Input Harian" sheetId="2" r:id="rId2"/></sheets></workbook>"""

private val WB_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

// Gaya: 0 biasa, 1 judul kolom, 2 angka #,##0, 3 angka tebal, 4 teks tebal, 5 persen, 6 tanggal
private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="dd/mm/yyyy"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFC8E6C9"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="7"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="3" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

private fun esc(s: String): String =
    s.filter { it >= ' ' || it == '\n' || it == '\t' }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun sheetXml(rows: List<List<C>>, widths: List<Int>): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
    sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
    sb.append("<cols>")
    widths.forEachIndexed { i, w -> sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>") }
    sb.append("</cols><sheetData>")
    rows.forEachIndexed { r, row ->
        sb.append("<row r=\"${r + 1}\">")
        row.forEachIndexed { c, cell ->
            val ref = "${'A' + c}${r + 1}"
            val v = cell.v
            if (v == null) {
                sb.append("<c r=\"$ref\" s=\"${cell.s}\"/>")
            } else if (v is String) {
                sb.append("<c r=\"$ref\" s=\"${cell.s}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>")
            } else {
                sb.append("<c r=\"$ref\" s=\"${cell.s}\"><v>$v</v></c>")
            }
        }
        sb.append("</row>")
    }
    sb.append("</sheetData></worksheet>")
    return sb.toString()
}

fun buildXlsx(led: Ledger, month: LocalDate): ByteArray {
    val end = month.plusMonths(1)
    val p = led.period(monthLabel(month), month, end)

    val sum = listOf(
        listOf(C("Keterangan", 1), C("Nilai", 1)),
        listOf(C("Bulan"), C(monthLabel(month))),
        listOf(C("Total Transaksi (orang)"), C(p.trx, 2)),
        listOf(C("Pemasukan"), C(p.income, 2)),
        listOf(C("Pengeluaran"), C(p.expense, 2)),
        listOf(C("Selisih Operasional"), C(p.diff, 2)),
        listOf(C("Pembelian Aset Baru"), C(p.newAsset, 2)),
        listOf(C("Perubahan Saving"), C(p.savingChange, 2)),
        listOf(C("Saving Akhir"), C(p.savingEnd, 2)),
        listOf(C("Non-Liquid Akhir"), C(p.nonLiquidEnd, 2)),
        listOf(C("Net Worth Akhir", 4), C(p.netWorth, 3)),
        listOf(C("% Saving dari Pemasukan"), C(p.pct / 100.0, 5))
    )

    val head = listOf(
        "Tanggal", "TRX Outlet 1", "TRX Outlet 2", "TRX Fuel", "Total Transaksi",
        "Pemasukan", "Pemasukan Lain", "Ket. Pemasukan Lain", "Total Pemasukan",
        "Pengeluaran Wajib", "Pengeluaran Lain", "Ket. Pengeluaran Lain", "BOP Toko",
        "Total Pengeluaran", "Perubahan Saving", "Saldo Saving"
    ).map { C(it, 1) }
    val numericCols = setOf(1, 2, 3, 4, 5, 6, 8, 9, 10, 12, 13, 14)
    val rows = ArrayList<List<C>>()
    rows.add(head)
    val tot = LongArray(16)
    var lastSaldo = 0L
    var d = month
    while (d.isBefore(end)) {
        val e = led.days.firstOrNull { it.date == d.toEpochDay() }
        val o1 = (e?.o1 ?: 0).toLong()
        val o2 = (e?.o2 ?: 0).toLong()
        val fu = (e?.fuel ?: 0).toLong()
        val inc = e?.income ?: 0L
        val inO = e?.incomeOther ?: 0L
        val man = e?.mandatory ?: 0L
        val oth = e?.otherExp ?: 0L
        val bop = e?.bop ?: 0L
        val tInc = inc + inO
        val tExp = man + oth + bop
        val saldo = led.savingBefore(d.plusDays(1))
        val vals = longArrayOf(0, o1, o2, fu, o1 + o2 + fu, inc, inO, 0, tInc, man, oth, 0, bop, tExp, tInc - tExp, saldo)
        for (i in numericCols) tot[i] += vals[i]
        lastSaldo = saldo
        rows.add(
            listOf(
                C(d.toEpochDay() + 25569, 6),
                C(vals[1], 2), C(vals[2], 2), C(vals[3], 2), C(vals[4], 2),
                C(vals[5], 2), C(vals[6], 2),
                C(e?.noteIn?.takeIf { it.isNotEmpty() }),
                C(vals[8], 2), C(vals[9], 2), C(vals[10], 2),
                C(e?.noteExp?.takeIf { it.isNotEmpty() }),
                C(vals[12], 2), C(vals[13], 2), C(vals[14], 2), C(vals[15], 2)
            )
        )
        d = d.plusDays(1)
    }
    val totalRow = ArrayList<C>()
    totalRow.add(C("TOTAL", 4))
    for (i in 1..15) {
        totalRow.add(
            when {
                i in numericCols -> C(tot[i], 3)
                i == 15 -> C(lastSaldo, 3)
                else -> C(null)
            }
        )
    }
    rows.add(totalRow)

    val bo = ByteArrayOutputStream()
    ZipOutputStream(bo).use { z ->
        fun put(name: String, text: String) {
            z.putNextEntry(ZipEntry(name))
            z.write(text.toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
        put("[Content_Types].xml", CT)
        put("_rels/.rels", ROOT_RELS)
        put("xl/workbook.xml", WORKBOOK)
        put("xl/_rels/workbook.xml.rels", WB_RELS)
        put("xl/styles.xml", STYLES)
        put("xl/worksheets/sheet1.xml", sheetXml(sum, listOf(30, 22)))
        put("xl/worksheets/sheet2.xml", sheetXml(rows, listOf(12, 10, 10, 10, 12, 15, 15, 24, 16, 17, 16, 24, 14, 17, 17, 17)))
    }
    return bo.toByteArray()
}

suspend fun exportMonthXlsx(ctx: Context, led: Ledger, month: LocalDate, uri: Uri): String =
    withContext(Dispatchers.IO) {
        try {
            val bytes = buildXlsx(led, month)
            val os = try {
                ctx.contentResolver.openOutputStream(uri, "wt")
            } catch (e: Exception) {
                ctx.contentResolver.openOutputStream(uri, "w")
            }
            if (os == null) throw Exception("File tidak bisa ditulis")
            os.use { it.write(bytes) }
            "Excel ${monthLabel(month)} tersimpan."
        } catch (e: Exception) {
            "Gagal membuat Excel: ${e.message}"
        }
    }
