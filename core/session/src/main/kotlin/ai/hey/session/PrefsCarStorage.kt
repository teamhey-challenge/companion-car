package ai.hey.session

import ai.hey.model.CarSettings
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Device-only storage in app-private SharedPreferences (not backed up: allowBackup=false). The driver token is
 * stored here and nowhere else; it is never logged. Registrations are kept per relay address, so trying another
 * relay never overwrites the one printed on the QR sticker.
 */
class PrefsCarStorage(context: Context, private val defaultRelayUrl: String = CarSettings().relayUrl) : CarStorage {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("hey_car", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<CarSettings> = _settings.asStateFlow()

    private fun read(): CarSettings {
        val d = CarSettings()
        return CarSettings(
            speechGapSec = prefs.getInt(KEY_GAP, d.speechGapSec),
            dark = prefs.getBoolean(KEY_DARK, d.dark),
            plateSuffix = prefs.getString(KEY_PLATE, null) ?: d.plateSuffix,
            relayUrl = prefs.getString(KEY_RELAY, null) ?: defaultRelayUrl,
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
                .putString(KEY_PLATE, next.plateSuffix)
                .putString(KEY_RELAY, next.relayUrl)
                .commit()
            if (kept) SaveResult.Saved else SaveResult.Failed(SaveFailure.WriteRejected)
        } catch (e: Exception) {
            SaveResult.Failed(SaveFailure.WriteError)
        }
    }

    private fun key(relayUrl: String, field: String) = "reg:${relayUrl.trimEnd('/')}:$field"

    override suspend fun loadRegistration(relayUrl: String): Registration? = withContext(Dispatchers.IO) {
        val code = prefs.getString(key(relayUrl, "code"), null) ?: return@withContext null
        val token = prefs.getString(key(relayUrl, "token"), null) ?: return@withContext null
        Registration(
            carCode = code,
            driverToken = token,
            qrUrl = prefs.getString(key(relayUrl, "qr"), null) ?: return@withContext null,
            relayUrl = relayUrl,
            plateSuffix = prefs.getString(key(relayUrl, "plate"), null).orEmpty(),
        )
    }

    override suspend fun saveRegistration(registration: Registration) {
        withContext(Dispatchers.IO) {
            val r = registration.relayUrl
            prefs.edit()
                .putString(key(r, "code"), registration.carCode)
                .putString(key(r, "token"), registration.driverToken)
                .putString(key(r, "qr"), registration.qrUrl)
                .putString(key(r, "plate"), registration.plateSuffix)
                .commit()
        }
    }

    override suspend fun forgetRegistration(relayUrl: String) {
        withContext(Dispatchers.IO) {
            val r = relayUrl
            prefs.edit()
                .remove(key(r, "code")).remove(key(r, "token")).remove(key(r, "qr")).remove(key(r, "plate"))
                .commit()
        }
    }

    private companion object {
        const val KEY_GAP = "speech_gap"
        const val KEY_DARK = "dark"
        const val KEY_PLATE = "plate"
        const val KEY_RELAY = "relay"
    }
}
