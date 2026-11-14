package ai.hey.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** [Connectivity] on the system's default network: available, or capabilities now include VALIDATED (internet works). */
class AndroidConnectivity(context: Context) : Connectivity {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

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
