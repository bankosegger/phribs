package me.bankosegger.phribs

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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.bankosegger.phribs.ui.theme.ThemePreferences

/** A rest period ending at [endAt]; [totalSeconds] is its full length, grown by every "+30s". */
data class RestTimerState(val endAt: Long, val totalSeconds: Int) {
    fun remainingSeconds(now: Long = System.currentTimeMillis()): Int = remainingSeconds(endAt, now)
}

// Rounded up, so a freshly started 90s timer reads "1:30" rather than "1:29".
internal fun remainingSeconds(endAt: Long, now: Long): Int =
    (((endAt - now).coerceAtLeast(0L) + 999L) / 1000L).toInt()

internal fun formatRestDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * The countdown that runs after a finished set.
 *
 * The deadline lives in prefs and in an [AlarmManager] alarm rather than in a coroutine, so a rest
 * period keeps running — and still alerts — when the app is backgrounded or killed mid-set. The
 * ongoing notification counts itself down via the platform chronometer, so nothing has to tick it.
 */
object RestTimer {
    const val DEFAULT_SECONDS = 90
    const val MIN_SECONDS = 15
    const val MAX_SECONDS = 600
    const val STEP_SECONDS = 15
    const val EXTEND_SECONDS = 30

    private const val PREFS = "rest_timer"
    private const val KEY_END_AT = "end_at"
    private const val KEY_TOTAL_SECONDS = "total_seconds"

    private const val CHANNEL_RUNNING = "rest_timer_running"
    private const val CHANNEL_DONE = "rest_timer_done"
    private const val ID_RUNNING = 1001
    private const val ID_DONE = 1002

    private const val REQ_FINISH = 1
    private const val REQ_EXTEND = 2
    private const val REQ_SKIP = 3
    private const val REQ_OPEN = 4

    // Long enough to be noticed, short enough not to linger into the next set.
    private const val DONE_TIMEOUT_MS = 60_000L

    private val _state = MutableStateFlow<RestTimerState?>(null)
    private var loaded = false

    /** Observable countdown, `null` when no rest period is running. */
    fun state(context: Context): StateFlow<RestTimerState?> {
        if (!loaded) {
            loaded = true
            val stored = storedEndAt(context)
            if (stored > System.currentTimeMillis()) {
                _state.value = RestTimerState(stored, prefs(context).getInt(KEY_TOTAL_SECONDS, DEFAULT_SECONDS))
            } else if (stored != 0L) {
                // Alarms do not survive a reboot, so a deadline already in the past is stale.
                clearStored(context)
            }
        }
        return _state.asStateFlow()
    }

    fun start(context: Context, seconds: Int) {
        val total = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
        schedule(context, System.currentTimeMillis() + total * 1000L, total)
    }

    /** Adds time to a running rest period; does nothing when none is running. */
    fun extend(context: Context, seconds: Int = EXTEND_SECONDS) {
        val running = state(context).value ?: return
        schedule(context, running.endAt + seconds * 1000L, running.totalSeconds + seconds)
    }

    /** Ends the rest period early, without alerting. */
    fun skip(context: Context) = clear(context)

    /**
     * Alerts that the rest period is over. Called by the alarm, and by the UI the moment it sees the
     * countdown reach zero — whichever happens first. [endAt] identifies the period, so the other
     * caller finds the stored deadline already gone and stays quiet.
     */
    fun finish(context: Context, endAt: Long) {
        if (endAt == 0L || storedEndAt(context) != endAt) return
        clear(context)
        ensureChannels(context)
        notify(context, ID_DONE, doneNotification(context))
        if (ThemePreferences.getRestVibrate(context)) vibrate(context)
    }

    private fun schedule(context: Context, endAt: Long, totalSeconds: Int) {
        prefs(context).edit {
            putLong(KEY_END_AT, endAt)
            putInt(KEY_TOTAL_SECONDS, totalSeconds)
        }
        loaded = true
        _state.value = RestTimerState(endAt, totalSeconds)

        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarm = finishIntent(context, endAt)
        // Exact alarms need a user grant from Android 14 on. The inexact fallback is still prompt
        // while the phone is awake, and the in-app countdown covers the foreground case anyway.
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, alarm)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, alarm)
            }
        }

        ensureChannels(context)
        NotificationManagerCompat.from(context).cancel(ID_DONE)
        notify(context, ID_RUNNING, runningNotification(context, endAt, totalSeconds))
    }

    private fun clear(context: Context) {
        runCatching {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarms.cancel(finishIntent(context, 0L))
        }
        clearStored(context)
        loaded = true
        _state.value = null
        NotificationManagerCompat.from(context).cancel(ID_RUNNING)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun storedEndAt(context: Context): Long = prefs(context).getLong(KEY_END_AT, 0L)

    private fun clearStored(context: Context) {
        prefs(context).edit { remove(KEY_END_AT); remove(KEY_TOTAL_SECONDS) }
    }

    // Extras are not part of PendingIntent identity, so the same request code both updates the
    // pending alarm's deadline and cancels it.
    private fun finishIntent(context: Context, endAt: Long): PendingIntent =
        broadcast(context, REQ_FINISH, RestTimerReceiver.ACTION_FINISH) {
            putExtra(RestTimerReceiver.EXTRA_END_AT, endAt)
        }

    private fun broadcast(
        context: Context,
        requestCode: Int,
        action: String,
        extras: Intent.() -> Unit = {}
    ): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java).setAction(action).apply(extras)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, REQ_OPEN,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun runningNotification(context: Context, endAt: Long, totalSeconds: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_rest_timer)
            .setContentTitle("Resting")
            .setContentText("${formatRestDuration(totalSeconds)} rest")
            .setWhen(endAt)
            .setShowWhen(true)
            // The platform ticks this down itself, so a backgrounded app costs nothing to keep current.
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(context))
            .addAction(0, "+${EXTEND_SECONDS}s", broadcast(context, REQ_EXTEND, RestTimerReceiver.ACTION_EXTEND))
            .addAction(0, "Skip", broadcast(context, REQ_SKIP, RestTimerReceiver.ACTION_SKIP))
            .build()

    private fun doneNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_rest_timer)
            .setContentTitle("Rest over")
            .setContentText("Time for your next set")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Pre-O only; from O on the channel owns the sound.
            .setDefaults(Notification.DEFAULT_SOUND)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(DONE_TIMEOUT_MS)
            .setContentIntent(openApp(context))
            .build()

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "Rest countdown", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The silent countdown shown while you rest between sets"
                enableVibration(false)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Rest finished", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts you when a rest period is over"
                // Vibration is driven from code instead, so the in-app setting stays in control.
                enableVibration(false)
            }
        )
    }

    // POST_NOTIFICATIONS may be denied; the countdown and the vibration still work without it.
    private fun notify(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        val pattern = longArrayOf(0, 400, 200, 400)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }
}

/** Fires the end-of-rest alert, and backs the notification's "+30s" and "Skip" actions. */
class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FINISH -> RestTimer.finish(context, intent.getLongExtra(EXTRA_END_AT, 0L))
            ACTION_EXTEND -> RestTimer.extend(context)
            ACTION_SKIP -> RestTimer.skip(context)
        }
    }

    companion object {
        const val ACTION_FINISH = "me.bankosegger.phribs.action.REST_FINISH"
        const val ACTION_EXTEND = "me.bankosegger.phribs.action.REST_EXTEND"
        const val ACTION_SKIP = "me.bankosegger.phribs.action.REST_SKIP"
        const val EXTRA_END_AT = "end_at"
    }
}
