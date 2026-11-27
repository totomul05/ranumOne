package com.ranum.one

import java.time.LocalDate
import kotlin.math.abs

data class Period(
    val label: String,
    val trx: Long,
    val income: Long,
    val expense: Long,
    val newAsset: Long,
    val savingEnd: Long,
    val nonLiquidEnd: Long,
    val loanEnd: Long = 0L,
    val loanOut: Long = 0L,
    val loanIn: Long = 0L
) {
    val diff: Long get() = income - expense
    val savingChange: Long get() = diff - newAsset - loanOut + loanIn
    val netWorth: Long get() = savingEnd + nonLiquidEnd + loanEnd
    val pct: Double get() = if (income == 0L) 0.0 else diff * 100.0 / income
}

class Ledger(
    val days: List<Daily>,
    val assets: List<Asset>,
    val priors: List<Prior>,
    val goldPrice: Long,
    val otherSaving: Long,
    val loans: List<Loan> = emptyList(),
    val pays: List<LoanPay> = emptyList()
) {
    fun inc(d: Daily): Long = d.income + d.incomeOther
    fun exp(d: Daily): Long = d.mandatory + d.otherExp + d.bop

    // Emas: berat x harga buyback saat ini. Aset lain: harga beli.
    fun value(a: Asset): Long = if (a.gold) Math.round(a.weight * goldPrice) else a.price

    val startSaving: Long get() = otherSaving + priors.sumOf { it.amount }

    // Saving = saldo awal + (pemasukan - pengeluaran) - uang tunai yang keluar untuk Aset Baru
    fun savingBefore(d: LocalDate): Long {
        val e = d.toEpochDay()
        val flow = days.filter { it.date < e }.sumOf { inc(it) - exp(it) }
        val bought = assets.filter { it.baru && it.date < e }.sumOf { it.price }
        val lent = loans.filter { it.date < e }.sumOf { it.amount }
        val back = pays.filter { it.date < e }.sumOf { it.amount }
        return startSaving + flow - bought - lent + back
    }

    // Hutang ke saving yang belum dikembalikan (dihitung sebagai bagian dari net worth)
    fun outstandingBefore(d: LocalDate): Long {
        val e = d.toEpochDay()
        return loans.filter { it.date < e }.sumOf { it.amount } - pays.filter { it.date < e }.sumOf { it.amount }
    }

    fun remaining(l: Loan): Long = l.amount - pays.filter { it.loanUid == l.uid }.sumOf { it.amount }

    // Aset Lama selalu dihitung; Aset Baru dihitung sejak tanggal belinya
    fun nonLiquidBefore(d: LocalDate): Long {
        val e = d.toEpochDay()
        return assets.filter { !it.baru || it.date < e }.sumOf { value(it) }
    }

    fun period(label: String, from: LocalDate, to: LocalDate): Period {
        val a = from.toEpochDay()
        val b = to.toEpochDay()
        val d = days.filter { it.date >= a && it.date < b }
        val na = assets.filter { it.baru && it.date >= a && it.date < b }.sumOf { it.price }
        return Period(
            label,
            d.sumOf { (it.o1 + it.o2 + it.fuel).toLong() },
            d.sumOf { inc(it) },
            d.sumOf { exp(it) },
            na,
            savingBefore(to),
            nonLiquidBefore(to),
            outstandingBefore(to),
            loans.filter { it.date >= a && it.date < b }.sumOf { it.amount },
            pays.filter { it.date >= a && it.date < b }.sumOf { it.amount }
        )
    }
}

fun rp(v: Long): String {
    val s = abs(v).toString().reversed().chunked(3).joinToString(".").reversed()
    return (if (v < 0) "-Rp" else "Rp") + s
}

fun String.num(): Long = filter { it.isDigit() }.toLongOrNull() ?: 0L
