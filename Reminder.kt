package com.ranum.one

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// Pengingat harian pukul 19.00, hanya muncul jika hari itu belum ada input.
object Reminder {
    private const val CH = "reminder_harian"
    private const val NOTIF_ID = 1901
    private const val REQ = 1901

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean("reminder_on", false)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("reminder_on", on).apply()
        if (on) schedule(ctx) else cancel(ctx)
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ, Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun nextTrigger(): Long {
        val now = LocalDateTime.now()
        var t = now.toLocalDate().atTime(19, 0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun schedule(ctx: Context) {
        if (!enabled(ctx)) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(), pending(ctx))
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // Mengembalikan false jika izin notifikasi belum diberikan
    fun notifyNow(ctx: Context): Boolean {
        if (!hasPermission(ctx)) return false
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH, "Pengingat input harian", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ranumOne")
            .setContentText("Belum ada input hari ini. Catat pemasukan dan pengeluaran hari ini.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, n)
        return true
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (Reminder.enabled(context)) {
                    val today = LocalDate.now().toEpochDay()
                    val has = AppDb.get(context).dao().allDaily().any { it.date == today && !it.deleted }
                    if (!has) Reminder.notifyNow(context)
                }
            } catch (e: Exception) {
            } finally {
                Reminder.schedule(context)
                result.finish()
            }
        }
    }
}

// Memasang ulang alarm setelah HP dinyalakan ulang atau aplikasi diperbarui
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.schedule(context)
    }
}

@Composable
fun ReminderPanel() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Reminder.enabled(ctx)) }

    fun enable() {
        Reminder.setEnabled(ctx, true)
        on = true
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            enable()
        } else {
            Toast.makeText(ctx, "Izin notifikasi diperlukan agar pengingat bisa muncul.", Toast.LENGTH_LONG).show()
        }
    }

    Panel("Pengingat harian") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ingatkan pukul 19.00", fontWeight = FontWeight.Bold)
                Text(
                    "Hanya muncul jika hari itu belum ada input. Jika sudah ada, tidak diingatkan.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = on,
                onCheckedChange = { want ->
                    if (!want) {
                        Reminder.setEnabled(ctx, false)
                        on = false
                    } else if (!Reminder.hasPermission(ctx)) {
                        perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        enable()
                    }
                }
            )
        }
        Text(
            "Waktunya bisa bergeser beberapa menit karena penghematan baterai Android.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            {
                if (!Reminder.notifyNow(ctx)) {
                    Toast.makeText(ctx, "Belum ada izin notifikasi untuk aplikasi ini.", Toast.LENGTH_LONG).show()
                }
            },
            Modifier.fillMaxWidth()
        ) { Text("Coba tampilkan pengingat sekarang") }
    }
}
