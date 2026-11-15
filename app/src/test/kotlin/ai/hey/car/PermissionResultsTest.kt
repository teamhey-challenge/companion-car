package ai.hey.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionResultsTest {
    private val mic = "android.permission.RECORD_AUDIO"
    private val notif = "android.permission.POST_NOTIFICATIONS"

    @Test fun microphoneGrantedAndDenied() {
        assertEquals(true, voiceAnswer(mapOf(mic to true)))
        assertEquals(false, voiceAnswer(mapOf(mic to false)))
    }

    @Test fun notificationAnswerNeverReachesTheVoiceSession() {
        assertNull(voiceAnswer(mapOf(notif to false)))
        assertNull(voiceAnswer(mapOf(notif to true)))
        assertNull(voiceAnswer(emptyMap())) // cancelled request
    }

    @Test fun combinedResultUsesOnlyTheMicrophone() {
        assertEquals(false, voiceAnswer(mapOf(notif to true, mic to false)))
        assertEquals(true, voiceAnswer(mapOf(notif to false, mic to true)))
    }

    @Test fun permissionsAreOnlyAskedWhileParked() {
        assertEquals(true, mayAskPermission(parked = true))
        assertEquals(false, mayAskPermission(parked = false)) // driving or unknown
    }

    @Test fun notificationsAreAskedOnlyParkedAfterConsentAndOnboardingOnce() {
        fun ask(parked: Boolean = true, consented: Boolean = true, done: Boolean = true, asked: Boolean = false, granted: Boolean = false, sdk: Int = 34) =
            shouldAskNotifications(parked, consented, done, asked, granted, sdk)
        assertEquals(true, ask())
        assertEquals(false, ask(parked = false)) // driving or unknown
        assertEquals(false, ask(consented = false))
        assertEquals(false, ask(done = false))
        assertEquals(false, ask(asked = true)) // a denial is kept
        assertEquals(false, ask(granted = true))
        assertEquals(false, ask(sdk = 32)) // no runtime permission before Android 13
    }

    @Test fun microphoneAndNotificationsAreTheRuntimePermissionsRequested() {
        assertEquals(setOf(mic, notif), requestedRuntimePermissions)
    }
}
