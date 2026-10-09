package me.bankosegger.phribs.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import me.bankosegger.phribs.RestTimer

enum class ThemeMode(val label: String) {
    DARK("Dark Mode"),
    LIGHT("Light Mode"),
    SYSTEM("System Default")
}

enum class WeightUnit(val label: String, val unitSymbol: String) {
    KG("Kilograms (kg)", "kg"),
    LB("Pounds (lb)", "lb")
}

fun Double.toUnit(unit: WeightUnit): Double = when (unit) {
    WeightUnit.KG -> this
    WeightUnit.LB -> this * 2.20462262185
}

fun Double.fromUnitToKg(unit: WeightUnit): Double = when (unit) {
    WeightUnit.KG -> this
    WeightUnit.LB -> this / 2.20462262185
}

object ThemePreferences {
    private const val PREFS = "app_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_WEEKLY_GOAL = "weekly_goal"
    private const val KEY_WEIGHT_UNIT = "weight_unit"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_REST_TIMER = "rest_timer_enabled"
    private const val KEY_REST_SECONDS = "rest_seconds"
    private const val KEY_REST_VIBRATE = "rest_vibrate"
    private const val DEFAULT_WEEKLY_GOAL = 3

    private fun prefs(context: Context) = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): ThemeMode {
        val raw = prefs(context)
            .getString(KEY_THEME, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        return try {
            ThemeMode.valueOf(raw)
        } catch (_: Exception) {
            ThemeMode.DARK
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit { putString(KEY_THEME, mode.name) }
    }

    fun getWeeklyGoal(context: Context): Int {
        return prefs(context).getInt(KEY_WEEKLY_GOAL, DEFAULT_WEEKLY_GOAL)
    }

    fun setWeeklyGoal(context: Context, goal: Int) {
        prefs(context).edit { putInt(KEY_WEEKLY_GOAL, goal) }
    }

    fun getWeightUnit(context: Context): WeightUnit {
        val raw = prefs(context)
            .getString(KEY_WEIGHT_UNIT, WeightUnit.KG.name) ?: WeightUnit.KG.name
        return try {
            WeightUnit.valueOf(raw)
        } catch (_: Exception) {
            WeightUnit.KG
        }
    }

    fun setWeightUnit(context: Context, unit: WeightUnit) {
        prefs(context).edit { putString(KEY_WEIGHT_UNIT, unit.name) }
    }

    fun getDynamicColor(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DYNAMIC_COLOR, false)

    fun setDynamicColor(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_DYNAMIC_COLOR, enabled) }
    }

    fun getRestTimerEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REST_TIMER, true)

    fun setRestTimerEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_REST_TIMER, enabled) }
    }

    fun getRestSeconds(context: Context): Int {
        return prefs(context).getInt(KEY_REST_SECONDS, RestTimer.DEFAULT_SECONDS)
            .coerceIn(RestTimer.MIN_SECONDS, RestTimer.MAX_SECONDS)
    }

    fun setRestSeconds(context: Context, seconds: Int) {
        prefs(context).edit { putInt(KEY_REST_SECONDS,
            seconds.coerceIn(RestTimer.MIN_SECONDS, RestTimer.MAX_SECONDS)) }
    }

    fun getRestVibrate(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REST_VIBRATE, true)

    fun setRestVibrate(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_REST_VIBRATE, enabled) }
    }
}

val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF0061A4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E4FF),
    onSecondaryContainer = Color(0xFF001D36),
    tertiary = Color(0xFF1B6B40),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0)
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun colorScheme(context: Context, dark: Boolean, dynamicColor: Boolean): ColorScheme = when {
    // Dynamic schemes use the same color for background and surface, which would make cards
    // blend into the page.
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (dark) dynamicDarkColorScheme(context).let { it.copy(surface = it.surfaceContainer) }
        else dynamicLightColorScheme(context).let { it.copy(background = it.surfaceContainer,
            surface = it.surfaceContainerLowest) }
    dark -> DarkColorScheme
    else -> LightColorScheme
}

@Composable
fun PhribsTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = supportsDynamicColor &&
            ThemePreferences.getDynamicColor(LocalContext.current),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colorScheme(LocalContext.current,
            themeMode.isDark(), dynamicColor),
        typography = Typography,
        content = content
    )
}