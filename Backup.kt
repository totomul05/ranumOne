package com.ranum.one

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

// Cadangan otomatis: tiap pukul 00.00 salinan data ditulis ke folder pilihan di memori HP atau kartu.
// Sinkronisasi utama tetap Google Drive; salinan ini hanya cadangan. Format sama dengan file sinkron,
// jadi bisa dipulihkan lewat Pengaturan > Sinkronisasi > Pulihkan dari file cadangan.
object Backup {
    private const val REQ = 2400
    private const val KEEP = 30

    fun dirUri(ctx: Context): Uri? = prefs(ctx).getString("backup_dir", null)?.let { Uri.parse(it) }

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean("backup_on", false) && dirUri(ctx) != null

    fun setDir(ctx: Context, u: Uri) {
        prefs(ctx).edit().putString("backup_dir", u.toString()).apply()
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("backup_on", on).apply()
        if (on) schedule(ctx) else cancel(ctx)
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ, Intent(ctx, BackupReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun nextMidnight(): Long =
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun schedule(ctx: Context) {
        if (!enabled(ctx)) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnight(), pending(ctx))
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
    }

    // Jika alarm tengah malam terlewat (HP mati atau dihemat baterai), salin saat aplikasi dibuka
    fun catchUp(ctx: Context) {
        if (!enabled(ctx)) return
        val today = LocalDate.now().toEpochDay()
        if (prefs(ctx).getLong("backup_last_day", 0L) >= today) return
        CoroutineScope(Dispatchers.IO).launch { run(ctx) }
    }

    fun folderLabel(tree: Uri?): String {
        if (tree == null) return "Belum dipilih"
        return try {
            val id = DocumentsContract.getTreeDocumentId(tree)
            val parts = id.split(":", limit = 2)
            val vol = if (parts[0] == "primary") "Memori internal" else "Kartu SD / penyimpanan lain"
            val path = parts.getOrElse(1) { "" }
            if (path.isEmpty()) vol else "$vol / $path"
        } catch (e: Exception) {
            "Folder terpilih"
        }
    }

    fun lastInfo(ctx: Context): String {
        val p = prefs(ctx)
        val t = p.getLong("backup_last_time", 0L)
        if (t == 0L) return "Belum pernah mencadangkan."
        val f = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(t))
        val head = if (p.getBoolean("backup_last_ok", false)) "Terakhir berhasil: " else "Terakhir tidak berhasil: "
        return head + f + "\n" + (p.getString("backup_last_msg", "") ?: "")
    }

    private fun record(ctx: Context, ok: Boolean, msg: String) {
        val e = prefs(ctx).edit()
            .putLong("backup_last_time", System.currentTimeMillis())
            .putBoolean("backup_last_ok", ok)
            .putString("backup_last_msg", msg)
        if (ok) e.putLong("backup_last_day", LocalDate.now().toEpochDay())
        e.apply()
    }

    private fun children(cr: ContentResolver, tree: Uri): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        cr.query(uri, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out.add(c.getString(0) to c.getString(1))
            }
        }
        return out
    }

    // Menyimpan salinan hari ini (satu file per hari, ditimpa jika dijalankan lagi di hari yang sama)
    // dan menghapus salinan lama di atas 30 file.
    suspend fun run(ctx: Context): String = withContext(Dispatchers.IO) {
        val tree = dirUri(ctx) ?: return@withContext "Folder belum dipilih."
        val result: Pair<Boolean, String> = try {
            val snap = snapshot(AppDb.get(ctx).dao())
            if (snap.isEmpty()) {
                false to "Belum ada data untuk dicadangkan (dilewati)."
            } else {
                val arr = JSONArray()
                snap.values.forEach { arr.put(it.json) }
                val text = JSONObject().put("v", 1).put("saved", System.currentTimeMillis())
                    .put("dev", Build.MODEL).put("records", arr).toString()
                val cr = ctx.contentResolver
                val name = "ranumOne-cadangan-" + LocalDate.now() + ".json"
                val kids = children(cr, tree)
                val existing = kids.firstOrNull { it.first == name }
                val target = if (existing != null) {
                    DocumentsContract.buildDocumentUriUsingTree(tree, existing.second)
                } else {
                    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    DocumentsContract.createDocument(cr, parent, "application/json", name)
                        ?: throw Exception("Tidak bisa membuat file di folder ini")
                }
                val os = try {
                    cr.openOutputStream(target, "wt")
                } catch (e: Exception) {
                    cr.openOutputStream(target, "w")
                } ?: throw Exception("File tidak bisa ditulis")
                os.bufferedWriter().use { it.write(text) }
                val back = cr.openInputStream(target)?.bufferedReader()?.use { it.readText() } ?: ""
                if (back.trim() != text.trim()) throw Exception("Isi file tidak cocok setelah ditulis")

                val mine = children(cr, tree)
                    .filter { it.first.startsWith("ranumOne-cadangan-") && it.first.endsWith(".json") }
                    .sortedByDescending { it.first }
                mine.drop(KEEP).forEach {
                    try {
                        DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, it.second))
                    } catch (e: Exception) {
                    }
                }
                true to ("Tersimpan: " + name + " (" + snap.size + " catatan)")
            }
        } catch (e: Exception) {
            false to ("Gagal: " + (e.message ?: e.javaClass.simpleName))
        }
        record(ctx, result.first, result.second)
        result.second
    }
}

class BackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (Backup.enabled(context)) Backup.run(context)
            } catch (e: Exception) {
            } finally {
                Backup.schedule(context)
                result.finish()
            }
        }
    }
}

@Composable
fun BackupPanel(scope: CoroutineScope) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Backup.enabled(ctx)) }
    var dir by remember { mutableStateOf(Backup.dirUri(ctx)) }
    var info by remember { mutableStateOf(Backup.lastInfo(ctx)) }
    var busy by remember { mutableStateOf(false) }

    fun runNow() {
        busy = true
        scope.launch {
            val m = Backup.run(ctx)
            info = Backup.lastInfo(ctx)
            busy = false
            Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
            }
            Backup.setDir(ctx, u)
            dir = u
            Backup.setEnabled(ctx, true)
            on = true
            runNow()
        }
    }

    Panel("Cadangan otomatis") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Salin data tiap tengah malam", fontWeight = FontWeight.Bold)
                Text(
                    "Sinkronisasi utama tetap di Google Drive. Ini hanya salinan di memori HP atau kartu.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = on,
                onCheckedChange = { want ->
                    if (!want) {
                        Backup.setEnabled(ctx, false)
                        on = false
                    } else if (dir == null) {
                        picker.launch(null)
                    } else {
                        Backup.setEnabled(ctx, true)
                        on = true
                        runNow()
                    }
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Folder: " + Backup.folderLabel(dir), style = MaterialTheme.typography.bodySmall)
        OutlinedButton({ picker.launch(dir) }, Modifier.fillMaxWidth()) {
            Text(if (dir == null) "Pilih folder penyimpanan" else "Ganti folder")
        }
        Spacer(Modifier.height(4.dp))
        Button({ runNow() }, Modifier.fillMaxWidth(), enabled = dir != null && !busy) {
            Text(if (busy) "Mencadangkan..." else "Cadangkan sekarang")
        }
        Spacer(Modifier.height(8.dp))
        Text(info, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Nama file: ranumOne-cadangan-TANGGAL.json. Disimpan 30 salinan terakhir; salinan yang lebih lama dihapus otomatis. " +
                "File tidak terenkripsi, jadi pilih folder yang aman. Untuk memulihkan: Pengaturan > Sinkronisasi > Pulihkan dari file cadangan.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
