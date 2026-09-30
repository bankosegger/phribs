package me.bankosegger.phribs.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import me.bankosegger.phribs.MainActivity
import me.bankosegger.phribs.data.AppDatabase
import me.bankosegger.phribs.startOfWeek
import me.bankosegger.phribs.ui.theme.DarkColorScheme
import me.bankosegger.phribs.ui.theme.LightColorScheme
import me.bankosegger.phribs.ui.theme.ThemeMode
import me.bankosegger.phribs.ui.theme.ThemePreferences
import me.bankosegger.phribs.visitDays
import me.bankosegger.phribs.weeklyStreaks

class PhribsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val workouts = AppDatabase.getDatabase(context).workoutDao()
            .getAllWorkouts().first()
            .map { it.toDomainModel() }
        val visits = visitDays(workouts, startOfWeek())
        val streak = weeklyStreaks(workouts).current
        val weeklyGoal = ThemePreferences.getWeeklyGoal(context)
        val colors = when (ThemePreferences.getThemeMode(context)) {
            ThemeMode.DARK -> ColorProviders(DarkColorScheme)
            ThemeMode.LIGHT -> ColorProviders(LightColorScheme)
            ThemeMode.SYSTEM -> ColorProviders(light = LightColorScheme, dark = DarkColorScheme)
        }

        provideContent {
            GlanceTheme(colors = colors) {
                WidgetContent(visits, streak, weeklyGoal)
            }
        }
    }
}

@Composable
private fun WidgetContent(visits: Int, streak: Int, weeklyGoal: Int) {
    val visitText = if (weeklyGoal > 0) "$visits / $weeklyGoal" else "$visits"
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PHRIBS",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = visitText,
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = if (weeklyGoal > 0) "weekly goal" else if (visits == 1) "visit this week" else "visits this week",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp)
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = "$streak week streak",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
        )
    }
}

class PhribsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PhribsWidget()
}
