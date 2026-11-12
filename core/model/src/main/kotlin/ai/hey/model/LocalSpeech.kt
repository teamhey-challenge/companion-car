package ai.hey.model

/**
 * Sentences the car itself speaks. Everything else the driver hears comes from the relay as `say` (the reviewed
 * `speech.*` sentences). The screen is blocked while driving (docs/architecture.md section 6), so every event a
 * driver must know has to be one of the two; `SpeechCoverageTest` lists them.
 */
object LocalSpeech {
    /** The trip reached ARRIVED (navigation event or the 도착 button): the relay sends no `say` for it. */
    const val ARRIVED = "목적지에 도착했어요. 정차하면 운행 끝내기를 눌러 주세요."
}
