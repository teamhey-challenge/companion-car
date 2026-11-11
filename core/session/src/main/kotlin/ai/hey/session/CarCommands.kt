package ai.hey.session

import ai.hey.model.CarSettings
import ai.hey.model.DropoffReasonId
import ai.hey.model.LatLng
import ai.hey.model.QuickReplyId

/** Everything a screen can ask for. Screens never reach the socket or the vehicle themselves. */
interface CarCommands {
    // ---- relay commands (ignored while driving, except nothing: all of these need a parked car)
    fun approveJoin(joinRequestId: String)
    fun declineJoin(joinRequestId: String)
    fun recheckCard()
    fun startRoute()
    fun proposeDropoff(point: LatLng, label: String, reasonId: DropoffReasonId?)
    fun decidePin(version: Int, accept: Boolean)
    fun markSpoken(requestId: String)
    fun sendReply(requestId: String?, replyId: QuickReplyId)
    fun arrive()
    fun endTrip()

    // ---- vehicle
    fun replay(textKo: String)

    // ---- local UI state
    fun openDropoff()
    fun closeDropoff()
    fun selectCandidate(candidateId: String)
    fun selectReason(index: Int)
    fun dismissRequests()
    fun openSettings()
    fun closeSettings()
    fun openEndDialog()
    fun closeEndDialog()
    fun dismissError()

    // ---- settings (device only)
    fun updateSettings(transform: (CarSettings) -> CarSettings)
}

/** For previews and snapshots. */
object NoCommands : CarCommands {
    override fun approveJoin(joinRequestId: String) = Unit
    override fun declineJoin(joinRequestId: String) = Unit
    override fun recheckCard() = Unit
    override fun startRoute() = Unit
    override fun proposeDropoff(point: LatLng, label: String, reasonId: DropoffReasonId?) = Unit
    override fun decidePin(version: Int, accept: Boolean) = Unit
    override fun markSpoken(requestId: String) = Unit
    override fun sendReply(requestId: String?, replyId: QuickReplyId) = Unit
    override fun arrive() = Unit
    override fun endTrip() = Unit
    override fun replay(textKo: String) = Unit
    override fun openDropoff() = Unit
    override fun closeDropoff() = Unit
    override fun selectCandidate(candidateId: String) = Unit
    override fun selectReason(index: Int) = Unit
    override fun dismissRequests() = Unit
    override fun openSettings() = Unit
    override fun closeSettings() = Unit
    override fun openEndDialog() = Unit
    override fun closeEndDialog() = Unit
    override fun dismissError() = Unit
    override fun updateSettings(transform: (CarSettings) -> CarSettings) = Unit
}
