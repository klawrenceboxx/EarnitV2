package com.example.earnitv2

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EntitlementStatus { Free, Active, GracePeriod, Expired, Unknown }
enum class EntitlementSource { None, Purchase, Beta, Debug }

data class EntitlementState(
    val status: EntitlementStatus,
    val source: EntitlementSource = EntitlementSource.None,
    val lastVerifiedAtMillis: Long? = null,
    val offline: Boolean = false
) {
    val grantsPremium: Boolean
        get() = status == EntitlementStatus.Active || status == EntitlementStatus.GracePeriod

    companion object {
        val Free = EntitlementState(EntitlementStatus.Free)
        val Unknown = EntitlementState(EntitlementStatus.Unknown)
    }
}

interface EntitlementRepository {
    val state: StateFlow<EntitlementState>
    fun refreshFromBuildConfiguration()
}

interface DebugEntitlementController {
    fun simulate(state: EntitlementState)
    fun reset()
}

object EntitlementDefaults {
    fun forBuild(betaEntitlement: Boolean): EntitlementState =
        if (betaEntitlement) {
            EntitlementState(EntitlementStatus.Active, EntitlementSource.Beta)
        } else {
            EntitlementState.Free
        }
}

class SharedPreferencesEntitlementRepository(
    context: Context,
    private val allowDebugOverrides: Boolean,
    private val betaEntitlement: Boolean,
    private val now: () -> Long = System::currentTimeMillis
) : EntitlementRepository, DebugEntitlementController {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(resolveInitialState())
    override val state: StateFlow<EntitlementState> = mutableState.asStateFlow()

    override fun refreshFromBuildConfiguration() {
        if (betaEntitlement) {
            setAndPersist(EntitlementState(EntitlementStatus.Active, EntitlementSource.Beta, now()))
        }
    }

    override fun simulate(state: EntitlementState) {
        if (!allowDebugOverrides) return
        setAndPersist(state.copy(lastVerifiedAtMillis = state.lastVerifiedAtMillis ?: now()))
    }

    override fun reset() {
        if (!allowDebugOverrides) return
        preferences.edit().clear().commit()
        mutableState.value = if (betaEntitlement) {
            EntitlementState(EntitlementStatus.Active, EntitlementSource.Beta, now())
        } else {
            EntitlementState.Free
        }
    }

    private fun resolveInitialState(): EntitlementState {
        if (betaEntitlement) {
            return EntitlementState(EntitlementStatus.Active, EntitlementSource.Beta, now())
        }
        if (!allowDebugOverrides || !preferences.contains(KEY_STATUS)) {
            return EntitlementDefaults.forBuild(betaEntitlement)
        }
        return EntitlementState(
            status = preferences.getString(KEY_STATUS, null)
                ?.let { value -> EntitlementStatus.entries.firstOrNull { it.name == value } }
                ?: EntitlementStatus.Free,
            source = preferences.getString(KEY_SOURCE, null)
                ?.let { value -> EntitlementSource.entries.firstOrNull { it.name == value } }
                ?: EntitlementSource.None,
            lastVerifiedAtMillis = preferences.getLong(KEY_VERIFIED_AT, 0L).takeIf { it > 0L },
            offline = preferences.getBoolean(KEY_OFFLINE, false)
        )
    }

    private fun setAndPersist(value: EntitlementState) {
        mutableState.value = value
        preferences.edit()
            .putString(KEY_STATUS, value.status.name)
            .putString(KEY_SOURCE, value.source.name)
            .putLong(KEY_VERIFIED_AT, value.lastVerifiedAtMillis ?: 0L)
            .putBoolean(KEY_OFFLINE, value.offline)
            .commit()
    }

    private companion object {
        const val PREFS_NAME = "earnit_debug_entitlement"
        const val KEY_STATUS = "status"
        const val KEY_SOURCE = "source"
        const val KEY_VERIFIED_AT = "verified_at"
        const val KEY_OFFLINE = "offline"
    }
}

enum class PremiumFeature {
    MoreThanTwoActiveRules,
    DeepWork,
    StrictMode,
    SevenDayAnalytics,
    PremiumInsights
}

enum class PremiumEntryPoint { RuleLimit, DeepWork, StrictMode, Analytics, Insights, Settings }
enum class AnalyticsRangeAccess { TodayOnly, SevenDays }

class FeatureAccessPolicy(val entitlement: EntitlementState) {
    fun canUse(feature: PremiumFeature): Boolean = entitlement.grantsPremium
    val activeRuleLimit: Int? get() = if (entitlement.grantsPremium) null else FREE_ACTIVE_RULE_LIMIT
    val analyticsRangeAccess: AnalyticsRangeAccess
        get() = if (entitlement.grantsPremium) AnalyticsRangeAccess.SevenDays else AnalyticsRangeAccess.TodayOnly

    fun reasonFeatureIsLocked(feature: PremiumFeature): String = when (feature) {
        PremiumFeature.MoreThanTwoActiveRules -> "Free includes up to 2 active Rules."
        PremiumFeature.DeepWork -> "Deep Work is included with Pro."
        PremiumFeature.StrictMode -> "Strict Mode is included with Pro."
        PremiumFeature.SevenDayAnalytics -> "7-day analytics are included with Pro."
        PremiumFeature.PremiumInsights -> "Premium insights are included with Pro."
    }

    fun premiumEntryPointFor(feature: PremiumFeature): PremiumEntryPoint = when (feature) {
        PremiumFeature.MoreThanTwoActiveRules -> PremiumEntryPoint.RuleLimit
        PremiumFeature.DeepWork -> PremiumEntryPoint.DeepWork
        PremiumFeature.StrictMode -> PremiumEntryPoint.StrictMode
        PremiumFeature.SevenDayAnalytics -> PremiumEntryPoint.Analytics
        PremiumFeature.PremiumInsights -> PremiumEntryPoint.Insights
    }

    companion object {
        const val FREE_ACTIVE_RULE_LIMIT = 2
    }
}

object PremiumSessionPolicy {
    fun canStartDeepWork(policy: FeatureAccessPolicy): Boolean =
        policy.canUse(PremiumFeature.DeepWork)

    fun canOpenStrictMode(policy: FeatureAccessPolicy, restrictiveSessionRunning: Boolean): Boolean =
        restrictiveSessionRunning || policy.canUse(PremiumFeature.StrictMode)

    /** Entitlement loss never weakens a restrictive session already in progress. */
    fun shouldContinueRestrictiveSession(): Boolean = true
}
