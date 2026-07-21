package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModeFoundationTest {
    @Test fun equivalentRename_isEquivalent() = assertComparison(base().copy(productiveName = "Focus"), RestrictionClassification.Equivalent)
    @Test fun rewardAppAdded_isStricter() = assertComparison(base().copy(blockedApps = base().blockedApps + app("yt")), RestrictionClassification.Stricter)
    @Test fun rewardAppRemoved_isLessRestrictive() = assertComparison(base().copy(blockedApps = listOf(app("ig"))), RestrictionClassification.LessRestrictive)
    @Test fun earnAppAdded_isStricter() = assertComparison(base().copy(productiveApps = base().productiveApps + app("book")), RestrictionClassification.Stricter)
    @Test fun earnAppRemoved_isLessRestrictive() = assertComparison(base().copy(productiveApps = emptyList(), productivePackage = ""), RestrictionClassification.LessRestrictive)
    @Test fun rewardRateIncrease_isLessRestrictive() = assertComparison(base().copy(rewardSecondsPerProductiveSecond = 5), RestrictionClassification.LessRestrictive)
    @Test fun rewardRateReduction_isStricter() = assertComparison(base().copy(rewardSecondsPerProductiveSecond = 1), RestrictionClassification.Stricter)
    @Test fun addedDay_isStricter() = assertComparison(base().copy(activeDays = setOf(1, 2, 3, 4, 5, 6)), RestrictionClassification.Stricter)
    @Test fun removedDay_isLessRestrictive() = assertComparison(base().copy(activeDays = setOf(1, 2, 3, 4)), RestrictionClassification.LessRestrictive)

    @Test fun compoundEdit_withAnyWeakerDimension_isLessRestrictive() {
        val proposed = base().copy(blockedApps = base().blockedApps + app("yt"), rewardSecondsPerProductiveSecond = 5)
        assertEquals(RestrictionClassification.LessRestrictive, RuleRestrictionPolicy.compare(base(), proposed).classification)
    }

    @Test fun compoundEdit_allStricter_isStricter() {
        val proposed = base().copy(blockedApps = base().blockedApps + app("yt"), rewardSecondsPerProductiveSecond = 1)
        assertEquals(RestrictionClassification.Stricter, RuleRestrictionPolicy.compare(base(), proposed).classification)
    }

    @Test fun completeRequirementIncreaseAndAdd_areStricter() {
        val current = complete()
        val proposed = current.copy(requirements = listOf(requirement("duo", 20), requirement("read", 10)))
        assertEquals(RestrictionClassification.Stricter, RuleRestrictionPolicy.compare(current, proposed).classification)
    }

    @Test fun completeRequirementReductionAndRemoval_areLessRestrictive() {
        val current = complete().copy(requirements = listOf(requirement("duo", 10), requirement("read", 10)))
        val proposed = current.copy(requirements = listOf(requirement("duo", 5)))
        assertEquals(RestrictionClassification.LessRestrictive, RuleRestrictionPolicy.compare(current, proposed).classification)
    }

    @Test fun scheduledBlockExtension_isStricter_andShorteningIsLessRestrictive() {
        val current = scheduled().copy(timeWindows = listOf(EarnItRuleStore.TimeWindow(9 * 60, 17 * 60)))
        assertEquals(RestrictionClassification.Stricter, RuleRestrictionPolicy.compare(current, current.copy(timeWindows = listOf(EarnItRuleStore.TimeWindow(8 * 60, 18 * 60)))).classification)
        assertEquals(RestrictionClassification.LessRestrictive, RuleRestrictionPolicy.compare(current, current.copy(timeWindows = listOf(EarnItRuleStore.TimeWindow(10 * 60, 16 * 60)))).classification)
    }

    @Test fun typeChange_failsClosed() {
        assertEquals(RestrictionClassification.LessRestrictive, RuleRestrictionPolicy.compare(base(), base().copy(type = EarnItRuleStore.RuleType.ScheduledBlock)).classification)
    }

    @Test fun activationPersistsAndRestoresAfterClockAdvances() {
        var now = 1_000L; val persistence = MemoryPersistence()
        RuleStrictModeStore(persistence, completeMethods) { now }.requestActivation("rule", StrictModeProtectionMethod.Charger, 5_000L)
        assertEquals(RuleStrictModeLifecycle.PendingActivation, RuleStrictModeStore(persistence, completeMethods) { now }.configuration("rule")?.lifecycle)
        now = 6_000L
        assertEquals(RuleStrictModeLifecycle.Active, RuleStrictModeStore(persistence, completeMethods) { now }.configuration("rule")?.lifecycle)
    }

    @Test fun duplicateActivationDoesNotResetTimestamp() {
        var now = 1_000L; val store = RuleStrictModeStore(MemoryPersistence()) { now }
        val first = store.requestActivation("rule", StrictModeProtectionMethod.Charger, 5_000L)
        now = 2_000L
        val duplicate = store.requestActivation("rule", StrictModeProtectionMethod.AccountabilityPin, 50_000L)
        assertEquals(first.activeFromMillis, duplicate.activeFromMillis)
        assertEquals(first.protectionMethod, duplicate.protectionMethod)
    }

    @Test fun invalidConfigurationFailsClosed() {
        val persistence = MemoryPersistence(configurations = encodedInvalidConfig())
        val config = RuleStrictModeStore(persistence) { 2_000L }.configuration("rule")!!
        assertEquals(RuleStrictModeLifecycle.Invalid, config.lifecycle)
        assertTrue(config.protectsLessRestrictiveChanges())
    }

    @Test fun ruleDetailUiRepresentsDisabledPendingActiveAndInvalidWithoutColor() {
        assertEquals("Strict Mode", ruleStrictModeStatusUi(null).title)
        val pending = activeConfig("rule").copy(lifecycle = RuleStrictModeLifecycle.PendingActivation, activeFromMillis = 2_000L)
        assertEquals("All Rules will be protected at Jul 22 at 9:00 AM.", ruleStrictModeStatusUi(pending) { "Jul 22 at 9:00 AM" }.detail)
        assertEquals("Strict Mode is active", ruleStrictModeStatusUi(activeConfig("rule")).title)
        assertTrue(ruleStrictModeStatusUi(activeConfig("rule").copy(lifecycle = RuleStrictModeLifecycle.Invalid)).detail.contains("remain protected"))
    }

    @Test fun pendingCreationAllowsOnlyOneAndCanReplace() {
        val fixture = fixture()
        val first = fixture.store.createPendingAction(fixture.rule, StrictModeActionDescriptor.Delete(fixture.rule.id)) as PendingActionCreationResult.Created
        assertTrue(fixture.store.createPendingAction(fixture.rule, StrictModeActionDescriptor.Pause(fixture.rule.id, 1000, "reset")) is PendingActionCreationResult.AlreadyPending)
        val replacement = fixture.store.createPendingAction(fixture.rule, StrictModeActionDescriptor.Pause(fixture.rule.id, 1000, "reset"), replaceExisting = true)
        assertTrue(replacement is PendingActionCreationResult.Created)
        assertEquals(StrictModeAuthorizationStatus.Cancelled, fixture.store.pendingActions().first { it.id == first.action.id }.authorizationStatus)
    }

    @Test fun cancellationAndExpiryArePersisted() {
        val fixture = fixture()
        val action = created(fixture, StrictModeActionDescriptor.Delete(fixture.rule.id))
        fixture.store.cancelRequest(action.id)
        assertEquals(StrictModeAuthorizationStatus.Cancelled, fixture.store.pendingActions().single().authorizationStatus)
        var now = 1_000L; val persistence = MemoryPersistence(); val setup = RuleStrictModeStore(persistence) { now }
        setup.requestActivation("rule", StrictModeProtectionMethod.Charger, 0); setup.configuration("rule")
        setup.createPendingAction(base(), StrictModeActionDescriptor.Delete("rule"))
        now += RuleStrictModeStore.REQUEST_EXPIRY_MILLIS + 1
        assertNull(RuleStrictModeStore(persistence) { now }.activePendingAction("rule"))
    }

    @Test fun pendingPausePayloadAndReasonRestoreAcrossProcessDeath() {
        val fixture = fixture(); created(fixture, StrictModeActionDescriptor.Pause("rule", 9_000L, "Need a reset"))
        val restored = RuleStrictModeStore((fixture.storePersistence())).pendingActions().single().descriptor as StrictModeActionDescriptor.Pause
        assertEquals(9_000L, restored.durationMillis)
        assertEquals("Need a reset", restored.reason)
    }

    @Test fun legacyActiveStateMigratesPerRuleWithoutWeakeningProtection() {
        val persistence = MemoryPersistence(); val store = RuleStrictModeStore(persistence) { 5_000L }
        store.migrateLegacyRule("one", StrictModeState(lifecycleState = StrictModeLifecycleState.Active, activatedAtMillis = 1_000L))
        store.migrateLegacyRule("two", StrictModeState(lifecycleState = StrictModeLifecycleState.Active, activatedAtMillis = 1_000L))
        assertEquals(setOf("one", "two"), store.configurations().keys)
        assertTrue(store.configurations().values.all { it.protectsLessRestrictiveChanges() })
    }

    @Test fun fingerprintChangesWhenRuleOrConfigurationChanges() {
        val rule = base()
        assertNotEquals(StrictModeFingerprint.rule(rule), StrictModeFingerprint.rule(rule.copy(rewardSecondsPerProductiveSecond = 5)))
        val config = activeConfig(rule.id)
        assertNotEquals(StrictModeFingerprint.configuration(config), StrictModeFingerprint.configuration(config.copy(configurationVersion = 2)))
    }

    @Test fun staleRuleInvalidatesAuthorizedRequest() {
        val fixture = fixture(); val action = created(fixture, StrictModeActionDescriptor.Delete(fixture.rule.id))
        fixture.store.markAuthorized(action.id)
        val result = fixture.store.validateForConfirmation(action.id, fixture.rule.copy(rewardSecondsPerProductiveSecond = 5))
        assertTrue(result is PendingActionValidation.Invalid)
        assertEquals(StrictModeAuthorizationStatus.Cancelled, fixture.store.pendingActions().single().authorizationStatus)
    }

    @Test fun strictModeFingerprintChangeInvalidatesAuthorizedRequest() {
        val fixture = fixture(); val action = created(fixture, StrictModeActionDescriptor.Delete(fixture.rule.id)); fixture.store.markAuthorized(action.id)
        fixture.store.replaceMethodAfterConfirmation(fixture.rule.id, StrictModeProtectionMethod.AccountabilityPin)
        assertTrue(fixture.store.validateForConfirmation(action.id, fixture.rule) is PendingActionValidation.Invalid)
    }

    @Test fun missingRuleFailsClosedAtFinalConfirmation() {
        val fixture = fixture(); val action = created(fixture, StrictModeActionDescriptor.Delete(fixture.rule.id)); fixture.store.markAuthorized(action.id)
        assertTrue(fixture.store.validateForConfirmation(action.id, null) is PendingActionValidation.Invalid)
    }

    @Test fun startCountdownCreatesPersistedRequestAndBeginsImmediately() {
        val countdown = countdownFixture()
        val result = countdown.store.beginCountdownDeactivation(countdown.rule)
        assertTrue(result is PendingActionCreationResult.Created)
        assertEquals(RuleStrictModeLifecycle.DeactivationCounting, countdown.store.configuration(countdown.rule.id)?.lifecycle)
        assertEquals(countdown.now() + RuleStrictModeStore.DEFAULT_COUNTDOWN_MILLIS, countdown.store.configuration(countdown.rule.id)?.deactivationAvailableAtMillis)
        assertEquals(PendingStrictModeActionType.DisableStrictMode, RuleStrictModeStore(countdown.persistence) { countdown.now() }.activePendingAction(countdown.rule.id)?.actionType)
    }

    @Test fun countdownSurvivesRestartAndDuplicateStartIsPrevented() {
        val countdown = countdownFixture()
        val first = countdown.store.beginCountdownDeactivation(countdown.rule) as PendingActionCreationResult.Created
        val restored = RuleStrictModeStore(countdown.persistence) { countdown.now() }
        val duplicate = restored.beginCountdownDeactivation(countdown.rule)
        assertTrue(duplicate is PendingActionCreationResult.AlreadyPending)
        assertEquals(first.action.id, (duplicate as PendingActionCreationResult.AlreadyPending).action.id)
        assertEquals(RuleStrictModeLifecycle.DeactivationCounting, restored.configuration(countdown.rule.id)?.lifecycle)
    }

    @Test fun countdownCompletionRequiresFinalConfirmation() {
        val countdown = countdownFixture()
        countdown.store.beginCountdownDeactivation(countdown.rule)
        countdown.advance(RuleStrictModeStore.DEFAULT_COUNTDOWN_MILLIS)
        val restored = RuleStrictModeStore(countdown.persistence) { countdown.now() }
        assertEquals(RuleStrictModeLifecycle.DeactivationReady, restored.configuration(countdown.rule.id)?.lifecycle)
        assertTrue(restored.configuration(countdown.rule.id)?.protectsLessRestrictiveChanges() == true)
        assertTrue(restored.confirmCountdownDeactivation(countdown.rule) is PendingActionValidation.Valid)
        assertEquals(RuleStrictModeLifecycle.Disabled, restored.configuration(countdown.rule.id)?.lifecycle)
        assertEquals(StrictModeAuthorizationStatus.Consumed, restored.pendingActions().single().authorizationStatus)
    }

    @Test fun countdownCanBeCancelledWithoutDisablingStrictMode() {
        val countdown = countdownFixture()
        val action = (countdown.store.beginCountdownDeactivation(countdown.rule) as PendingActionCreationResult.Created).action
        countdown.store.cancelCountdownDeactivation(countdown.rule.id)
        assertEquals(RuleStrictModeLifecycle.Active, countdown.store.configuration(countdown.rule.id)?.lifecycle)
        assertEquals(StrictModeAuthorizationStatus.Cancelled, countdown.store.pendingActions().first { it.id == action.id }.authorizationStatus)
    }

    @Test fun migratedExistingCountdownConfigurationRemainsValid() {
        val persistence = MemoryPersistence(); val store = RuleStrictModeStore(persistence) { 10_000L }
        val migrated = store.migrateLegacyRule(
            "rule",
            StrictModeState(
                lifecycleState = StrictModeLifecycleState.Active,
                configuration = StrictModeConfiguration(deactivationCountdownMillis = 30 * 60_000L),
                activatedAtMillis = 1_000L
            )
        )!!
        assertEquals(RuleStrictModeLifecycle.Active, migrated.lifecycle)
        assertEquals(StrictModeProtectionMethod.Countdown, migrated.protectionMethod)
        assertEquals(30 * 60_000L, migrated.deactivationWaitMillis)
    }

    @Test fun firstFoundationChargerMappingIsRecoveredAsCountdown() {
        val raw = listOf("rule", "Invalid", "ChargerWait", "1000", "2000", "1", "1000", "2000").joinToString("\u001F")
        val store = RuleStrictModeStore(MemoryPersistence(configurations = raw)) { 3_000L }
        val recovered = store.configuration("rule")!!
        assertEquals(RuleStrictModeLifecycle.Active, recovered.lifecycle)
        assertEquals(StrictModeProtectionMethod.Countdown, recovered.protectionMethod)
        assertEquals(RuleStrictModeStore.DEFAULT_COUNTDOWN_MILLIS, recovered.deactivationWaitMillis)
    }

    @Test fun cancelRequestCanRemovePersistedRequestCompletely() {
        val fixture = fixture(); val action = created(fixture, StrictModeActionDescriptor.Update("rule", base().copy(rewardSecondsPerProductiveSecond = 5)))
        assertTrue(fixture.store.removeRequest(action.id))
        assertTrue(fixture.store.pendingActions().isEmpty())
    }

    @Test fun countdownTimerUsesStableClockFormatting() {
        assertEquals("09:42", strictModeTimerLabel(targetMillis = 582_000L, nowMillis = 0L))
        assertEquals("01:09:42", strictModeTimerLabel(targetMillis = 4_182_000L, nowMillis = 0L))
    }

    @Test fun blockedReviewNavigationTargetsAffectedRuleAndDiscardsDraft() {
        val view = blockedReviewNavigation(BlockedReviewAction.ViewStrictMode, "protected-rule", "draft-rule")
        assertEquals("protected-rule", view.affectedRuleId)
        assertTrue(view.openStrictMode)
        assertTrue(view.discardUnsavedEdit)
        assertTrue(!view.removePendingRequest)

        val cancel = blockedReviewNavigation(BlockedReviewAction.CancelRequest, "protected-rule", "draft-rule")
        assertTrue(cancel.removePendingRequest)
        assertTrue(cancel.returnToRuleDetail)
        assertTrue(cancel.discardUnsavedEdit)

        val systemBack = blockedReviewNavigation(BlockedReviewAction.SystemBack, "protected-rule", "draft-rule")
        assertEquals(cancel, systemBack)
    }

    @Test fun activeCountdownRuleDetailUsesCorrectStatusAndSharedBackLabel() {
        val status = ruleStrictModeStatusUi(activeConfig("rule"))
        assertEquals("Strict Mode is active", status.title)
        assertEquals("All Rules are protected.", status.detail)
        assertEquals("< Back", EARNIT_BACK_LABEL)
    }

    @Test fun strictModeActiveScreenModelContainsOnlyCurrentRuleInformation() {
        val ui = strictModeActiveUiState(
            StrictModeState(
                lifecycleState = StrictModeLifecycleState.Active,
                configuration = StrictModeConfiguration(deactivationCountdownMillis = 10 * 60_000L)
            )
        )
        assertEquals("Countdown", ui.protectionMethod)
        assertEquals("10 minutes deactivation wait", ui.deactivationWait)
        assertTrue(ui.description.contains("All Rules"))
        assertTrue(listOf(ui.title, ui.description, ui.protectionMethod, ui.deactivationWait).none { it.contains("Enabled Rules") || it.contains("Protected Rules") })
    }

    @Test fun oneActivePerRuleConfigurationMigratesToGlobalActive() {
        val persistence = MemoryPersistence(); val store = RuleStrictModeStore(persistence) { 1_000L }
        store.requestActivation("one", StrictModeProtectionMethod.Countdown, 0L, 60_000L)
        val global = store.migrateToGlobal(StrictModeState())
        assertEquals(RuleStrictModeStore.GLOBAL_CONFIGURATION_ID, global.ruleId)
        assertEquals(RuleStrictModeLifecycle.Active, global.lifecycle)
    }

    @Test fun multiplePerRuleConfigurationsChooseStrongestCountdown() {
        val persistence = MemoryPersistence(); val store = RuleStrictModeStore(persistence) { 1_000L }
        store.requestActivation("one", StrictModeProtectionMethod.Countdown, 0L, 60_000L)
        store.requestActivation("two", StrictModeProtectionMethod.Countdown, 0L, 15 * 60_000L)
        val global = store.migrateToGlobal(StrictModeState())
        assertEquals(15 * 60_000L, global.deactivationWaitMillis)
        assertEquals(RuleStrictModeLifecycle.Active, global.lifecycle)
    }

    @Test fun latestInProgressCountdownMigratesGloballyAndRestores() {
        val clock = TestClock(1_000L); val persistence = MemoryPersistence(); val store = RuleStrictModeStore(persistence) { clock.value }
        val one = base().copy(id = "one"); val two = base().copy(id = "two")
        store.requestActivation(one.id, StrictModeProtectionMethod.Countdown, 0L, 60_000L)
        store.requestActivation(two.id, StrictModeProtectionMethod.Countdown, 0L, 10 * 60_000L)
        store.beginCountdownDeactivation(one)
        clock.value += 1_000L
        store.beginCountdownDeactivation(two)
        val global = store.migrateToGlobal(StrictModeState())
        assertEquals(RuleStrictModeLifecycle.DeactivationCounting, global.lifecycle)
        assertEquals(clock.value + 10 * 60_000L, global.deactivationAvailableAtMillis)
        assertEquals(global, RuleStrictModeStore(persistence) { clock.value }.globalConfiguration())
    }

    @Test fun globalMigrationIsIdempotent() {
        val persistence = MemoryPersistence(); val store = RuleStrictModeStore(persistence) { 1_000L }
        store.requestActivation("one", StrictModeProtectionMethod.Countdown, 0L, 60_000L)
        val first = store.migrateToGlobal(StrictModeState())
        val persisted = persistence.configurations
        val second = store.migrateToGlobal(StrictModeState(lifecycleState = StrictModeLifecycleState.Active))
        assertEquals(first, second)
        assertEquals(persisted, persistence.configurations)
    }

    @Test fun activeGlobalConfigurationProtectsEveryExistingAndFutureRule() {
        val global = globalFixture()
        val existing = base().copy(id = "existing")
        val future = base().copy(id = "future")
        assertTrue(global.store.createPendingAction(existing, StrictModeActionDescriptor.Delete(existing.id)) is PendingActionCreationResult.Created)
        assertTrue(global.store.createPendingAction(future, StrictModeActionDescriptor.Pause(future.id, 1_000L, null)) is PendingActionCreationResult.Created)
        assertTrue(global.store.globalConfiguration()?.protectsLessRestrictiveChanges() == true)
    }

    @Test fun pendingRuleActionUsesRuleIdAndGlobalFingerprint() {
        val global = globalFixture(); val rule = base().copy(id = "affected")
        val action = (global.store.createPendingAction(rule, StrictModeActionDescriptor.Update(rule.id, rule.copy(rewardSecondsPerProductiveSecond = 5))) as PendingActionCreationResult.Created).action
        assertEquals("affected", action.ruleId)
        assertEquals(StrictModeFingerprint.configuration(global.store.globalConfiguration()!!), action.originalStrictModeFingerprint)
    }

    @Test fun migrationPreservesPendingRuleActionAndRebindsGlobalFingerprint() {
        val persistence = MemoryPersistence(); val store = RuleStrictModeStore(persistence) { 1_000L }; val rule = base().copy(id = "affected")
        store.requestActivation(rule.id, StrictModeProtectionMethod.Countdown, 0L, 60_000L)
        val original = (store.createPendingAction(rule, StrictModeActionDescriptor.Delete(rule.id)) as PendingActionCreationResult.Created).action
        val global = store.migrateToGlobal(StrictModeState())
        val migrated = store.pendingActions().first { it.id == original.id }
        assertEquals("affected", migrated.ruleId)
        assertEquals(StrictModeFingerprint.configuration(global), migrated.originalStrictModeFingerprint)
        assertEquals(StrictModeAuthorizationStatus.AwaitingAuthorization, migrated.authorizationStatus)
    }

    @Test fun globalFingerprintChangeInvalidatesRuleRequest() {
        val global = globalFixture(); val rule = base()
        val action = (global.store.createPendingAction(rule, StrictModeActionDescriptor.Delete(rule.id)) as PendingActionCreationResult.Created).action
        global.store.markAuthorized(action.id)
        global.store.replaceMethodAfterConfirmation(RuleStrictModeStore.GLOBAL_CONFIGURATION_ID, StrictModeProtectionMethod.AccountabilityPin)
        assertTrue(global.store.validateForConfirmation(action.id, rule) is PendingActionValidation.Invalid)
    }

    @Test fun globalDeactivationDoesNotRequireRuleAndFinalConfirmationIsRequired() {
        val global = globalFixture()
        val request = global.store.beginGlobalCountdownDeactivation()
        assertTrue(request is PendingActionCreationResult.Created)
        assertEquals(RuleStrictModeStore.GLOBAL_CONFIGURATION_ID, (request as PendingActionCreationResult.Created).action.ruleId)
        assertTrue(global.store.confirmGlobalCountdownDeactivation() is PendingActionValidation.Invalid)
        global.clock.value += RuleStrictModeStore.DEFAULT_COUNTDOWN_MILLIS
        assertEquals(RuleStrictModeLifecycle.DeactivationReady, global.store.globalConfiguration()?.lifecycle)
        assertTrue(global.store.confirmGlobalCountdownDeactivation() is PendingActionValidation.Valid)
    }

    @Test fun disablingGlobalStrictModeCancelsOutstandingProtectedRuleRequests() {
        val global = globalFixture(); val rule = base().copy(id = "affected")
        val ruleRequest = (global.store.createPendingAction(rule, StrictModeActionDescriptor.Delete(rule.id)) as PendingActionCreationResult.Created).action
        global.store.beginGlobalCountdownDeactivation()
        global.clock.value += RuleStrictModeStore.DEFAULT_COUNTDOWN_MILLIS
        global.store.globalConfiguration()
        assertTrue(global.store.confirmGlobalCountdownDeactivation() is PendingActionValidation.Valid)
        assertEquals(StrictModeAuthorizationStatus.Cancelled, global.store.pendingActions().first { it.id == ruleRequest.id }.authorizationStatus)
    }

    @Test fun deletingRulesDoesNotAlterGlobalStrictMode() {
        val global = globalFixture(); val before = global.store.globalConfiguration()
        // Rule deletion is intentionally outside Strict Mode persistence; even zero Rules leaves global state intact.
        assertEquals(before, RuleStrictModeStore(global.persistence) { global.clock.value }.globalConfiguration())
    }

    @Test fun corruptedPerRuleMigrationFailsClosedGlobally() {
        val raw = listOf("rule", "Active", "UnknownMethod", "1000", "2000", "1", "1000", "1000").joinToString("\u001F")
        val store = RuleStrictModeStore(MemoryPersistence(configurations = raw)) { 3_000L }
        val global = store.migrateToGlobal(StrictModeState())
        assertEquals(RuleStrictModeLifecycle.Invalid, global.lifecycle)
        assertTrue(global.protectsLessRestrictiveChanges())
    }

    @Test fun globalStrictModeBackNavigationReturnsToOpeningSurface() {
        assertEquals(StrictModeReturnTarget.Settings, strictModeReturnTarget(true, null))
        assertEquals(StrictModeReturnTarget.RuleDetail, strictModeReturnTarget(false, "rule"))
        assertEquals(StrictModeReturnTarget.Home, strictModeReturnTarget(false, null))
    }

    @Test fun compactRowAndOverflowShareGlobalStrictModeDestinationLabel() {
        assertEquals("Strict Mode", ruleStrictModeStatusUi(null).title)
        assertEquals("Strict Mode", ruleDetailOverflowActionLabel(RuleDetailOverflowAction.StrictMode))
    }

    @Test fun finalConfirmationIsRequiredAndConsumedRequestCannotReplay() {
        val fixture = fixture(); val action = created(fixture, StrictModeActionDescriptor.Delete(fixture.rule.id))
        assertTrue(fixture.store.validateForConfirmation(action.id, fixture.rule) is PendingActionValidation.Invalid)
        val fixture2 = fixture(); val authorized = created(fixture2, StrictModeActionDescriptor.Delete(fixture2.rule.id)); fixture2.store.markAuthorized(authorized.id)
        assertTrue(fixture2.store.validateForConfirmation(authorized.id, fixture2.rule) is PendingActionValidation.Valid)
        fixture2.store.consume(authorized.id)
        assertTrue(fixture2.store.validateForConfirmation(authorized.id, fixture2.rule) is PendingActionValidation.Invalid)
    }

    @Test fun authorizationIsBoundToRuleAndAction() {
        val fixture = fixture(); val action = created(fixture, StrictModeActionDescriptor.Pause(fixture.rule.id, 5_000, "break")); fixture.store.markAuthorized(action.id)
        assertTrue(fixture.store.validateForConfirmation(action.id, fixture.rule.copy(id = "other")) is PendingActionValidation.Invalid)
    }

    @Test fun pauseDeleteDisableUpdateAndReplacementUseTypedDescriptors() {
        val descriptors = listOf<StrictModeActionDescriptor>(
            StrictModeActionDescriptor.Pause("rule", 5_000, "break"), StrictModeActionDescriptor.Delete("rule"),
            StrictModeActionDescriptor.Disable("rule"), StrictModeActionDescriptor.Update("rule", base()),
            StrictModeActionDescriptor.ReplaceMethod("rule", StrictModeProtectionMethod.AccountabilityPin)
        )
        val expected = PendingStrictModeActionType.entries
        descriptors.forEachIndexed { index, descriptor ->
            val fixture = fixture(); val action = created(fixture, descriptor)
            assertEquals(expected[index], action.actionType)
        }
    }

    private fun assertComparison(proposed: EarnItRuleStore.Rule, expected: RestrictionClassification) = assertEquals(expected, RuleRestrictionPolicy.compare(base(), proposed).classification)
    private fun app(id: String) = EarnItRuleStore.RuleApp(id, id)
    private fun requirement(id: String, minutes: Long) = EarnItRuleStore.RuleRequirement(app(id), minutes * 60)
    private fun base() = EarnItRuleStore.Rule("rule", "duo", "Duo", listOf(app("ig"), app("snap")), 2, setOf(1,2,3,4,5), 0, 1_440, productiveApps = listOf(app("duo")))
    private fun complete() = base().copy(productivePackage = "", productiveName = "", type = EarnItRuleStore.RuleType.CompleteToUnlock, productiveApps = emptyList(), requirements = listOf(requirement("duo", 10)))
    private fun scheduled() = complete().copy(type = EarnItRuleStore.RuleType.ScheduledBlock, requirements = emptyList())
    private fun activeConfig(ruleId: String) = RuleStrictModeConfiguration(
        ruleId = ruleId,
        lifecycle = RuleStrictModeLifecycle.Active,
        protectionMethod = StrictModeProtectionMethod.Countdown,
        activationRequestedAtMillis = 0,
        activeFromMillis = 0,
        deactivationWaitMillis = RuleStrictModeStore.DEFAULT_COUNTDOWN_MILLIS,
        configurationVersion = 1,
        createdAtMillis = 0,
        updatedAtMillis = 0
    )

    private data class Fixture(val store: RuleStrictModeStore, val rule: EarnItRuleStore.Rule, val persistence: MemoryPersistence)
    private fun fixture(replaceNow: Long? = null): Fixture {
        var now = 1_000L; val persistence = MemoryPersistence(); val rule = base()
        val setup = RuleStrictModeStore(persistence) { now }; setup.requestActivation(rule.id, StrictModeProtectionMethod.Charger, 0); setup.configuration(rule.id)
        if (replaceNow != null) now = replaceNow + 1_000L
        return Fixture(RuleStrictModeStore(persistence) { now }, rule, persistence)
    }
    private fun Fixture.storePersistence() = persistence
    private class CountdownFixture(
        val store: RuleStrictModeStore,
        val rule: EarnItRuleStore.Rule,
        val persistence: MemoryPersistence,
        private val clock: TestClock
    ) {
        fun now() = clock.value
        fun advance(millis: Long) { clock.value += millis }
    }
    private data class TestClock(var value: Long)
    private data class GlobalFixture(
        val store: RuleStrictModeStore,
        val persistence: MemoryPersistence,
        val clock: TestClock
    )
    private fun globalFixture(): GlobalFixture {
        val clock = TestClock(1_000L)
        val persistence = MemoryPersistence()
        val store = RuleStrictModeStore(persistence) { clock.value }
        store.migrateToGlobal(StrictModeState())
        store.requestGlobalActivation(StrictModeProtectionMethod.Countdown, 0L, RuleStrictModeStore.DEFAULT_COUNTDOWN_MILLIS)
        store.globalConfiguration()
        return GlobalFixture(store, persistence, clock)
    }
    private fun countdownFixture(): CountdownFixture {
        val clock = TestClock(1_000L)
        val persistence = MemoryPersistence()
        val rule = base()
        val store = RuleStrictModeStore(persistence) { clock.value }
        store.requestActivation(rule.id, StrictModeProtectionMethod.Countdown, 0L)
        store.configuration(rule.id)
        return CountdownFixture(store, rule, persistence, clock)
    }
    private fun created(fixture: Fixture, descriptor: StrictModeActionDescriptor) = (fixture.store.createPendingAction(fixture.rule, descriptor) as PendingActionCreationResult.Created).action
    private fun encodedInvalidConfig() = listOf("rule", "Active", "", "1000", "2000", "1", "1000", "1000").joinToString("\u001F")

    private class MemoryPersistence(
        var configurations: String? = null,
        var pending: String? = null
    ) : StrictModeFoundationPersistence {
        override fun readConfigurations() = configurations
        override fun writeConfigurations(value: String) { configurations = value }
        override fun readPendingActions() = pending
        override fun writePendingActions(value: String) { pending = value }
    }

    private val completeMethods = StrictModeAuthorizationMethodProvider { method ->
        object : StrictModeAuthorizationMethodHandler {
            override val method = method
            override fun isConfigurationComplete(configuration: RuleStrictModeConfiguration) = true
            override fun begin(request: PendingStrictModeAction) = StrictModeAuthorizationStatus.Authorized
            override fun restore(request: PendingStrictModeAction) = request.authorizationStatus
            override fun cancel(request: PendingStrictModeAction) = Unit
        }
    }
}
