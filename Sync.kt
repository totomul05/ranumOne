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

fun parseDoc(text: String): Map<String, Rec> {
    val m = LinkedHashMap<String, Rec>()
    if (text.isBlank()) return m
    val arr = JSONObject(text).getJSONArray("records")
    for (i in 0 until arr.length()) {
        val r = fromJson(arr.getJSONObject(i))
        if (r != null) m[r.key] = r
    }
    return m
}

suspend fun snapshot(dao: AppDao): Map<String, Rec> {
    val m = LinkedHashMap<String, Rec>()
    dao.allDaily().forEach { val r = recOf(it); m[r.key] = r }
    dao.allAsset().forEach { val r = recOf(it); m[r.key] = r }
    dao.allPrior().forEach { val r = recOf(it); m[r.key] = r }
    dao.allSetting().forEach { val r = recOf(it); m[r.key] = r }
    return m
}

fun writeDoc(ctx: Context, uri: Uri, recs: Collection<Rec>, now: Long) {
    val arr = JSONArray()
    recs.forEach { arr.put(it.json) }
    val out = JSONObject().put("v", 1).put("saved", now).put("records", arr).toString()
    val os = try {
        ctx.contentResolver.openOutputStream(uri, "wt")
    } catch (e: Exception) {
        ctx.contentResolver.openOutputStream(uri, "w")
    }
    if (os == null) throw Exception("File tidak bisa ditulis")
    os.bufferedWriter().use { it.write(out) }
}

suspend fun applyLocal(dao: AppDao, r: Rec) {
    when (val e = r.ent) {
        is Daily -> dao.putDaily(e)
        is Asset -> dao.putAsset(e)
        is Prior -> dao.putPrior(e)
        is Setting -> dao.putSetting(e)
    }
}

fun bump(r: Rec, now: Long): Rec {
    val o = JSONObject(r.json.toString())
    o.put("updated", now)
    return fromJson(o)!!
}

// Menggabungkan data HP dengan isi file. policy = null: berhenti dan laporkan konflik.
// write = true: hasil gabungan ditulis kembali ke file (sinkron). false: hanya memulihkan ke HP.
suspend fun doSync(ctx: Context, dao: AppDao, uri: Uri, policy: Policy?, write: Boolean): SyncResult =
    withContext(Dispatchers.IO) {
        try {
            val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val remote = parseDoc(text)
            val local = snapshot(dao)
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
                            conflicts.add("• ${l.label}\n  HP: ${l.detail}\n  OneDrive: ${r.detail}")
                            merged[k] = l
                        }
                        Policy.LOCAL -> {
                            val b = bump(l, now)
                            merged[k] = b
                            toLocal.add(b)
                        }
                        Policy.REMOTE -> {
                            val b = bump(r, now)
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

            toLocal.forEach { applyLocal(dao, it) }
            if (write) {
                writeDoc(ctx, uri, merged.values, now)
                sp.edit().putLong("last", now).apply()
            }
            val t = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale("id", "ID")).format(java.util.Date(now))
            SyncResult.Done((if (write) "Sinkron selesai $t" else "Pemulihan selesai $t") + " · ${toLocal.size} catatan diperbarui di HP")
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
