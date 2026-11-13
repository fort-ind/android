package com.fortind.zenith.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn

@JvmInline
value class NetworkId(private val handle: Long) {
    companion object {
        val Unmonitored = NetworkId(0L)

        fun of(network: Network) = NetworkId(network.networkHandle)
    }
}

val unmonitoredNetwork: Flow<NetworkId?> = flow {
    emit(NetworkId.Unmonitored)
    awaitCancellation()
}

fun defaultNetworkOf(context: Context, scope: CoroutineScope): Flow<NetworkId?> = callbackFlow {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val callback = object : ConnectivityManager.NetworkCallback() {
        private var current: Network? = null

        override fun onAvailable(network: Network) {
            current = network
            trySend(NetworkId.of(network))
        }

        override fun onLost(network: Network) {
            if (network != current) return
            current = null
            trySend(null)
        }

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            if (network == current) trySend(if (blocked) null else NetworkId.of(network))
        }
    }
    val registered = connectivity != null && try {
        connectivity.registerDefaultNetworkCallback(callback)
        true
    } catch (_: RuntimeException) {
        false
    }
    if (!registered) trySend(NetworkId.Unmonitored)
    awaitClose { if (registered) connectivity?.unregisterNetworkCallback(callback) }
}.distinctUntilChanged().shareIn(scope, SharingStarted.WhileSubscribed(NETWORK_STOP_TIMEOUT_MS, replayExpirationMillis = 0), replay = 1)

private const val NETWORK_STOP_TIMEOUT_MS = 5_000L
