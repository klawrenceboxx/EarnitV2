package com.example.earnitv2

import android.os.BatteryManager
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargerTest {
    @Test fun chargerConfigurationPersistsWithoutDuration() {
        val f = fixture()
        val restored = GlobalStrictModeStore(f.persistence) { f.clock.now }.globalConfiguration()!!
        assertEquals(StrictModeProtectionMethod.Charger, restored.protectionMethod)
        assertTrue(restored.configurationComplete)
        assertFalse(f.persistence.configurations.orEmpty().contains("ChargerWait"))
        assertEquals(11, f.persistence.configurations!!.split(FIELD).size)
    }

    @Test fun setupHasNoChargerDurationSelection() {
        val setup = StrictModeSetupState(
            durationType = StrictModeDurationType.Indefinite,
            timedDurationMillis = null,
            deactivationCountdownMillis = GlobalStrictModeStore.DEFAULT_COUNTDOWN_MILLIS,
            protectionMethod = StrictModeProtectionMethod.Charger
        )
        assertTrue(setup.isValid)
        assertEquals("Charger", strictModeActiveUiState(StrictModeState(), fConfig()).protectionMethod)
        assertEquals("A charger is required to disable Strict Mode or make protected changes.", strictModeActiveUiState(StrictModeState(), fConfig()).deactivationWait)
        assertEquals("Review Change", chargerContinueLabel(PendingStrictModeActionType.UpdateRule))
        assertEquals("Continue to Pause", chargerContinueLabel(PendingStrictModeActionType.PauseRule))
        assertEquals("Continue to Delete", chargerContinueLabel(PendingStrictModeActionType.DeleteRule))
        assertEquals("Continue to Disable", chargerContinueLabel(PendingStrictModeActionType.DisableStrictMode))
    }

    @Test fun mainScreenChargerButtonTracksLiveChargingState() {
        assertEquals(ChargerDeactivationButtonUi("Connect charger to deactivate", false), chargerDeactivationButtonUi(false))
        assertEquals(ChargerDeactivationButtonUi("Disable Strict Mode", true), chargerDeactivationButtonUi(true))
    }

    @Test fun acUsbWirelessAndOtherSourcesRequireActiveCharging() {
        listOf(
            BatteryManager.BATTERY_PLUGGED_AC to ChargingPowerSource.Ac,
            BatteryManager.BATTERY_PLUGGED_USB to ChargingPowerSource.Usb,
            BatteryManager.BATTERY_PLUGGED_WIRELESS to ChargingPowerSource.Wireless,
            8 to ChargingPowerSource.Other
        ).forEach { (plugged, source) ->
            val state = chargingStateFromBatteryValues(plugged, BatteryManager.BATTERY_STATUS_CHARGING)
            assertTrue(state.isActivelyCharging)
            assertEquals(source, state.source)
        }
        val suspended = chargingStateFromBatteryValues(BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_STATUS_NOT_CHARGING)
        assertTrue(suspended.isPlugged)
        assertFalse(suspended.isActivelyCharging)
    }

    @Test fun alreadyChargingIsReadyImmediatelyButDoesNotAuthorizePassively() {
        val f = fixture()
        val action = f.begin(charging = true)
        assertEquals(ChargerAuthorizationState.Ready, f.store.beginOrRestoreCharger(action.id, charging())?.state)
        assertEquals(StrictModeAuthorizationStatus.AwaitingAuthorization, f.store.pendingActions().first { it.id == action.id }.authorizationStatus)
    }

    @Test fun unpluggedWaitsAndCannotAuthorize() {
        val f = fixture(); val action = f.begin(charging = false)
        assertEquals(ChargerAuthorizationState.WaitingForCharger, f.store.beginOrRestoreCharger(action.id, unplugged())?.state)
        assertTrue(f.store.authorizeCharger(action.id, unplugged()) is PendingActionValidation.Invalid)
        assertEquals(StrictModeAuthorizationStatus.AwaitingAuthorization, f.store.pendingActions().first { it.id == action.id }.authorizationStatus)
    }

    @Test fun unplugBeforeContinueDisablesAndReconnectReenables() {
        val f = fixture(); val action = f.begin(charging = true)
        assertEquals(ChargerAuthorizationState.WaitingForCharger, f.store.reconcileActiveChargerSession(unplugged())?.state)
        assertEquals(ChargerAuthorizationState.Ready, f.store.reconcileActiveChargerSession(charging())?.state)
        assertEquals(StrictModeAuthorizationStatus.AwaitingAuthorization, f.store.pendingActions().first { it.id == action.id }.authorizationStatus)
    }

    @Test fun explicitContinueAuthorizesOnlyThisRequestAndFinalConfirmationIsStillRequired() {
        val f = fixture(); val action = f.begin(charging = true)
        assertTrue(f.store.authorizeCharger(action.id, charging()) is PendingActionValidation.Valid)
        assertEquals(StrictModeAuthorizationStatus.AwaitingFinalConfirmation, f.store.pendingActions().first { it.id == action.id }.authorizationStatus)
        assertEquals(RuleStrictModeLifecycle.DeactivationReady, f.store.globalConfiguration()?.lifecycle)
        assertTrue(f.store.globalConfiguration()?.protectsLessRestrictiveChanges() == true)
    }

    @Test fun unplugAfterContinuePreservesAuthorizedState() {
        val f = fixture(); val action = f.begin(charging = true)
        f.store.authorizeCharger(action.id, charging())
        val restored = GlobalStrictModeStore(f.persistence) { f.clock.now }
            .beginOrRestoreCharger(action.id, unplugged())
        assertEquals(ChargerAuthorizationState.Authorized, restored?.state)
    }

    @Test fun globalDisableAppliesOnlyAfterFinalConfirmationAndCannotReplay() {
        val f = fixture(); val action = f.begin(charging = true)
        f.store.authorizeCharger(action.id, charging())
        assertTrue(f.store.confirmGlobalDeactivation() is PendingActionValidation.Valid)
        assertEquals(RuleStrictModeLifecycle.Disabled, f.store.globalConfiguration()?.lifecycle)
        assertEquals(StrictModeAuthorizationStatus.Consumed, f.store.pendingActions().first { it.id == action.id }.authorizationStatus)
        assertTrue(f.store.confirmGlobalDeactivation() is PendingActionValidation.Invalid)
    }

    @Test fun singlePageConfirmationDisablesOnlyWhileActivelyCharging() {
        val unpluggedFixture = fixture()
        assertTrue(unpluggedFixture.store.confirmGlobalChargerDeactivation(unplugged()) is PendingActionValidation.Invalid)
        assertEquals(RuleStrictModeLifecycle.Active, unpluggedFixture.store.globalConfiguration()?.lifecycle)
        assertNull(unpluggedFixture.store.activePendingAction(GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID))

        val chargingFixture = fixture()
        assertTrue(chargingFixture.store.confirmGlobalChargerDeactivation(charging()) is PendingActionValidation.Valid)
        assertEquals(RuleStrictModeLifecycle.Disabled, chargingFixture.store.globalConfiguration()?.lifecycle)
    }

    @Test fun repeatedSinglePageConfirmationCannotReplayAuthorization() {
        val f = fixture()
        assertTrue(f.store.confirmGlobalChargerDeactivation(charging()) is PendingActionValidation.Valid)
        assertTrue(f.store.confirmGlobalChargerDeactivation(charging()) is PendingActionValidation.Invalid)
    }

    @Test fun cancellationPreventsReplayAndLeavesStrictModeActive() {
        val f = fixture(); val action = f.begin(charging = true)
        f.store.cancelGlobalDeactivation()
        assertEquals(StrictModeAuthorizationStatus.Cancelled, f.store.pendingActions().first { it.id == action.id }.authorizationStatus)
        assertEquals(ChargerAuthorizationState.Cancelled, f.store.chargerSession(action)?.state)
        assertEquals(RuleStrictModeLifecycle.Active, f.store.globalConfiguration()?.lifecycle)
    }

    @Test fun duplicateBeginDeactivationReopensTheSameRequest() {
        val f = fixture(); val first = f.begin(charging = false); val second = f.begin(charging = true)
        assertEquals(first.id, second.id)
        assertEquals(1, f.store.pendingActions().count { it.authorizationStatus !in setOf(StrictModeAuthorizationStatus.Cancelled, StrictModeAuthorizationStatus.Consumed, StrictModeAuthorizationStatus.Expired, StrictModeAuthorizationStatus.Invalid) })
    }

    @Test fun weakerRuleActionNeedsItsOwnContinueAndConfirmation() {
        val f = fixture(); val rule = rule()
        val action = (f.store.createPendingAction(rule, StrictModeActionDescriptor.Delete(rule.id)) as PendingActionCreationResult.Created).action
        f.store.beginOrRestoreCharger(action.id, charging())
        assertTrue(f.store.validateForConfirmation(action.id, rule) is PendingActionValidation.Invalid)

        val retry = (f.store.createPendingAction(rule, StrictModeActionDescriptor.Delete(rule.id), replaceExisting = true) as PendingActionCreationResult.Created).action
        f.store.beginOrRestoreCharger(retry.id, charging())
        f.store.authorizeCharger(retry.id, charging())
        assertTrue(f.store.validateForConfirmation(retry.id, rule) is PendingActionValidation.Valid)
        f.store.consume(retry.id)
        assertTrue(f.store.validateForConfirmation(retry.id, rule) is PendingActionValidation.Invalid)
    }

    @Test fun pauseAndUpdateDescriptorsRemainBoundToOriginalRule() {
        val f = fixture(); val rule = rule()
        val pause = (f.store.createPendingAction(rule, StrictModeActionDescriptor.Pause(rule.id, 60_000L, "Break")) as PendingActionCreationResult.Created).action
        assertEquals(rule.id, pause.ruleId)
        f.store.cancelRequest(pause.id)
        val proposed = rule.copy(enabled = false)
        val update = (f.store.createPendingAction(rule, StrictModeActionDescriptor.Update(rule.id, proposed)) as PendingActionCreationResult.Created).action
        f.store.beginOrRestoreCharger(update.id, charging())
        f.store.authorizeCharger(update.id, charging())
        assertTrue(f.store.validateForConfirmation(update.id, rule) is PendingActionValidation.Valid)
        assertTrue(f.store.validateForConfirmation(update.id, rule.copy(rewardSecondsPerProductiveSecond = 2)) is PendingActionValidation.Invalid)
    }

    @Test fun conflictingSecondChargerRequestCannotReuseTheFirstAuthorization() {
        val f = fixture(); val firstRule = rule(); val secondRule = rule().copy(id = "second")
        val first = (f.store.createPendingAction(firstRule, StrictModeActionDescriptor.Delete(firstRule.id)) as PendingActionCreationResult.Created).action
        f.store.beginOrRestoreCharger(first.id, charging())
        val second = f.store.createPendingAction(secondRule, StrictModeActionDescriptor.Delete(secondRule.id))
        assertTrue(second is PendingActionCreationResult.Rejected)
        assertEquals(StrictModeAuthorizationStatus.AwaitingAuthorization, f.store.pendingActions().first { it.id == first.id }.authorizationStatus)
    }

    @Test fun methodReplacementAlwaysRequiresTheCurrentMethod() {
        val f = fixture()
        val result = f.store.requestGlobalMethodChange(
            StrictModeProtectionMethod.Countdown,
            GlobalStrictModeStore.DEFAULT_COUNTDOWN_MILLIS,
            charging()
        )
        assertTrue(result is StrictModeMethodChangeResult.AuthorizationRequired)
        assertEquals(StrictModeProtectionMethod.Charger, (result as StrictModeMethodChangeResult.AuthorizationRequired).action.authorizationMethod)
    }

    @Test fun chargerToChargerIsEquivalentAndCountdownStrengthStillUsesDuration() {
        assertEquals(RestrictionClassification.Equivalent, StrictModeProtectionStrengthPolicy.compare(StrictModeProtectionMethod.Charger, null, StrictModeProtectionMethod.Charger, null))
        assertEquals(RestrictionClassification.LessRestrictive, StrictModeProtectionStrengthPolicy.compare(StrictModeProtectionMethod.Charger, null, StrictModeProtectionMethod.Countdown, 600_000L))
        assertEquals(RestrictionClassification.Stricter, StrictModeProtectionStrengthPolicy.compare(StrictModeProtectionMethod.Countdown, 600_000L, StrictModeProtectionMethod.Countdown, 1_800_000L))
        assertEquals(RestrictionClassification.LessRestrictive, StrictModeProtectionStrengthPolicy.compare(StrictModeProtectionMethod.Countdown, 1_800_000L, StrictModeProtectionMethod.Countdown, 600_000L))
    }

    @Test fun oldChargerWaitDurationsMigrateIdempotentlyWithoutGrantingAuthorization() {
        listOf(600_000L, 1_800_000L, 3_600_000L, 86_400_000L).forEach { duration ->
            val persistence = MemoryPersistence(configurations = legacyConfiguration(duration, RuleStrictModeLifecycle.Active))
            val store = GlobalStrictModeStore(persistence) { NOW }
            val first = store.globalConfiguration()!!
            val persisted = persistence.configurations
            val second = store.globalConfiguration()!!
            assertEquals(StrictModeProtectionMethod.Charger, first.protectionMethod)
            assertNull(first.deactivationWaitMillis)
            assertEquals(first, second)
            assertEquals(persisted, persistence.configurations)
            assertFalse(persisted.orEmpty().contains("ChargerWait"))
        }
    }

    @Test fun inProgressLegacyWaitMigratesToWaitingAndOldProgressDoesNotAuthorize() {
        val f = fixture(); val action = f.begin(charging = true)
        f.store.authorizeCharger(action.id, charging())
        f.persistence.configurations = legacyConfiguration(86_400_000L, RuleStrictModeLifecycle.DeactivationReady)
        f.persistence.legacySessions = legacySession(action.id, accumulated = 86_400_000L)
        f.persistence.sessions = null

        val restored = GlobalStrictModeStore(f.persistence) { f.clock.now }
        val config = restored.globalConfiguration()!!
        val migratedAction = restored.pendingActions().first { it.id == action.id }
        assertEquals(RuleStrictModeLifecycle.DeactivationCounting, config.lifecycle)
        assertEquals(StrictModeAuthorizationStatus.AwaitingAuthorization, migratedAction.authorizationStatus)
        assertEquals(ChargerAuthorizationState.WaitingForCharger, restored.chargerSession(migratedAction)?.state)
        assertTrue(restored.authorizeCharger(action.id, unplugged()) is PendingActionValidation.Invalid)
        assertNull(f.persistence.legacySessions)
    }

    @Test fun pendingActivationMigratesWithoutBeingActivatedOrDisabled() {
        val persistence = MemoryPersistence(configurations = legacyConfiguration(600_000L, RuleStrictModeLifecycle.PendingActivation))
        val restored = GlobalStrictModeStore(persistence) { NOW - 1L }.globalConfiguration()!!
        assertEquals(StrictModeProtectionMethod.Charger, restored.protectionMethod)
        assertEquals(RuleStrictModeLifecycle.PendingActivation, restored.lifecycle)
    }

    @Test fun corruptedPersistedAuthorizationFailsClosed() {
        val f = fixture(); val action = f.begin(charging = true)
        f.persistence.sessions = "${encode(action.id)}${FIELD}not-a-valid-session"
        assertNull(f.store.beginOrRestoreCharger(action.id, charging()))
        assertEquals(StrictModeAuthorizationStatus.Invalid, f.store.pendingActions().first { it.id == action.id }.authorizationStatus)
        assertEquals(RuleStrictModeLifecycle.Active, f.store.globalConfiguration()?.lifecycle)
    }

    @Test fun restorationBeforeContinueRechecksCurrentChargingState() {
        val f = fixture(); val action = f.begin(charging = true)
        assertEquals(ChargerAuthorizationState.WaitingForCharger, GlobalStrictModeStore(f.persistence) { f.clock.now }.beginOrRestoreCharger(action.id, unplugged())?.state)
    }

    @Test fun expiredRequestCannotAuthorize() {
        val f = fixture(); val action = f.begin(charging = true)
        f.clock.now += GlobalStrictModeStore.REQUEST_EXPIRY_MILLIS + 1L
        assertTrue(f.store.authorizeCharger(action.id, charging()) is PendingActionValidation.Invalid)
        assertNotEquals(StrictModeAuthorizationStatus.AwaitingFinalConfirmation, f.store.pendingActions().first { it.id == action.id }.authorizationStatus)
        assertEquals(RuleStrictModeLifecycle.Active, f.store.globalConfiguration()?.lifecycle)
    }

    @Test fun countdownLifecycleAndTimerFormattingRemainUnchanged() {
        var now = 1_000L; val persistence = MemoryPersistence(); val store = GlobalStrictModeStore(persistence) { now }
        store.migrateToGlobal(StrictModeState())
        store.requestGlobalActivation(StrictModeProtectionMethod.Countdown, 0L, 60_000L)
        store.globalConfiguration()
        store.beginGlobalCountdownDeactivation()
        now += 60_000L
        assertEquals(RuleStrictModeLifecycle.DeactivationReady, store.globalConfiguration()?.lifecycle)
        assertEquals("01:00", strictModeTimerLabel(61_000L, 1_000L))
    }

    @Test fun ruleDetailUsesCompactChargerCopy() {
        assertEquals("Charger required for protected changes", ruleStrictModeStatusUi(fConfig()).detail)
    }

    private fun fixture(): Fixture {
        val persistence = MemoryPersistence(); val clock = Clock(); val store = GlobalStrictModeStore(persistence) { clock.now }
        store.migrateToGlobal(StrictModeState())
        store.requestGlobalActivation(StrictModeProtectionMethod.Charger, 0L, null)
        store.globalConfiguration()
        return Fixture(store, persistence, clock)
    }

    private data class Fixture(val store: GlobalStrictModeStore, val persistence: MemoryPersistence, val clock: Clock) {
        fun begin(charging: Boolean): PendingStrictModeAction = when (val result = store.beginGlobalDeactivation(if (charging) ChargingState(true, true, ChargingPowerSource.Ac) else ChargingState(false, false))) {
            is PendingActionCreationResult.Created -> result.action
            is PendingActionCreationResult.AlreadyPending -> result.action
            is PendingActionCreationResult.Rejected -> error(result.message)
        }
    }

    private class Clock(var now: Long = NOW)

    private fun fConfig() = GlobalStrictModeConfiguration(
        ruleId = GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID,
        lifecycle = RuleStrictModeLifecycle.Active,
        protectionMethod = StrictModeProtectionMethod.Charger,
        configurationVersion = 2,
        createdAtMillis = NOW,
        updatedAtMillis = NOW
    )

    private fun rule() = EarnItRuleStore.Rule(
        id = "rule", productivePackage = "focus", productiveName = "Focus",
        blockedApps = listOf(EarnItRuleStore.RuleApp("reward", "Reward")),
        rewardSecondsPerProductiveSecond = 1, activeDays = EarnItRuleStore.allDays.toSet(),
        startMinute = 0, endMinute = 1_440, enabled = true
    )

    private fun charging() = ChargingState(true, true, ChargingPowerSource.Ac)
    private fun unplugged() = ChargingState(false, false)

    private fun legacyConfiguration(duration: Long, lifecycle: RuleStrictModeLifecycle): String = listOf(
        GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID, lifecycle.name, "ChargerWait", NOW, NOW, 2L, NOW, NOW,
        "", "", "", duration
    ).joinToString(FIELD) { encode(it.toString()) }

    private fun legacySession(requestId: String, accumulated: Long): String = listOf(
        requestId, 86_400_000L, accumulated, NOW, NOW, 1L, "WaitComplete", NOW, NOW + GlobalStrictModeStore.REQUEST_EXPIRY_MILLIS, NOW
    ).joinToString(FIELD) { encode(it.toString()) }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private class MemoryPersistence(
        var configurations: String? = null,
        var pending: String? = null,
        var sessions: String? = null,
        var legacySessions: String? = null
    ) : StrictModeFoundationPersistence {
        override fun readConfigurations() = configurations
        override fun writeConfigurations(value: String) { configurations = value }
        override fun readPendingActions() = pending
        override fun writePendingActions(value: String) { pending = value }
        override fun readChargerSessions() = sessions
        override fun writeChargerSessions(value: String) { sessions = value }
        override fun readLegacyChargerWaitSessions() = legacySessions
        override fun clearLegacyChargerWaitSessions() { legacySessions = null }
    }

    companion object {
        private const val NOW = 1_000L
        private const val FIELD = "\u001F"
    }
}
