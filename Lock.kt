package com.ranum.one

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
}

fun clearLock(ctx: Context) {
    prefs(ctx).edit().remove("lock_type").remove("lock_salt").remove("lock_hash").apply()
}

fun checkLock(ctx: Context, secret: String): Boolean {
    val p = prefs(ctx)
    val salt = p.getString("lock_salt", null) ?: return false
    return hashOf(salt, secret) == p.getString("lock_hash", null)
}

@Composable
fun PinPad(onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }

    fun press(k: String) {
        when (k) {
            "⌫" -> pin = pin.dropLast(1)
            "OK" -> {
                if (pin.length >= 4) {
                    val p = pin
                    pin = ""
                    onSubmit(p)
                }
            }
            else -> {
                if (pin.length < 6) pin += k
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (pin.isEmpty()) " " else "●".repeat(pin.length),
            style = MaterialTheme.typography.headlineMedium, color = Green
        )
        Spacer(Modifier.height(12.dp))
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK").chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { k ->
                    OutlinedButton({ press(k) }, Modifier.size(width = 76.dp, height = 56.dp)) {
                        Text(k, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
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
fun SecretEntry(type: String, prompt: String, error: String, onSubmit: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(prompt, style = MaterialTheme.typography.titleMedium, color = Green)
        if (error.isNotEmpty()) {
            Text(error, color = RedInk, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        if (type == "pin") PinPad(onSubmit) else PatternPad(onSubmit)
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

    Box(
        Modifier.fillMaxSize().background(Color(0xFFFFFDF7)).pointerInput(Unit) {
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
                if (wait > 0) "Terlalu banyak percobaan. Tunggu $wait detik." else error
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
                    if (type == "pin") "Masukkan PIN baru (4-6 angka)" else "Gambar pola baru (min. 4 titik)"
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

    if (verifying) {
        val t = type ?: "pin"
        AlertDialog(
            onDismissRequest = { verifying = false },
            title = { Text("Konfirmasi") },
            text = {
                SecretEntry(t, if (t == "pin") "Masukkan PIN saat ini" else "Gambar pola saat ini", err) { s ->
                    if (s.isNotEmpty() && checkLock(ctx, s)) {
                        verifying = false
                        err = ""
                        if (action == "off") {
                            clearLock(ctx)
                            type = null
                        } else {
                            setup = true
                        }
                    } else {
                        err = "Salah, coba lagi."
                    }
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
