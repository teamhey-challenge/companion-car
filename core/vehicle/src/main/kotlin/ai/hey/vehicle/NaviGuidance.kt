package ai.hey.vehicle

import ai.hey.model.HeyLog

/**
 * Whether the car is really guiding to a destination. The Pleos navigation service also calls `onRouteStarted` with no
 * route at all (the emulator does it every few seconds), so a callback alone never means guidance: guiding needs a route
 * that is on (route started / routing / ROUTE_DRIVE) AND a destination that is actually set. Every change is logged once.
 */
class NaviGuidance(private val onChange: (Boolean) -> Unit) {
    private var routeActive = false
    private var hasDestination = false
    private var guiding = false

    @Synchronized fun routeStarted() = update("onRouteStarted") { routeActive = true }

    @Synchronized fun routeStopped(why: String) = update(why) { routeActive = false; hasDestination = false }

    @Synchronized fun routing(isRouting: Boolean) = update("routeState.isRouting=$isRouting") {
        routeActive = isRouting
        if (!isRouting) hasDestination = false
    }

    @Synchronized fun status(routeDrive: Boolean) = update("status.routeDrive=$routeDrive") {
        routeActive = routeDrive
        if (!routeDrive) hasDestination = false
    }

    @Synchronized fun destination(present: Boolean) = update("destination.present=$present") { hasDestination = present }

    private inline fun update(field: String, change: () -> Unit) {
        change()
        val now = routeActive && hasDestination
        if (now == guiding) return
        guiding = now
        HeyLog.i("Navi", "navi: guiding=$now because $field (route=$routeActive destination=$hasDestination)")
        onChange(now)
    }
}
