package com.ranum.one

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

// Kunci aplikasi: PIN 4-6 angka atau pola 3x3. Hanya berlaku di HP ini (tidak ikut tersinkron).

fun lockType(ctx: Context): String? = prefs(ctx).getString("lock_type", null)

fun isLockEnabled(ctx: Context): Boolean = lockType(ctx) != null

private fun hashOf(salt: String, secret: String): String =
    MessageDigest.getInstance("SHA-256").digest((salt + secret).toByteArray())
        .joinToString("") { "%02x".format(it) }

fun setLock(ctx: Context, type: String, secret: String) {
    val salt = UUID.randomUUID().toString()
    prefs(ctx).edit()
        .putString("lock_type", type)
        .putString("lock_salt", salt)
        .putString("lock_hash", hashOf(salt, secret))
        .apply()
    // PIN baru selalu 6 angka; pola tidak punya panjang
    if (type == "pin") prefs(ctx).edit().putInt("lock_len", 6).apply() else prefs(ctx).edit().remove("lock_len").apply()
}

fun clearLock(ctx: Context) {
    prefs(ctx).edit().remove("lock_type").remove("lock_salt").remove("lock_hash").remove("lock_len").apply()
}

fun checkLock(ctx: Context, secret: String): Boolean {
    val p = prefs(ctx)
    val salt = p.getString("lock_salt", null) ?: return false
    return hashOf(salt, secret) == p.getString("lock_hash", null)
}

@Composable
fun EyeIcon(open: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(width = 2.2f * u, cap = StrokeCap.Round)
        if (open) {
            val p = Path().apply {
                moveTo(2f * u, 12f * u)
                quadraticBezierTo(12f * u, 3f * u, 22f * u, 12f * u)
                quadraticBezierTo(12f * u, 21f * u, 2f * u, 12f * u)
                close()
            }
            drawPath(p, color, style = stroke)
            drawCircle(color, radius = 3.4f * u, center = Offset(12f * u, 12f * u))
        } else {
            val p = Path().apply {
                moveTo(2f * u, 10f * u)
                quadraticBezierTo(12f * u, 19f * u, 22f * u, 10f * u)
            }
            drawPath(p, color, style = stroke)
            drawLine(color, Offset(6f * u, 14.5f * u), Offset(4.5f * u, 18f * u), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
            drawLine(color, Offset(12f * u, 16f * u), Offset(12f * u, 20f * u), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
            drawLine(color, Offset(18f * u, 14.5f * u), Offset(19.5f * u, 18f * u), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        }
    }
}

// Input PIN memakai keyboard angka bawaan HP. Tampil 6 garis, berubah jadi * saat diketik.
// Otomatis dikirim begitu angka ke-6 terisi, tanpa tombol OK.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PinInput(len: Int, onChange: (String) -> Unit, onComplete: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val keyboard = LocalSoftwareKeyboardController.current

    // Minta fokus dan tampilkan keyboard angka; diulang beberapa kali karena jendela kadang belum siap
    LaunchedEffect(Unit) {
        for (attempt in 0 until 3) {
            delay(if (attempt == 0) 250L else 400L)
            try {
                focus.requestFocus()
            } catch (e: Exception) {
            }
            keyboard?.show()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until len) {
                    val ch = if (i < pin.length) (if (show) pin[i].toString() else "*") else "—"
                    Text(
                        ch,
                        modifier = Modifier.width(30.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Green
                    )
                }
            }
            BasicTextField(
                value = pin,
                onValueChange = { v ->
                    val d = v.filter { it.isDigit() }.take(len)
                    pin = d
                    onChange(d)
                    if (d.length == len) {
                        scope.launch {
                            delay(150)
                            onComplete(d)
                            pin = ""
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.matchParentSize().alpha(0f).focusRequester(focus)
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(40.dp).clickable { show = !show }, contentAlignment = Alignment.Center) {
            EyeIcon(show, Green)
        }
    }
}

// Mengembalikan pola sebagai teks "0-1-2-5"; teks kosong jika kurang dari 4 titik.
@Composable
fun PatternPad(onDone: (String) -> Unit) {
    var path by remember { mutableStateOf(listOf<Int>()) }
    var cur by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        Modifier.size(260.dp).pointerInput(Unit) {
            fun hit(o: Offset): Int? {
                val w = size.width.toFloat()
                for (i in 0 until 9) {
                    val cx = w * (i % 3 + 0.5f) / 3f
                    val cy = w * (i / 3 + 0.5f) / 3f
                    val dx = o.x - cx
                    val dy = o.y - cy
                    if (dx * dx + dy * dy < (w / 8f) * (w / 8f)) return i
                }
                return null
            }
            detectDragGestures(
                onDragStart = { o ->
                    cur = o
                    path = listOfNotNull(hit(o))
                },
                onDrag = { change, _ ->
                    cur = change.position
                    val h = hit(change.position)
                    if (h != null && h !in path) path = path + h
                },
                onDragEnd = {
                    val p = path
                    path = listOf()
                    cur = null
                    if (p.size >= 4) {
                        onDone(p.joinToString("-"))
                    } else if (p.isNotEmpty()) {
                        onDone("")
                    }
                },
                onDragCancel = {
                    path = listOf()
                    cur = null
                }
            )
        }
    ) {
        val w = size.width
        fun center(i: Int) = Offset(w * (i % 3 + 0.5f) / 3f, w * (i / 3 + 0.5f) / 3f)
        for (k in 0 until path.size - 1) {
            drawLine(Green, center(path[k]), center(path[k + 1]), strokeWidth = 8f)
        }
        val c = cur
        if (path.isNotEmpty() && c != null) {
            drawLine(Green, center(path.last()), c, strokeWidth = 8f)
        }
        for (i in 0 until 9) {
            drawCircle(if (i in path) Green else GreenMid, radius = w / 22f, center = center(i))
        }
    }
}

@Composable
fun SecretEntry(
    type: String, prompt: String, error: String,
    onChange: (String) -> Unit = {}, onSubmit: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(prompt, style = MaterialTheme.typography.titleMedium, color = Green)
        if (error.isNotEmpty()) {
            Text(error, color = RedInk, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        if (type == "pin") PinInput(6, onChange, onSubmit) else PatternPad(onSubmit)
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val type = lockType(ctx) ?: "pin"
    var error by remember { mutableStateOf("") }
    var fails by remember { mutableIntStateOf(0) }
    var wait by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val legacy = type == "pin" && !prefs(ctx).contains("lock_len")

    Box(
        Modifier.fillMaxSize().background(Pal.cur.bg).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ranumOne", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Green)
            Spacer(Modifier.height(24.dp))
            SecretEntry(
                type,
                if (type == "pin") "Masukkan PIN" else "Gambar pola",
                if (wait > 0) "Terlalu banyak percobaan. Tunggu $wait detik." else error,
                onChange = { d ->
                    if (legacy && d.length in 4..5 && wait == 0 && checkLock(ctx, d)) onUnlock()
                }
            ) { s ->
                if (wait > 0) return@SecretEntry
                if (s.isNotEmpty() && checkLock(ctx, s)) {
                    onUnlock()
                } else {
                    fails++
                    error = if (s.isEmpty()) "Pola minimal 4 titik." else "Salah, coba lagi."
                    if (fails >= 5) {
                        fails = 0
                        wait = 30
                        scope.launch {
                            while (wait > 0) {
                                delay(1000)
                                wait--
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LockSetupDialog(type: String, onSaved: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == "pin") "Atur PIN" else "Atur pola") },
        text = {
            SecretEntry(
                type,
                if (first == null) {
                    if (type == "pin") "Masukkan PIN baru (6 angka)" else "Gambar pola baru (min. 4 titik)"
                } else {
                    "Ulangi untuk konfirmasi"
                },
                error
            ) { s ->
                if (s.isEmpty()) {
                    error = "Pola minimal 4 titik."
                    return@SecretEntry
                }
                val f = first
                if (f == null) {
                    first = s
                    error = ""
                } else if (f == s) {
                    onSaved(s)
                } else {
                    first = null
                    error = "Tidak sama. Ulangi dari awal."
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Batal") } }
    )
}

@Composable
fun LockPanel() {
    val ctx = LocalContext.current
    var type by remember { mutableStateOf(lockType(ctx)) }
    var action by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    fun begin(a: String) {
        action = a
        err = ""
        if (type == null) setup = true else verifying = true
    }

    Panel("Kunci aplikasi") {
        Text(
            when (type) {
                "pin" -> "Aktif: PIN"
                "pattern" -> "Aktif: pola"
                else -> "Tidak aktif."
            }
        )
        Text(
            "Kunci hanya berlaku di HP ini dan diminta ulang jika aplikasi ditinggalkan lebih dari 1 menit.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(6.dp))
        Button({ begin("pin") }, Modifier.fillMaxWidth()) {
            Text(if (type == "pin") "Ganti PIN" else "Gunakan PIN")
        }
        OutlinedButton({ begin("pattern") }, Modifier.fillMaxWidth()) {
            Text(if (type == "pattern") "Ganti pola" else "Gunakan pola")
        }
        if (type != null) {
            TextButton({ begin("off") }) { Text("Matikan kunci") }
        }
    }

    fun verified() {
        verifying = false
        err = ""
        if (action == "off") {
            clearLock(ctx)
            type = null
        } else {
            setup = true
        }
    }

    if (verifying) {
        val t = type ?: "pin"
        AlertDialog(
            onDismissRequest = { verifying = false },
            title = { Text("Konfirmasi") },
            text = {
                SecretEntry(
                    t, if (t == "pin") "Masukkan PIN saat ini" else "Gambar pola saat ini", err,
                    onChange = { d ->
                        if (t == "pin" && !prefs(ctx).contains("lock_len") && d.length in 4..5 && checkLock(ctx, d)) verified()
                    }
                ) { s ->
                    if (s.isNotEmpty() && checkLock(ctx, s)) verified() else err = "Salah, coba lagi."
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ verifying = false }) { Text("Batal") } }
        )
    }
    if (setup) {
        LockSetupDialog(
            action,
            { s ->
                setLock(ctx, action, s)
                type = action
                setup = false
            },
            { setup = false }
        )
    }
}
