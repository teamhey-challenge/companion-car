package ai.hey.session

import ai.hey.model.CarSettings
import ai.hey.model.ConsentType
import ai.hey.model.OnboardingStep
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
 * The consents this device has recorded on the server and keeps a copy of, so the app can pick /onboarding or /home at
 * once, without waiting for the network. Only `granted = true` entries are in [granted].
 */
data class ConsentMirror(val granted: Set<ConsentType> = emptySet()) {
    /** The required consent (C1 "record"): without it the app shows /onboarding and has no conversation. */
    val recordDone: Boolean get() = ConsentType.Record in granted
}

/** Wording version recorded with every consent POST (bump when the C1/W1 copy changes). */
const val CONSENT_VERSION = "2026-11-15"

/**
 * Device-only storage. The car token (what /v1/cars/register gave this install) lives here and nowhere else; it is
 * never logged and never part of [CarUiState].
 */
interface CarStorage {
    /** Local mirror of the consents; changed only after the server accepted the change. */
    val consents: StateFlow<ConsentMirror>
    suspend fun setConsent(type: ConsentType, granted: Boolean)

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

    /** onboarding: the step the first-run flow is at. [OnboardingStep.Done] for installs that never started it. */
    val onboardingStep: StateFlow<OnboardingStep>
    suspend fun setOnboardingStep(step: OnboardingStep)

    /** §6.1: O8 was skipped, so home says "폰 연결하면 일기를 볼 수 있어" once. Cleared when it was shown or the phone is linked. */
    val phoneHintPending: StateFlow<Boolean>
    suspend fun setPhoneHintPending(pending: Boolean)
}

class InMemoryCarStorage(
    initial: CarSettings = CarSettings(),
    private val install: String = "test-install-0001",
    initialConsents: ConsentMirror = ConsentMirror(),
    initialStep: OnboardingStep = OnboardingStep.Done,
) : CarStorage {
    private val _settings = MutableStateFlow(initial)
    private val _consents = MutableStateFlow(initialConsents)
    private val tokens = HashMap<String, String>()
    private val _step = MutableStateFlow(initialStep)
    override val onboardingStep: StateFlow<OnboardingStep> = _step.asStateFlow()
    override suspend fun setOnboardingStep(step: OnboardingStep) { _step.value = step }
    private val _hint = MutableStateFlow(false)
    override val phoneHintPending: StateFlow<Boolean> = _hint.asStateFlow()
    override suspend fun setPhoneHintPending(pending: Boolean) { _hint.value = pending }
    override val settings: StateFlow<CarSettings> = _settings.asStateFlow()
    override val consents: StateFlow<ConsentMirror> = _consents.asStateFlow()
    override suspend fun setConsent(type: ConsentType, granted: Boolean) {
        _consents.update { ConsentMirror(if (granted) it.granted + type else it.granted - type) }
    }
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
