package com.shamia.assistant.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.shamia.assistant.model.DeviceTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceTelemetryMonitor(private val context: Context) {

    private val _telemetry = MutableStateFlow(readSnapshot())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
    }

    init {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }
    }

    fun refresh() {
        _telemetry.value = readSnapshot()
    }

    private fun readSnapshot(): DeviceTelemetry {
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else null
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val online = runCatching {
            val active = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(active)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(false)

        return DeviceTelemetry(
            batteryPct = pct,
            isCharging = charging,
            isOnline = online
        )
    }

    fun release() {
        runCatching {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
    }
}
