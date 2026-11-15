package ai.hey.model

/**
 * The first-run flow, in the planner's order: title -> whale intro -> consent sheet -> lights -> three questions ->
 * found -> long press -> heyri's greeting -> memory cards -> first look -> phone link -> farewell -> home.
 *
 * A step with a [wire] name is spoken by the server over the talk socket and works in every driving state (motion and sound
 * keep going). A step that is [parkedOnly] needs the driver's hands (a press) and waits until the car is parked; the stored
 * step is kept, so a restart or a drive in the middle resumes where it stopped. A step that [awaitsScreen] is finished by its
 * screen (a press), not by the end of its spoken line.
 */
enum class OnboardingStep(
    val wire: String?,
    val parkedOnly: Boolean,
    val awaitsScreen: Boolean,
    /** The server says this step without the record / external_ai consent (fixed lines only), so it may run before C1. */
    val consentFree: Boolean,
    /** The voice that speaks the step (reverb is added to the whale only). */
    val whale: Boolean = false,
    /** How long the screen stays after the spoken line ended before the flow goes on by itself. */
    val holdMillis: Long = 0,
) {
    /** O1 title: one press on "다음". */
    Title(null, true, true, true),

    /** O1 the whale shows up and speaks the world line. */
    WhaleIntro("whale_intro", false, false, true, whale = true),

    /** O2 the consent sheet (C1): finished by the consent being recorded, parked only. */
    Consent(null, true, true, true),

    /** O3 "every light can be your heyri". */
    Lights("lights", false, false, true, whale = true),

    /** O3 the three whale questions, answered by voice. */
    Q1("q1", false, false, false, whale = true),
    Q2("q2", false, false, false, whale = true),
    Q3("q3", false, false, false, whale = true),

    /** O4 "found you": the whale announces the ring. */
    Found("found", false, false, true, whale = true),

    /** O4 long press on the ring brings heyri (parked only). */
    Summon(null, true, true, true),

    /** O5 heyri's first greeting, using the answers. */
    Greeting("greeting", false, false, false),

    /** O6 "너를 이만큼 알아": heyri says it while the three cards show. */
    Memory("memory", false, false, true, holdMillis = 3_000),

    /** O7 first dress-up: heyri asks, the driver picks (parked only). */
    Dress("dress", true, true, true),

    /** O8 phone link with the QR, skippable (parked only). */
    Qr("qr", true, true, true),

    /** O8 "let's go". */
    Farewell("done", false, false, true),
    Done(null, false, false, true),
    ;

    /** True for the steps the server speaks. */
    val spoken: Boolean get() = wire != null

    /** True for the three questions: the driver's answer follows the whale's line. */
    val isQuestion: Boolean get() = this == Q1 || this == Q2 || this == Q3

    /** This step can run now: parked-only steps wait for park, every other step runs while driving too. */
    fun canRun(parked: Boolean): Boolean = this != Done && (!parkedOnly || parked)

    fun next(): OnboardingStep = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]

    companion object {
        /** A stored name; anything unknown (or no value: an install from before this flow) is [Done]. */
        fun fromName(name: String?): OnboardingStep = entries.firstOrNull { it.name == name } ?: Done
    }
}

/** What the driver said to the three whale questions; null = skipped (that polaroid is dropped, /17:57 answer). */
data class OnboardingAnswers(val name: String? = null, val trips: String? = null, val topics: String? = null) {
    val any: Boolean get() = name != null || trips != null || topics != null
}
