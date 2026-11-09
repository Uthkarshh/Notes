package com.notesapp.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.data.prefs.FeaturePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockSession @Inject constructor() {
    val unlocked = MutableStateFlow(false)
    fun lock() { unlocked.value = false }
    fun unlock() { unlocked.value = true }
}

abstract class ProtectedActivity : FragmentActivity() {
    @Inject lateinit var featurePreferences: FeaturePreferences
    @Inject lateinit var lockSession: LockSession
    private var credentialResult: ((Boolean) -> Unit)? = null
    private var prompting = false
    override fun onCreate(savedInstanceState: Bundle?) {
        // Protect the first frame, before asynchronous preferences can establish the lock policy.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
    }
    private val credentials = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        prompting = false
        credentialResult?.invoke(it.resultCode == RESULT_OK)
        credentialResult = null
    }

    fun canAuthenticate(): Boolean = getSystemService(KeyguardManager::class.java).isDeviceSecure ||
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(callback: (Boolean) -> Unit) {
        if (prompting) return
        prompting = true
        fun deviceCredential() {
            val manager = getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION") val intent = manager.createConfirmDeviceCredentialIntent("Unlock Notes App", "Confirm your device PIN, pattern, or password.")
            if (intent == null) { prompting = false; callback(false) }
            else { credentialResult = callback; credentials.launch(intent) }
        }
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            deviceCredential(); return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { prompting = false; callback(true) }
            override fun onAuthenticationError(code: Int, message: CharSequence) {
                if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON && getSystemService(KeyguardManager::class.java).isDeviceSecure) deviceCredential()
                else { prompting = false; callback(false) }
            }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Notes App")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(if (getSystemService(KeyguardManager::class.java).isDeviceSecure) "Use device PIN" else "Cancel").build())
    }

    @Composable
    fun ProtectedContent(content: @Composable () -> Unit) {
        val settings by featurePreferences.settings.collectAsStateWithLifecycle(initialValue = null)
        val unlocked by lockSession.unlocked.collectAsStateWithLifecycle()
        val holder = rememberSaveableStateHolder()
        val current = settings
        DisposableEffect(current?.lockEnabled) {
            if (current?.lockEnabled != false) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { }
        }
        when {
            current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            !current.lockEnabled || unlocked -> holder.SaveableStateProvider("protected") { content() }
            else -> Surface(Modifier.fillMaxSize().testTag("app_locked")) {
                Column(Modifier.fillMaxSize().padding(androidx.compose.ui.unit.Dp(24f)), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Notes App is locked", style = MaterialTheme.typography.headlineSmall)
                    Text("Unlock to view your collections and knowledge.")
                    if (!canAuthenticate()) {
                        Text("Set up a device PIN, password, or biometrics in Android Settings to unlock.")
                        TextButton(onClick = { startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }) { Text("Android security settings") }
                    }
                    Button(onClick = { authenticate { if (it) lockSession.unlock() } }, modifier = Modifier.testTag("app_unlock")) { Text("Unlock") }
                }
            }
        }
    }
}
