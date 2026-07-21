package com.example.earnitv2

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

internal enum class RestrictionClassification { Stricter, Equivalent, LessRestrictive }

internal enum class RestrictionReason {
    RewardAppsAdded, RewardAppsRemoved, EarnAppsAdded, EarnAppsRemoved,
    RequirementIncreased, RequirementReduced, RequirementAdded, RequirementRemoved,
    RewardRateReduced, RewardRateIncreased, ScheduleExpanded, ScheduleReduced,
    RuleTypeChanged, Enabled, Disabled, UnsupportedChange
}

internal data class RestrictionDimension(
    val reason: RestrictionReason,
    val classification: RestrictionClassification
)

internal data class RuleRestrictionComparison(
    val classification: RestrictionClassification,
    val dimensions: List<RestrictionDimension>
)

/** One fail-closed comparison policy used by every Rule mutation entry point. */
internal object RuleRestrictionPolicy {
    fun compare(current: EarnItRuleStore.Rule, proposed: EarnItRuleStore.Rule): RuleRestrictionComparison {
        if (current.id != proposed.id || current.type != proposed.type) {
            return result(RestrictionDimension(RestrictionReason.RuleTypeChanged, RestrictionClassification.LessRestrictive))
        }
        val changes = mutableListOf<RestrictionDimension>()
        compareSets(current.blockedApps.packages(), proposed.blockedApps.packages(), RestrictionReason.RewardAppsAdded, RestrictionReason.RewardAppsRemoved, changes)
        compareSets(current.activeDays, proposed.activeDays, RestrictionReason.ScheduleExpanded, RestrictionReason.ScheduleReduced, changes)
        compareSchedule(current, proposed)?.let(changes::add)
        if (current.enabled != proposed.enabled) {
            changes += RestrictionDimension(
                if (proposed.enabled) RestrictionReason.Enabled else RestrictionReason.Disabled,
                if (proposed.enabled) RestrictionClassification.Stricter else RestrictionClassification.LessRestrictive
            )
        }
        when (current.type) {
            EarnItRuleStore.RuleType.EarnRewardTime -> {
                compareSets(current.earnAppPackages, proposed.earnAppPackages, RestrictionReason.EarnAppsAdded, RestrictionReason.EarnAppsRemoved, changes)
                if (current.rewardSecondsPerProductiveSecond != proposed.rewardSecondsPerProductiveSecond) {
                    val weaker = proposed.rewardSecondsPerProductiveSecond > current.rewardSecondsPerProductiveSecond
                    changes += RestrictionDimension(
                        if (weaker) RestrictionReason.RewardRateIncreased else RestrictionReason.RewardRateReduced,
                        if (weaker) RestrictionClassification.LessRestrictive else RestrictionClassification.Stricter
                    )
                }
            }
            EarnItRuleStore.RuleType.CompleteToUnlock -> compareRequirements(current, proposed, changes)
            EarnItRuleStore.RuleType.ScheduledBlock -> Unit
        }
        return result(*changes.toTypedArray())
    }

    private fun compareRequirements(current: EarnItRuleStore.Rule, proposed: EarnItRuleStore.Rule, out: MutableList<RestrictionDimension>) {
        val before = current.requirements.associate { it.app.packageName to it.requiredSeconds }
        val after = proposed.requirements.associate { it.app.packageName to it.requiredSeconds }
        compareSets(before.keys, after.keys, RestrictionReason.RequirementAdded, RestrictionReason.RequirementRemoved, out)
        (before.keys intersect after.keys).forEach { packageName ->
            if (before[packageName] != after[packageName]) {
                val stronger = requireNotNull(after[packageName]) > requireNotNull(before[packageName])
                out += RestrictionDimension(
                    if (stronger) RestrictionReason.RequirementIncreased else RestrictionReason.RequirementReduced,
                    if (stronger) RestrictionClassification.Stricter else RestrictionClassification.LessRestrictive
                )
            }
        }
    }

    private fun compareSchedule(current: EarnItRuleStore.Rule, proposed: EarnItRuleStore.Rule): RestrictionDimension? {
        val before = scheduleCoverage(current)
        val after = scheduleCoverage(proposed)
        if (before.contentEquals(after)) return null
        var added = false
        var removed = false
        before.indices.forEach { index ->
            if (!before[index] && after[index]) added = true
            if (before[index] && !after[index]) removed = true
        }
        return when {
            removed -> RestrictionDimension(RestrictionReason.ScheduleReduced, RestrictionClassification.LessRestrictive)
            added -> RestrictionDimension(RestrictionReason.ScheduleExpanded, RestrictionClassification.Stricter)
            else -> RestrictionDimension(RestrictionReason.UnsupportedChange, RestrictionClassification.LessRestrictive)
        }
    }

    private fun scheduleCoverage(rule: EarnItRuleStore.Rule): BooleanArray {
        val coverage = BooleanArray(7 * 1_440)
        for (day in 1..7) for (minute in 0 until 1_440) coverage[(day - 1) * 1_440 + minute] = rule.isActiveAt(day, minute)
        return coverage
    }

    private fun <T> compareSets(before: Set<T>, after: Set<T>, added: RestrictionReason, removed: RestrictionReason, out: MutableList<RestrictionDimension>) {
        if ((after - before).isNotEmpty()) out += RestrictionDimension(added, RestrictionClassification.Stricter)
        if ((before - after).isNotEmpty()) out += RestrictionDimension(removed, RestrictionClassification.LessRestrictive)
    }

    private fun result(vararg dimensions: RestrictionDimension): RuleRestrictionComparison {
        val overall = when {
            dimensions.any { it.classification == RestrictionClassification.LessRestrictive } -> RestrictionClassification.LessRestrictive
            dimensions.any { it.classification == RestrictionClassification.Stricter } -> RestrictionClassification.Stricter
            else -> RestrictionClassification.Equivalent
        }
        return RuleRestrictionComparison(overall, dimensions.toList())
    }

    private fun List<EarnItRuleStore.RuleApp>.packages() = map { it.packageName }.toSet()
}

internal enum class RuleStrictModeLifecycle { Disabled, PendingActivation, Active, DeactivationCounting, DeactivationReady, Invalid }
internal enum class StrictModeProtectionMethod { Countdown, ChargerWait, AccountabilityPin }

internal data class GlobalStrictModeConfiguration(
    val ruleId: String,
    val lifecycle: RuleStrictModeLifecycle = RuleStrictModeLifecycle.Disabled,
    val protectionMethod: StrictModeProtectionMethod? = null,
    val activationRequestedAtMillis: Long? = null,
    val activeFromMillis: Long? = null,
    val deactivationWaitMillis: Long? = null,
    val deactivationStartedAtMillis: Long? = null,
    val deactivationAvailableAtMillis: Long? = null,
    val configurationVersion: Long = 1,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    val configurationComplete: Boolean get() = protectionMethod != null &&
        (protectionMethod != StrictModeProtectionMethod.Countdown || (deactivationWaitMillis ?: 0L) > 0L)
    fun protectsLessRestrictiveChanges(): Boolean = lifecycle in setOf(
        RuleStrictModeLifecycle.Active,
        RuleStrictModeLifecycle.DeactivationCounting,
        RuleStrictModeLifecycle.DeactivationReady,
        RuleStrictModeLifecycle.Invalid
    )
}

// Source-compatible name for decoding the previous per-Rule records during migration.
internal typealias RuleStrictModeConfiguration = GlobalStrictModeConfiguration

internal enum class PendingStrictModeActionType { PauseRule, DeleteRule, DisableStrictMode, UpdateRule, ReplaceProtectionMethod }
internal enum class StrictModeAuthorizationStatus { NotStarted, AwaitingAuthorization, Authorized, AwaitingFinalConfirmation, Consumed, Cancelled, Expired, Invalid }

internal sealed class StrictModeActionDescriptor {
    abstract val ruleId: String
    data class Pause(override val ruleId: String, val durationMillis: Long, val reason: String?) : StrictModeActionDescriptor()
    data class Delete(override val ruleId: String) : StrictModeActionDescriptor()
    data class Disable(override val ruleId: String) : StrictModeActionDescriptor()
    data class ReplaceMethod(override val ruleId: String, val newMethod: StrictModeProtectionMethod) : StrictModeActionDescriptor()
    data class Update(override val ruleId: String, val proposedRule: EarnItRuleStore.Rule) : StrictModeActionDescriptor()
}

internal data class PendingStrictModeAction(
    val id: String,
    val actionType: PendingStrictModeActionType,
    val descriptor: StrictModeActionDescriptor,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val originalRuleFingerprint: String,
    val originalStrictModeFingerprint: String,
    val authorizationStatus: StrictModeAuthorizationStatus,
    val authorizationMethod: StrictModeProtectionMethod,
    val authorizationExpiresAtMillis: Long? = null,
    val consumedAtMillis: Long? = null,
    val cancelledAtMillis: Long? = null
) { val ruleId: String get() = descriptor.ruleId }

internal interface StrictModeAuthorizationMethodHandler {
    val method: StrictModeProtectionMethod
    fun isConfigurationComplete(configuration: RuleStrictModeConfiguration): Boolean
    fun begin(request: PendingStrictModeAction): StrictModeAuthorizationStatus
    fun restore(request: PendingStrictModeAction): StrictModeAuthorizationStatus
    fun cancel(request: PendingStrictModeAction)
}

internal fun interface StrictModeAuthorizationMethodProvider {
    fun handlerFor(method: StrictModeProtectionMethod): StrictModeAuthorizationMethodHandler?
}

internal class DefaultStrictModeAuthorizationMethodProvider : StrictModeAuthorizationMethodProvider {
    private val handlers = StrictModeProtectionMethod.entries.associateWith { method ->
        if (method == StrictModeProtectionMethod.Countdown) CountdownStrictModeMethodHandler else UnavailableStrictModeMethodHandler(method)
    }
    override fun handlerFor(method: StrictModeProtectionMethod): StrictModeAuthorizationMethodHandler? = handlers[method]
}

private object CountdownStrictModeMethodHandler : StrictModeAuthorizationMethodHandler {
    override val method = StrictModeProtectionMethod.Countdown
    override fun isConfigurationComplete(configuration: RuleStrictModeConfiguration) =
        configuration.protectionMethod == method && (configuration.deactivationWaitMillis ?: 0L) > 0L
    override fun begin(request: PendingStrictModeAction) = StrictModeAuthorizationStatus.AwaitingAuthorization
    override fun restore(request: PendingStrictModeAction) = request.authorizationStatus
    override fun cancel(request: PendingStrictModeAction) = Unit
}

internal class UnavailableStrictModeMethodHandler(override val method: StrictModeProtectionMethod) : StrictModeAuthorizationMethodHandler {
    override fun isConfigurationComplete(configuration: RuleStrictModeConfiguration) = false
    override fun begin(request: PendingStrictModeAction) = StrictModeAuthorizationStatus.AwaitingAuthorization
    override fun restore(request: PendingStrictModeAction) = request.authorizationStatus
    override fun cancel(request: PendingStrictModeAction) = Unit
}

internal interface StrictModeFoundationPersistence {
    fun readConfigurations(): String?
    fun writeConfigurations(value: String)
    fun readPendingActions(): String?
    fun writePendingActions(value: String)
}

internal class SharedPreferencesStrictModeFoundationPersistence(context: Context) : StrictModeFoundationPersistence {
    private val prefs = context.getSharedPreferences("earnit_rule_strict_mode_v2", Context.MODE_PRIVATE)
    override fun readConfigurations() = prefs.getString("configurations", null)
    override fun writeConfigurations(value: String) { prefs.edit().putString("configurations", value).commit() }
    override fun readPendingActions() = prefs.getString("pending_actions", null)
    override fun writePendingActions(value: String) { prefs.edit().putString("pending_actions", value).commit() }
}

internal sealed class PendingActionCreationResult {
    data class Created(val action: PendingStrictModeAction) : PendingActionCreationResult()
    data class AlreadyPending(val action: PendingStrictModeAction) : PendingActionCreationResult()
    data class Rejected(val message: String) : PendingActionCreationResult()
}

internal sealed class PendingActionValidation {
    data class Valid(val action: PendingStrictModeAction) : PendingActionValidation()
    data class Invalid(val message: String) : PendingActionValidation()
}

internal class GlobalStrictModeStore(
    private val persistence: StrictModeFoundationPersistence,
    private val authorizationMethods: StrictModeAuthorizationMethodProvider = DefaultStrictModeAuthorizationMethodProvider(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun configurations(): Map<String, RuleStrictModeConfiguration> {
        val decoded = decodeConfigurations(persistence.readConfigurations())
        val normalized = decoded.mapValues { (_, config) -> normalize(config) }
        if (normalized != decoded) saveConfigurations(normalized.values)
        return normalized
    }

    fun configuration(ruleId: String): RuleStrictModeConfiguration? = configurations()[ruleId]

    fun globalConfiguration(): RuleStrictModeConfiguration? = configuration(GLOBAL_CONFIGURATION_ID)

    /** Idempotently consolidates all historical per-Rule and legacy app-wide state. */
    fun migrateToGlobal(legacy: StrictModeState): RuleStrictModeConfiguration {
        globalConfiguration()?.let { return it }
        val perRuleCandidates = configurations().values.filter { it.ruleId != GLOBAL_CONFIGURATION_ID }.map(::normalize)
        val candidates = perRuleCandidates + listOfNotNull(
            legacy.takeIf { it.lifecycleState != StrictModeLifecycleState.Inactive }?.toGlobalConfiguration(nowMillis())
        )
        val protecting = candidates.filter { it.protectsLessRestrictiveChanges() }
        val counting = protecting.filter { it.lifecycle == RuleStrictModeLifecycle.DeactivationCounting }
        val ready = protecting.filter { it.lifecycle == RuleStrictModeLifecycle.DeactivationReady }
        val active = protecting.filter { it.lifecycle == RuleStrictModeLifecycle.Active }
        val invalid = protecting.filter { it.lifecycle == RuleStrictModeLifecycle.Invalid }
        val pending = candidates.filter { it.lifecycle == RuleStrictModeLifecycle.PendingActivation }
        val now = nowMillis()

        val chosen = when {
            counting.isNotEmpty() -> counting.maxBy { it.deactivationAvailableAtMillis ?: Long.MIN_VALUE }
            ready.isNotEmpty() -> ready.maxBy { it.deactivationWaitMillis ?: 0L }
            active.isNotEmpty() -> active.maxBy { it.deactivationWaitMillis ?: 0L }
            invalid.isNotEmpty() -> invalid.first()
            pending.isNotEmpty() -> pending.minBy { it.activeFromMillis ?: Long.MAX_VALUE }
            legacy.lifecycleState != StrictModeLifecycleState.Inactive -> legacy.toGlobalConfiguration(now)
            else -> RuleStrictModeConfiguration(
                ruleId = GLOBAL_CONFIGURATION_ID,
                lifecycle = RuleStrictModeLifecycle.Disabled,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }

        val strongestWait = (candidates.mapNotNull { it.deactivationWaitMillis } +
            listOfNotNull(legacy.configuration.deactivationCountdownMillis)).maxOrNull()
        val global = chosen.copy(
            ruleId = GLOBAL_CONFIGURATION_ID,
            deactivationWaitMillis = strongestWait ?: chosen.deactivationWaitMillis,
            configurationVersion = candidates.maxOfOrNull { it.configurationVersion } ?: chosen.configurationVersion,
            updatedAtMillis = now
        )
        putConfiguration(global)
        val persistedGlobal = globalConfiguration() ?: global
        migratePendingRequestsToGlobal(persistedGlobal)
        return persistedGlobal
    }

    private fun migratePendingRequestsToGlobal(global: RuleStrictModeConfiguration) {
        val migrated = pendingActions().map { action ->
            if (action.authorizationStatus in terminalStatuses) return@map action
            if (action.actionType == PendingStrictModeActionType.DisableStrictMode && action.ruleId != GLOBAL_CONFIGURATION_ID) {
                return@map action.copy(authorizationStatus = StrictModeAuthorizationStatus.Cancelled, cancelledAtMillis = nowMillis())
            }
            action.copy(
                originalStrictModeFingerprint = StrictModeFingerprint.configuration(global),
                authorizationMethod = global.protectionMethod ?: action.authorizationMethod,
                authorizationStatus = if (action.authorizationStatus in setOf(
                        StrictModeAuthorizationStatus.Authorized,
                        StrictModeAuthorizationStatus.AwaitingFinalConfirmation
                    )
                ) StrictModeAuthorizationStatus.AwaitingAuthorization else action.authorizationStatus,
                authorizationExpiresAtMillis = null
            )
        }
        savePending(migrated)
    }

    private fun StrictModeState.toGlobalConfiguration(now: Long): RuleStrictModeConfiguration {
        val lifecycle = when (lifecycleState) {
            StrictModeLifecycleState.Inactive -> RuleStrictModeLifecycle.Disabled
            StrictModeLifecycleState.Activating -> RuleStrictModeLifecycle.PendingActivation
            StrictModeLifecycleState.Active -> RuleStrictModeLifecycle.Active
            StrictModeLifecycleState.DeactivationCounting -> RuleStrictModeLifecycle.DeactivationCounting
            StrictModeLifecycleState.DeactivationReady -> RuleStrictModeLifecycle.DeactivationReady
        }
        return RuleStrictModeConfiguration(
            ruleId = GLOBAL_CONFIGURATION_ID,
            lifecycle = lifecycle,
            protectionMethod = StrictModeProtectionMethod.Countdown,
            activationRequestedAtMillis = activationGraceStartedAtMillis ?: activatedAtMillis ?: now,
            activeFromMillis = activationGraceEndsAtMillis ?: activatedAtMillis ?: now,
            deactivationWaitMillis = configuration.deactivationCountdownMillis ?: DEFAULT_COUNTDOWN_MILLIS,
            deactivationStartedAtMillis = deactivationStartedAtMillis,
            deactivationAvailableAtMillis = deactivationAvailableAtMillis,
            createdAtMillis = activationGraceStartedAtMillis ?: activatedAtMillis ?: now,
            updatedAtMillis = now
        )
    }

    fun requestGlobalActivation(
        method: StrictModeProtectionMethod,
        delayMillis: Long,
        deactivationWaitMillis: Long?
    ): RuleStrictModeConfiguration = requestActivation(GLOBAL_CONFIGURATION_ID, method, delayMillis, deactivationWaitMillis)

    fun cancelGlobalActivation(): RuleStrictModeConfiguration? = cancelPendingActivation(GLOBAL_CONFIGURATION_ID)

    fun requestActivation(
        ruleId: String,
        method: StrictModeProtectionMethod,
        delayMillis: Long,
        deactivationWaitMillis: Long? = if (method == StrictModeProtectionMethod.Countdown) DEFAULT_COUNTDOWN_MILLIS else null
    ): RuleStrictModeConfiguration {
        require(delayMillis >= 0L)
        require(method != StrictModeProtectionMethod.Countdown || (deactivationWaitMillis ?: 0L) > 0L)
        val existing = configuration(ruleId)
        if (existing?.lifecycle == RuleStrictModeLifecycle.Active || existing?.lifecycle == RuleStrictModeLifecycle.PendingActivation) return existing
        val now = nowMillis()
        val activeFrom = safeAdd(now, delayMillis) ?: Long.MAX_VALUE
        val config = RuleStrictModeConfiguration(
            ruleId = ruleId,
            lifecycle = RuleStrictModeLifecycle.PendingActivation,
            protectionMethod = method,
            activationRequestedAtMillis = now,
            activeFromMillis = activeFrom,
            deactivationWaitMillis = deactivationWaitMillis,
            configurationVersion = (existing?.configurationVersion ?: 0) + 1,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now
        )
        val normalized = normalize(config)
        putConfiguration(normalized)
        return normalized
    }

    fun cancelPendingActivation(ruleId: String): RuleStrictModeConfiguration? {
        val current = configuration(ruleId) ?: return null
        if (current.lifecycle != RuleStrictModeLifecycle.PendingActivation) return current
        return current.copy(lifecycle = RuleStrictModeLifecycle.Disabled, activationRequestedAtMillis = null, activeFromMillis = null, configurationVersion = current.configurationVersion + 1, updatedAtMillis = nowMillis()).also(::putConfiguration)
    }

    /** One-time compatibility bridge from the previous app-wide Strict Mode store. */
    fun migrateLegacyRule(ruleId: String, legacy: StrictModeState): RuleStrictModeConfiguration? {
        if (configuration(ruleId) != null || legacy.lifecycleState == StrictModeLifecycleState.Inactive) return configuration(ruleId)
        val now = nowMillis()
        val lifecycle = when (legacy.lifecycleState) {
            StrictModeLifecycleState.Inactive -> RuleStrictModeLifecycle.Disabled
            StrictModeLifecycleState.Activating -> RuleStrictModeLifecycle.PendingActivation
            StrictModeLifecycleState.Active -> RuleStrictModeLifecycle.Active
            StrictModeLifecycleState.DeactivationCounting -> RuleStrictModeLifecycle.DeactivationCounting
            StrictModeLifecycleState.DeactivationReady -> RuleStrictModeLifecycle.DeactivationReady
        }
        val migrated = RuleStrictModeConfiguration(
            ruleId = ruleId,
            lifecycle = lifecycle,
            protectionMethod = StrictModeProtectionMethod.Countdown,
            activationRequestedAtMillis = legacy.activationGraceStartedAtMillis ?: legacy.activatedAtMillis ?: now,
            activeFromMillis = legacy.activationGraceEndsAtMillis ?: legacy.activatedAtMillis ?: now,
            deactivationWaitMillis = legacy.configuration.deactivationCountdownMillis ?: DEFAULT_COUNTDOWN_MILLIS,
            deactivationStartedAtMillis = legacy.deactivationStartedAtMillis,
            deactivationAvailableAtMillis = legacy.deactivationAvailableAtMillis,
            configurationVersion = 1,
            createdAtMillis = legacy.activationGraceStartedAtMillis ?: legacy.activatedAtMillis ?: now,
            updatedAtMillis = now
        )
        putConfiguration(migrated)
        return normalize(migrated)
    }

    fun beginCountdownDeactivation(rule: EarnItRuleStore.Rule): PendingActionCreationResult {
        val current = configuration(rule.id) ?: return PendingActionCreationResult.Rejected("Strict Mode is not configured for this Rule.")
        if (current.lifecycle == RuleStrictModeLifecycle.DeactivationCounting || current.lifecycle == RuleStrictModeLifecycle.DeactivationReady) {
            return activePendingAction(rule.id)?.let(PendingActionCreationResult::AlreadyPending)
                ?: PendingActionCreationResult.Rejected("The existing deactivation request needs attention.")
        }
        if (current.lifecycle != RuleStrictModeLifecycle.Active || current.protectionMethod != StrictModeProtectionMethod.Countdown) {
            return PendingActionCreationResult.Rejected("Countdown deactivation is not available for this Rule.")
        }
        activePendingAction(rule.id)?.let { return PendingActionCreationResult.AlreadyPending(it) }
        val wait = current.deactivationWaitMillis?.takeIf { it > 0L }
            ?: return PendingActionCreationResult.Rejected("The countdown configuration needs attention.")
        val now = nowMillis()
        val availableAt = safeAdd(now, wait)
            ?: return PendingActionCreationResult.Rejected("The countdown configuration needs attention.")
        val counting = current.copy(
            lifecycle = RuleStrictModeLifecycle.DeactivationCounting,
            deactivationStartedAtMillis = now,
            deactivationAvailableAtMillis = availableAt,
            updatedAtMillis = now
        )
        putConfiguration(counting)
        return createPendingAction(rule, StrictModeActionDescriptor.Disable(rule.id))
    }

    fun beginGlobalCountdownDeactivation(): PendingActionCreationResult {
        val current = globalConfiguration() ?: return PendingActionCreationResult.Rejected("Strict Mode is not configured.")
        if (current.lifecycle in setOf(RuleStrictModeLifecycle.DeactivationCounting, RuleStrictModeLifecycle.DeactivationReady)) {
            return activePendingAction(GLOBAL_CONFIGURATION_ID)?.let(PendingActionCreationResult::AlreadyPending)
                ?: createGlobalPendingAction(StrictModeActionDescriptor.Disable(GLOBAL_CONFIGURATION_ID))
        }
        if (current.lifecycle != RuleStrictModeLifecycle.Active || current.protectionMethod != StrictModeProtectionMethod.Countdown) {
            return PendingActionCreationResult.Rejected("Countdown deactivation is not available.")
        }
        activePendingAction(GLOBAL_CONFIGURATION_ID)?.let { return PendingActionCreationResult.AlreadyPending(it) }
        val wait = current.deactivationWaitMillis?.takeIf { it > 0L }
            ?: return PendingActionCreationResult.Rejected("The countdown configuration needs attention.")
        val now = nowMillis()
        val availableAt = safeAdd(now, wait)
            ?: return PendingActionCreationResult.Rejected("The countdown configuration needs attention.")
        putConfiguration(current.copy(
            lifecycle = RuleStrictModeLifecycle.DeactivationCounting,
            deactivationStartedAtMillis = now,
            deactivationAvailableAtMillis = availableAt,
            updatedAtMillis = now
        ))
        return createGlobalPendingAction(StrictModeActionDescriptor.Disable(GLOBAL_CONFIGURATION_ID))
    }

    fun cancelGlobalCountdownDeactivation(): RuleStrictModeConfiguration? = cancelCountdownDeactivation(GLOBAL_CONFIGURATION_ID)

    fun confirmGlobalCountdownDeactivation(): PendingActionValidation {
        val config = globalConfiguration()
        val action = activePendingAction(GLOBAL_CONFIGURATION_ID)
        if (config?.lifecycle != RuleStrictModeLifecycle.DeactivationReady || action?.actionType != PendingStrictModeActionType.DisableStrictMode) {
            return PendingActionValidation.Invalid("The deactivation countdown is not ready.")
        }
        markAuthorized(action.id)
        val current = pendingActions().firstOrNull { it.id == action.id }
        val valid = current?.authorizationStatus == StrictModeAuthorizationStatus.AwaitingFinalConfirmation &&
            current.expiresAtMillis > nowMillis() &&
            StrictModeFingerprint.configuration(config) == current.originalStrictModeFingerprint
        if (!valid) {
            cancelRequest(action.id)
            return PendingActionValidation.Invalid("Strict Mode changed while the request was open. Begin again.")
        }
        disableAfterConfirmation(GLOBAL_CONFIGURATION_ID)
        consume(action.id)
        cancelOutstandingProtectedActions(exceptRequestId = action.id)
        return PendingActionValidation.Valid(action)
    }

    private fun cancelOutstandingProtectedActions(exceptRequestId: String) {
        val now = nowMillis()
        val updated = pendingActions().map { pending ->
            if (pending.id != exceptRequestId && pending.authorizationStatus !in terminalStatuses) {
                pending.copy(authorizationStatus = StrictModeAuthorizationStatus.Cancelled, cancelledAtMillis = now)
            } else pending
        }
        savePending(updated)
    }

    fun keepGlobalStrictModeActive(): RuleStrictModeConfiguration? = cancelGlobalCountdownDeactivation()

    fun cancelCountdownDeactivation(ruleId: String): RuleStrictModeConfiguration? {
        activePendingAction(ruleId)?.let { cancelRequest(it.id) }
        val current = configuration(ruleId) ?: return null
        if (current.lifecycle !in setOf(RuleStrictModeLifecycle.DeactivationCounting, RuleStrictModeLifecycle.DeactivationReady)) return current
        return current.copy(
            lifecycle = RuleStrictModeLifecycle.Active,
            deactivationStartedAtMillis = null,
            deactivationAvailableAtMillis = null,
            updatedAtMillis = nowMillis()
        ).also(::putConfiguration)
    }

    fun confirmCountdownDeactivation(rule: EarnItRuleStore.Rule): PendingActionValidation {
        val config = configuration(rule.id)
        val action = activePendingAction(rule.id)
        if (config?.lifecycle != RuleStrictModeLifecycle.DeactivationReady || action?.actionType != PendingStrictModeActionType.DisableStrictMode) {
            return PendingActionValidation.Invalid("The deactivation countdown is not ready.")
        }
        markAuthorized(action.id)
        return when (val validation = validateForConfirmation(action.id, rule)) {
            is PendingActionValidation.Valid -> {
                disableAfterConfirmation(rule.id)
                consume(action.id)
                validation
            }
            is PendingActionValidation.Invalid -> validation
        }
    }

    fun keepStrictModeActive(ruleId: String): RuleStrictModeConfiguration? = cancelCountdownDeactivation(ruleId)

    fun pendingActions(): List<PendingStrictModeAction> {
        val now = nowMillis()
        val decoded = decodePending(persistence.readPendingActions())
        val normalized = decoded.map { action ->
            when {
                action.authorizationStatus in terminalStatuses -> action
                action.expiresAtMillis <= now -> action.copy(authorizationStatus = StrictModeAuthorizationStatus.Expired, cancelledAtMillis = now)
                action.authorizationExpiresAtMillis != null && action.authorizationExpiresAtMillis <= now -> action.copy(authorizationStatus = StrictModeAuthorizationStatus.Expired, cancelledAtMillis = now)
                else -> action
            }
        }
        if (normalized != decoded) savePending(normalized)
        return normalized
    }

    fun activePendingAction(ruleId: String): PendingStrictModeAction? = pendingActions().firstOrNull { it.ruleId == ruleId && it.authorizationStatus !in terminalStatuses }

    fun createPendingAction(rule: EarnItRuleStore.Rule, descriptor: StrictModeActionDescriptor, replaceExisting: Boolean = false): PendingActionCreationResult {
        if (descriptor.ruleId != rule.id) return PendingActionCreationResult.Rejected("The requested change does not match this Rule.")
        val config = (globalConfiguration() ?: configuration(rule.id))?.let(::normalize)
            ?: return PendingActionCreationResult.Rejected("Strict Mode is not configured.")
        if (!config.protectsLessRestrictiveChanges() || config.protectionMethod == null) return PendingActionCreationResult.Rejected("Strict Mode configuration needs attention.")
        val actions = pendingActions().toMutableList()
        val existing = actions.firstOrNull { it.ruleId == rule.id && it.authorizationStatus !in terminalStatuses }
        if (existing != null && !replaceExisting) return PendingActionCreationResult.AlreadyPending(existing)
        if (existing != null) actions[actions.indexOf(existing)] = existing.copy(authorizationStatus = StrictModeAuthorizationStatus.Cancelled, cancelledAtMillis = nowMillis())
        val now = nowMillis()
        val request = PendingStrictModeAction(
            id = UUID.randomUUID().toString(), actionType = descriptor.actionType(), descriptor = descriptor,
            createdAtMillis = now, expiresAtMillis = safeAdd(now, REQUEST_EXPIRY_MILLIS) ?: Long.MAX_VALUE,
            originalRuleFingerprint = StrictModeFingerprint.rule(rule), originalStrictModeFingerprint = StrictModeFingerprint.configuration(config),
            authorizationStatus = StrictModeAuthorizationStatus.AwaitingAuthorization, authorizationMethod = config.protectionMethod
        )
        actions += request
        savePending(actions)
        return PendingActionCreationResult.Created(request)
    }

    private fun createGlobalPendingAction(descriptor: StrictModeActionDescriptor): PendingActionCreationResult {
        val config = globalConfiguration() ?: return PendingActionCreationResult.Rejected("Strict Mode is not configured.")
        val existing = activePendingAction(GLOBAL_CONFIGURATION_ID)
        if (existing != null) return PendingActionCreationResult.AlreadyPending(existing)
        val now = nowMillis()
        val request = PendingStrictModeAction(
            id = UUID.randomUUID().toString(),
            actionType = descriptor.actionType(),
            descriptor = descriptor,
            createdAtMillis = now,
            expiresAtMillis = safeAdd(now, REQUEST_EXPIRY_MILLIS) ?: Long.MAX_VALUE,
            originalRuleFingerprint = GLOBAL_ACTION_FINGERPRINT,
            originalStrictModeFingerprint = StrictModeFingerprint.configuration(config),
            authorizationStatus = StrictModeAuthorizationStatus.AwaitingAuthorization,
            authorizationMethod = config.protectionMethod ?: return PendingActionCreationResult.Rejected("Strict Mode needs attention.")
        )
        savePending(pendingActions() + request)
        return PendingActionCreationResult.Created(request)
    }

    fun markAuthorized(requestId: String): PendingStrictModeAction? = updatePending(requestId) { action ->
        if (action.authorizationStatus != StrictModeAuthorizationStatus.AwaitingAuthorization) action else action.copy(
            authorizationStatus = StrictModeAuthorizationStatus.AwaitingFinalConfirmation,
            authorizationExpiresAtMillis = safeAdd(nowMillis(), AUTHORIZATION_VALIDITY_MILLIS)
        )
    }

    fun cancelRequest(requestId: String): PendingStrictModeAction? = updatePending(requestId) { it.copy(authorizationStatus = StrictModeAuthorizationStatus.Cancelled, cancelledAtMillis = nowMillis()) }

    fun removeRequest(requestId: String): Boolean {
        val all = pendingActions().toMutableList()
        val removed = all.removeAll { it.id == requestId }
        if (removed) savePending(all)
        return removed
    }

    fun validateForConfirmation(requestId: String, currentRule: EarnItRuleStore.Rule?): PendingActionValidation {
        val action = pendingActions().firstOrNull { it.id == requestId } ?: return PendingActionValidation.Invalid("This change request no longer exists. Begin again.")
        val config = (globalConfiguration() ?: configuration(action.ruleId))?.let(::normalize)
        val valid = currentRule != null && action.authorizationStatus == StrictModeAuthorizationStatus.AwaitingFinalConfirmation &&
            action.expiresAtMillis > nowMillis() && (action.authorizationExpiresAtMillis ?: 0L) > nowMillis() &&
            config != null && config.protectsLessRestrictiveChanges() && config.protectionMethod == action.authorizationMethod &&
            StrictModeFingerprint.rule(currentRule) == action.originalRuleFingerprint &&
            StrictModeFingerprint.configuration(config) == action.originalStrictModeFingerprint && descriptorStillValid(action, currentRule)
        if (!valid) {
            cancelRequest(action.id)
            return PendingActionValidation.Invalid("This Rule changed while the request was open. Begin again.")
        }
        return PendingActionValidation.Valid(action)
    }

    fun consume(requestId: String): PendingStrictModeAction? = updatePending(requestId) { action ->
        if (action.authorizationStatus != StrictModeAuthorizationStatus.AwaitingFinalConfirmation) action else action.copy(authorizationStatus = StrictModeAuthorizationStatus.Consumed, consumedAtMillis = nowMillis())
    }

    fun disableAfterConfirmation(ruleId: String) {
        val config = configuration(ruleId) ?: return
        putConfiguration(config.copy(lifecycle = RuleStrictModeLifecycle.Disabled, activationRequestedAtMillis = null, activeFromMillis = null, deactivationStartedAtMillis = null, deactivationAvailableAtMillis = null, configurationVersion = config.configurationVersion + 1, updatedAtMillis = nowMillis()))
    }

    fun replaceMethodAfterConfirmation(ruleId: String, method: StrictModeProtectionMethod) {
        val config = configuration(ruleId) ?: return
        putConfiguration(config.copy(protectionMethod = method, lifecycle = RuleStrictModeLifecycle.Active, deactivationWaitMillis = if (method == StrictModeProtectionMethod.Countdown) config.deactivationWaitMillis ?: DEFAULT_COUNTDOWN_MILLIS else null, deactivationStartedAtMillis = null, deactivationAvailableAtMillis = null, configurationVersion = config.configurationVersion + 1, updatedAtMillis = nowMillis()))
    }

    private fun descriptorStillValid(action: PendingStrictModeAction, current: EarnItRuleStore.Rule): Boolean = when (val descriptor = action.descriptor) {
        is StrictModeActionDescriptor.Update -> RuleRestrictionPolicy.compare(current, descriptor.proposedRule).classification == RestrictionClassification.LessRestrictive
        is StrictModeActionDescriptor.Pause -> descriptor.durationMillis > 0L
        is StrictModeActionDescriptor.Delete, is StrictModeActionDescriptor.Disable, is StrictModeActionDescriptor.ReplaceMethod -> true
    }

    private fun normalize(config: RuleStrictModeConfiguration): RuleStrictModeConfiguration {
        val now = nowMillis()
        // Compatibility with the first per-Rule foundation build, which mapped the legacy
        // countdown identifier to ChargerWait before Countdown had its own persisted value.
        if (config.protectionMethod == StrictModeProtectionMethod.ChargerWait && config.configurationVersion == 1L) {
            val compatibleLifecycle = if (
                config.lifecycle == RuleStrictModeLifecycle.Invalid &&
                config.activationRequestedAtMillis != null && config.activeFromMillis != null
            ) RuleStrictModeLifecycle.Active else config.lifecycle
            return normalize(
                config.copy(
                    lifecycle = compatibleLifecycle,
                    protectionMethod = StrictModeProtectionMethod.Countdown,
                    deactivationWaitMillis = config.deactivationWaitMillis ?: DEFAULT_COUNTDOWN_MILLIS
                )
            )
        }
        val structurallyInvalid = config.ruleId.isBlank() || config.protectionMethod == null ||
            (config.lifecycle == RuleStrictModeLifecycle.PendingActivation && (config.activationRequestedAtMillis == null || config.activeFromMillis == null)) ||
            (config.activeFromMillis != null && config.activationRequestedAtMillis != null && config.activeFromMillis < config.activationRequestedAtMillis)
        if (structurallyInvalid && config.lifecycle != RuleStrictModeLifecycle.Disabled) return config.copy(lifecycle = RuleStrictModeLifecycle.Invalid, updatedAtMillis = now)
        if (config.lifecycle == RuleStrictModeLifecycle.PendingActivation && now >= requireNotNull(config.activeFromMillis)) {
            return normalize(config.copy(lifecycle = RuleStrictModeLifecycle.Active, updatedAtMillis = now))
        }
        if (config.lifecycle == RuleStrictModeLifecycle.DeactivationCounting) {
            val started = config.deactivationStartedAtMillis
            val available = config.deactivationAvailableAtMillis
            if (started == null || available == null || available < started) return config.copy(lifecycle = RuleStrictModeLifecycle.Invalid, updatedAtMillis = now)
            if (now >= available) return config.copy(lifecycle = RuleStrictModeLifecycle.DeactivationReady, updatedAtMillis = now)
        }
        if (config.lifecycle == RuleStrictModeLifecycle.Active || config.lifecycle == RuleStrictModeLifecycle.DeactivationCounting || config.lifecycle == RuleStrictModeLifecycle.DeactivationReady) {
            val handler = config.protectionMethod?.let(authorizationMethods::handlerFor)
            if (handler == null || !handler.isConfigurationComplete(config)) {
                return config.copy(lifecycle = RuleStrictModeLifecycle.Invalid, updatedAtMillis = now)
            }
        }
        return config
    }

    private fun putConfiguration(config: RuleStrictModeConfiguration) {
        val all = configurations().toMutableMap(); all[config.ruleId] = config; saveConfigurations(all.values)
    }
    private fun updatePending(id: String, transform: (PendingStrictModeAction) -> PendingStrictModeAction): PendingStrictModeAction? {
        val all = pendingActions().toMutableList(); val index = all.indexOfFirst { it.id == id }; if (index < 0) return null
        all[index] = transform(all[index]); savePending(all); return all[index]
    }
    private fun saveConfigurations(configs: Collection<RuleStrictModeConfiguration>) = persistence.writeConfigurations(StrictModeFoundationCodec.encodeConfigurations(configs))
    private fun savePending(actions: List<PendingStrictModeAction>) = persistence.writePendingActions(StrictModeFoundationCodec.encodePending(actions))
    private fun decodeConfigurations(raw: String?) = StrictModeFoundationCodec.decodeConfigurations(raw)
    private fun decodePending(raw: String?) = StrictModeFoundationCodec.decodePending(raw)
    private fun safeAdd(left: Long, right: Long) = if (right < 0 || left > Long.MAX_VALUE - right) null else left + right

    companion object {
        const val GLOBAL_CONFIGURATION_ID = "__global_strict_mode__"
        private const val GLOBAL_ACTION_FINGERPRINT = "global"
        const val REQUEST_EXPIRY_MILLIS = 7L * 24L * 60L * 60_000L
        const val AUTHORIZATION_VALIDITY_MILLIS = 5L * 60_000L
        const val DEFAULT_COUNTDOWN_MILLIS = 10L * 60_000L
        private val terminalStatuses = setOf(StrictModeAuthorizationStatus.Consumed, StrictModeAuthorizationStatus.Cancelled, StrictModeAuthorizationStatus.Expired, StrictModeAuthorizationStatus.Invalid)
    }
}

// Deprecated compatibility name used only by migration-focused tests and older call sites.
internal typealias RuleStrictModeStore = GlobalStrictModeStore

internal object StrictModeFingerprint {
    fun rule(rule: EarnItRuleStore.Rule): String = sha256(EarnItRuleStore.encodeRules(listOf(rule)))
    fun configuration(config: RuleStrictModeConfiguration): String {
        val lifecycleFingerprint = if (config.protectsLessRestrictiveChanges()) "PROTECTING" else config.lifecycle.name
        return sha256(listOf(config.ruleId, lifecycleFingerprint, config.protectionMethod?.name.orEmpty(), config.activationRequestedAtMillis, config.activeFromMillis, config.deactivationWaitMillis, config.configurationVersion).joinToString("|"))
    }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

private fun StrictModeActionDescriptor.actionType() = when (this) {
    is StrictModeActionDescriptor.Pause -> PendingStrictModeActionType.PauseRule
    is StrictModeActionDescriptor.Delete -> PendingStrictModeActionType.DeleteRule
    is StrictModeActionDescriptor.Disable -> PendingStrictModeActionType.DisableStrictMode
    is StrictModeActionDescriptor.Update -> PendingStrictModeActionType.UpdateRule
    is StrictModeActionDescriptor.ReplaceMethod -> PendingStrictModeActionType.ReplaceProtectionMethod
}

private object StrictModeFoundationCodec {
    private const val RECORD = "\u001E"
    private const val FIELD = "\u001F"
    fun encodeConfigurations(configs: Collection<RuleStrictModeConfiguration>) = configs.joinToString(RECORD) { c ->
        fields(c.ruleId, c.lifecycle.name, c.protectionMethod?.name, c.activationRequestedAtMillis, c.activeFromMillis, c.configurationVersion, c.createdAtMillis, c.updatedAtMillis, c.deactivationWaitMillis, c.deactivationStartedAtMillis, c.deactivationAvailableAtMillis)
    }
    fun decodeConfigurations(raw: String?): Map<String, RuleStrictModeConfiguration> = raw.orEmpty().split(RECORD).mapNotNull { record ->
        if (record.isBlank()) return@mapNotNull null
        val f = record.split(FIELD).map(::decode)
        val id = f.getOrNull(0)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val lifecycle = f.getOrNull(1)?.let { value -> RuleStrictModeLifecycle.entries.firstOrNull { it.name == value } } ?: RuleStrictModeLifecycle.Invalid
        val method = f.getOrNull(2)?.let { value -> StrictModeProtectionMethod.entries.firstOrNull { it.name == value } }
        id to RuleStrictModeConfiguration(
            ruleId = id,
            lifecycle = lifecycle,
            protectionMethod = method,
            activationRequestedAtMillis = f.long(3),
            activeFromMillis = f.long(4),
            configurationVersion = f.long(5) ?: 1,
            createdAtMillis = f.long(6) ?: 0,
            updatedAtMillis = f.long(7) ?: 0,
            deactivationWaitMillis = f.long(8) ?: if (method == StrictModeProtectionMethod.Countdown) GlobalStrictModeStore.DEFAULT_COUNTDOWN_MILLIS else null,
            deactivationStartedAtMillis = f.long(9),
            deactivationAvailableAtMillis = f.long(10)
        )
    }.toMap()
    fun encodePending(actions: List<PendingStrictModeAction>) = actions.joinToString(RECORD) { a ->
        val payload = when (val d = a.descriptor) {
            is StrictModeActionDescriptor.Pause -> fields(d.durationMillis, d.reason)
            is StrictModeActionDescriptor.Update -> EarnItRuleStore.encodeRules(listOf(d.proposedRule))
            is StrictModeActionDescriptor.ReplaceMethod -> d.newMethod.name
            else -> ""
        }
        fields(a.id, a.actionType.name, a.ruleId, payload, a.createdAtMillis, a.expiresAtMillis, a.originalRuleFingerprint, a.originalStrictModeFingerprint, a.authorizationStatus.name, a.authorizationMethod.name, a.authorizationExpiresAtMillis, a.consumedAtMillis, a.cancelledAtMillis)
    }
    fun decodePending(raw: String?): List<PendingStrictModeAction> = raw.orEmpty().split(RECORD).mapNotNull { record ->
        if (record.isBlank()) return@mapNotNull null
        val f = record.split(FIELD).map(::decode); val type = f.getOrNull(1)?.let { value -> PendingStrictModeActionType.entries.firstOrNull { it.name == value } } ?: return@mapNotNull null
        val id = f.getOrNull(0) ?: return@mapNotNull null; val ruleId = f.getOrNull(2) ?: return@mapNotNull null; val payload = f.getOrNull(3).orEmpty()
        val descriptor = when (type) {
            PendingStrictModeActionType.PauseRule -> payload.split(FIELD).map(::decode).let { StrictModeActionDescriptor.Pause(ruleId, it.long(0) ?: return@mapNotNull null, it.getOrNull(1)?.takeIf(String::isNotBlank)) }
            PendingStrictModeActionType.DeleteRule -> StrictModeActionDescriptor.Delete(ruleId)
            PendingStrictModeActionType.DisableStrictMode -> StrictModeActionDescriptor.Disable(ruleId)
            PendingStrictModeActionType.ReplaceProtectionMethod -> StrictModeProtectionMethod.entries.firstOrNull { it.name == payload }?.let { StrictModeActionDescriptor.ReplaceMethod(ruleId, it) } ?: return@mapNotNull null
            PendingStrictModeActionType.UpdateRule -> EarnItRuleStore.decodeRules(payload).singleOrNull()?.takeIf { it.id == ruleId }?.let { StrictModeActionDescriptor.Update(ruleId, it) } ?: return@mapNotNull null
        }
        PendingStrictModeAction(id, type, descriptor, f.long(4) ?: return@mapNotNull null, f.long(5) ?: return@mapNotNull null, f.getOrNull(6).orEmpty(), f.getOrNull(7).orEmpty(), f.getOrNull(8)?.let { value -> StrictModeAuthorizationStatus.entries.firstOrNull { it.name == value } } ?: StrictModeAuthorizationStatus.Invalid, f.getOrNull(9)?.let { value -> StrictModeProtectionMethod.entries.firstOrNull { it.name == value } } ?: return@mapNotNull null, f.long(10), f.long(11), f.long(12))
    }
    private fun fields(vararg values: Any?) = values.joinToString(FIELD) { encode(it?.toString().orEmpty()) }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun decode(value: String) = runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault("")
    private fun List<String>.long(index: Int) = getOrNull(index)?.toLongOrNull()
}
