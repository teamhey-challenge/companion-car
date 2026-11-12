package ai.hey.model

/**
 * Sentences the car itself speaks, for the events the relay sends no `say` for. Everything else the driver hears comes
 * from the relay as `say` (the reviewed `speech.*` sentences; hey-shared has none for these three, so they are car-local).
 * The screen is blocked while driving (docs/architecture.md section 6), so every event a driver must know has to be one
 * of the two. `TripRepositoryTest` pins which snapshot transitions speak here; `RelayIntegrationTest` pins the relay's side.
 */
object LocalSpeech {
    /** The trip reached ARRIVED (navigation event or the 도착 button): the relay sends no `say` for it. */
    const val ARRIVED = "목적지에 도착했어요. 정차하면 운행 끝내기를 눌러 주세요."

    /** The trip reached ENDED, whatever the reason (driver, passenger lead, 4 h cap). */
    const val ENDED = "운행을 마쳤어요."

    /** A join request disappeared (REQUESTED -> IDLE) without the driver deciding it. */
    const val JOIN_EXPIRED = "승객의 연결 요청이 끝났어요."
}
