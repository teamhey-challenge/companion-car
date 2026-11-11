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

interface CarStorage {
    val settings: StateFlow<CarSettings>
    suspend fun updateSettings(transform: (CarSettings) -> CarSettings)

    /** The registration for exactly this relay address; one per relay, never overwritten by another relay's. */
    suspend fun loadRegistration(relayUrl: String): Registration?
    suspend fun saveRegistration(registration: Registration)
    suspend fun forgetRegistration(relayUrl: String)
}

class InMemoryCarStorage(initial: CarSettings = CarSettings()) : CarStorage {
    private val _settings = MutableStateFlow(initial)
    private val registrations = HashMap<String, Registration>()
    override val settings: StateFlow<CarSettings> = _settings.asStateFlow()
    override suspend fun updateSettings(transform: (CarSettings) -> CarSettings) = _settings.update(transform)
    override suspend fun loadRegistration(relayUrl: String): Registration? = synchronized(registrations) { registrations[relayUrl.trimEnd('/')] }
    override suspend fun saveRegistration(registration: Registration) {
        synchronized(registrations) { registrations[registration.relayUrl.trimEnd('/')] = registration }
    }
    override suspend fun forgetRegistration(relayUrl: String) {
        synchronized(registrations) { registrations.remove(relayUrl.trimEnd('/')) }
    }
}
