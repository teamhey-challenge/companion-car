package ai.hey.session

import ai.hey.model.CarSettings
import ai.hey.model.HeyJson
import ai.hey.model.PresetId
import ai.hey.model.SpeechRate
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Device-only storage in app-private SharedPreferences (not backed up: allowBackup=false). The driver token is
 * stored here and nowhere else; it is never logged.
 */
class PrefsCarStorage(context: Context, private val defaultRelayUrl: String = CarSettings().relayUrl) : CarStorage {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("hey_car", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<CarSettings> = _settings.asStateFlow()

    private fun read(): CarSettings {
        val d = CarSettings()
        val disabled = prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty()
        return CarSettings(
            enabledPresets = PresetId.entries.filter { it.name !in disabled || !it.driverCanDisable }.toSet(),
            speechGapSec = prefs.getInt(KEY_GAP, d.speechGapSec),
            speechRate = prefs.getString(KEY_RATE, null)?.let { runCatching { SpeechRate.valueOf(it) }.getOrNull() } ?: d.speechRate,
            dark = prefs.getBoolean(KEY_DARK, d.dark),
            plateSuffix = prefs.getString(KEY_PLATE, null) ?: d.plateSuffix,
            relayUrl = prefs.getString(KEY_RELAY, null) ?: defaultRelayUrl,
        )
    }

    override suspend fun updateSettings(transform: (CarSettings) -> CarSettings) {
        withContext(Dispatchers.IO) {
            val next = transform(_settings.value)
            prefs.edit()
                .putStringSet(KEY_DISABLED, PresetId.entries.filter { it !in next.enabledPresets }.map { it.name }.toSet())
                .putInt(KEY_GAP, next.speechGapSec)
                .putString(KEY_RATE, next.speechRate.name)
                .putBoolean(KEY_DARK, next.dark)
                .putString(KEY_PLATE, next.plateSuffix)
                .putString(KEY_RELAY, next.relayUrl)
                .apply()
            _settings.value = next
        }
    }

    override suspend fun loadRegistration(): Registration? = withContext(Dispatchers.IO) {
        val code = prefs.getString(KEY_CODE, null) ?: return@withContext null
        val token = prefs.getString(KEY_TOKEN, null) ?: return@withContext null
        Registration(
            carCode = code,
            driverToken = token,
            qrUrl = prefs.getString(KEY_QR, null) ?: return@withContext null,
            relayUrl = prefs.getString(KEY_REG_RELAY, null) ?: return@withContext null,
            plateSuffix = prefs.getString(KEY_REG_PLATE, null).orEmpty(),
        )
    }

    override suspend fun saveRegistration(registration: Registration) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_CODE, registration.carCode)
                .putString(KEY_TOKEN, registration.driverToken)
                .putString(KEY_QR, registration.qrUrl)
                .putString(KEY_REG_RELAY, registration.relayUrl)
                .putString(KEY_REG_PLATE, registration.plateSuffix)
                .commit()
        }
    }

    private companion object {
        const val KEY_DISABLED = "presets_off"
        const val KEY_GAP = "speech_gap"
        const val KEY_RATE = "speech_rate"
        const val KEY_DARK = "dark"
        const val KEY_PLATE = "plate"
        const val KEY_RELAY = "relay"
        const val KEY_CODE = "reg_code"
        const val KEY_TOKEN = "reg_token"
        const val KEY_QR = "reg_qr"
        const val KEY_REG_RELAY = "reg_relay"
        const val KEY_REG_PLATE = "reg_plate"
    }
}
