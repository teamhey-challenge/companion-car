package ai.hey.vehicle

import android.media.AudioAttributes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldAudioTest {
    @Test fun `our own assistant voice never blocks a proactive line`() {
        assertFalse(HeldAudio.blocks(listOf(AudioAttributes.USAGE_ASSISTANT)))
        assertFalse(HeldAudio.blocks(emptyList()))
    }

    @Test fun `media, guidance and calls block, also next to our own voice`() {
        assertTrue(HeldAudio.blocks(listOf(AudioAttributes.USAGE_MEDIA)))
        assertTrue(HeldAudio.blocks(listOf(AudioAttributes.USAGE_ASSISTANT, AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)))
        assertTrue(HeldAudio.blocks(listOf(AudioAttributes.USAGE_VOICE_COMMUNICATION)))
        assertTrue(HeldAudio.blocks(listOf(AudioAttributes.USAGE_UNKNOWN)))
    }
}
