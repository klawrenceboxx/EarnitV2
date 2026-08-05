package com.kaleel.earnitv2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

internal enum class ChargingPowerSource { Ac, Usb, Wireless, Other, None }

internal data class ChargingState(
    val isPlugged: Boolean,
    val isActivelyCharging: Boolean,
    val source: ChargingPowerSource = ChargingPowerSource.None
)

internal fun interface ChargingStateListener { fun onChargingStateChanged(state: ChargingState) }

internal interface ChargingStateObserver {
    fun currentState(): ChargingState
    fun observe(listener: ChargingStateListener)
    fun stopObserving(listener: ChargingStateListener)
}

internal class AndroidChargingStateObserver(private val context: Context) : ChargingStateObserver {
    private val listeners = linkedSetOf<ChargingStateListener>()
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = currentState()
            listeners.toList().forEach { it.onChargingStateChanged(state) }
        }
    }

    override fun currentState(): ChargingState {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ChargingState(false, false)
        return chargingStateFromBatteryIntent(battery)
    }

    override fun observe(listener: ChargingStateListener) {
        listeners += listener
        listener.onChargingStateChanged(currentState())
        if (!registered) {
            context.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                }
            )
            registered = true
        }
    }

    override fun stopObserving(listener: ChargingStateListener) {
        listeners -= listener
        if (listeners.isEmpty() && registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
    }
}

internal fun chargingStateFromBatteryIntent(intent: Intent): ChargingState {
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    return chargingStateFromBatteryValues(plugged, status)
}

internal fun chargingStateFromBatteryValues(plugged: Int, status: Int): ChargingState {
    val source = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> ChargingPowerSource.Ac
        BatteryManager.BATTERY_PLUGGED_USB -> ChargingPowerSource.Usb
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargingPowerSource.Wireless
        0 -> ChargingPowerSource.None
        else -> ChargingPowerSource.Other
    }
    return ChargingState(
        isPlugged = plugged != 0,
        isActivelyCharging = plugged != 0 && status == BatteryManager.BATTERY_STATUS_CHARGING,
        source = source
    )
}

internal enum class ChargerAuthorizationState {
    WaitingForCharger, Ready, Authorized, Expired, Invalid, Consumed, Cancelled
}

internal data class ChargerAuthorizationSession(
    val requestId: String,
    val state: ChargerAuthorizationState,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val updatedAtMillis: Long
)

internal object StrictModeProtectionStrengthPolicy {
    fun compare(
        currentMethod: StrictModeProtectionMethod,
        currentDurationMillis: Long?,
        proposedMethod: StrictModeProtectionMethod,
        proposedDurationMillis: Long?
    ): RestrictionClassification {
        if (currentMethod == proposedMethod) {
            return when (currentMethod) {
                StrictModeProtectionMethod.Countdown -> {
                    val current = currentDurationMillis ?: return RestrictionClassification.LessRestrictive
                    val proposed = proposedDurationMillis ?: return RestrictionClassification.LessRestrictive
                    when {
                        proposed > current -> RestrictionClassification.Stricter
                        proposed == current -> RestrictionClassification.Equivalent
                        else -> RestrictionClassification.LessRestrictive
                    }
                }
                StrictModeProtectionMethod.Charger -> RestrictionClassification.Equivalent
                StrictModeProtectionMethod.Pin -> {
                    val current = currentDurationMillis ?: return RestrictionClassification.LessRestrictive
                    val proposed = proposedDurationMillis ?: return RestrictionClassification.LessRestrictive
                    when {
                        proposed > current -> RestrictionClassification.Stricter
                        proposed == current -> RestrictionClassification.Equivalent
                        else -> RestrictionClassification.LessRestrictive
                    }
                }
            }
        }
        // Cross-method strength is intentionally incomparable; replacement is protected.
        return RestrictionClassification.LessRestrictive
    }
}
