package com.ranum.one

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Palet warna yang bisa dipilih di Pengaturan. p = utama, pm = utama sedang, pt = tint utama,
// a = aksen emas, at = tint aksen, ai = teks di atas aksen, bg = latar, bd = garis, tx = teks, mu = teks redup.
class AppPalette(
    val name: String,
    val p: Color, val pm: Color, val pt: Color,
    val a: Color, val at: Color, val ai: Color,
    val bg: Color, val bd: Color, val tx: Color, val mu: Color
)

val Palettes = listOf(
    AppPalette(
        "Emerald",
        Color(0xFF0F7B5A), Color(0xFF2E9E7A), Color(0xFFE3F2EC),
        Color(0xFFE0A82E), Color(0xFFFFF4D6), Color(0xFF4A3500),
        Color(0xFFF7FAF8), Color(0xFFE3EAE6), Color(0xFF1B2B24), Color(0xFF6B7C74)
    ),
    AppPalette(
        "Teal",
        Color(0xFF0E7C86), Color(0xFF2B9AA4), Color(0xFFE1F1F3),
        Color(0xFFF2B544), Color(0xFFFFF1D6), Color(0xFF4A3500),
        Color(0xFFF5F9FA), Color(0xFFE1EAEC), Color(0xFF17292B), Color(0xFF68797C)
    ),
    AppPalette(
        "Forest",
        Color(0xFF2F6F4E), Color(0xFF4C8C6B), Color(0xFFE6F0EA),
        Color(0xFFC9A227), Color(0xFFF8F0D2), Color(0xFF3E3000),
        Color(0xFFF6F8F4), Color(0xFFE4E9E0), Color(0xFF1F2A22), Color(0xFF6E7B70)
    ),
    AppPalette(
        "Samudra",
        Color(0xFF1F5FA8), Color(0xFF3F7FC7), Color(0xFFE4EEF9),
        Color(0xFFF0A93B), Color(0xFFFFF0D8), Color(0xFF4A3000),
        Color(0xFFF6F9FC), Color(0xFFE2E9F2), Color(0xFF1A2430), Color(0xFF6A7788)
    ),
    AppPalette(
        "Anggur",
        Color(0xFF7A3E8E), Color(0xFF9A5FAE), Color(0xFFF1E8F5),
        Color(0xFFE0A82E), Color(0xFFFFF4D6), Color(0xFF4A3500),
        Color(0xFFFAF7FB), Color(0xFFEBE3EF), Color(0xFF2A1F2E), Color(0xFF7C6E82)
    )
)

object Pal {
    var idx by mutableIntStateOf(0)
    val cur: AppPalette get() = Palettes[idx.coerceIn(0, Palettes.size - 1)]
}

// Nama lama dipertahankan agar seluruh layar otomatis mengikuti palet yang dipilih
val Green: Color get() = Pal.cur.p
val GreenMid: Color get() = Pal.cur.pm
val GreenSoft: Color get() = Pal.cur.pt
val Gold: Color get() = Pal.cur.a
val GoldSoft: Color get() = Pal.cur.at
val GoldInk: Color get() = Pal.cur.ai

fun appScheme(): ColorScheme {
    val c = Pal.cur
    return lightColorScheme(
        primary = c.p, onPrimary = Color.White,
        primaryContainer = c.pt, onPrimaryContainer = c.p,
        secondary = c.a, onSecondary = c.ai,
        secondaryContainer = c.pt, onSecondaryContainer = c.p,
        background = c.bg, onBackground = c.tx,
        surface = c.bg, onSurface = c.tx, onSurfaceVariant = c.mu,
        outline = c.bd,
        surfaceContainer = Color.White, surfaceContainerHighest = Color.White
    )
}
