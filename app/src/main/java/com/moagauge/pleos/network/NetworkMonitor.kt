package com.moagauge.pleos.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Observes usable default internet without changing the vehicle's network configuration. */
class NetworkMonitor(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean = manager?.activeNetwork?.let {
        manager.getNetworkCapabilities(it).isValidatedInternet()
    } ?: false

    val online: Flow<Boolean> = callbackFlow {
        val connectivity = manager
        if (connectivity == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        val lock = Any()
        val state = DefaultNetworkState<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(lock) { trySend(state.available(network)) }
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                synchronized(lock) {
                    trySend(state.capabilities(network,
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)))
                }
            }
            override fun onLost(network: Network) {
                synchronized(lock) { trySend(state.lost(network)) }
            }
        }
        // Seed after registration so an initially offline device also emits a value.
        // Revision protects any newer callback that arrives during the synchronous snapshot.
        val revision = synchronized(lock) { state.revision }
        connectivity.registerDefaultNetworkCallback(callback)
        try {
            val active = connectivity.activeNetwork
            val usable = active?.let { connectivity.getNetworkCapabilities(it).isValidatedInternet() } ?: false
            synchronized(lock) { trySend(state.seedIfUnchanged(revision, active, usable)) }
            awaitClose { connectivity.unregisterNetworkCallback(callback) }
        } catch (error: Throwable) {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
            throw error
        }
    }.distinctUntilChanged()
}

private fun NetworkCapabilities?.isValidatedInternet(): Boolean =
    this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
