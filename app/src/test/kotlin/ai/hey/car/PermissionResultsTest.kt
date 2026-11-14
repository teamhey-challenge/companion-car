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

    @Test fun microphoneIsTheOnlyRuntimePermissionRequested() {
        assertEquals(setOf(mic), requestedRuntimePermissions)
        assertEquals(false, notif in requestedRuntimePermissions)
    }
}
