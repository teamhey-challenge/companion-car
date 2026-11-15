package ai.hey.car

/**
 * The one rule for every runtime-permission dialog: only while the car is parked. A system permission
 * dialog cannot be closed from the app, and if gear D arrives while it is open the Pleos system covers the app with
 * "주행 중에는 사용할 수 없습니다" (AC-3.5-05). [parked] is `CarUiState.isParked`, which is false for unknown motion.
 */
internal fun mayAskPermission(parked: Boolean): Boolean = parked

internal const val CAR_SPEED = "android.car.permission.CAR_SPEED"
