package ai.hey.feature.home

import ai.hey.designsystem.component.HeyIcon
import ai.hey.designsystem.component.HeyIconSize
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.theme.HeyTone
import ai.hey.session.CarFault
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * `car/error` while driving (screens-and-flows 4.9): the drive screen does not change, the problem is spoken
 * (see [FaultAnnouncer]) and this one icon sits in the status slot. No words on the road. Faults that are not a
 * lasting condition (connecting, a turn that heard nothing) show nothing.
 */
@Composable
internal fun DriveFaultIcon(fault: CarFault) {
    val (icon, label) = when (fault) {
        CarFault.Microphone -> HeyIcons.Mic to R.string.drive_fault_microphone
        CarFault.Audio -> HeyIcons.VolumeOff to R.string.drive_fault_audio
        CarFault.Offline -> HeyIcons.Warning to R.string.drive_fault_offline
        CarFault.Server -> HeyIcons.Warning to R.string.drive_fault_server
        CarFault.Connecting, CarFault.Recognition -> return
    }
    HeyIcon(icon, stringResource(label), size = HeyIconSize.Large, tone = HeyTone.Danger)
}
