package com.notesapp.ui.item

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.notesapp.ui.components.SectionTitle
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun ReminderControls(reminderAt: Long?, onSet: (Long?) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) "Android notifications enabled." else "Enable Android notifications to receive reminders."
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("item_reminder")) {
        SectionTitle("Reminder")
        reminderAt?.let { Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val calendar = Calendar.getInstance().apply { timeInMillis = reminderAt ?: (System.currentTimeMillis() + 3_600_000) }
                DatePickerDialog(context, { _, year, month, day ->
                    TimePickerDialog(context, { _, hour, minute ->
                        calendar.set(year, month, day, hour, minute, 0); calendar.set(Calendar.MILLISECOND, 0)
                        if (calendar.timeInMillis <= System.currentTimeMillis()) message = "Choose a future date and time."
                        else {
                            onSet(calendar.timeInMillis)
                            message = null
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            }, modifier = Modifier.testTag("item_set_reminder")) { Text(if (reminderAt == null) "Set reminder" else "Change reminder") }
            if (reminderAt != null) TextButton(onClick = { onSet(null); message = null }, modifier = Modifier.testTag("item_clear_reminder")) { Text("Clear") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (reminderAt != null) {
            Text("Manage notification and exact-alarm access in Notifications & reminders.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onOpenSettings) { Text("Notifications & reminders") }
        }
    }
}
