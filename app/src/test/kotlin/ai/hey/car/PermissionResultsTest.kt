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

    @Test fun notificationWaitsForParkAndIsAskedOnce() {
        assertEquals(false, shouldAskNotification(34, granted = false, parked = false, alreadyAsked = false))
        assertEquals(true, shouldAskNotification(34, granted = false, parked = true, alreadyAsked = false))
        assertEquals(false, shouldAskNotification(34, granted = false, parked = true, alreadyAsked = true))
        assertEquals(false, shouldAskNotification(34, granted = true, parked = true, alreadyAsked = false))
        assertEquals(false, shouldAskNotification(32, granted = false, parked = true, alreadyAsked = false))
    }
}
