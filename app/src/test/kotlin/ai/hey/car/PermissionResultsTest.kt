package ai.hey.car

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionResultsTest {
    @Test fun permissionsAreOnlyAskedWhileParked() {
        assertEquals(true, mayAskPermission(parked = true))
        assertEquals(false, mayAskPermission(parked = false)) // driving or unknown
    }
}
