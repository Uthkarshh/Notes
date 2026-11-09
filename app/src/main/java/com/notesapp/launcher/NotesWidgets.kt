package com.notesapp.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.notesapp.MainActivity
import com.notesapp.data.prefs.FeaturePreferences
import com.notesapp.data.repository.FactRepository
import com.notesapp.domain.usecase.RotateDailyFact
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetServices { fun features(): FeaturePreferences; fun facts(): FactRepository; fun rotate(): RotateDailyFact; fun settings(): com.notesapp.data.prefs.SettingsRepository }
private fun services(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, WidgetServices::class.java)

class DailyFactWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = services(context)
        val initialFeatures = app.features().settings.first()
        if (!initialFeatures.lockEnabled) app.rotate().ensureToday()
        val initialDay = app.settings().dailyFact.first()
        val factFlow = app.facts().observeAll()
        val initialFacts = factFlow.first()
        provideContent {
            val features by app.features().settings.collectAsState(initialFeatures)
            val day by app.settings().dailyFact.collectAsState(initialDay)
            val facts by factFlow.collectAsState(initialFacts)
            val locked = features.lockEnabled
            val fact = if (locked) null else facts.firstOrNull { it.fact.id == day.factId }?.fact
            Column(GlanceModifier.fillMaxSize().background(Color(0xFFF2F4FF)).padding(16.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(if (locked) "Notes App is locked" else "Morning fact", style = TextStyle(color = ColorProvider(Color(0xFF24304F)), fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(8.dp))
                Text(if (locked) "Unlock the app to view your knowledge." else fact?.text?.take(500) ?: "Save your first fact in the Vault.", maxLines = if (LocalSize.current.height < 220.dp) 2 else 5, style = TextStyle(color = ColorProvider(Color(0xFF24304F))))
                Spacer(GlanceModifier.height(8.dp))
                if (locked) Button("Unlock", actionStartActivity(Intent(context, MainActivity::class.java)), style = TextStyle(color = ColorProvider(Color.White)))
                else Button("Next fact", actionRunCallback<RefreshFact>())
            }
        }
    }
}
class RefreshFact : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = services(context)
        if (!app.features().settings.first().lockEnabled) app.rotate().next()
        DailyFactWidget().updateAll(context)
    }
}
class DailyFactWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = DailyFactWidget() }

class CaptureWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Row(GlanceModifier.fillMaxSize().background(Color(0xFFF2F4FF)).padding(8.dp), verticalAlignment = Alignment.Vertical.CenterVertically, horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                Button("Text", actionStartActivity(LauncherActions.intent(context, "capture")))
                Spacer(GlanceModifier.width(4.dp))
                Button("Voice", actionStartActivity(LauncherActions.intent(context, "voice")))
                Spacer(GlanceModifier.width(4.dp))
                Button("Pick", actionStartActivity(LauncherActions.intent(context, "random")))
            }
        }
    }
}
class CaptureWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = CaptureWidget() }
