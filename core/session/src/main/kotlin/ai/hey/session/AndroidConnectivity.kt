package ai.hey.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** Whether the device has a default network with internet right now (PLEOS-148: a settings save does not wait on a dead link). */
fun interface OnlineCheck {
    fun isOnline(): Boolean
}

/** [Connectivity] on the system's default network: available, or capabilities now include VALIDATED (internet works). */
class AndroidConnectivity(context: Context) : Connectivity, OnlineCheck {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    @android.annotation.SuppressLint("MissingPermission")
    override fun isOnline(): Boolean {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    @android.annotation.SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is declared in the app manifest
    override fun watch(onReady: () -> Unit, onLost: () -> Unit): AutoCloseable {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onReady()

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) onReady()
            }

            override fun onLost(network: Network) = onLost()
        }
        manager.registerDefaultNetworkCallback(callback)
        return AutoCloseable { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
}
