package me.bankosegger.phribs.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import me.bankosegger.phribs.RestTimer
import me.bankosegger.phribs.RestTimerState
import me.bankosegger.phribs.formatRestDuration

/**
 * Returns a "start resting for n seconds" action that also asks for notification permission the
 * first time, so the countdown the user just started is actually allowed to alert them.
 */
@Composable
fun rememberRestTimerStarter(): (Int) -> Unit {
    val context = LocalContext.current
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The timer runs either way; a denied prompt just means no notification. */ }
    return remember(context, requestNotifications) {
        { seconds ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            RestTimer.start(context, seconds)
        }
    }
}

/**
 * The in-app face of the rest countdown, meant for a `Scaffold` bottom bar. The container is always
 * emitted so the bar keeps reserving whatever window insets [modifier] carries, even while idle.
 */
@Composable
fun RestTimerBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timer by RestTimer.state(context).collectAsState()
    Column(modifier.fillMaxWidth()) {
        timer?.let { RestTimerCard(it) }
    }
}

@Composable
private fun RestTimerCard(timer: RestTimerState) {
    val context = LocalContext.current
    var remaining by remember(timer.endAt) { mutableIntStateOf(timer.remainingSeconds()) }
    LaunchedEffect(timer.endAt) {
        while (remaining > 0) {
            // Sub-second polling keeps the displayed seconds from lagging behind the real deadline.
            delay(200)
            remaining = timer.remainingSeconds()
        }
        // Alerting from the foreground keeps things punctual even when exact alarms are withheld.
        RestTimer.finish(context, timer.endAt)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Rest", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = formatRestDuration(remaining),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                val buttonColors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                TextButton(onClick = { RestTimer.extend(context) }, colors = buttonColors) {
                    Text("+${RestTimer.EXTEND_SECONDS}s", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { RestTimer.skip(context) }, colors = buttonColors) {
                    Text("Skip", fontWeight = FontWeight.SemiBold)
                }
            }
            LinearProgressIndicator(
                progress = {
                    if (timer.totalSeconds > 0) remaining.toFloat() / timer.totalSeconds else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f)
            )
        }
    }
}
