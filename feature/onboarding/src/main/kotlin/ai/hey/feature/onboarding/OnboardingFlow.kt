package ai.hey.feature.onboarding

/** "동의하고 시작" without the required consent ticked is refused on the screen: nothing is asked or sent. */
fun canStart(voiceTicked: Boolean): Boolean = voiceTicked
