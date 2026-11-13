package ai.hey.session

import ai.hey.model.CarSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

/**
 * Device-only storage. The car token (what /v1/cars/register gave this install) lives here and nowhere else; it is
 * never logged and never part of [CarUiState].
 */
interface CarStorage {
    val settings: StateFlow<CarSettings>

    /**
     * Applies [transform] to the current settings. [settings] always shows the new value (this session uses it), the
     * result says whether the device also kept it for the next start.
     */
    suspend fun updateSettings(transform: (CarSettings) -> CarSettings): SaveResult

    /** Random, stable per installation (8-64 chars of A-Za-z0-9_-): what the server keys the car token on. */
    suspend fun installId(): String

    /** The car token for exactly this server address; one per server, never overwritten by another server's. */
    suspend fun loadCarToken(serverUrl: String): String?
    suspend fun saveCarToken(serverUrl: String, token: String)
    suspend fun forgetCarToken(serverUrl: String)
}

class InMemoryCarStorage(initial: CarSettings = CarSettings(), private val install: String = "test-install-0001") : CarStorage {
    private val _settings = MutableStateFlow(initial)
    private val tokens = HashMap<String, String>()
    override val settings: StateFlow<CarSettings> = _settings.asStateFlow()
    override suspend fun updateSettings(transform: (CarSettings) -> CarSettings): SaveResult {
        _settings.update(transform)
        return SaveResult.Saved
    }
    override suspend fun installId(): String = install
    override suspend fun loadCarToken(serverUrl: String): String? = synchronized(tokens) { tokens[serverUrl.trimEnd('/')] }
    override suspend fun saveCarToken(serverUrl: String, token: String) {
        synchronized(tokens) { tokens[serverUrl.trimEnd('/')] = token }
    }
    override suspend fun forgetCarToken(serverUrl: String) {
        synchronized(tokens) { tokens.remove(serverUrl.trimEnd('/')) }
    }
}
