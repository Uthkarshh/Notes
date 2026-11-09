package com.notesapp.data

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.rule.GrantPermissionRule
import androidx.work.WorkManager
import com.notesapp.automation.*
import com.notesapp.data.prefs.NotificationSettings
import com.notesapp.data.repository.NewItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class NotificationDeliveryTest : DataTestBase() {
    @get:Rule val notificationsPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val helper get() = NotificationHelper(context)

    @After fun dismissTestNotifications() { context.getSystemService(NotificationManager::class.java).cancelAll() }

    @Test fun dailyNotificationMatchesFiltersAndPostsOnlyOncePerDay() = runTest {
        env.facts.create("A food fact", topic = "Food")
        val fact = env.facts.create("An animal fact", topic = "Animals", source = "A book")
        env.settings.setNotifications(NotificationSettings(enabled = true, minuteOfDay = 480, topic = "Animals", source = "book"))
        val delivery = DailyFactDelivery(env.settings, env.rotateDailyFact, env.db, helper, env.clock)
        assertTrue(delivery.deliver())
        assertFalse(delivery.deliver())
        assertEquals(1, env.facts.get(fact)!!.shownCount)
        val manager = context.getSystemService(NotificationManager::class.java)
        var posted = false
        repeat(30) { if (manager.activeNotifications.any { it.tag == "daily" }) posted = true else if (!posted) Thread.sleep(50) }
        assertTrue("The notification must reach Android's notification manager", posted)
    }

    @Test fun disabledScheduleDoesNotPostOrCountAnImpression() = runTest {
        val fact = env.facts.create("Not shown")
        assertFalse(DailyFactDelivery(env.settings, env.rotateDailyFact, env.db, helper, env.clock).deliver())
        assertEquals(0, env.facts.get(fact)!!.shownCount)
    }

    @Test fun deniedPermissionDoesNotPostOrCountAnImpression() = runTest {
        val fact = env.facts.create("Not shown without permission")
        env.settings.setNotifications(NotificationSettings(enabled = true))
        val deniedContext = object : ContextWrapper(context) {
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.POST_NOTIFICATIONS) PackageManager.PERMISSION_DENIED else super.checkPermission(permission, pid, uid)
        }
        assertFalse(DailyFactDelivery(env.settings, env.rotateDailyFact, env.db, NotificationHelper(deniedContext), env.clock).deliver())
        assertEquals(0, env.facts.get(fact)!!.shownCount)
    }

    @Test fun staleAndDuplicateReminderAlarmsCannotDeliverTwice() = runTest {
        val id = env.items.create(NewItem("Reminder integration"))
        val at = env.clock.millis() - 1
        env.items.setReminder(id, at)
        val delivery = ReminderDelivery(env.db, helper, env.clock)
        assertFalse(delivery.deliver(id, at - 1))
        assertTrue(delivery.deliver(id, at))
        assertNull(env.items.get(id)!!.reminderAt)
        assertFalse(delivery.deliver(id, at))
    }

    @Test fun anEarlyAlarmDoesNotConsumeTheFutureReminder() = runTest {
        val id = env.items.create(NewItem("Future reminder"))
        val at = env.clock.millis() + 3_600_000
        env.items.setReminder(id, at)
        assertFalse(ReminderDelivery(env.db, helper, env.clock).deliver(id, at))
        assertEquals(at, env.items.get(id)!!.reminderAt)
    }

    @Test fun startupPreservesAnAlreadyDueDailyJobAndDisablingCancelsIt() = runTest {
        val work = WorkManager.getInstance(context)
        val scheduler = AutomationScheduler(context, env.db, env.settings, env.clock)
        fun active() = work.getWorkInfosByTag(AutomationScheduler.DAILY_TAG).get().filter { !it.state.isFinished }
        try {
            env.settings.setNotifications(NotificationSettings(enabled = true, minuteOfDay = 600))
            scheduler.scheduleDaily()
            val original = active().single().id
            scheduler.rescheduleAll()
            assertEquals(original, active().single().id)
            env.clock.advance(Duration.ofMinutes(61))
            scheduler.rescheduleAll()
            assertEquals("Startup must retain an occurrence that is already due", original, active().single().id)
            env.settings.setNotifications(NotificationSettings(enabled = false))
            scheduler.scheduleDaily()
            assertTrue(active().isEmpty())
        } finally { work.cancelAllWorkByTag(AutomationScheduler.DAILY_TAG).result.get() }
    }
}
