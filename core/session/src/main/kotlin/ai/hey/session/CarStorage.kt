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
    suspend fun loadRegistration(): Registration?
    suspend fun saveRegistration(registration: Registration)
}

class InMemoryCarStorage(initial: CarSettings = CarSettings(), private var registration: Registration? = null) : CarStorage {
    private val _settings = MutableStateFlow(initial)
    override val settings: StateFlow<CarSettings> = _settings.asStateFlow()
    override suspend fun updateSettings(transform: (CarSettings) -> CarSettings) = _settings.update(transform)
    override suspend fun loadRegistration(): Registration? = registration
    override suspend fun saveRegistration(registration: Registration) {
        this.registration = registration
    }
}
