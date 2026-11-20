package com.ranum.one

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class Policy { LOCAL, REMOTE }

sealed class SyncResult {
    data class Done(val msg: String) : SyncResult()
    data class Fail(val msg: String) : SyncResult()
    data class Conflicts(val lines: List<String>, val uri: Uri, val write: Boolean) : SyncResult()
}

class Rec(
    val key: String,
    val updated: Long,
    val json: JSONObject,
    val label: String,
    val detail: String,
    val ent: Any
)

// Penanda waktu logis: selalu lebih besar dari semua perubahan yang pernah terlihat,
// sehingga selisih jam antar HP tidak membuat perubahan baru kalah dari yang lama.
object Stamp {
    @Volatile var seen = 0L
    @Synchronized fun next(): Long {
        val n = maxOf(System.currentTimeMillis(), seen + 1)
        seen = n
        return n
    }
    @Synchronized fun see(t: Long) {
        if (t > seen) seen = t
    }
}

fun prefs(c: Context) = c.getSharedPreferences("ranum", Context.MODE_PRIVATE)

fun recOf(e: Any): Rec = when (e) {
    is Daily -> Rec(
        "d|${e.date}", e.updated,
        JSONObject().put("t", "d").put("date", e.date).put("o1", e.o1).put("o2", e.o2).put("fuel", e.fuel)
            .put("income", e.income).put("incomeOther", e.incomeOther).put("noteIn", e.noteIn)
            .put("mandatory", e.mandatory).put("otherExp", e.otherExp).put("noteExp", e.noteExp)
            .put("bop", e.bop).put("updated", e.updated).put("deleted", e.deleted),
        "Hari ${LocalDate.ofEpochDay(e.date)}",
        if (e.deleted) "dihapus" else "masuk ${rp(e.income + e.incomeOther)}, keluar ${rp(e.mandatory + e.otherExp + e.bop)}",
        e
    )
    is Asset -> Rec(
        "a|${e.uid}", e.updated,
        JSONObject().put("t", "a").put("uid", e.uid).put("name", e.name).put("type", e.type)
            .put("weight", e.weight).put("price", e.price).put("date", e.date).put("baru", e.baru)
            .put("gold", e.gold).put("updated", e.updated).put("deleted", e.deleted),
        "Aset ${e.name}",
        if (e.deleted) "dihapus" else "${e.weight} gr, beli ${rp(e.price)}",
        e
    )
    is Prior -> Rec(
        "p|${e.uid}", e.updated,
        JSONObject().put("t", "p").put("uid", e.uid).put("note", e.note).put("date", e.date)
            .put("amount", e.amount).put("updated", e.updated).put("deleted", e.deleted),
        "Sisa usaha ${e.note}",
        if (e.deleted) "dihapus" else rp(e.amount),
        e
    )
    is Setting -> Rec(
        "s|${e.k}", e.updated,
        JSONObject().put("t", "s").put("k", e.k).put("v", e.v).put("updated", e.updated),
        "Pengaturan ${e.k}",
        e.v,
        e
    )
    else -> throw IllegalArgumentException("tipe tidak dikenal")
}

fun fromJson(o: JSONObject): Rec? = when (o.optString("t")) {
    "d" -> recOf(
        Daily(
            o.getLong("date"), o.getInt("o1"), o.getInt("o2"), o.getInt("fuel"),
            o.getLong("income"), o.getLong("incomeOther"), o.optString("noteIn"),
            o.getLong("mandatory"), o.getLong("otherExp"), o.optString("noteExp"),
            o.getLong("bop"), o.getLong("updated"), o.getBoolean("deleted")
        )
    )
    "a" -> recOf(
        Asset(
            o.getString("uid"), o.getString("name"), o.getString("type"), o.getDouble("weight"),
            o.getLong("price"), o.getLong("date"), o.getBoolean("baru"), o.getBoolean("gold"),
            o.getLong("updated"), o.getBoolean("deleted")
        )
    )
    "p" -> recOf(
        Prior(
            o.getString("uid"), o.getString("note"), o.getString("date"),
            o.getLong("amount"), o.getLong("updated"), o.getBoolean("deleted")
        )
    )
    "s" -> recOf(Setting(o.getString("k"), o.getString("v"), o.getLong("updated")))
    else -> null
}

class Doc(val recs: Map<String, Rec>, val saved: Long, val dev: String, val size: Int)

fun parseDoc(raw: String): Doc {
    val text = raw.trim().removePrefix("\uFEFF")
    val m = LinkedHashMap<String, Rec>()
    if (text.isBlank()) return Doc(m, 0L, "", raw.length)
    val o = JSONObject(text)
    val arr = o.getJSONArray("records")
    for (i in 0 until arr.length()) {
        val r = fromJson(arr.getJSONObject(i))
        if (r != null) m[r.key] = r
    }
    return Doc(m, o.optLong("saved", 0L), o.optString("dev", ""), raw.length)
}

suspend fun snapshot(dao: AppDao): Map<String, Rec> {
    val m = LinkedHashMap<String, Rec>()
    dao.allDaily().forEach { val r = recOf(it); m[r.key] = r }
    dao.allAsset().forEach { val r = recOf(it); m[r.key] = r }
    dao.allPrior().forEach { val r = recOf(it); m[r.key] = r }
    dao.allSetting().forEach { val r = recOf(it); m[r.key] = r }
    return m
}

fun readAll(ctx: Context, uri: Uri): String =
    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""

// Mengembalikan teks yang ditulis. Jika penyedia file tidak mendukung mode "wt" (potong file),
// teks diberi spasi di akhir sampai selisih isi lama tertutup.
fun writeDoc(ctx: Context, uri: Uri, recs: Collection<Rec>, now: Long, oldLen: Int = 0): String {
    val arr = JSONArray()
    recs.forEach { arr.put(it.json) }
    var out = JSONObject().put("v", 1).put("saved", now).put("dev", android.os.Build.MODEL)
        .put("records", arr).toString()
    var truncated = true
    val os = try {
        ctx.contentResolver.openOutputStream(uri, "wt")
    } catch (e: Exception) {
        truncated = false
        ctx.contentResolver.openOutputStream(uri, "w")
    }
    if (os == null) throw Exception("File tidak bisa ditulis")
    if (!truncated && out.length < oldLen) out = out.padEnd(oldLen, ' ')
    os.bufferedWriter().use { it.write(out) }
    return out
}

suspend fun applyLocal(dao: AppDao, r: Rec) {
    when (val e = r.ent) {
        is Daily -> dao.putDaily(e)
        is Asset -> dao.putAsset(e)
        is Prior -> dao.putPrior(e)
        is Setting -> dao.putSetting(e)
    }
}

fun bump(r: Rec): Rec {
    val o = JSONObject(r.json.toString())
    o.put("updated", Stamp.next())
    return fromJson(o)!!
}

// Menggabungkan data HP dengan isi file. policy = null: berhenti dan laporkan konflik.
// write = true: hasil gabungan ditulis kembali ke file (sinkron). false: hanya memulihkan ke HP.
suspend fun doSync(ctx: Context, dao: AppDao, uri: Uri, policy: Policy?, write: Boolean): SyncResult =
    withContext(Dispatchers.IO) {
        try {
            // Minta penyedia file (Google Drive) menyegarkan data sebelum dibaca
            try { ctx.contentResolver.refresh(uri, null, null) } catch (e: Exception) { }
            try { ctx.contentResolver.query(uri, null, null, null, null)?.close() } catch (e: Exception) { }

            val text = readAll(ctx, uri)
            val doc = parseDoc(text)
            val remote = doc.recs
            val local = snapshot(dao)
            local.values.forEach { Stamp.see(it.updated) }
            remote.values.forEach { Stamp.see(it.updated) }
            val sp = prefs(ctx)
            val last = if (write) sp.getLong("last", 0L) else 0L
            val now = System.currentTimeMillis()
            val merged = LinkedHashMap<String, Rec>()
            val toLocal = ArrayList<Rec>()
            val conflicts = ArrayList<String>()

            for (k in local.keys + remote.keys) {
                if (merged.containsKey(k)) continue
                val l = local[k]
                val r = remote[k]
                if (l == null) {
                    merged[k] = r!!
                    toLocal.add(r)
                } else if (r == null) {
                    merged[k] = l
                } else if (l.json.toString() == r.json.toString()) {
                    merged[k] = l
                } else if (l.updated > last && r.updated > last) {
                    when (policy) {
                        null -> {
                            conflicts.add("• ${l.label}\n  HP: ${l.detail}\n  OneDrive/Drive: ${r.detail}")
                            merged[k] = l
                        }
                        Policy.LOCAL -> {
                            val b = bump(l)
                            merged[k] = b
                            toLocal.add(b)
                        }
                        Policy.REMOTE -> {
                            val b = bump(r)
                            merged[k] = b
                            toLocal.add(b)
                        }
                    }
                } else if (l.updated > r.updated) {
                    merged[k] = l
                } else {
                    merged[k] = r
                    toLocal.add(r)
                }
            }

            if (conflicts.isNotEmpty()) return@withContext SyncResult.Conflicts(conflicts, uri, write)

            // Lewati catatan yang diubah pengguna selagi sinkron berjalan; akan dirapikan pada sinkron berikutnya
            val fresh = if (toLocal.isEmpty()) local else snapshot(dao)
            toLocal.forEach { r ->
                if (fresh[r.key]?.json?.toString() == local[r.key]?.json?.toString()) applyLocal(dao, r)
            }
            val pushed = merged.values.count { remote[it.key]?.json?.toString() != it.json.toString() }
            var warn = ""
            if (write) {
                if (pushed > 0 || text.isBlank()) {
                    val written = writeDoc(ctx, uri, merged.values, now, text.length)
                    val back = readAll(ctx, uri)
                    if (back.trim() != written.trim()) {
                        warn = " ⚠ Isi file setelah ditulis tidak sama dengan yang dikirim (Drive belum menerima penuh)."
                    }
                }
                sp.edit().putLong("last", Stamp.seen).putLong("seen", Stamp.seen).apply()
            }
            val fmt = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale("id", "ID"))
            val fileInfo = if (text.isBlank()) {
                "file kosong"
            } else {
                "file ${doc.size / 1024 + 1} KB, ${remote.size} catatan, terakhir ditulis oleh " +
                    (if (doc.dev.isEmpty()) "?" else doc.dev) + " " +
                    (if (doc.saved > 0) fmt.format(java.util.Date(doc.saved)) else "-")
            }
            SyncResult.Done(
                (if (write) "Sinkron " else "Pemulihan ") + fmt.format(java.util.Date(now)) +
                    " · $fileInfo · di HP ${local.size} catatan · diterapkan ke HP ${toLocal.size}" +
                    (if (write) " · dikirim ke file $pushed" else "") + warn
            )
        } catch (e: Exception) {
            SyncResult.Fail("Gagal: ${e.message}")
        }
    }

suspend fun exportTo(ctx: Context, dao: AppDao, uri: Uri): String = withContext(Dispatchers.IO) {
    try {
        val s = snapshot(dao)
        writeDoc(ctx, uri, s.values, System.currentTimeMillis())
        "Cadangan tersimpan (${s.size} catatan)."
    } catch (e: Exception) {
        "Gagal menyimpan cadangan: ${e.message}"
    }
}
