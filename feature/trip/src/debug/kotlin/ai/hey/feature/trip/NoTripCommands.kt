package ai.hey.feature.trip

import ai.hey.model.CarSettings
import ai.hey.session.CarCommands
import ai.hey.session.VehicleIssue

/** Safe commands that do nothing, for previews and snapshots. There is no hands-on counterpart: [ai.hey.session.ParkedCommands] cannot be built outside the repository. */
internal object NoTripCommands : CarCommands {
    override fun replay(textKo: String) = Unit
    override fun replayRequest(requestId: String) = Unit
    override fun closeDropoff() = Unit
    override fun selectCandidate(candidateId: String) = Unit
    override fun selectReason(index: Int) = Unit
    override fun dismissRequests() = Unit
    override fun toggleDriverFirst() = Unit
    override fun cancelVoice() = Unit
    override fun voicePermissionResult(granted: Boolean) = Unit
    override fun closeSettings() = Unit
    override fun closeEndDialog() = Unit
    override fun updateSettings(transform: (CarSettings) -> CarSettings) = Unit
    override fun retryVehicleIssue(issue: VehicleIssue) = Unit
}
