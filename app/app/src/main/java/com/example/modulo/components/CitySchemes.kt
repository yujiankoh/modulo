package com.example.modulo.components

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit

data class CityPalette(
    val buildings: List<Color>,
    val sea: Color,
    val sand: Color,
    val grass: Color,
    val grassAlt: Color,
    val soil: Color,
    val wave: Color,
    val window: Color,
)

data class CityScheme(
    val key: String,
    val name: String,
    val accent: Color,
    val day: CityPalette,
    val night: CityPalette,
)

private val DAY_WINDOW = Color(0xC7FFFFFF)
private val NIGHT_WINDOW = Color(0xFFF4CF6D)

val CITY_SCHEMES: Map<String, CityScheme> = linkedMapOf(
    "classic" to CityScheme(
        key = "classic", name = "Classic", accent = Color(0xFFE8B04A),
        day = CityPalette(
            buildings = listOf(Color(0xFFE8B04A), Color(0xFF6F9FE8), Color(0xFF8FC9CF), Color(0xFFD98A7A), Color(0xFFB3A1E0)),
            sea = Color(0xFFA9CDF2), sand = Color(0xFFECDCAE), grass = Color(0xFF9ECF90),
            grassAlt = Color(0xFF93C785), soil = Color(0xFFB9946A), wave = Color(0xFF8FB9E2), window = DAY_WINDOW,
        ),
        night = CityPalette(
            buildings = listOf(Color(0xFFA87F33), Color(0xFF4A72B8), Color(0xFF5E989E), Color(0xFFA06153), Color(0xFF7F6FAE)),
            sea = Color(0xFF17304F), sand = Color(0xFF6D5F3E), grass = Color(0xFF2F5C3D),
            grassAlt = Color(0xFF2A5237), soil = Color(0xFF4A3A28), wave = Color(0xFF23446B), window = NIGHT_WINDOW,
        ),
    ),
    "dreamland" to CityScheme(
        key = "dreamland", name = "Dreamland", accent = Color(0xFFFFAFCC),
        day = CityPalette(
            buildings = listOf(Color(0xFFCDB4DB), Color(0xFFFFC8DD), Color(0xFFFFAFCC), Color(0xFFBDE0FE), Color(0xFFA2D2FF)),
            sea = Color(0xFFD6E4FF), sand = Color(0xFFF7DFE8), grass = Color(0xFFC8E7C9),
            grassAlt = Color(0xFFBCDEBD), soil = Color(0xFFB79EC4), wave = Color(0xFFB8D0F5), window = DAY_WINDOW,
        ),
        night = CityPalette(
            buildings = listOf(Color(0xFF9A86A8), Color(0xFFC495A8), Color(0xFFC07F97), Color(0xFF8AA6C2), Color(0xFF7D9FC4)),
            sea = Color(0xFF2A3352), sand = Color(0xFF8A7386), grass = Color(0xFF5D7A62),
            grassAlt = Color(0xFF52705A), soil = Color(0xFF6B5878), wave = Color(0xFF3D4A75), window = NIGHT_WINDOW,
        ),
    ),
    "ocean" to CityScheme(
        key = "ocean", name = "Ocean", accent = Color(0xFF81C3D7),
        day = CityPalette(
            buildings = listOf(Color(0xFF3A7CA5), Color(0xFF81C3D7), Color(0xFFD9DCD6), Color(0xFF2F6690), Color(0xFF16425B)),
            sea = Color(0xFFBCD8E8), sand = Color(0xFFD9DCD6), grass = Color(0xFF7FA696),
            grassAlt = Color(0xFF74998A), soil = Color(0xFF5B7085), wave = Color(0xFF9FC4D8), window = DAY_WINDOW,
        ),
        night = CityPalette(
            buildings = listOf(Color(0xFF2F6485), Color(0xFF5F93A3), Color(0xFF9A9D98), Color(0xFF27506F), Color(0xFF123448)),
            sea = Color(0xFF10293A), sand = Color(0xFF5C6360), grass = Color(0xFF29473D),
            grassAlt = Color(0xFF244037), soil = Color(0xFF34495C), wave = Color(0xFF1E3D54), window = NIGHT_WINDOW,
        ),
    ),
    "sunset" to CityScheme(
        key = "sunset", name = "Sunset", accent = Color(0xFFF4D58D),
        day = CityPalette(
            buildings = listOf(Color(0xFF708D81), Color(0xFFF4D58D), Color(0xFFBF0603), Color(0xFF8D0801), Color(0xFF001427)),
            sea = Color(0xFFEEC695), sand = Color(0xFFF4D58D), grass = Color(0xFF94A07A),
            grassAlt = Color(0xFF8A966F), soil = Color(0xFF8D5A3A), wave = Color(0xFFD9A86F), window = DAY_WINDOW,
        ),
        night = CityPalette(
            buildings = listOf(Color(0xFF5A7168), Color(0xFFC2A86B), Color(0xFF8F0402), Color(0xFF6B0601), Color(0xFF12263D)),
            sea = Color(0xFF3A2635), sand = Color(0xFF8A6B4A), grass = Color(0xFF4A5240),
            grassAlt = Color(0xFF414A38), soil = Color(0xFF4F3222), wave = Color(0xFF57394D), window = NIGHT_WINDOW,
        ),
    ),
    "ember" to CityScheme(
        key = "ember", name = "Ember", accent = Color(0xFFFF7B1A),
        day = CityPalette(
            buildings = listOf(Color(0xFFFFC22E), Color(0xFFFF6F12), Color(0xFFE03C08), Color(0xFFA81F05), Color(0xFF421002)),
            sea = Color(0xFFFF9E54), sand = Color(0xFFF2701E), grass = Color(0xFFC2571F),
            grassAlt = Color(0xFFB64E1A), soil = Color(0xFF701F06), wave = Color(0xFFFFC46E), window = Color(0xFFFFF3D0),
        ),
        night = CityPalette(
            buildings = listOf(Color(0xFFFFB52E), Color(0xFFF56A12), Color(0xFFD4380A), Color(0xFF951C06), Color(0xFF3A0C02)),
            sea = Color(0xFF4C0F04), sand = Color(0xFFB84A10), grass = Color(0xFF48200E),
            grassAlt = Color(0xFF3E1A0A), soil = Color(0xFF160603), wave = Color(0xFFFF6A1A), window = Color(0xFFFFE8B0),
        ),
    ),

    "neon" to CityScheme(
        key = "neon", name = "Midnight", accent = Color(0xFFF72585),
        day = CityPalette(
            buildings = listOf(Color(0xFFF72585), Color(0xFFB5179E), Color(0xFF7209B7), Color(0xFF3F37C9), Color(0xFF4CC9F0)),
            sea = Color(0xFFC9C3F5), sand = Color(0xFFE3D9FA), grass = Color(0xFFA3AEE0),
            grassAlt = Color(0xFF97A2D6), soil = Color(0xFF6B5F9E), wave = Color(0xFFA99AE8), window = DAY_WINDOW,
        ),
        night = CityPalette(
            buildings = listOf(Color(0xFFC21D69), Color(0xFF8D127B), Color(0xFF58078E), Color(0xFF302B9C), Color(0xFF3A9DBD)),
            sea = Color(0xFF12102B), sand = Color(0xFF3A2F5E), grass = Color(0xFF232A52),
            grassAlt = Color(0xFF1E244A), soil = Color(0xFF2C2350), wave = Color(0xFF241F45), window = NIGHT_WINDOW,
        ),
    ),
)

val CITY_SCHEME_ORDER: List<String> = CITY_SCHEMES.keys.toList()

fun cityScheme(key: String): CityScheme = CITY_SCHEMES[key] ?: CITY_SCHEMES.getValue("classic")

fun nextCityScheme(key: String): String {
    val i = CITY_SCHEME_ORDER.indexOf(key)
    return CITY_SCHEME_ORDER[(i + 1) % CITY_SCHEME_ORDER.size]
}

object CitySchemeStore {
    private const val PREFS = "modulo_city"
    private const val KEY = "scheme"

    fun get(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "classic")
        return if (saved != null && CITY_SCHEMES.containsKey(saved)) saved else "classic"
    }

    fun set(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, key) }
    }
}
