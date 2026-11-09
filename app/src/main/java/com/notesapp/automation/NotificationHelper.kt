package com.notesapp.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.notesapp.MainActivity
import com.notesapp.R
import com.notesapp.data.db.entity.FactEntity
import com.notesapp.data.db.entity.ItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class NotificationHelper @Inject constructor(@ApplicationContext private val context: Context, private val features: com.notesapp.data.prefs.FeaturePreferences? = null) {
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    fun createChannels() {
        manager.createNotificationChannels(listOf(
            NotificationChannel(DAILY_CHANNEL, "Morning facts", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Your scheduled daily fact" },
            NotificationChannel(REMINDER_CHANNEL, "Item reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Reminders you set on saved items" },
        ))
    }

    fun canNotify(channel: String? = null): Boolean {
        createChannels()
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() && (channel == null || manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE)
    }

    private fun open(type: String, id: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setData("notesapp://$type/$id".toUri())
            .putExtra(EXTRA_TYPE, type).putExtra(EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    suspend fun showFact(fact: FactEntity): Boolean = post("daily", DAILY_CHANNEL, "Your morning fact", fact.text,
        listOfNotNull(fact.source, fact.topic).joinToString(" · "), open("fact", fact.id))

    suspend fun showReminder(item: ItemEntity): Boolean = post("item:${item.id}", REMINDER_CHANNEL, item.title, item.description ?: "A reminder from your saved items.", "Item reminder", open("item", item.id))
    fun cancelReminder(itemId: Long) { manager.cancel("item:$itemId", 0) }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun post(tag: String, channel: String, title: String, text: String, subText: String, open: PendingIntent): Boolean {
        if (!canNotify(channel)) return false
        val private = features?.settings?.first()?.lockEnabled == true
        val displayTitle = if (private) "Notes App" else title
        val displayText = if (private) "Unlock Notes App to view this notification." else text
        return try {
            val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(displayTitle).setContentText(displayText.take(200)).setSubText(if (private) "Private notification" else subText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(displayText.take(4000)))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(open).setAutoCancel(true).build()
            NotificationManagerCompat.from(context).notify(tag, 0, notification)
            true
        } catch (_: SecurityException) { false }
    }

    companion object {
        const val DAILY_CHANNEL = "morning_facts"
        const val REMINDER_CHANNEL = "item_reminders"
        const val EXTRA_TYPE = "notification_type"
        const val EXTRA_ID = "notification_record_id"
    }
}
