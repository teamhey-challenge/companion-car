package ai.hey.session

import ai.hey.model.CarSettings
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

    private companion object {
        const val KEY_GAP = "speech_gap"
        const val KEY_DARK = "dark"
        const val KEY_SERVER = "server"
        const val KEY_INSTALL = "install_id"
    }
}
