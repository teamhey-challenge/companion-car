package ai.hey.model

/**
 * The first-run flow after the C1 consent (D-48 2, 3). The spoken steps ([wire] != null) are asked of the server over the talk
 * socket and work in any driving state; the others need the driver's hands and wait until the car is parked ([parkedOnly]).
 * The step is stored on the device, so a restart or a drive in the middle resumes where it stopped.
 */
enum class OnboardingStep(val wire: String?, val parkedOnly: Boolean) {
    /** (a) The whale shows up and speaks one world line. */
    WhaleIntro("whale_intro", false),

    /** (b) The three whale questions, answered by voice. */
    Q1("q1", false),
    Q2("q2", false),
    Q3("q3", false),

    /** (c) Long press to summon heyri. */
    Summon(null, true),

    /** (d) Heyri's first greeting, using the answers. */
    Greeting("greeting", false),

    /** (e) "너를 이만큼 알아": up to three memory cards, view only. */
    Cards(null, true),

    /** (f) First look picker. */
    Look(null, true),

    /** (g) Phone link suggestion with the QR (skippable). */
    Pair(null, true),
    Done(null, false),
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
