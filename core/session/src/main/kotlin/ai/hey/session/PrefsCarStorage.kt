package ai.hey.session

import ai.hey.model.CarSettings
import ai.hey.model.ConsentType
import ai.hey.model.OnboardingStep
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Device-only storage in app-private SharedPreferences (not backed up: allowBackup=false). The car token is stored
 * here and nowhere else; it is never logged. Tokens are kept per server address, so trying another server never
 * overwrites the one that is in use.
 */
class PrefsCarStorage(context: Context, private val defaultServerUrl: String = CarSettings().serverUrl) : CarStorage {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("hey_car", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<CarSettings> = _settings.asStateFlow()

    private val _consents = MutableStateFlow(ConsentMirror(ConsentType.entries.filter { prefs.getBoolean(consentKey(it), false) }.toSet()))
    override val consents: StateFlow<ConsentMirror> = _consents.asStateFlow()

    private fun consentKey(type: ConsentType) = "consent:${type.wire}"

    override suspend fun setConsent(type: ConsentType, granted: Boolean) {
        withContext(Dispatchers.IO) { prefs.edit().putBoolean(consentKey(type), granted).commit() }
        _consents.value = ConsentMirror(if (granted) _consents.value.granted + type else _consents.value.granted - type)
    }

    private fun read(): CarSettings {
        val d = CarSettings()
        return CarSettings(
            speechGapSec = prefs.getInt(KEY_GAP, d.speechGapSec),
            dark = prefs.getBoolean(KEY_DARK, d.dark),
            serverUrl = prefs.getString(KEY_SERVER, null) ?: defaultServerUrl,
        )
    }

    override suspend fun updateSettings(transform: (CarSettings) -> CarSettings): SaveResult = withContext(Dispatchers.IO) {
        val next = transform(_settings.value)
        // The session uses the new value either way; the result tells whether it also survives a restart.
        _settings.value = next
        try {
            val kept = prefs.edit()
                .putInt(KEY_GAP, next.speechGapSec)
                .putBoolean(KEY_DARK, next.dark)
                .putString(KEY_SERVER, next.serverUrl)
                .commit()
            if (kept) SaveResult.Saved else SaveResult.Failed(SaveFailure.WriteRejected)
        } catch (e: Exception) {
            SaveResult.Failed(SaveFailure.WriteError)
        }
    }

    override suspend fun installId(): String = withContext(Dispatchers.IO) {
        prefs.getString(KEY_INSTALL, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_INSTALL, it).commit() }
    }

    private fun tokenKey(serverUrl: String) = "token:${serverUrl.trimEnd('/')}"

    override suspend fun loadCarToken(serverUrl: String): String? = withContext(Dispatchers.IO) { prefs.getString(tokenKey(serverUrl), null) }

    override suspend fun saveCarToken(serverUrl: String, token: String) {
        withContext(Dispatchers.IO) { prefs.edit().putString(tokenKey(serverUrl), token).commit() }
    }

    override suspend fun forgetCarToken(serverUrl: String) {
        withContext(Dispatchers.IO) { prefs.edit().remove(tokenKey(serverUrl)).commit() }
    }

    // A first run (no stored step and no consent yet) starts the flow at the title; an install from before the flow is Done.
    private val _step = MutableStateFlow(
        prefs.getString(KEY_STEP, null)?.let { OnboardingStep.fromName(it) }
            ?: if (ConsentType.Record in _consents.value.granted) OnboardingStep.Done else OnboardingStep.Title,
    )
    override val onboardingStep: StateFlow<OnboardingStep> = _step.asStateFlow()

    override suspend fun setOnboardingStep(step: OnboardingStep) {
        _step.value = step
        withContext(Dispatchers.IO) { prefs.edit().putString(KEY_STEP, step.name).commit() }
    }

    private companion object {
        const val KEY_STEP = "onboarding_step"
        const val KEY_GAP = "speech_gap"
        const val KEY_DARK = "dark"
        const val KEY_SERVER = "server"
        const val KEY_INSTALL = "install_id"
    }
}
