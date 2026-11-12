package ai.hey.session

import ai.hey.model.CarSettings
import ai.hey.model.ClientType
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
 * A hands-on action that changes the trip on the relay, with the message types it sends. This is the unit the relay's
 * transition table (`docs/transitions.json`) is checked against: a phase may enable a command only in a trip state
 * whose table row lists every one of [wire] for the car.
 */
enum class CarCommand(vararg wireTypes: String) {
    ApproveJoin(ClientType.JOIN_APPROVE),
    DeclineJoin(ClientType.JOIN_DECLINE),
    Recheck(ClientType.CARD_RECHECK),
    StartRoute(ClientType.ROUTE_STARTED, ClientType.ROUTE_FAILED),
    Propose(ClientType.DROPOFF_PROPOSE),
    DecidePin(ClientType.DROPOFF_DECIDE),
    Reply(ClientType.REPLY_SEND),
    Arrive(ClientType.TRIP_ARRIVE),
    End(ClientType.TRIP_END),
    ;

    val wire: Set<String> = wireTypes.toSet()
}

/**
 * Commands that need the driver's hands. Only [TripRepository] creates one (the constructor is module-internal), and
 * the only way to reach it is [CarUiState.parked], which the repository fills with one of these only while the
 * phase is parked and clears otherwise. Every method also asks the repository's live [CarPhase.Parked.enabled], so a
 * reference kept across a phase change cannot send what the relay would refuse.
 */
abstract class ParkedCommands internal constructor() {
    abstract fun approveJoin(joinRequestId: String)
    abstract fun declineJoin(joinRequestId: String)
    abstract fun recheckCard()
    abstract fun startRoute()

    /** Abandons the route start that waits for 안내 시작 in the navigation; a late answer from it is ignored. */
    abstract fun cancelRoute()

    /** Opens the navigation app (it must have been opened once after boot to take a route), then the start can be retried. */
    abstract fun openNavi()

    /** Sends the chosen candidate with its structured fields (kind, entranceType, ref, street) so passengers get localized labels. */
    abstract fun proposeDropoff(candidate: DropoffCandidate, reasonId: DropoffReasonId?)
    abstract fun decidePin(version: Int, accept: Boolean)
    /** Answers one open question; a reply that belongs to no request is not something the driver can send (화면-정의서 D6). */
    abstract fun sendReply(requestId: String, replyId: QuickReplyId)
    abstract fun arrive()
    abstract fun endTrip()
    abstract fun openDropoff()
    abstract fun openSettings()
    abstract fun openEndDialog()

    /** Drops the registration this relay no longer knows and registers again (the QR sticker changes). */
    abstract fun reregister()
}
