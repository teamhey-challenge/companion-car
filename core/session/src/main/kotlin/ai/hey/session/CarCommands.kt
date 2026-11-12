package ai.hey.session

import ai.hey.model.CarSettings
import ai.hey.model.DropoffCandidate
import ai.hey.model.DropoffReasonId
import ai.hey.model.QuickReplyId

/**
 * Commands that are safe in any phase: speech, local view state, settings, and recovering a vehicle service.
 * Nothing here changes the trip, so a moving car may use all of it.
 */
interface CarCommands {
    fun replay(textKo: String)

    /** Reads a request again; a request that was never heard is marked spoken when the reading ends. */
    fun replayRequest(requestId: String)

    fun closeDropoff()
    fun selectCandidate(candidateId: String)
    fun selectReason(index: Int)
    fun dismissRequests()
    fun closeSettings()
    fun closeEndDialog()
    fun updateSettings(transform: (CarSettings) -> CarSettings)

    /** D1 retry for a navigation or speech problem; safe while moving. */
    fun retryVehicleIssue(issue: VehicleIssue)
}

/**
 * Commands that need the driver's hands. Only [CarPhase.Parked.requireParked] can hand these out, so a screen cannot
 * reach them from a driving phase even if a stale button tap arrives.
 */
interface ParkedCommands {
    fun approveJoin(joinRequestId: String)
    fun declineJoin(joinRequestId: String)
    fun recheckCard()
    fun startRoute()

    /** Sends the chosen candidate with its structured fields (kind, entranceType, ref, street) so passengers get localized labels. */
    fun proposeDropoff(candidate: DropoffCandidate, reasonId: DropoffReasonId?)
    fun decidePin(version: Int, accept: Boolean)
    fun sendReply(requestId: String?, replyId: QuickReplyId)
    fun arrive()
    fun endTrip()
    fun openDropoff()
    fun openSettings()
    fun openEndDialog()

    /** Drops the registration this relay no longer knows and registers again (the QR sticker changes). */
    fun reregister()
}

/** For previews and snapshots. */
object NoCommands : CarCommands, ParkedCommands {
    override fun approveJoin(joinRequestId: String) = Unit
    override fun declineJoin(joinRequestId: String) = Unit
    override fun recheckCard() = Unit
    override fun startRoute() = Unit
    override fun proposeDropoff(candidate: DropoffCandidate, reasonId: DropoffReasonId?) = Unit
    override fun decidePin(version: Int, accept: Boolean) = Unit
    override fun sendReply(requestId: String?, replyId: QuickReplyId) = Unit
    override fun arrive() = Unit
    override fun endTrip() = Unit
    override fun replay(textKo: String) = Unit
    override fun replayRequest(requestId: String) = Unit
    override fun reregister() = Unit
    override fun openDropoff() = Unit
    override fun closeDropoff() = Unit
    override fun selectCandidate(candidateId: String) = Unit
    override fun selectReason(index: Int) = Unit
    override fun dismissRequests() = Unit
    override fun openSettings() = Unit
    override fun closeSettings() = Unit
    override fun openEndDialog() = Unit
    override fun closeEndDialog() = Unit
    override fun updateSettings(transform: (CarSettings) -> CarSettings) = Unit
    override fun retryVehicleIssue(issue: VehicleIssue) = Unit
}
