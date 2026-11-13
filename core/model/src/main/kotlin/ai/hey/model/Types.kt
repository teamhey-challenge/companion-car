package ai.hey.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A navigation destination as the Pleos navigation service wants it (used for trip estimates only). */
@Serializable
data class Place(
    val placeId: String,
    val nameLocal: String,
    val nameKo: String,
    val addressKo: String,
    val area: String,
    val lat: Double,
    val lng: Double,
)

/** How urgently a text is read by the local speech engine. */
@Serializable
enum class SpeechPriority {
    @SerialName("high") High,
    @SerialName("normal") Normal,
}
