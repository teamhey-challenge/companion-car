package ai.hey.session

import ai.hey.model.CarSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the relay gave this car. The driver token lives only on the device; it is never logged and never part of
 * [CarUiState].
 */
data class Registration(
    val carCode: String,
    val driverToken: String,
    val qrUrl: String,
    val relayUrl: String,
    val plateSuffix: String,
) {
    override fun toString(): String = "Registration(carCode=$carCode, relayUrl=$relayUrl, plateSuffix=$plateSuffix)"
}

/** Why the device refused to keep a value. */
enum class SaveFailure {
    /** The platform reported that the write did not happen (disk full, storage unavailable). */
    WriteRejected,

    /** The write threw. */
    WriteError,
}

/** The outcome of writing settings: a type, not a boolean, so a caller cannot forget the failure branch. */
sealed interface SaveResult {
    data object Saved : SaveResult
    data class Failed(val reason: SaveFailure) : SaveResult
}

interface CarStorage {
    val settings: StateFlow<CarSettings>

    /**
     * Applies [transform] to the current settings. [settings] always shows the new value (this session uses it), the
     * result says whether the device also kept it for the next start.
     */
    suspend fun updateSettings(transform: (CarSettings) -> CarSettings): SaveResult

    /** The registration for exactly this relay address; one per relay, never overwritten by another relay's. */
    suspend fun loadRegistration(relayUrl: String): Registration?
    suspend fun saveRegistration(registration: Registration)
    suspend fun forgetRegistration(relayUrl: String)
}

class InMemoryCarStorage(initial: CarSettings = CarSettings()) : CarStorage {
    private val _settings = MutableStateFlow(initial)
    private val registrations = HashMap<String, Registration>()
    override val settings: StateFlow<CarSettings> = _settings.asStateFlow()
    override suspend fun updateSettings(transform: (CarSettings) -> CarSettings): SaveResult {
        _settings.update(transform)
        return SaveResult.Saved
    }
    override suspend fun loadRegistration(relayUrl: String): Registration? = synchronized(registrations) { registrations[relayUrl.trimEnd('/')] }
    override suspend fun saveRegistration(registration: Registration) {
        synchronized(registrations) { registrations[registration.relayUrl.trimEnd('/')] = registration }
    }
    override suspend fun forgetRegistration(relayUrl: String) {
        synchronized(registrations) { registrations.remove(relayUrl.trimEnd('/')) }
    }
}
