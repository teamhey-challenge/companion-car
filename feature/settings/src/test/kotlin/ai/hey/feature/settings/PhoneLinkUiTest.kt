package ai.hey.feature.settings

import ai.hey.designsystem.component.PhoneLinkUi
import ai.hey.session.PairingUi
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneLinkUiTest {
    private val pending = PairingUi.Pending("K7M3Q9XD", "2026-11-15T10:05:00Z")

    @Test fun `a code is drawn as a QR only while parked`() {
        assertEquals(PhoneLinkUi.Code("K7M3Q9XD"), phoneLinkUi(pending, parked = true, flashing = false))
        // QR hidden while the car moves (§6.6): the same pairing state draws no code
        assertEquals(PhoneLinkUi.Loading, phoneLinkUi(pending, parked = false, flashing = false))
    }

    @Test fun `no pairing yet is a short loading state, the server out of reach is the error`() {
        assertEquals(PhoneLinkUi.Loading, phoneLinkUi(PairingUi.None, parked = true, flashing = false))
        assertEquals(PhoneLinkUi.Loading, phoneLinkUi(PairingUi.Creating, parked = true, flashing = false))
        assertEquals(PhoneLinkUi.Offline, phoneLinkUi(PairingUi.Failed, parked = true, flashing = false))
    }

    @Test fun `the linked frame flashes 연결됐어요 once then settles on the account`() {
        val fresh = PairingUi.Claimed("민수", fresh = true)
        assertEquals(PhoneLinkUi.Flash("민수"), phoneLinkUi(fresh, parked = true, flashing = true))
        assertEquals(PhoneLinkUi.Linked("민수"), phoneLinkUi(fresh, parked = true, flashing = false))
        // a car that was already linked when the screen opened never flashes
        assertEquals(PhoneLinkUi.Linked("민수"), phoneLinkUi(PairingUi.Claimed("민수"), parked = true, flashing = true))
        assertEquals(PhoneLinkUi.Linked(null), phoneLinkUi(PairingUi.Claimed(null), parked = true, flashing = false))
    }
}
