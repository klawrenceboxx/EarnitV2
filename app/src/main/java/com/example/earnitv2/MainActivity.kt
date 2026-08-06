package com.kaleel.earnitv2

import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.posthog.PostHog
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kaleel.earnitv2.ui.theme.EarnitV2Theme
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import java.time.LocalDate

data class RuleDashboardState(
    val rule: EarnItRuleStore.Rule,
    val productiveUsageSeconds: Long,
    val remainingRewardSeconds: Long,
    val requirementProgressSeconds: Map<String, Long> = emptyMap()
)

internal enum class RuleBuilderEntryContext {
    Create,
    Edit
}

internal enum class RuleBuilderExitTarget {
    RuleTypeSelection,
    RuleDetail
}

internal data class RuleBuilderExitDestination(
    val target: RuleBuilderExitTarget,
    val ruleDetailId: String? = null
)

internal enum class BlockedReviewAction { ViewStrictMode, CancelRequest, SystemBack }

internal data class BlockedReviewNavigation(
    val affectedRuleId: String?,
    val discardUnsavedEdit: Boolean,
    val removePendingRequest: Boolean,
    val openStrictMode: Boolean,
    val returnToRuleDetail: Boolean
)

internal fun blockedReviewNavigation(
    action: BlockedReviewAction,
    pendingRuleId: String?,
    editingRuleId: String?
): BlockedReviewNavigation {
    val ruleId = pendingRuleId ?: editingRuleId
    return when (action) {
        BlockedReviewAction.ViewStrictMode -> BlockedReviewNavigation(ruleId, true, false, true, false)
        BlockedReviewAction.CancelRequest, BlockedReviewAction.SystemBack -> BlockedReviewNavigation(ruleId, true, true, false, true)
    }
}

internal enum class StrictModeReturnTarget { Settings, RuleDetail, Home }

internal fun strictModeReturnTarget(returnToSettings: Boolean, returnRuleId: String?): StrictModeReturnTarget = when {
    returnToSettings -> StrictModeReturnTarget.Settings
    returnRuleId != null -> StrictModeReturnTarget.RuleDetail
    else -> StrictModeReturnTarget.Home
}

internal fun firstStageBuilderExitDestination(
    entryContext: RuleBuilderEntryContext?,
    editingRuleId: String?
): RuleBuilderExitDestination {
    return when (entryContext) {
        RuleBuilderEntryContext.Edit -> RuleBuilderExitDestination(
            target = RuleBuilderExitTarget.RuleDetail,
            ruleDetailId = editingRuleId
        )
        RuleBuilderEntryContext.Create,
        null -> RuleBuilderExitDestination(target = RuleBuilderExitTarget.RuleTypeSelection)
    }
}

internal fun unavailableRequirementAppPackages(
    requirements: List<EarnItRuleStore.RuleRequirement>,
    editingIndex: Int?
): Set<String> {
    val editingPackage = editingIndex?.let { requirements.getOrNull(it)?.app?.packageName }
    return requirements.map { it.app.packageName }.filterNot { it == editingPackage }.toSet()
}

class MainActivity : ComponentActivity() {
    private val feedbackViewModel: FeedbackViewModel by viewModels()
    private var feedbackOpen by mutableStateOf(false)
    private var feedbackCrashPrompt by mutableStateOf<CrashDiagnostics?>(null)
    private lateinit var feedbackPreferences: FeedbackPreferences
    private var shakeSettingError by mutableStateOf<String?>(null)
    private var activityResumed = false
    private lateinit var shakeController: ForegroundShakeController
    private var rules by mutableStateOf(emptyList<EarnItRuleStore.Rule>())
    private var ruleStates by mutableStateOf(emptyList<RuleDashboardState>())
    private var pauseExpirations by mutableStateOf(emptyMap<String, Long>())
    private lateinit var strictModeStore: StrictModeStore
    private var strictModeState by mutableStateOf(StrictModeState())
    private lateinit var ruleStrictModeStore: GlobalStrictModeStore
    private lateinit var strictModePinStore: StrictModePinStore
    private var globalStrictModeConfiguration by mutableStateOf<GlobalStrictModeConfiguration?>(null)
    private var strictModeReturnRuleId by mutableStateOf<String?>(null)
    private var strictModeReturnToSettings by mutableStateOf(false)
    private var pendingStrictModeAction by mutableStateOf<PendingStrictModeAction?>(null)
    private var chargerSession by mutableStateOf<ChargerAuthorizationSession?>(null)
    private var chargingState by mutableStateOf(ChargingState(false, false))
    private lateinit var chargingStateObserver: ChargingStateObserver
    private val chargingListener = ChargingStateListener { state ->
        chargingState = state
        chargerSession = ruleStrictModeStore.reconcileActiveChargerSession(state)
        refreshStrictModeState()
    }
    private var strictModeActionMessage by mutableStateOf("Protection method not available yet. Your Rule was not changed.")
    private var editingRuleTemplate by mutableStateOf<EarnItRuleStore.Rule?>(null)
    private var builderEntryContext by mutableStateOf<RuleBuilderEntryContext?>(null)
    private var builderReturnRuleDetailId by mutableStateOf<String?>(null)
    private var launchableApps by mutableStateOf(emptyList<EarnItRuleStore.LaunchableApp>())
    private var appLoadInProgress = false
    private var appListLoading by mutableStateOf(false)
    private var appListLoadedAtMillis = 0L
    private var selectedProductivePackage by mutableStateOf("")
    private var selectedProductivePackages by mutableStateOf(emptySet<String>())
    private var selectedBlockedPackages by mutableStateOf(emptySet<String>())
    private var selectedRequirements by mutableStateOf(emptyList<EarnItRuleStore.RuleRequirement>())
    private var requirementPickerOpen by mutableStateOf(false)
    private var requirementSearch by mutableStateOf("")
    private var selectedRequirementPackage by mutableStateOf<String?>(null)
    private var requirementPickerOriginalPackage by mutableStateOf<String?>(null)
    private var selectedRequirementMinutes by mutableStateOf(10)
    private var editingRequirementIndex by mutableStateOf<Int?>(null)
    private var selectedRatio by mutableStateOf(1)
    private var selectedActiveDays by mutableStateOf(EarnItRuleStore.allDays.toSet())
    private var selectedStartMinute by mutableStateOf(0)
    private var selectedEndMinute by mutableStateOf(1_440)
    private var selectedTimeWindows by mutableStateOf(listOf(EarnItRuleStore.TimeWindow(0, 1_440)))
    private var scheduleWindowEditorOpen by mutableStateOf(false)
    private var editingScheduleWindowIndex by mutableStateOf<Int?>(null)
    private var scheduleEditorStartMinute by mutableStateOf(9 * 60)
    private var scheduleEditorEndMinute by mutableStateOf(17 * 60)
    private var productivePickerOpen by mutableStateOf(false)
    private var blockedPickerOpen by mutableStateOf(false)
    private var blockedPickerOriginalPackages by mutableStateOf<Set<String>?>(null)
    private var selectedBlockedDomains by mutableStateOf<List<String>>(emptyList())
    private var blockedPickerOriginalDomains by mutableStateOf<List<String>?>(null)
    private var productiveSearch by mutableStateOf("")
    private var blockedSearch by mutableStateOf("")
    private var usageAccessGranted by mutableStateOf(false)
    private var settingsLaunchInProgress = false
    private var usageStatusMessage by mutableStateOf("")
    private var ruleStatusMessage by mutableStateOf("")
    private var accessibilityServiceEnabled by mutableStateOf(false)
    private var manageRulesOpen by mutableStateOf(false)
    private var selectedRuleDetailId by mutableStateOf<String?>(null)
    private var settingsOpen by mutableStateOf(false)
    private var analyticsOpen by mutableStateOf(false)
    private var franklinDashboardOpen by mutableStateOf(false)
    private var franklinCalendarOpen by mutableStateOf(false)
    private var analyticsRange by mutableStateOf(defaultAnalyticsRange())
    private var analyticsSelectedDate by mutableStateOf(LocalDate.now())
    private var analyticsState by mutableStateOf<AnalyticsUiState>(AnalyticsUiState.Loading)
    private var analyticsInsights by mutableStateOf(emptyList<InsightCandidate>())
    private var analyticsAppPackage by mutableStateOf<String?>(null)
    private val analyticsCache = mutableMapOf<Pair<AnalyticsRange, LocalDate>, Pair<AnalyticsSummary, List<InsightCandidate>>>()
    private var strictModeOpen by mutableStateOf(false)
    private var strictModeBlockedActionOpen by mutableStateOf(false)
    private var ruleTypeSelectionOpen by mutableStateOf(false)
    private var unavailableRuleType by mutableStateOf<RuleTypeOption?>(null)
    private lateinit var onboardingStore: EarnItOnboardingStore
    private var onboardingActive by mutableStateOf(false)
    private var onboardingStep by mutableStateOf(OnboardingStep.Value)
    private var builderStep by mutableStateOf(RuleBuilderStep.Earn)
    private var deepWorkSession by mutableStateOf(DeepWorkSession())
    private var deepWorkSetupOpen by mutableStateOf(false)
    private lateinit var entitlementRepository: SharedPreferencesEntitlementRepository
    private lateinit var purchaseProvider: LocalPurchaseProvider
    private val subscriptionConfig = SubscriptionConfig.Placeholder
    private var proFlowState by mutableStateOf<ProFlowState?>(null)
    private var premiumSimulatorOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analyticsSelectedDate = savedInstanceState?.getString(STATE_ANALYTICS_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        enableEdgeToEdge()
        CrashMarkerStore.install(this)
        feedbackPreferences = FeedbackPreferences(this)
        shakeController = ForegroundShakeController(this) {
            runOnUiThread {
                if (!feedbackOpen && !onboardingActive) openFeedback(FeedbackEntrySource.SHAKE)
            }
        }
        feedbackCrashPrompt = CrashMarkerStore.consume(this)
        entitlementRepository = SharedPreferencesEntitlementRepository(
            context = this,
            allowDebugOverrides = BuildConfig.ENABLE_ENTITLEMENT_SIMULATOR,
            betaEntitlement = BuildConfig.GRANT_BETA_ENTITLEMENT
        )
        purchaseProvider = LocalPurchaseProvider(
            config = subscriptionConfig,
            entitlementController = entitlementRepository,
            simulationEnabled = BuildConfig.ENABLE_ENTITLEMENT_SIMULATOR
        )
        strictModeStore = StrictModeStore(SharedPreferencesStrictModePersistence(this))
        chargingStateObserver = AndroidChargingStateObserver(applicationContext)
        strictModePinStore = SharedPreferencesStrictModePinStore(this)
        ruleStrictModeStore = GlobalStrictModeStore(
            persistence = SharedPreferencesStrictModeFoundationPersistence(this),
            pinStore = strictModePinStore
        )
        chargingState = chargingStateObserver.currentState()
        deepWorkSession = DeepWorkStore.load(this)
        onboardingStore = EarnItOnboardingStore(this)
        val onboardingSeen = onboardingStore.initialize(EarnItRuleStore.hasDurablePriorUse(this))
        EarnItRuleStore.reconcileForEntitlement(
            this,
            FeatureAccessPolicy(entitlementRepository.state.value)
        )
        val legacyStrictMode = strictModeStore.state()
        val migratedGlobal = ruleStrictModeStore.migrateToGlobal(legacyStrictMode)
        if (migratedGlobal.lifecycle in setOf(RuleStrictModeLifecycle.DeactivationCounting, RuleStrictModeLifecycle.DeactivationReady) &&
            ruleStrictModeStore.activePendingAction(GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID) == null
        ) ruleStrictModeStore.beginGlobalCountdownDeactivation()
        onboardingStep = onboardingStore.step()
        onboardingActive = !onboardingSeen || (isDebugBuild() && onboardingStore.replayActive())
        refreshDashboardState()
        reconcileOnboardingWithPermissions()
        val restorablePendingActions = ruleStrictModeStore.pendingActions().filter {
            it.authorizationStatus !in setOf(
                StrictModeAuthorizationStatus.Consumed,
                StrictModeAuthorizationStatus.Cancelled,
                StrictModeAuthorizationStatus.Expired,
                StrictModeAuthorizationStatus.Invalid
            )
        }
        val obsoleteGlobalChargerDeactivation = restorablePendingActions.firstOrNull {
            it.ruleId == GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID &&
                it.actionType == PendingStrictModeActionType.DisableStrictMode &&
                it.authorizationMethod == StrictModeProtectionMethod.Charger
        }
        if (obsoleteGlobalChargerDeactivation != null) {
            ruleStrictModeStore.cancelGlobalDeactivation()
            refreshStrictModeState()
        }
        val navigablePendingActions = restorablePendingActions - listOfNotNull(obsoleteGlobalChargerDeactivation).toSet()
        (navigablePendingActions.firstOrNull { it.ruleId == GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID }
            ?: navigablePendingActions.firstOrNull())?.let { restored ->
            pendingStrictModeAction = restored
            strictModeReturnRuleId = restored.ruleId.takeUnless { it == GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID }
            refreshStrictModeState()
            val restoredLifecycle = ruleStrictModeStore.globalConfiguration()?.lifecycle
            if (restored.authorizationMethod == StrictModeProtectionMethod.Charger ||
                (restored.actionType == PendingStrictModeActionType.DisableStrictMode &&
                    restoredLifecycle in setOf(RuleStrictModeLifecycle.DeactivationCounting, RuleStrictModeLifecycle.DeactivationReady))) {
                if (restored.authorizationMethod == StrictModeProtectionMethod.Charger) {
                    chargerSession = ruleStrictModeStore.beginOrRestoreCharger(restored.id, chargingState)
                }
                strictModeOpen = true
            } else {
                strictModeActionMessage = "A protected change is still in progress. Protection method not available yet; your Rule has not changed."
                strictModeBlockedActionOpen = true
            }
        }
        handleNavigationIntent(intent)
        setContent {
            EarnitV2Theme {
                val entitlementState by entitlementRepository.state.collectAsState()
                val purchaseState by purchaseProvider.state.collectAsState()
                val shakeEnabled by feedbackPreferences.shakeEnabled.collectAsStateWithLifecycle()
                val featurePolicy = FeatureAccessPolicy(entitlementState)
                LaunchedEffect(shakeEnabled, feedbackOpen) {
                    syncShakeController(shakeEnabled)
                }
                LaunchedEffect(entitlementState) {
                    EarnItRuleStore.reconcileForEntitlement(this@MainActivity, featurePolicy)
                    refreshDashboardState()
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (feedbackOpen) {
                        FeedbackScreen(
                            viewModel = feedbackViewModel,
                            strictModeEnabled = strictModeState.lifecycleState.isStrictModeProtecting(),
                            onClose = ::closeFeedback,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (onboardingActive) {
                        EarnItOnboarding(
                            currentStep = onboardingStep,
                            permissions = currentOnboardingPermissions(),
                            onBack = ::backOnboarding,
                            onContinue = ::continueOnboarding,
                            onNotNow = ::leaveOnboardingIncomplete,
                            onOpenUsageAccessSettings = ::openUsageAccessSettings,
                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                            onCreateFirstRule = ::completeFirstLaunchAndCreateRule,
                            onGoHome = ::completeOnboardingAndGoHome,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        Dashboard(
                            ruleStates = ruleStates,
                            editingRule = editingRuleTemplate,
                            apps = launchableApps,
                            appsLoading = appListLoading,
                            selectedProductivePackage = selectedProductivePackage,
                            selectedProductivePackages = selectedProductivePackages,
                            selectedBlockedPackages = selectedBlockedPackages,
                            selectedBlockedDomains = selectedBlockedDomains,
                            selectedRequirements = selectedRequirements,
                            requirementPickerOpen = requirementPickerOpen,
                            requirementSearch = requirementSearch,
                            selectedRequirementPackage = selectedRequirementPackage,
                            selectedRequirementMinutes = selectedRequirementMinutes,
                            editingRequirementIndex = editingRequirementIndex,
                            selectedRatio = selectedRatio,
                            selectedActiveDays = selectedActiveDays,
                            selectedStartMinute = selectedStartMinute,
                            selectedEndMinute = selectedEndMinute,
                            selectedTimeWindows = selectedTimeWindows,
                            scheduleWindowEditorOpen = scheduleWindowEditorOpen,
                            editingScheduleWindowIndex = editingScheduleWindowIndex,
                            scheduleEditorStartMinute = scheduleEditorStartMinute,
                            scheduleEditorEndMinute = scheduleEditorEndMinute,
                            productivePickerOpen = productivePickerOpen,
                            blockedPickerOpen = blockedPickerOpen,
                            productiveSearch = productiveSearch,
                            blockedSearch = blockedSearch,
                            usageAccessGranted = usageAccessGranted,
                            usageStatusMessage = usageStatusMessage,
                            ruleStatusMessage = ruleStatusMessage,
                            accessibilityServiceEnabled = accessibilityServiceEnabled,
                            showDeveloperTools = isDebugBuild(),
                            manageRulesOpen = manageRulesOpen,
                            pauseExpirations = pauseExpirations,
                            selectedRuleDetailId = selectedRuleDetailId,
                            settingsOpen = settingsOpen,
                            analyticsOpen = analyticsOpen,
                            franklinDashboardOpen = franklinDashboardOpen,
                            franklinCalendarOpen = franklinCalendarOpen,
                            analyticsRange = analyticsRange,
                            analyticsState = analyticsState,
                            analyticsInsights = analyticsInsights,
                            analyticsAppPackage = analyticsAppPackage,
                            strictModeOpen = strictModeOpen,
                            strictModeState = strictModeState,
                            globalStrictModeConfiguration = globalStrictModeConfiguration,
                            pendingStrictModeAction = pendingStrictModeAction,
                            chargerSession = chargerSession,
                            chargingState = chargingState,
                            ruleTypeSelectionOpen = ruleTypeSelectionOpen,
                            unavailableRuleType = unavailableRuleType,
                            deepWorkSession = deepWorkSession,
                            deepWorkSetupOpen = deepWorkSetupOpen,
                            entitlementState = entitlementState,
                            featurePolicy = featurePolicy,
                            purchaseState = purchaseState,
                            purchaseProvider = purchaseProvider,
                            subscriptionConfig = subscriptionConfig,
                            proFlowState = proFlowState,
                            onProFlowChange = { proFlowState = it },
                            onCloseProFlow = { proFlowState = null },
                            onPurchasePlan = { plan ->
                                PostHog.capture(
                                    "subscription_purchase_started",
                                    properties = mapOf("plan_id" to plan.id, "billing_period" to plan.billingPeriod.name.lowercase())
                                )
                                purchaseProvider.purchase(plan)
                            },
                            onRestorePurchases = purchaseProvider::restorePurchases,
                            onSimulateEntitlement = entitlementRepository::simulate,
                            onResetEntitlement = entitlementRepository::reset,
                            premiumSimulatorOpen = premiumSimulatorOpen,
                            onOpenPremiumSimulator = { premiumSimulatorOpen = true },
                            onClosePremiumSimulator = { premiumSimulatorOpen = false },
                            onOpenDeepWork = {
                                if (PremiumSessionPolicy.canStartDeepWork(featurePolicy)) {
                                    if (deepWorkSession.phase == DeepWorkPhase.Inactive) deepWorkSetupOpen = true
                                } else {
                                    openProGate(PremiumEntryPoint.DeepWork)
                                }
                            },
                            onDismissDeepWorkSetup = { deepWorkSetupOpen = false },
                            onStartDeepWork = { goal ->
                                if (PremiumSessionPolicy.canStartDeepWork(featurePolicy)) {
                                    deepWorkSetupOpen = false
                                    deepWorkSession = DeepWorkStore.begin(this, goal)
                                    PostHog.capture(
                                        "deep_work_started",
                                        properties = mapOf("has_goal" to (goal != null))
                                    )
                                } else {
                                    deepWorkSetupOpen = false
                                    openProGate(PremiumEntryPoint.DeepWork)
                                }
                            },
                            onActivateDeepWork = { deepWorkSession = DeepWorkStore.activate(this, deepWorkSession) },
                            onContinueDeepWork = { elapsed -> deepWorkSession = DeepWorkStore.continueOpenEnded(this, deepWorkSession, elapsed) },
                            onFinishDeepWork = { elapsed ->
                                val completedSession = deepWorkSession
                                DeepWorkStore.finish(this, completedSession, elapsed)
                                PostHog.capture(
                                    "deep_work_completed",
                                    properties = mapOf(
                                        "elapsed_seconds" to elapsed,
                                        "has_goal" to (completedSession.goalSeconds != null),
                                        "linked_to_rule" to (completedSession.linkedRuleId != null)
                                    )
                                )
                                deepWorkSession = DeepWorkSession()
                                refreshDashboardState()
                            },
                            onOpenUsageAccessSettings = ::openUsageAccessSettings,
                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                            onContinueSetup = ::continueOnboardingSetup,
                            onOpenSettings = { settingsOpen = true },
                            onOpenAnalytics = ::openAnalytics,
                            onCloseAnalytics = { analyticsOpen = false; analyticsAppPackage = null },
                            onAnalyticsRangeChange = ::changeAnalyticsRange,
                            analyticsSelectedDate = analyticsSelectedDate,
                            onAnalyticsDateChange = ::changeAnalyticsDate,
                            onOpenAnalyticsApp = { analyticsAppPackage = it },
                            onBackFromAnalyticsApp = { analyticsAppPackage = null },
                            onCloseSettings = { settingsOpen = false },
                            onOpenBenjaminFranklin = { franklinDashboardOpen = true },
                            onCloseBenjaminFranklin = { franklinDashboardOpen = false },
                            onOpenBenjaminFranklinCalendar = { franklinCalendarOpen = true },
                            onCloseBenjaminFranklinCalendar = { franklinCalendarOpen = false },
                            pendingFeedbackCount = feedbackViewModel.pendingCount(),
                            shakeToReportEnabled = shakeEnabled,
                            shakeSettingError = shakeSettingError,
                            onOpenFeedback = { openFeedback(FeedbackEntrySource.SETTINGS) },
                            onShakeToReportChange = {
                                shakeSettingError = if (feedbackPreferences.setShakeEnabled(it)) {
                                    null
                                } else {
                                    "Couldn't save this setting. Your previous choice was restored."
                                }
                            },
                            onDebugShakeFeedback = { openFeedback(FeedbackEntrySource.SHAKE) },
                            onClearDebugFeedback = { feedbackViewModel.clearDebugQueue() },
                            onReplayOnboarding = ::replayOnboarding,
                            onOpenStrictMode = {
                                refreshStrictModeState()
                                if (!PremiumSessionPolicy.canOpenStrictMode(
                                        featurePolicy,
                                        strictModeState.lifecycleState.isStrictModeProtecting()
                                    )
                                ) {
                                    openProGate(PremiumEntryPoint.StrictMode)
                                } else {
                                    strictModeReturnToSettings = settingsOpen
                                    strictModeReturnRuleId = selectedRuleDetailId
                                    settingsOpen = false
                                    refreshStrictModeState()
                                    strictModeOpen = true
                                }
                            },
                            onCloseStrictMode = {
                                strictModeOpen = false
                                when (strictModeReturnTarget(strictModeReturnToSettings, strictModeReturnRuleId)) {
                                    StrictModeReturnTarget.Settings -> { settingsOpen = true; selectedRuleDetailId = null }
                                    StrictModeReturnTarget.RuleDetail -> { settingsOpen = false; selectedRuleDetailId = strictModeReturnRuleId }
                                    StrictModeReturnTarget.Home -> { settingsOpen = false; selectedRuleDetailId = null }
                                }
                            },
                            onSaveStrictModeConfiguration = ::saveStrictModeConfiguration,
                            onBeginStrictModeActivation = ::beginStrictModeActivation,
                            onCancelStrictModeActivation = ::cancelStrictModeActivation,
                            onBeginStrictModeDeactivation = ::beginStrictModeDeactivation,
                            onConfirmChargerStrictModeDeactivation = ::confirmChargerStrictModeDeactivation,
                            onCancelStrictModeDeactivation = ::cancelStrictModeDeactivation,
                            onConfirmStrictModeDeactivation = ::confirmStrictModeDeactivation,
                            onKeepStrictModeActive = ::keepStrictModeActive,
                            onStrictModeTick = ::refreshStrictModeState,
                            onAuthorizeCharger = ::authorizeCurrentChargerRequest,
                            onVerifyPin = ::verifyCurrentPinRequest,
                            onConfirmProtectedAction = ::confirmProtectedAction,
                            onCancelProtectedRequest = ::cancelProtectedRequest,
                            onRequestStrictModeMethodChange = ::requestStrictModeMethodChange,
                            onStrictModeBlockedAction = ::showStrictModeBlockedAction,
                            onOpenEarnApp = ::openEarnApp,
                            onAddRule = ::startAddingRule,
                            onBackFromRuleTypeSelection = ::closeRuleTypeSelection,
                            onBackFromUnavailableRuleType = { unavailableRuleType = null },
                            onSelectRuleType = ::startRuleType,
                            onEditRule = ::startEditingRule,
                            onToggleRuleEnabled = ::toggleRuleEnabled,
                            onToggleDailyCommitment = ::toggleDailyCommitment,
                            onPauseRuleFor = ::pauseRuleFor,
                            onResumeRule = ::resumeRule,
                            onPauseTimerTick = ::refreshDashboardState,
                            onDeleteRule = ::deleteRule,
                            onToggleManageRules = { manageRulesOpen = !manageRulesOpen },
                            onOpenRuleDetail = ::openRuleDetail,
                            onBackFromRuleDetail = { selectedRuleDetailId = null },
                            onCancelEditingRule = ::exitBuilderFromFirstStage,
                            onOpenProductivePicker = {
                                productivePickerOpen = true
                                refreshLaunchableApps()
                            },
                            onCloseProductivePicker = { productivePickerOpen = false },
                            onOpenBlockedPicker = ::openBlockedPicker,
                            onCloseBlockedPicker = ::saveBlockedPicker,
                            onDismissBlockedPicker = ::dismissBlockedPicker,
                            onProductiveSearchChange = { productiveSearch = it },
                            onBlockedSearchChange = { blockedSearch = it },
                            onSelectProductiveApp = ::selectProductiveApp,
                            onOpenRequirementPicker = ::openRequirementPicker,
                            onCloseRequirementPicker = ::cancelRequirementEditor,
                            onDismissRequirementAppPicker = ::dismissRequirementPicker,
                            onUseRequirementApp = ::useRequirementApp,
                            onRequirementSearchChange = { requirementSearch = it },
                            onSelectRequirementApp = ::selectRequirementApp,
                            onSelectRequirementMinutes = { selectedRequirementMinutes = it },
                            onSaveRequirement = ::saveRequirement,
                            onEditRequirement = ::editRequirement,
                            onDeleteRequirement = ::deleteRequirement,
                            onToggleBlockedApp = ::toggleBlockedApp,
                            onBlockedDomainsChange = { selectedBlockedDomains = it },
                            onSelectRatio = { selectedRatio = it },
                            onToggleActiveDay = ::toggleActiveDay,
                            onSelectActiveDays = { selectedActiveDays = it },
                            onSelectAllDay = ::selectAllDaySchedule,
                            onSetHours = ::setHoursSchedule,
                            onAddTimeWindow = ::addScheduleWindow,
                            onEditTimeWindow = ::editScheduleWindow,
                            onRemoveTimeWindow = ::removeScheduleWindow,
                            onSaveTimeWindow = ::saveScheduleWindow,
                            onCancelTimeWindow = ::cancelScheduleWindow,
                            onEditStartTime = ::showScheduleEditorStartPicker,
                            onEditEndTime = ::showScheduleEditorEndPicker,
                            builderStep = builderStep,
                            onBuilderStepChange = { builderStep = it },
                            onSaveRule = ::saveRule,
                            modifier = Modifier.padding(innerPadding)
                        )
                        if (strictModeBlockedActionOpen) {
                            StrictModeProtectedActionDialog(
                                message = strictModeActionMessage,
                                dismissLabel = if (pendingStrictModeAction != null) "Cancel request" else "Close",
                                onViewStrictMode = {
                                    val navigation = blockedReviewNavigation(
                                        BlockedReviewAction.ViewStrictMode,
                                        pendingStrictModeAction?.ruleId ?: strictModeReturnRuleId,
                                        editingRuleTemplate?.id
                                    )
                                    strictModeBlockedActionOpen = false
                                    if (navigation.discardUnsavedEdit && editingRuleTemplate != null) cancelEditingRule()
                                    selectedRuleDetailId = null
                                    strictModeReturnRuleId = navigation.affectedRuleId
                                    strictModeReturnToSettings = false
                                    refreshStrictModeState()
                                    settingsOpen = false
                                    strictModeOpen = navigation.openStrictMode
                                },
                                onClose = {
                                    val navigation = blockedReviewNavigation(
                                        BlockedReviewAction.CancelRequest,
                                        pendingStrictModeAction?.ruleId,
                                        editingRuleTemplate?.id
                                    )
                                    if (navigation.removePendingRequest) pendingStrictModeAction?.let { ruleStrictModeStore.removeRequest(it.id) }
                                    pendingStrictModeAction = null
                                    strictModeBlockedActionOpen = false
                                    if (navigation.discardUnsavedEdit && editingRuleTemplate != null) cancelEditingRule()
                                    selectedRuleDetailId = if (navigation.returnToRuleDetail) navigation.affectedRuleId else null
                                }
                        )
                    }
                    feedbackCrashPrompt?.let { crash ->
                        CrashFollowUpPrompt(
                            onReview = {
                                feedbackCrashPrompt = null
                                openFeedback(FeedbackEntrySource.CRASH_FOLLOW_UP, crash)
                            },
                            onDismiss = { feedbackCrashPrompt = null }
                        )
                    }
                }
            }
        }
    }

    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        settingsLaunchInProgress = false
        refreshDashboardState()
        reconcileOnboardingWithPermissions()
        if (analyticsOpen) loadAnalytics(forceRefresh = true)
        syncShakeController()
    }

    override fun onPause() {
        activityResumed = false
        if (::shakeController.isInitialized) shakeController.unregister()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_ANALYTICS_DATE, analyticsSelectedDate.toString())
        super.onSaveInstanceState(outState)
    }

    private fun openFeedback(source: FeedbackEntrySource, crash: CrashDiagnostics? = null) {
        val route = when {
            settingsOpen -> "Settings"
            analyticsOpen -> "Analytics"
            selectedRuleDetailId != null -> "Rule detail"
            else -> "Home"
        }
        CrashMarkerStore.saveLastRoute(this, route)
        feedbackViewModel.begin(source, route, crash)
        feedbackOpen = true
        shakeController.unregister()
    }

    private fun closeFeedback() {
        feedbackOpen = false
        shakeController.detector.rearm()
        syncShakeController()
    }

    private fun syncShakeController(enabled: Boolean = feedbackPreferences.shakeEnabled.value) {
        if (!::shakeController.isInitialized || !::feedbackPreferences.isInitialized) return
        if (shouldRegisterShakeDetector(enabled, activityResumed, feedbackOpen)) {
            shakeController.register()
        } else {
            shakeController.unregister()
        }
    }

    override fun onDestroy() {
        if (::feedbackPreferences.isInitialized) feedbackPreferences.close()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        chargingStateObserver.observe(chargingListener)
    }

    override fun onStop() {
        chargingStateObserver.stopObserving(chargingListener)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshDashboardState()
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val requestedRuleId = intent?.getStringExtra(EXTRA_OPEN_RULE_DETAIL_ID)?.takeIf { it.isNotBlank() } ?: return
        intent.removeExtra(EXTRA_OPEN_RULE_DETAIL_ID)
        openRuleDetail(requestedRuleId)
    }

    private fun openRuleDetail(ruleId: String) {
        if (rules.none { it.id == ruleId }) return
        editingRuleTemplate = null
        builderEntryContext = null
        builderReturnRuleDetailId = null
        settingsOpen = false
        strictModeOpen = false
        strictModeBlockedActionOpen = false
        ruleTypeSelectionOpen = false
        unavailableRuleType = null
        deepWorkSetupOpen = false
        selectedRuleDetailId = ruleId
    }

    private fun refreshDashboardState() {
        refreshStrictModeState()
        val savedRules = EarnItRuleStore.getRules(
            this,
            FeatureAccessPolicy(entitlementRepository.state.value)
        )
        pauseExpirations = EarnItPauseStore.pauseExpirations(this)
        rules = savedRules
        refreshLaunchableApps()
        refreshUsageStats(savedRules)
        accessibilityServiceEnabled = isAccessibilityServiceEnabled()
    }

    private fun openAnalytics() {
        analyticsRange = defaultAnalyticsRange()
        analyticsAppPackage = null
        analyticsOpen = true
        loadAnalytics()
    }

    private fun changeAnalyticsRange(range: AnalyticsRange) {
        val decision = analyticsRangeDecision(
            range,
            FeatureAccessPolicy(entitlementRepository.state.value).canUse(PremiumFeature.SevenDayAnalytics)
        )
        if (decision.openPremiumGate) {
            openProGate(PremiumEntryPoint.Analytics)
            return
        }
        val allowedRange = decision.rangeToLoad ?: return
        if (analyticsRange == allowedRange && analyticsState is AnalyticsUiState.Ready) return
        analyticsRange = allowedRange
        loadAnalytics()
    }

    private fun changeAnalyticsDate(date: LocalDate) {
        if (date.isAfter(LocalDate.now()) || date == analyticsSelectedDate) return
        analyticsSelectedDate = date
        analyticsAppPackage = null
        if (analyticsRange != AnalyticsRange.Today) analyticsRange = AnalyticsRange.Today
        loadAnalytics()
    }

    private fun loadAnalytics(forceRefresh: Boolean = false) {
        if (!hasUsageAccess()) {
            analyticsState = AnalyticsUiState.PermissionRequired
            return
        }
        val requestedRange = analyticsRange
        val requestedDate = analyticsSelectedDate
        val cacheKey = requestedRange to requestedDate
        if (!forceRefresh) {
            analyticsCache[cacheKey]?.let { (summary, insights) ->
                analyticsState = AnalyticsUiState.Ready(summary)
                analyticsInsights = insights
                return
            }
        }
        analyticsState = AnalyticsUiState.Loading
        thread(name = "EarnItAnalytics") {
            val result = runCatching { AnalyticsRepository(applicationContext).load(requestedRange, requestedDate) }
            val loaded = result.fold({ AnalyticsUiState.Ready(it) }, { AnalyticsUiState.Unavailable(it.message ?: "Usage history could not be read.") })
            val insights = result.getOrNull()?.let { summary ->
                AnalyticsInsightEngine.generate(summary, AnalyticsInsightHistoryStore.load(applicationContext)).also {
                    AnalyticsInsightHistoryStore.record(applicationContext, it)
                }
            }.orEmpty()
            runOnUiThread {
                result.getOrNull()?.let { analyticsCache[cacheKey] = it to insights }
                if (analyticsRange == requestedRange && analyticsSelectedDate == requestedDate) {
                    analyticsState = loaded
                    analyticsInsights = insights
                }
            }
        }
    }

    private fun refreshStrictModeState() {
        if (::strictModeStore.isInitialized) {
            globalStrictModeConfiguration = ruleStrictModeStore.globalConfiguration()
            ruleStrictModeStore.restoreCountdownAuthorization()
            ruleStrictModeStore.completePinDeactivationIfReady()
            globalStrictModeConfiguration = ruleStrictModeStore.globalConfiguration()
            strictModeState = globalStrictModeConfiguration?.toLegacyStrictModeState() ?: strictModeStore.state()
            pendingStrictModeAction = ruleStrictModeStore.activePendingAction(GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID)
                ?: strictModeReturnRuleId?.let { ruleStrictModeStore.activePendingAction(it) }
            chargerSession = pendingStrictModeAction
                ?.takeIf { it.authorizationMethod == StrictModeProtectionMethod.Charger }
                ?.let { ruleStrictModeStore.beginOrRestoreCharger(it.id, chargingState) }
        }
    }

    private fun saveStrictModeConfiguration(configuration: StrictModeConfiguration) {
        // Setup is a draft until activation; the migrated global record is the only persisted source of truth.
        strictModeState = strictModeState.copy(configuration = configuration)
    }

    private fun beginStrictModeActivation(configuration: StrictModeConfiguration, method: StrictModeProtectionMethod, pin: String?) {
        if (!FeatureAccessPolicy(entitlementRepository.state.value).canUse(PremiumFeature.StrictMode)) {
            strictModeOpen = false
            openProGate(PremiumEntryPoint.StrictMode)
            return
        }
        if (method == StrictModeProtectionMethod.Pin) {
            val characters = pin?.toCharArray() ?: return
            val saved = try { strictModePinStore.save(characters) } finally { characters.fill('\u0000') }
            if (!saved) {
                strictModeActionMessage = "Choose a numeric PIN between 4 and 8 digits."
                strictModeBlockedActionOpen = true
                return
            }
        }
        ruleStrictModeStore.requestGlobalActivation(
            method = method,
            delayMillis = StrictModeStore.ACTIVATION_GRACE_MILLIS,
            deactivationWaitMillis = configuration.deactivationCountdownMillis.takeIf {
                method in setOf(StrictModeProtectionMethod.Countdown, StrictModeProtectionMethod.Pin)
            }
        )
        refreshStrictModeState()
    }

    private fun cancelStrictModeActivation() {
        ruleStrictModeStore.cancelGlobalActivation()
        refreshStrictModeState()
    }

    private fun beginStrictModeDeactivation() {
        chargingState = chargingStateObserver.currentState()
        when (val result = ruleStrictModeStore.beginGlobalDeactivation(chargingState)) {
            is PendingActionCreationResult.Created -> {
                pendingStrictModeAction = result.action
                if (result.action.authorizationMethod == StrictModeProtectionMethod.Charger) {
                    chargerSession = ruleStrictModeStore.beginOrRestoreCharger(result.action.id, chargingState)
                }
            }
            is PendingActionCreationResult.AlreadyPending -> {
                pendingStrictModeAction = result.action
                if (result.action.authorizationMethod == StrictModeProtectionMethod.Charger) {
                    chargerSession = ruleStrictModeStore.beginOrRestoreCharger(result.action.id, chargingState)
                }
            }
            is PendingActionCreationResult.Rejected -> {
                strictModeActionMessage = result.message
                strictModeBlockedActionOpen = true
            }
        }
        refreshStrictModeState()
    }

    private fun confirmChargerStrictModeDeactivation(): PendingActionValidation {
        chargingState = chargingStateObserver.currentState()
        val result = ruleStrictModeStore.confirmGlobalChargerDeactivation(chargingState)
        if (result is PendingActionValidation.Invalid) {
            pendingStrictModeAction?.takeIf {
                it.actionType == PendingStrictModeActionType.DisableStrictMode &&
                    it.authorizationMethod == StrictModeProtectionMethod.Charger
            }?.let { ruleStrictModeStore.cancelRequest(it.id) }
        }
        pendingStrictModeAction = null
        chargerSession = null
        refreshStrictModeState()
        return result
    }

    private fun cancelStrictModeDeactivation() {
        ruleStrictModeStore.cancelGlobalDeactivation()
        pendingStrictModeAction = null
        chargerSession = null
        refreshStrictModeState()
    }

    private fun confirmStrictModeDeactivation() {
        when (val result = ruleStrictModeStore.confirmGlobalDeactivation()) {
            is PendingActionValidation.Valid -> {
                pendingStrictModeAction = null
                chargerSession = null
                refreshStrictModeState()
            }
            is PendingActionValidation.Invalid -> {
                strictModeActionMessage = result.message
                strictModeBlockedActionOpen = true
            }
        }
    }

    private fun keepStrictModeActive() {
        ruleStrictModeStore.keepGlobalStrictModeActive()
        pendingStrictModeAction = null
        chargerSession = null
        refreshStrictModeState()
    }

    private fun authorizeCurrentChargerRequest() {
        val pending = pendingStrictModeAction ?: return
        chargingState = chargingStateObserver.currentState()
        when (val result = ruleStrictModeStore.authorizeCharger(pending.id, chargingState)) {
            is PendingActionValidation.Valid -> {
                pendingStrictModeAction = result.action
                chargerSession = ruleStrictModeStore.beginOrRestoreCharger(result.action.id, chargingState)
                refreshStrictModeState()
            }
            is PendingActionValidation.Invalid -> {
                strictModeActionMessage = result.message
                strictModeBlockedActionOpen = true
            }
        }
    }

    private fun verifyCurrentPinRequest(pin: String): PinVerificationResult {
        val pending = pendingStrictModeAction
            ?: return PinVerificationResult.Rejected("This request no longer exists. Begin again.")
        val characters = pin.toCharArray()
        val result = try { ruleStrictModeStore.verifyPin(pending.id, characters) } finally { characters.fill('\u0000') }
        if (result is PinVerificationResult.Verified) refreshStrictModeState()
        return result
    }

    private fun confirmProtectedAction() {
        val pending = pendingStrictModeAction ?: return
        if (pending.actionType == PendingStrictModeActionType.DisableStrictMode) {
            confirmStrictModeDeactivation()
            return
        }
        if (pending.actionType == PendingStrictModeActionType.ReplaceProtectionMethod) {
            when (val validation = ruleStrictModeStore.validateGlobalForConfirmation(pending.id)) {
                is PendingActionValidation.Invalid -> {
                    strictModeActionMessage = validation.message
                    strictModeBlockedActionOpen = true
                }
                is PendingActionValidation.Valid -> {
                    val descriptor = validation.action.descriptor as StrictModeActionDescriptor.ReplaceMethod
                    ruleStrictModeStore.replaceMethodAfterConfirmation(
                        GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID,
                        descriptor.newMethod,
                        descriptor.newDurationMillis,
                        exceptRequestId = pending.id
                    )
                    ruleStrictModeStore.consume(pending.id)
                    pendingStrictModeAction = null
                    chargerSession = null
                    refreshStrictModeState()
                }
            }
            return
        }
        val rule = rules.firstOrNull { it.id == pending.ruleId }
        when (val validation = ruleStrictModeStore.validateForConfirmation(pending.id, rule)) {
            is PendingActionValidation.Invalid -> {
                strictModeActionMessage = validation.message
                strictModeBlockedActionOpen = true
            }
            is PendingActionValidation.Valid -> {
                when (val descriptor = validation.action.descriptor) {
                    is StrictModeActionDescriptor.Update -> EarnItRuleStore.saveRule(this, descriptor.proposedRule)
                    is StrictModeActionDescriptor.Pause -> {
                        EarnItPauseStore.pauseUntil(this, descriptor.ruleId, System.currentTimeMillis() + descriptor.durationMillis, descriptor.reason)
                        EarnItRuleStore.setRuleEnabled(this, descriptor.ruleId, false)
                    }
                    is StrictModeActionDescriptor.Delete -> {
                        EarnItRuleStore.deleteRule(this, descriptor.ruleId)
                        EarnItPauseStore.clearPause(this, descriptor.ruleId)
                    }
                    else -> return
                }
                ruleStrictModeStore.consume(pending.id)
                pendingStrictModeAction = null
                chargerSession = null
                strictModeOpen = false
                editingRuleTemplate = null
                builderEntryContext = null
                selectedRuleDetailId = pending.ruleId.takeIf { rules.any { rule -> rule.id == it } }
                refreshDashboardState()
            }
        }
    }

    private fun cancelProtectedRequest() {
        val pending = pendingStrictModeAction
        if (pending?.actionType == PendingStrictModeActionType.DisableStrictMode) {
            cancelStrictModeDeactivation()
        } else {
            pending?.let { ruleStrictModeStore.cancelRequest(it.id) }
            if (pending?.ruleId == GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID) ruleStrictModeStore.cancelGlobalDeactivation()
            pendingStrictModeAction = null
            chargerSession = null
            strictModeOpen = false
            if (editingRuleTemplate != null) cancelEditingRule()
            selectedRuleDetailId = strictModeReturnRuleId
            refreshDashboardState()
        }
    }

    private fun requestStrictModeMethodChange(method: StrictModeProtectionMethod, durationMillis: Long?, pin: String?) {
        if (method == StrictModeProtectionMethod.Pin) {
            val characters = pin?.toCharArray() ?: return
            val saved = try { strictModePinStore.save(characters) } finally { characters.fill('\u0000') }
            if (!saved) {
                strictModeActionMessage = "Choose a numeric PIN between 4 and 8 digits."
                strictModeBlockedActionOpen = true
                return
            }
        }
        chargingState = chargingStateObserver.currentState()
        when (val result = ruleStrictModeStore.requestGlobalMethodChange(method, durationMillis, chargingState)) {
            is StrictModeMethodChangeResult.Applied -> {
                globalStrictModeConfiguration = result.configuration
                refreshStrictModeState()
            }
            is StrictModeMethodChangeResult.AuthorizationRequired -> {
                pendingStrictModeAction = result.action
                chargerSession = result.action.takeIf { it.authorizationMethod == StrictModeProtectionMethod.Charger }
                    ?.let { ruleStrictModeStore.beginOrRestoreCharger(it.id, chargingState) }
                refreshStrictModeState()
            }
            is StrictModeMethodChangeResult.Rejected -> {
                strictModeActionMessage = result.message
                strictModeBlockedActionOpen = true
            }
        }
    }

    private fun refreshLaunchableApps(force: Boolean = false) {
        if (appLoadInProgress) return
        val now = System.currentTimeMillis()
        val stale = now - appListLoadedAtMillis > APP_LIST_REFRESH_INTERVAL_MS
        if (!force && launchableApps.isNotEmpty() && !stale) return

        appLoadInProgress = true
        appListLoading = launchableApps.isEmpty()
        thread(name = "EarnItAppLoader") {
            val loadedApps = EarnItRuleStore.launchableApps(this)
            runOnUiThread {
                launchableApps = loadedApps
                appListLoadedAtMillis = System.currentTimeMillis()
                appLoadInProgress = false
                appListLoading = false
            }
        }
    }

    private fun refreshUsageStats(savedRules: List<EarnItRuleStore.Rule>) {
        usageAccessGranted = hasUsageAccess()
        val enabledCount = savedRules.count { it.enabled }
        ruleStatusMessage = when {
            savedRules.isEmpty() -> "No rules saved. Add a rule to start."
            savedRules.size == 1 -> "1 rule saved. ${if (enabledCount == 1) "Enabled" else "Disabled"}."
            else -> "${savedRules.size} rules saved. $enabledCount enabled."
        }

        if (!usageAccessGranted) {
            ruleStates = savedRules.map { rule ->
                RuleDashboardState(
                    rule = rule,
                    productiveUsageSeconds = 0L,
                    remainingRewardSeconds = RewardLedger.snapshot(this, rule).remainingRewardSeconds,
                    requirementProgressSeconds = if (rule.type == EarnItRuleStore.RuleType.CompleteToUnlock) {
                        RewardLedger.completionProgress(this, rule)
                    } else {
                        emptyMap()
                    }
                )
            }
            usageStatusMessage = "Usage Access is off."
            return
        }

        ruleStates = savedRules.map { rule ->
            val productiveSeconds = if (rule.enabled) getTodayActiveProductiveUsageSeconds(rule) else 0L
            val requirementProgress = if (rule.enabled && rule.type == EarnItRuleStore.RuleType.CompleteToUnlock) {
                RewardLedger.creditCompletionProgress(
                    context = this,
                    rule = rule,
                    usageStatsManager = getSystemService(UsageStatsManager::class.java),
                    includeTrackedHandoffs = true
                )
            } else if (rule.type == EarnItRuleStore.RuleType.CompleteToUnlock) {
                RewardLedger.completionProgress(this, rule)
            } else {
                emptyMap()
            }
            val snapshot = if (rule.enabled) {
                val deepWork = DeepWorkStore.load(this)
                if (!(deepWork.phase == DeepWorkPhase.Active && deepWork.linkedRuleId == rule.id)) {
                    RewardLedger.creditProductiveUsage(this, rule, productiveSeconds)
                } else {
                    RewardLedger.snapshot(this, rule)
                }
            } else {
                RewardLedger.snapshot(this, rule)
            }
            RuleDashboardState(
                rule = rule,
                productiveUsageSeconds = productiveSeconds,
                remainingRewardSeconds = snapshot.remainingRewardSeconds,
                requirementProgressSeconds = requirementProgress
            )
        }
        usageStatusMessage = if (savedRules.any { it.enabled }) {
            "Tracking enabled rules today. Android usage data can be delayed."
        } else {
            "All rules are disabled."
        }
    }

    private fun startAddingRule() {
        if (!currentOnboardingPermissions().isReady) {
            continueOnboardingSetup()
            return
        }
        settingsOpen = false
        analyticsOpen = false
        analyticsAppPackage = null
        selectedRuleDetailId = null
        builderStep = RuleBuilderStep.Earn
        manageRulesOpen = false
        unavailableRuleType = null
        builderEntryContext = null
        builderReturnRuleDetailId = null
        ruleTypeSelectionOpen = true
    }

    private fun closeRuleTypeSelection() {
        unavailableRuleType = null
        ruleTypeSelectionOpen = false
    }

    private fun startRuleType(ruleType: EarnItRuleStore.RuleType) {
        ruleTypeSelectionOpen = false
        unavailableRuleType = null
        startBuilder(
            rule = EarnItRuleStore.newRuleFromDefault(this, ruleType),
            entryContext = RuleBuilderEntryContext.Create
        )
    }

    private fun startEditingRule(rule: EarnItRuleStore.Rule) {
        startBuilder(rule = rule, entryContext = RuleBuilderEntryContext.Edit)
    }

    private fun startBuilder(rule: EarnItRuleStore.Rule, entryContext: RuleBuilderEntryContext) {
        if (entryContext == RuleBuilderEntryContext.Edit) refreshStrictModeState()
        settingsOpen = false
        selectedRuleDetailId = null
        ruleTypeSelectionOpen = false
        unavailableRuleType = null
        builderStep = if (rule.type == EarnItRuleStore.RuleType.ScheduledBlock) RuleBuilderStep.Reward else RuleBuilderStep.Earn
        manageRulesOpen = false
        editingRuleTemplate = rule
        builderEntryContext = entryContext
        builderReturnRuleDetailId = if (entryContext == RuleBuilderEntryContext.Edit) rule.id else null
        val hasEarnSelection = rule.productiveApps.isNotEmpty() || rule.productivePackage.isNotBlank()
        selectedProductivePackage = if (hasEarnSelection) {
            rule.earnApps.firstOrNull()?.packageName.orEmpty()
        } else {
            ""
        }
        selectedProductivePackages = if (hasEarnSelection) {
            rule.earnApps.map { it.packageName }.filter { it.isNotBlank() }.toSet()
        } else {
            emptySet()
        }
        selectedBlockedPackages = rule.blockedApps.map { it.packageName }.toSet()
        selectedBlockedDomains = rule.normalizedBlockedDomains
        selectedRequirements = rule.requirements
        requirementPickerOpen = false
        requirementSearch = ""
        selectedRequirementPackage = null
        requirementPickerOriginalPackage = null
        selectedRequirementMinutes = 10
        editingRequirementIndex = null
        selectedRatio = rule.rewardSecondsPerProductiveSecond
        selectedActiveDays = rule.activeDays
        selectedTimeWindows = rule.effectiveTimeWindows
        selectedStartMinute = selectedTimeWindows.first().startMinute
        selectedEndMinute = selectedTimeWindows.first().endMinute
        scheduleWindowEditorOpen = false
        editingScheduleWindowIndex = null
        scheduleEditorStartMinute = 9 * 60
        scheduleEditorEndMinute = 17 * 60
        productivePickerOpen = false
        blockedPickerOpen = false
        blockedPickerOriginalPackages = null
        blockedPickerOriginalDomains = null
        productiveSearch = ""
        blockedSearch = ""
    }

    private fun cancelEditingRule() {
        builderStep = RuleBuilderStep.Earn
        editingRuleTemplate = null
        builderEntryContext = null
        builderReturnRuleDetailId = null
        unavailableRuleType = null
        productivePickerOpen = false
        blockedPickerOpen = false
        blockedPickerOriginalPackages = null
        blockedPickerOriginalDomains = null
        requirementPickerOpen = false
        cancelScheduleWindow()
    }

    private fun exitBuilderFromFirstStage() {
        val destination = firstStageBuilderExitDestination(
            entryContext = builderEntryContext,
            editingRuleId = builderReturnRuleDetailId ?: editingRuleTemplate?.id
        )
        cancelEditingRule()
        when (destination.target) {
            RuleBuilderExitTarget.RuleTypeSelection -> ruleTypeSelectionOpen = true
            RuleBuilderExitTarget.RuleDetail -> selectedRuleDetailId = destination.ruleDetailId
        }
    }

    private fun returnToRuleTypeSelection() {
        builderStep = RuleBuilderStep.Earn
        editingRuleTemplate = null
        builderEntryContext = null
        builderReturnRuleDetailId = null
        unavailableRuleType = null
        productivePickerOpen = false
        blockedPickerOpen = false
        blockedPickerOriginalPackages = null
        requirementPickerOpen = false
        cancelScheduleWindow()
        ruleTypeSelectionOpen = true
    }

    private fun selectProductiveApp(packageName: String) {
        selectedProductivePackages = if (packageName in selectedProductivePackages) {
            selectedProductivePackages - packageName
        } else {
            selectedProductivePackages + packageName
        }
        selectedProductivePackage = selectedProductivePackages.firstOrNull().orEmpty()
        productiveSearch = ""
    }

    private fun openRequirementPicker() {
        requirementPickerOriginalPackage = selectedRequirementPackage
        requirementPickerOpen = true
        requirementSearch = ""
        if (selectedRequirementPackage == null && editingRequirementIndex == null) {
            selectedRequirementMinutes = selectedRequirementMinutes.coerceAtLeast(1)
        }
        refreshLaunchableApps()
    }

    private fun selectRequirementApp(packageName: String) {
        selectedRequirementPackage = packageName
    }

    private fun useRequirementApp() {
        if (selectedRequirementPackage == null) return
        requirementPickerOpen = false
        requirementSearch = ""
        requirementPickerOriginalPackage = null
    }

    private fun dismissRequirementPicker() {
        selectedRequirementPackage = requirementPickerOriginalPackage
        requirementPickerOriginalPackage = null
        requirementPickerOpen = false
        requirementSearch = ""
    }

    private fun saveRequirement() {
        val packageName = selectedRequirementPackage ?: return
        val app = launchableApps.firstOrNull { it.packageName == packageName }?.let {
            EarnItRuleStore.RuleApp(it.packageName, it.name)
        } ?: selectedRequirements.firstOrNull { it.app.packageName == packageName }?.app
            ?: return
        val requirement = EarnItRuleStore.RuleRequirement(
            app = app,
            requiredSeconds = selectedRequirementMinutes.coerceAtLeast(1) * 60L
        )
        val editIndex = editingRequirementIndex
        selectedRequirements = if (editIndex != null && editIndex in selectedRequirements.indices) {
            selectedRequirements.mapIndexed { index, existing -> if (index == editIndex) requirement else existing }
        } else {
            (selectedRequirements.filterNot { it.app.packageName == requirement.app.packageName } + requirement)
        }
        selectedRequirementPackage = null
        requirementPickerOriginalPackage = null
        selectedRequirementMinutes = 10
        editingRequirementIndex = null
        requirementPickerOpen = false
        requirementSearch = ""
    }

    private fun editRequirement(index: Int) {
        val requirement = selectedRequirements.getOrNull(index) ?: return
        editingRequirementIndex = index
        selectedRequirementPackage = requirement.app.packageName
        requirementPickerOriginalPackage = null
        selectedRequirementMinutes = (requirement.requiredSeconds / 60L).toInt().coerceAtLeast(1)
        requirementPickerOpen = false
        requirementSearch = ""
    }

    private fun deleteRequirement(index: Int) {
        selectedRequirements = selectedRequirements.filterIndexed { itemIndex, _ -> itemIndex != index }
        if (editingRequirementIndex == index) {
            editingRequirementIndex = null
            selectedRequirementPackage = null
            requirementPickerOriginalPackage = null
            selectedRequirementMinutes = 10
            requirementPickerOpen = false
            requirementSearch = ""
        }
    }

    private fun cancelRequirementEditor() {
        requirementPickerOpen = false
        requirementSearch = ""
        selectedRequirementPackage = null
        requirementPickerOriginalPackage = null
        selectedRequirementMinutes = 10
        editingRequirementIndex = null
    }
    private fun toggleActiveDay(day: Int) {
        selectedActiveDays = if (day in selectedActiveDays) {
            (selectedActiveDays - day).ifEmpty { setOf(day) }
        } else {
            selectedActiveDays + day
        }
    }

    private fun toggleBlockedApp(packageName: String) {
        selectedBlockedPackages = if (packageName in selectedBlockedPackages) {
            selectedBlockedPackages - packageName
        } else {
            selectedBlockedPackages + packageName
        }
    }

    private fun openBlockedPicker() {
        blockedPickerOriginalPackages = selectedBlockedPackages
        blockedPickerOriginalDomains = selectedBlockedDomains
        blockedPickerOpen = true
        blockedSearch = ""
        refreshLaunchableApps()
    }

    private fun saveBlockedPicker() {
        selectedBlockedPackages = resolveRewardAppPickerSelection(
            originalPackages = blockedPickerOriginalPackages ?: selectedBlockedPackages,
            stagedPackages = selectedBlockedPackages,
            applyChanges = true
        )
        blockedPickerOriginalPackages = null
        blockedPickerOriginalDomains = null
        blockedPickerOpen = false
        blockedSearch = ""
    }

    private fun dismissBlockedPicker() {
        selectedBlockedPackages = resolveRewardAppPickerSelection(
            originalPackages = blockedPickerOriginalPackages ?: selectedBlockedPackages,
            stagedPackages = selectedBlockedPackages,
            applyChanges = false
        )
        selectedBlockedDomains = blockedPickerOriginalDomains ?: selectedBlockedDomains
        blockedPickerOriginalPackages = null
        blockedPickerOriginalDomains = null
        blockedPickerOpen = false
        blockedSearch = ""
    }

    private fun toggleRuleEnabled(rule: EarnItRuleStore.Rule) {
        refreshStrictModeState()
        if (!rule.enabled && !currentOnboardingPermissions().isReady) {
            continueOnboardingSetup()
            return
        }
        if (rule.enabled && isRuleProtected(rule)) {
            createProtectedAction(rule, StrictModeActionDescriptor.Update(rule.id, rule.copy(enabled = false)))
            return
        }
        if (!rule.enabled) {
            EarnItPauseStore.clearPause(this, rule.id)
        }
        val result = EarnItRuleStore.setRuleEnabled(
            this,
            rule.id,
            !rule.enabled,
            FeatureAccessPolicy(entitlementRepository.state.value)
        )
        if (result is RuleActivationResult.Denied) {
            openProGate(PremiumEntryPoint.RuleLimit)
            return
        }
        refreshDashboardState()
    }

    private fun toggleDailyCommitment(rule: EarnItRuleStore.Rule, enabled: Boolean) {
        if (rule.type != EarnItRuleStore.RuleType.CompleteToUnlock) return
        EarnItRuleStore.saveRule(this, rule.copy(requiresDailyCommitment = enabled))
        refreshDashboardState()
    }

    private fun pauseRuleFor(rule: EarnItRuleStore.Rule, durationMillis: Long, reason: String? = null) {
        refreshStrictModeState()
        if (isRuleProtected(rule)) {
            createProtectedAction(rule, StrictModeActionDescriptor.Pause(rule.id, durationMillis, reason))
            return
        }
        val expiresAt = System.currentTimeMillis() + durationMillis.coerceAtLeast(1L)
        EarnItPauseStore.pauseUntil(this, rule.id, expiresAt, reason)
        EarnItRuleStore.setRuleEnabled(this, rule.id, false)
        PostHog.capture(
            "rule_paused",
            properties = mapOf("rule_type" to rule.type.name, "duration_minutes" to durationMillis / 60_000L)
        )
        refreshDashboardState()
    }

    private fun resumeRule(rule: EarnItRuleStore.Rule) {
        EarnItPauseStore.clearPause(this, rule.id)
        val result = EarnItRuleStore.setRuleEnabled(
            this,
            rule.id,
            true,
            FeatureAccessPolicy(entitlementRepository.state.value)
        )
        if (result is RuleActivationResult.Denied) {
            openProGate(PremiumEntryPoint.RuleLimit)
            return
        }
        PostHog.capture(
            "rule_resumed",
            properties = mapOf("rule_type" to rule.type.name)
        )
        refreshDashboardState()
    }

    private fun deleteRule(rule: EarnItRuleStore.Rule) {
        refreshStrictModeState()
        if (isRuleProtected(rule)) {
            createProtectedAction(rule, StrictModeActionDescriptor.Delete(rule.id))
            return
        }
        if (selectedRuleDetailId == rule.id) {
            selectedRuleDetailId = null
        }
        EarnItRuleStore.deleteRule(this, rule.id)
        EarnItPauseStore.clearPause(this, rule.id)
        PostHog.capture(
            "rule_deleted",
            properties = mapOf("rule_type" to rule.type.name)
        )
        if (editingRuleTemplate?.id == rule.id) {
            cancelEditingRule()
        }
        refreshDashboardState()
    }

    private fun showStrictModeBlockedAction() {
        refreshStrictModeState()
        strictModeActionMessage = "Less-restrictive changes require the selected protection method. No change has been applied."
        strictModeBlockedActionOpen = true
    }

    private fun isRuleProtected(rule: EarnItRuleStore.Rule): Boolean {
        return globalStrictModeConfiguration?.protectsLessRestrictiveChanges() == true
    }

    private fun createProtectedAction(rule: EarnItRuleStore.Rule, descriptor: StrictModeActionDescriptor) {
        chargingState = chargingStateObserver.currentState()
        when (val result = ruleStrictModeStore.createPendingAction(rule, descriptor)) {
            is PendingActionCreationResult.Created -> {
                pendingStrictModeAction = result.action
                if (result.action.authorizationMethod == StrictModeProtectionMethod.Charger) {
                    chargerSession = ruleStrictModeStore.beginOrRestoreCharger(result.action.id, chargingState)
                    strictModeActionMessage = "Connect your charger to review this change."
                } else if (result.action.authorizationMethod == StrictModeProtectionMethod.Pin) {
                    strictModeActionMessage = "Enter your PIN to review this change."
                } else strictModeActionMessage = "Your change is saved as a pending request. Complete the protection method to continue."
            }
            is PendingActionCreationResult.AlreadyPending -> {
                pendingStrictModeAction = result.action
                strictModeActionMessage = "Another change is already in progress for this Rule. Cancel it before starting a different change."
            }
            is PendingActionCreationResult.Rejected -> strictModeActionMessage = result.message
        }
        strictModeReturnRuleId = rule.id
        if (pendingStrictModeAction?.authorizationMethod in setOf(StrictModeProtectionMethod.Charger, StrictModeProtectionMethod.Pin)) {
            settingsOpen = false
            selectedRuleDetailId = null
            strictModeOpen = true
        } else strictModeBlockedActionOpen = true
    }

    private fun selectAllDaySchedule() {
        selectedTimeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440))
        selectedStartMinute = 0
        selectedEndMinute = 1_440
        cancelScheduleWindow()
    }

    private fun setHoursSchedule() {
        if (selectedTimeWindows.size == 1 && selectedTimeWindows.first() == EarnItRuleStore.TimeWindow(0, 1_440)) {
            selectedTimeWindows = listOf(EarnItRuleStore.TimeWindow(9 * 60, 17 * 60))
        }
        selectedStartMinute = selectedTimeWindows.first().startMinute
        selectedEndMinute = selectedTimeWindows.first().endMinute
    }

    private fun addScheduleWindow() {
        scheduleWindowEditorOpen = true
        editingScheduleWindowIndex = null
        scheduleEditorStartMinute = 9 * 60
        scheduleEditorEndMinute = 17 * 60
    }

    private fun editScheduleWindow(index: Int) {
        val window = selectedTimeWindows.getOrNull(index) ?: return
        scheduleWindowEditorOpen = true
        editingScheduleWindowIndex = index
        scheduleEditorStartMinute = window.startMinute
        scheduleEditorEndMinute = window.endMinute
    }

    private fun removeScheduleWindow(index: Int) {
        val updated = selectedTimeWindows.filterIndexed { itemIndex, _ -> itemIndex != index }
        selectedTimeWindows = updated.ifEmpty { listOf(EarnItRuleStore.TimeWindow(9 * 60, 17 * 60)) }
        selectedStartMinute = selectedTimeWindows.first().startMinute
        selectedEndMinute = selectedTimeWindows.first().endMinute
        if (editingScheduleWindowIndex == index) cancelScheduleWindow()
    }

    private fun saveScheduleWindow() {
        val window = EarnItRuleStore.TimeWindow(scheduleEditorStartMinute, scheduleEditorEndMinute)
        if (window.startMinute == window.endMinute) return
        val editIndex = editingScheduleWindowIndex
        val updated = if (editIndex != null && editIndex in selectedTimeWindows.indices) {
            selectedTimeWindows.mapIndexed { index, existing -> if (index == editIndex) window else existing }
        } else {
            selectedTimeWindows.filterNot { it == EarnItRuleStore.TimeWindow(0, 1_440) } + window
        }
        selectedTimeWindows = EarnItRuleStore.normalizeTimeWindows(updated)
        selectedStartMinute = selectedTimeWindows.first().startMinute
        selectedEndMinute = selectedTimeWindows.first().endMinute
        cancelScheduleWindow()
    }

    private fun cancelScheduleWindow() {
        scheduleWindowEditorOpen = false
        editingScheduleWindowIndex = null
    }

    private fun showScheduleEditorStartPicker() {
        showTimePicker(scheduleEditorStartMinute) { scheduleEditorStartMinute = it.coerceIn(0, 1_439) }
    }

    private fun showScheduleEditorEndPicker() {
        val dialogMinute = if (scheduleEditorEndMinute == 1_440) 0 else scheduleEditorEndMinute
        showTimePicker(dialogMinute) { selectedMinute ->
            scheduleEditorEndMinute = if (selectedMinute == 0) 1_440 else selectedMinute.coerceIn(1, 1_439)
        }
    }

    private fun showTimePicker(initialMinute: Int, onTimeSelected: (Int) -> Unit) {
        val safeMinute = initialMinute.coerceIn(0, 1_439)
        TimePickerDialog(
            this,
            { _, hourOfDay, minute -> onTimeSelected(hourOfDay * 60 + minute) },
            safeMinute / 60,
            safeMinute % 60,
            DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun saveRule() {
        val editingRule = editingRuleTemplate ?: return
        val savedEarnApps = editingRule.earnApps.associateBy { it.packageName }
        val launchableEarnApps = launchableApps.associateBy { it.packageName }
        val productiveApps = selectedProductivePackages.mapNotNull { packageName ->
            launchableEarnApps[packageName]?.let {
                EarnItRuleStore.RuleApp(packageName = it.packageName, name = it.name)
            } ?: savedEarnApps[packageName]
        }
        val primaryProductiveApp = productiveApps.firstOrNull()
            ?: EarnItRuleStore.RuleApp(editingRule.productivePackage, editingRule.productiveName)
        if (editingRule.type == EarnItRuleStore.RuleType.EarnRewardTime && productiveApps.isEmpty()) return
        val savedBlockedApps = editingRule.blockedApps.associateBy { it.packageName }
        val launchableBlockedApps = launchableApps.associateBy { it.packageName }
        val blockedApps = selectedBlockedPackages.mapNotNull { packageName ->
            launchableBlockedApps[packageName]?.let {
                EarnItRuleStore.RuleApp(packageName = it.packageName, name = it.name)
            } ?: savedBlockedApps[packageName]
        }
        if (blockedApps.isEmpty() && selectedBlockedDomains.isEmpty()) return

        val rule = EarnItRuleStore.Rule(
            id = editingRule.id,
            productivePackage = primaryProductiveApp.packageName,
            productiveName = primaryProductiveApp.name,
            blockedApps = blockedApps,
            rewardSecondsPerProductiveSecond = selectedRatio,
            activeDays = selectedActiveDays,
            startMinute = selectedStartMinute,
            endMinute = selectedEndMinute,
            timeWindows = selectedTimeWindows,
            enabled = editingRule.enabled,
            type = editingRule.type,
            productiveApps = if (editingRule.type == EarnItRuleStore.RuleType.EarnRewardTime) productiveApps else emptyList(),
            requirements = if (editingRule.type == EarnItRuleStore.RuleType.CompleteToUnlock) selectedRequirements else emptyList(),
            blockedDomains = selectedBlockedDomains,
            lastActivatedAtMillis = editingRule.lastActivatedAtMillis,
            inactiveReason = editingRule.inactiveReason
        )
        if (builderEntryContext == RuleBuilderEntryContext.Edit && isRuleProtected(editingRule)) {
            val comparison = RuleRestrictionPolicy.compare(editingRule, rule)
            if (comparison.classification == RestrictionClassification.LessRestrictive) {
                createProtectedAction(editingRule, StrictModeActionDescriptor.Update(editingRule.id, rule))
                return
            }
        }
        val eventName = if (builderEntryContext == RuleBuilderEntryContext.Create) "rule_created" else "rule_updated"
        val saveResult = EarnItRuleStore.saveRule(
            this,
            rule,
            FeatureAccessPolicy(entitlementRepository.state.value)
        )
        if (saveResult is RuleActivationResult.Denied) {
            openProGate(PremiumEntryPoint.RuleLimit)
            return
        }
        PostHog.capture(
            eventName,
            properties = mapOf(
                "rule_type" to rule.type.name,
                "earn_app_count" to rule.earnApps.size,
                "reward_app_count" to rule.blockedApps.size,
                "website_count" to rule.normalizedBlockedDomains.size,
                "requirement_count" to rule.requirements.size
            )
        )
        editingRuleTemplate = null
        builderEntryContext = null
        builderReturnRuleDetailId = null
        builderStep = RuleBuilderStep.Earn
        productivePickerOpen = false
        blockedPickerOpen = false
        blockedPickerOriginalPackages = null
        refreshDashboardState()
        if (!currentOnboardingPermissions().isReady) {
            continueOnboardingSetup()
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getTodayActiveProductiveUsageSeconds(rule: EarnItRuleStore.Rule): Long {
        val usageStatsManager = getSystemService(UsageStatsManager::class.java)
        return RewardLedger.activeProductiveUsageSecondsToday(this, usageStatsManager, rule)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = ComponentName(this, EarnItAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        splitter.forEach { enabledService ->
            val componentName = ComponentName.unflattenFromString(enabledService)
            if (componentName == expectedService) {
                return true
            }
        }
        return false
    }

    private fun openUsageAccessSettings() {
        if (settingsLaunchInProgress) return
        settingsLaunchInProgress = true
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        if (settingsLaunchInProgress) return
        settingsLaunchInProgress = true
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openEarnApp(packageName: String) {
        if (!currentOnboardingPermissions().isReady) {
            continueOnboardingSetup()
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        TrackedAppLaunchStore.registerPendingLaunch(
            context = this,
            logicalPackageName = packageName,
            launchedPackageName = launchIntent.targetPackageName(packageName)
        )
        try {
            startActivity(launchIntent)
        } catch (_: ActivityNotFoundException) {
            TrackedAppLaunchStore.savePendingLaunch(this, null)
        }
    }

    private fun completeFirstLaunchAndCreateRule() {
        captureOnboardingCompleted("create_first_rule")
        finishOnboarding()
        startAddingRule()
    }

    private fun completeOnboardingAndGoHome() {
        captureOnboardingCompleted("go_home")
        finishOnboarding()
    }

    private fun captureOnboardingCompleted(completionPath: String) {
        val permissions = currentOnboardingPermissions()
        PostHog.capture(
            "onboarding_completed",
            properties = mapOf(
                "completion_path" to completionPath,
                "usage_access_granted" to permissions.earningProgressAllowed,
                "accessibility_granted" to permissions.appBlockingAllowed
            )
        )
    }

    private fun leaveOnboardingIncomplete() {
        finishOnboarding()
    }

    private fun finishOnboarding() {
        onboardingStore.markSeen()
        onboardingActive = false
        settingsOpen = false
    }

    private fun continueOnboarding() {
        persistOnboardingStep(nextOnboardingStep(onboardingStep, currentOnboardingPermissions()))
    }

    private fun backOnboarding() {
        previousOnboardingStep(onboardingStep)?.let(::persistOnboardingStep)
    }

    private fun persistOnboardingStep(step: OnboardingStep) {
        onboardingStep = step
        onboardingStore.saveStep(step)
    }

    private fun currentOnboardingPermissions() = OnboardingPermissionState(
        earningProgressAllowed = usageAccessGranted,
        appBlockingAllowed = accessibilityServiceEnabled
    )

    private fun reconcileOnboardingWithPermissions() {
        if (!onboardingActive) return
        val reconciled = reconcileOnboardingStep(onboardingStep, currentOnboardingPermissions())
        if (reconciled != onboardingStep) persistOnboardingStep(reconciled)
    }

    private fun continueOnboardingSetup() {
        settingsOpen = false
        onboardingActive = true
        persistOnboardingStep(focusedRepairStep(currentOnboardingPermissions()))
    }

    private fun replayOnboarding() {
        if (!isDebugBuild()) return
        settingsOpen = false
        onboardingStore.beginReplay()
        onboardingActive = true
        onboardingStep = OnboardingStep.Value
    }

    private fun isDebugBuild(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun openProGate(entryPoint: PremiumEntryPoint) {
        proFlowState = ProFlowState(route = ProRoute.Gate, entryPoint = entryPoint)
    }

    companion object {
        internal const val EXTRA_OPEN_RULE_DETAIL_ID = "com.kaleel.earnitv2.extra.OPEN_RULE_DETAIL_ID"
        private const val APP_LIST_REFRESH_INTERVAL_MS = 60_000L
        private const val STATE_ANALYTICS_DATE = "analytics_selected_date"
    }
}

private fun GlobalStrictModeConfiguration.toLegacyStrictModeState(): StrictModeState {
    return StrictModeState(
        lifecycleState = when (lifecycle) {
            RuleStrictModeLifecycle.Disabled -> StrictModeLifecycleState.Inactive
            RuleStrictModeLifecycle.PendingActivation -> StrictModeLifecycleState.Activating
            RuleStrictModeLifecycle.Active, RuleStrictModeLifecycle.Invalid -> StrictModeLifecycleState.Active
            RuleStrictModeLifecycle.DeactivationCounting -> StrictModeLifecycleState.DeactivationCounting
            RuleStrictModeLifecycle.DeactivationReady -> StrictModeLifecycleState.DeactivationReady
        },
        configuration = StrictModeConfiguration(
            durationType = StrictModeDurationType.Indefinite,
            timedDurationMillis = null,
            deactivationMethod = StrictModeDeactivationMethod.Countdown,
            deactivationCountdownMillis = deactivationWaitMillis ?: GlobalStrictModeStore.DEFAULT_COUNTDOWN_MILLIS
        ),
        activationGraceStartedAtMillis = activationRequestedAtMillis,
        activationGraceEndsAtMillis = activeFromMillis,
        activatedAtMillis = activeFromMillis?.takeIf { lifecycle != RuleStrictModeLifecycle.PendingActivation },
        deactivationStartedAtMillis = deactivationStartedAtMillis,
        deactivationAvailableAtMillis = deactivationAvailableAtMillis
    )
}

internal fun resolveRewardAppPickerSelection(
    originalPackages: Set<String>,
    stagedPackages: Set<String>,
    applyChanges: Boolean
): Set<String> = if (applyChanges) stagedPackages else originalPackages

@Composable
internal fun Dashboard(
    ruleStates: List<RuleDashboardState>,
    editingRule: EarnItRuleStore.Rule?,
    apps: List<EarnItRuleStore.LaunchableApp>,
    appsLoading: Boolean,
    selectedProductivePackage: String,
    selectedProductivePackages: Set<String>,
    selectedBlockedPackages: Set<String>,
    selectedRequirements: List<EarnItRuleStore.RuleRequirement>,
    requirementPickerOpen: Boolean,
    requirementSearch: String,
    selectedRequirementPackage: String?,
    selectedRequirementMinutes: Int,
    editingRequirementIndex: Int?,
    selectedRatio: Int,
    selectedActiveDays: Set<Int>,
    selectedStartMinute: Int,
    selectedEndMinute: Int,
    selectedTimeWindows: List<EarnItRuleStore.TimeWindow>,
    scheduleWindowEditorOpen: Boolean,
    editingScheduleWindowIndex: Int?,
    scheduleEditorStartMinute: Int,
    scheduleEditorEndMinute: Int,
    productivePickerOpen: Boolean,
    blockedPickerOpen: Boolean,
    productiveSearch: String,
    blockedSearch: String,
    usageAccessGranted: Boolean,
    usageStatusMessage: String,
    ruleStatusMessage: String,
    accessibilityServiceEnabled: Boolean,
    showDeveloperTools: Boolean,
    manageRulesOpen: Boolean,
    pauseExpirations: Map<String, Long>,
    strictModeOpen: Boolean,
    strictModeState: StrictModeState,
    globalStrictModeConfiguration: GlobalStrictModeConfiguration?,
    pendingStrictModeAction: PendingStrictModeAction?,
    chargerSession: ChargerAuthorizationSession?,
    chargingState: ChargingState,
    selectedRuleDetailId: String?,
    settingsOpen: Boolean,
    analyticsOpen: Boolean,
    franklinDashboardOpen: Boolean,
    franklinCalendarOpen: Boolean,
    analyticsRange: AnalyticsRange,
    analyticsSelectedDate: LocalDate,
    analyticsState: AnalyticsUiState,
    analyticsInsights: List<InsightCandidate>,
    analyticsAppPackage: String?,
    ruleTypeSelectionOpen: Boolean,
    unavailableRuleType: RuleTypeOption?,
    deepWorkSession: DeepWorkSession,
    deepWorkSetupOpen: Boolean,
    entitlementState: EntitlementState,
    featurePolicy: FeatureAccessPolicy,
    purchaseState: PurchaseState,
    purchaseProvider: LocalPurchaseProvider,
    subscriptionConfig: SubscriptionConfig,
    proFlowState: ProFlowState?,
    onProFlowChange: (ProFlowState) -> Unit,
    onCloseProFlow: () -> Unit,
    onPurchasePlan: (SubscriptionPlan) -> Unit,
    onRestorePurchases: () -> Unit,
    onSimulateEntitlement: (EntitlementState) -> Unit,
    onResetEntitlement: () -> Unit,
    premiumSimulatorOpen: Boolean,
    onOpenPremiumSimulator: () -> Unit,
    onClosePremiumSimulator: () -> Unit,
    onOpenDeepWork: () -> Unit,
    onDismissDeepWorkSetup: () -> Unit,
    onStartDeepWork: (Long?) -> Unit,
    onActivateDeepWork: () -> Unit,
    onContinueDeepWork: (Long) -> Unit,
    onFinishDeepWork: (Long) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onContinueSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onCloseAnalytics: () -> Unit,
    onAnalyticsRangeChange: (AnalyticsRange) -> Unit,
    onAnalyticsDateChange: (LocalDate) -> Unit,
    onOpenAnalyticsApp: (String) -> Unit,
    onBackFromAnalyticsApp: () -> Unit,
    onCloseSettings: () -> Unit,
    onOpenBenjaminFranklin: () -> Unit,
    onCloseBenjaminFranklin: () -> Unit,
    onOpenBenjaminFranklinCalendar: () -> Unit,
    onCloseBenjaminFranklinCalendar: () -> Unit,
    pendingFeedbackCount: Int,
    shakeToReportEnabled: Boolean,
    shakeSettingError: String?,
    onOpenFeedback: () -> Unit,
    onShakeToReportChange: (Boolean) -> Unit,
    onDebugShakeFeedback: () -> Unit,
    onClearDebugFeedback: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenStrictMode: () -> Unit,
    onCloseStrictMode: () -> Unit,
    onSaveStrictModeConfiguration: (StrictModeConfiguration) -> Unit,
    onBeginStrictModeActivation: (StrictModeConfiguration, StrictModeProtectionMethod, String?) -> Unit,
    onCancelStrictModeActivation: () -> Unit,
    onBeginStrictModeDeactivation: () -> Unit,
    onConfirmChargerStrictModeDeactivation: () -> PendingActionValidation,
    onCancelStrictModeDeactivation: () -> Unit,
    onConfirmStrictModeDeactivation: () -> Unit,
    onKeepStrictModeActive: () -> Unit,
    onStrictModeTick: () -> Unit,
    onAuthorizeCharger: () -> Unit,
    onVerifyPin: (String) -> PinVerificationResult,
    onConfirmProtectedAction: () -> Unit,
    onCancelProtectedRequest: () -> Unit,
    onRequestStrictModeMethodChange: (StrictModeProtectionMethod, Long?, String?) -> Unit,
    onStrictModeBlockedAction: () -> Unit,
    onOpenEarnApp: (String) -> Unit,
    onAddRule: () -> Unit,
    onBackFromRuleTypeSelection: () -> Unit,
    onBackFromUnavailableRuleType: () -> Unit,
    onSelectRuleType: (EarnItRuleStore.RuleType) -> Unit,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleRuleEnabled: (EarnItRuleStore.Rule) -> Unit,
    onToggleDailyCommitment: (EarnItRuleStore.Rule, Boolean) -> Unit,
    onPauseRuleFor: (EarnItRuleStore.Rule, Long, String?) -> Unit,
    onResumeRule: (EarnItRuleStore.Rule) -> Unit,
    onPauseTimerTick: () -> Unit,
    onDeleteRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleManageRules: () -> Unit,
    onOpenRuleDetail: (String) -> Unit,
    onBackFromRuleDetail: () -> Unit,
    onCancelEditingRule: () -> Unit,
    onOpenProductivePicker: () -> Unit,
    onCloseProductivePicker: () -> Unit,
    onOpenBlockedPicker: () -> Unit,
    onCloseBlockedPicker: () -> Unit,
    onDismissBlockedPicker: () -> Unit,
    onProductiveSearchChange: (String) -> Unit,
    onBlockedSearchChange: (String) -> Unit,
    onSelectProductiveApp: (String) -> Unit,
    onOpenRequirementPicker: () -> Unit,
    onCloseRequirementPicker: () -> Unit,
    onDismissRequirementAppPicker: () -> Unit,
    onUseRequirementApp: () -> Unit,
    onRequirementSearchChange: (String) -> Unit,
    onSelectRequirementApp: (String) -> Unit,
    onSelectRequirementMinutes: (Int) -> Unit,
    onSaveRequirement: () -> Unit,
    onEditRequirement: (Int) -> Unit,
    onDeleteRequirement: (Int) -> Unit,
    onToggleBlockedApp: (String) -> Unit,
    onSelectRatio: (Int) -> Unit,
    onToggleActiveDay: (Int) -> Unit,
    onSelectActiveDays: (Set<Int>) -> Unit,
    onSelectAllDay: () -> Unit,
    onSetHours: () -> Unit,
    onAddTimeWindow: () -> Unit,
    onEditTimeWindow: (Int) -> Unit,
    onRemoveTimeWindow: (Int) -> Unit,
    onSaveTimeWindow: () -> Unit,
    onCancelTimeWindow: () -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit,
    builderStep: RuleBuilderStep,
    onBuilderStepChange: (RuleBuilderStep) -> Unit,
    onSaveRule: () -> Unit,
    selectedBlockedDomains: List<String> = emptyList(),
    onBlockedDomainsChange: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LaunchedEffect(pauseExpirations) {
        while (pauseExpirations.isNotEmpty()) {
            delay(1_000)
            onPauseTimerTick()
        }
    }
    LaunchedEffect(strictModeState.lifecycleState, strictModeState.activationGraceEndsAtMillis, strictModeState.expiresAtMillis) {
        while (strictModeState.lifecycleState == StrictModeLifecycleState.Activating ||
            (strictModeState.lifecycleState.isStrictModeProtecting() && strictModeState.expiresAtMillis != null)
        ) {
            delay(1_000)
            onStrictModeTick()
        }
    }

    if (proFlowState != null) {
        EarnItProScreen(
            flow = proFlowState,
            config = subscriptionConfig,
            entitlement = entitlementState,
            purchaseState = purchaseState,
            onFlowChange = onProFlowChange,
            onPurchase = onPurchasePlan,
            onRestore = onRestorePurchases,
            onClose = onCloseProFlow,
            modifier = modifier
        )
        return
    }

    if (editingRule == null) {
        val homeRules = ruleStates.map { state ->
            homeRuleUiState(
                state = state,
                usageAccessGranted = usageAccessGranted,
                appBlockingEnabled = accessibilityServiceEnabled
            )
        }
        val permissionState = EarnItUiStateAdapters.permissionSetup(
            usageAccessGranted = usageAccessGranted,
            appBlockingEnabled = accessibilityServiceEnabled
        )
        val selectedHomeRule = selectedRuleDetailId?.let { selectedRuleId ->
            homeRules.firstOrNull { it.rule.id == selectedRuleId }
        }

        if (premiumSimulatorOpen && showDeveloperTools) {
            PremiumSimulatorScreen(
                entitlement = entitlementState,
                purchaseProvider = purchaseProvider,
                onEntitlement = onSimulateEntitlement,
                onReset = onResetEntitlement,
                onBack = onClosePremiumSimulator,
                modifier = modifier
            )
        } else if (deepWorkSession.phase != DeepWorkPhase.Inactive) {
            DeepWorkScreen(deepWorkSession, onActivateDeepWork, onContinueDeepWork, onFinishDeepWork)
        } else if (ruleTypeSelectionOpen) {
            EarnItRuleTypeSelection(
                onBack = onBackFromRuleTypeSelection,
                onSelectRuleType = onSelectRuleType,
                modifier = modifier
            )
        } else if (strictModeOpen) {
            EarnItStrictModeScreen(
                state = strictModeState,
                foundationLifecycle = globalStrictModeConfiguration?.lifecycle,
                globalConfiguration = globalStrictModeConfiguration,
                pendingAction = pendingStrictModeAction,
                chargerSession = chargerSession,
                chargingState = chargingState,
                onBack = onCloseStrictMode,
                onSaveConfiguration = onSaveStrictModeConfiguration,
                onBeginActivation = onBeginStrictModeActivation,
                onCancelActivation = onCancelStrictModeActivation,
                onBeginDeactivation = onBeginStrictModeDeactivation,
                onConfirmChargerDeactivation = onConfirmChargerStrictModeDeactivation,
                onCancelDeactivation = onCancelStrictModeDeactivation,
                onConfirmDeactivation = onConfirmStrictModeDeactivation,
                onKeepStrictModeActive = onKeepStrictModeActive,
                onTick = onStrictModeTick,
                onAuthorizeCharger = onAuthorizeCharger,
                onVerifyPin = onVerifyPin,
                onConfirmProtectedAction = onConfirmProtectedAction,
                onCancelProtectedRequest = onCancelProtectedRequest,
                onRequestMethodChange = onRequestStrictModeMethodChange,
                modifier = modifier
            )
        } else if (franklinCalendarOpen) {
            BenjaminFranklinCalendarScreen(
                onBack = onCloseBenjaminFranklinCalendar,
                modifier = modifier
            )
        } else if (franklinDashboardOpen) {
            BenjaminFranklinDashboard(
                onBack = onCloseBenjaminFranklin,
                onOpenHistory = onOpenBenjaminFranklinCalendar,
                modifier = modifier
            )
        } else if (analyticsOpen) {
            AnalyticsScreen(
                range = analyticsRange,
                selectedDate = analyticsSelectedDate,
                state = analyticsState,
                insights = analyticsInsights,
                rules = ruleStates.map { it.rule },
                selectedAppPackage = analyticsAppPackage,
                onRangeChange = onAnalyticsRangeChange,
                onDateChange = onAnalyticsDateChange,
                onOpenApp = onOpenAnalyticsApp,
                onBackFromApp = onBackFromAnalyticsApp,
                onBack = onCloseAnalytics,
                onCreateRule = onAddRule,
                onRepairPermission = onOpenUsageAccessSettings,
                premiumInsightsEnabled = featurePolicy.canUse(PremiumFeature.PremiumInsights),
                onPremiumGate = { onProFlowChange(ProFlowState(ProRoute.Gate, PremiumEntryPoint.Analytics)) },
                modifier = modifier
            )
        } else if (settingsOpen) {
            EarnItSettings(
                permissionState = permissionState,
                hasRules = homeRules.isNotEmpty(),
                strictModeState = strictModeState,
                entitlementState = entitlementState,
                purchaseProvider = purchaseProvider,
                onOpenPro = { onProFlowChange(ProFlowState(ProRoute.Intro, PremiumEntryPoint.Settings)) },
                onRestorePurchases = { onProFlowChange(ProFlowState(ProRoute.Restore, PremiumEntryPoint.Settings)) },
                onManageSubscription = {
                    purchaseProvider.openManageSubscription()
                    onProFlowChange(ProFlowState(ProRoute.PurchaseStatus, PremiumEntryPoint.Settings))
                },
                onSimulateEntitlement = onSimulateEntitlement,
                onResetEntitlement = onResetEntitlement,
                onOpenPremiumSimulator = onOpenPremiumSimulator,
                onBack = onCloseSettings,
                onOpenAnalytics = onOpenAnalytics,
                onOpenBenjaminFranklin = onOpenBenjaminFranklin,
                onOpenStrictMode = onOpenStrictMode,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onCreateFirstRule = onAddRule,
                showDeveloperTools = showDeveloperTools,
                onReplayOnboarding = onReplayOnboarding,
                pendingFeedbackCount = pendingFeedbackCount,
                shakeToReportEnabled = shakeToReportEnabled,
                shakeSettingError = shakeSettingError,
                onOpenFeedback = onOpenFeedback,
                onShakeToReportChange = onShakeToReportChange,
                onDebugShakeFeedback = onDebugShakeFeedback,
                onClearDebugFeedback = onClearDebugFeedback,
                modifier = modifier
            )
        } else if (selectedHomeRule != null) {
            EarnItRuleDetail(
                homeRule = selectedHomeRule,
                detail = EarnItUiStateAdapters.ruleDetail(
                    card = selectedHomeRule.card,
                    rule = selectedHomeRule.rule
                ),
                pausedUntilMillis = pauseExpirations[selectedHomeRule.rule.id],
                permissionState = permissionState,
                onBack = onBackFromRuleDetail,
                onOpenEarnApp = onOpenEarnApp,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onEditRule = onEditRule,
                onPauseRuleFor = onPauseRuleFor,
                onResumeRule = onResumeRule,
                onToggleDailyCommitment = onToggleDailyCommitment,
                isProtectedByStrictMode = StrictModePolicy.isRuleProtected(
                    strictModeState = globalStrictModeConfiguration?.toLegacyStrictModeState()
                        ?: StrictModeState(),
                    rule = selectedHomeRule.rule
                ),
                strictModeConfiguration = globalStrictModeConfiguration,
                onOpenStrictMode = onOpenStrictMode,
                onStrictModeTick = onStrictModeTick,
                onProtectedActionBlocked = onStrictModeBlockedAction,
                onDeleteRule = { rule ->
                    onBackFromRuleDetail()
                    onDeleteRule(rule)
                },
                modifier = modifier
            )
        } else {
            EarnItHome(
                rules = homeRules,
                permissionState = permissionState,
                manageRulesOpen = manageRulesOpen,
                deepWorkActive = deepWorkSession.phase != DeepWorkPhase.Inactive,
                deepWorkPremium = featurePolicy.canUse(PremiumFeature.DeepWork),
                onOpenDeepWork = onOpenDeepWork,
                onAddRule = onAddRule,
                onOpenEarnApp = onOpenEarnApp,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onContinueSetup = onContinueSetup,
                onOpenSettings = onOpenSettings,
                onToggleManageRules = onToggleManageRules,
                onOpenRuleDetail = onOpenRuleDetail,
                onEditRule = onEditRule,
                onToggleRuleEnabled = onToggleRuleEnabled,
                onDeleteRule = onDeleteRule,
                modifier = modifier
            )
            if (deepWorkSetupOpen) {
                val linked = DeepWorkStore.linkedRuleId(androidx.compose.ui.platform.LocalContext.current)?.let { id -> ruleStates.firstOrNull { it.rule.id == id }?.rule }
                DeepWorkSetupSheet(linked, apps, onDismissDeepWorkSetup, onStartDeepWork)
            }
        }
    } else if (requirementPickerOpen) {
        BuilderAppPickerSurface(
            title = "Choose requirement app",
            searchLabel = "Search apps",
            apps = apps,
            selectedPackages = selectedRequirementPackage?.let(::setOf) ?: emptySet(),
            searchQuery = requirementSearch,
            loading = appsLoading,
            onSearchQueryChange = onRequirementSearchChange,
            onToggleApp = onSelectRequirementApp,
            onSave = onUseRequirementApp,
            onBack = onDismissRequirementAppPicker,
            multiSelect = false,
            disabledPackages = unavailableRequirementAppPackages(selectedRequirements, editingRequirementIndex),
            saveLabel = "Use App",
            saveEnabled = selectedRequirementPackage != null,
            modifier = modifier
        )
    } else if (productivePickerOpen) {
        BuilderAppPickerSurface(
            title = "Choose Earn Apps",
            searchLabel = "Search Earn Apps",
            apps = apps,
            selectedPackages = selectedProductivePackages,
            searchQuery = productiveSearch,
            loading = appsLoading,
            onSearchQueryChange = onProductiveSearchChange,
            onToggleApp = onSelectProductiveApp,
            onSave = onCloseProductivePicker,
            modifier = modifier
        )
    } else if (blockedPickerOpen) {
        RewardTargetPickerSurface(
            title = rewardAppPickerTitle(editingRule.type),
            supportingText = rewardAppPickerSupportingText(editingRule.type),
            searchLabel = rewardAppPickerSearchLabel(editingRule.type),
            apps = apps,
            selectedPackages = selectedBlockedPackages,
            searchQuery = blockedSearch,
            loading = appsLoading,
            onSearchQueryChange = onBlockedSearchChange,
            onToggleApp = onToggleBlockedApp,
            onSave = onCloseBlockedPicker,
            onBack = onDismissBlockedPicker,
            multiSelect = true,
            saveLabel = "Save",
            selectedDomains = selectedBlockedDomains,
            onDomainsChange = onBlockedDomainsChange,
            accessibilityEnabled = accessibilityServiceEnabled,
            modifier = modifier
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RuleEditor(
                rule = editingRule,
                apps = apps,
                selectedProductivePackage = selectedProductivePackage,
                selectedProductivePackages = selectedProductivePackages,
                selectedBlockedPackages = selectedBlockedPackages,
                selectedBlockedDomains = selectedBlockedDomains,
                selectedRequirements = selectedRequirements,
                selectedRequirementPackage = selectedRequirementPackage,
                selectedRequirementMinutes = selectedRequirementMinutes,
                editingRequirementIndex = editingRequirementIndex,
                selectedRatio = selectedRatio,
                selectedActiveDays = selectedActiveDays,
                selectedStartMinute = selectedStartMinute,
                selectedEndMinute = selectedEndMinute,
                selectedTimeWindows = selectedTimeWindows,
                scheduleWindowEditorOpen = scheduleWindowEditorOpen,
                editingScheduleWindowIndex = editingScheduleWindowIndex,
                scheduleEditorStartMinute = scheduleEditorStartMinute,
                scheduleEditorEndMinute = scheduleEditorEndMinute,
                onOpenProductivePicker = onOpenProductivePicker,
                onOpenBlockedPicker = onOpenBlockedPicker,
                onOpenRequirementPicker = onOpenRequirementPicker,
                onCloseRequirementPicker = onCloseRequirementPicker,
                onSelectRequirementMinutes = onSelectRequirementMinutes,
                onSaveRequirement = onSaveRequirement,
                onEditRequirement = onEditRequirement,
                onDeleteRequirement = onDeleteRequirement,
                onSelectRatio = onSelectRatio,
                onToggleActiveDay = onToggleActiveDay,
                onSelectActiveDays = onSelectActiveDays,
                onSelectAllDay = onSelectAllDay,
                onSetHours = onSetHours,
                onAddTimeWindow = onAddTimeWindow,
                onEditTimeWindow = onEditTimeWindow,
                onRemoveTimeWindow = onRemoveTimeWindow,
                onSaveTimeWindow = onSaveTimeWindow,
                onCancelTimeWindow = onCancelTimeWindow,
                onEditStartTime = onEditStartTime,
                onEditEndTime = onEditEndTime,
                builderStep = builderStep,
                onBuilderStepChange = onBuilderStepChange,
                onSaveRule = onSaveRule,
                onCancel = onCancelEditingRule
            )
        }
    }
}

@Composable
private fun RuleRow(
    state: RuleDashboardState,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleRuleEnabled: (EarnItRuleStore.Rule) -> Unit,
    onDeleteRule: (EarnItRuleStore.Rule) -> Unit
) {
    val rule = state.rule
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = rule.productiveName, style = MaterialTheme.typography.titleSmall)
            Text(text = "Blocked: ${rule.blockedSummary}")
            Text(text = "Ratio: ${rule.ratioLabel}")
            Text(text = "Schedule: ${rule.scheduleLabel}")
            Text(text = "State: ${if (rule.enabled) "Enabled" else "Disabled"}")
            Text(text = "Productive today: ${formatDuration(state.productiveUsageSeconds)}")
            Text(text = "Available reward: ${formatDuration(state.remainingRewardSeconds)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onEditRule(rule) }) {
                    Text(text = "Edit")
                }
                Button(onClick = { onToggleRuleEnabled(rule) }) {
                    Text(text = if (rule.enabled) "Disable" else "Enable")
                }
                Button(onClick = { onDeleteRule(rule) }) {
                    Text(text = "Delete")
                }
            }
        }
    }
}

@Composable
private fun RuleEditor(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedProductivePackage: String,
    selectedProductivePackages: Set<String>,
    selectedBlockedPackages: Set<String>,
    selectedRequirements: List<EarnItRuleStore.RuleRequirement>,
    selectedRequirementPackage: String?,
    selectedRequirementMinutes: Int,
    editingRequirementIndex: Int?,
    selectedRatio: Int,
    selectedActiveDays: Set<Int>,
    selectedStartMinute: Int,
    selectedEndMinute: Int,
    selectedTimeWindows: List<EarnItRuleStore.TimeWindow>,
    scheduleWindowEditorOpen: Boolean,
    editingScheduleWindowIndex: Int?,
    scheduleEditorStartMinute: Int,
    scheduleEditorEndMinute: Int,
    onOpenProductivePicker: () -> Unit,
    onOpenBlockedPicker: () -> Unit,
    onOpenRequirementPicker: () -> Unit,
    onCloseRequirementPicker: () -> Unit,
    onSelectRequirementMinutes: (Int) -> Unit,
    onSaveRequirement: () -> Unit,
    onEditRequirement: (Int) -> Unit,
    onDeleteRequirement: (Int) -> Unit,
    onSelectRatio: (Int) -> Unit,
    onToggleActiveDay: (Int) -> Unit,
    onSelectActiveDays: (Set<Int>) -> Unit,
    onSelectAllDay: () -> Unit,
    onSetHours: () -> Unit,
    onAddTimeWindow: () -> Unit,
    onEditTimeWindow: (Int) -> Unit,
    onRemoveTimeWindow: (Int) -> Unit,
    onSaveTimeWindow: () -> Unit,
    onCancelTimeWindow: () -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit,
    builderStep: RuleBuilderStep,
    onBuilderStepChange: (RuleBuilderStep) -> Unit,
    onSaveRule: () -> Unit,
    onCancel: () -> Unit,
    selectedBlockedDomains: List<String> = emptyList()
) {
    EarnItRuleBuilder(
        rule = rule,
        apps = apps,
        selectedProductivePackage = selectedProductivePackage,
        selectedProductivePackages = selectedProductivePackages,
        selectedBlockedPackages = selectedBlockedPackages,
        selectedBlockedDomains = selectedBlockedDomains,
        selectedRequirements = selectedRequirements,
        selectedRequirementPackage = selectedRequirementPackage,
        selectedRequirementMinutes = selectedRequirementMinutes,
        editingRequirementIndex = editingRequirementIndex,
        selectedRatio = selectedRatio,
        selectedActiveDays = selectedActiveDays,
        selectedStartMinute = selectedStartMinute,
        selectedEndMinute = selectedEndMinute,
        selectedTimeWindows = selectedTimeWindows,
        scheduleWindowEditorOpen = scheduleWindowEditorOpen,
        editingScheduleWindowIndex = editingScheduleWindowIndex,
        scheduleEditorStartMinute = scheduleEditorStartMinute,
        scheduleEditorEndMinute = scheduleEditorEndMinute,
        builderStep = builderStep,
        onBuilderStepChange = onBuilderStepChange,
        onOpenProductivePicker = onOpenProductivePicker,
        onOpenBlockedPicker = onOpenBlockedPicker,
        onOpenRequirementPicker = onOpenRequirementPicker,
        onCloseRequirementPicker = onCloseRequirementPicker,
        onSelectRequirementMinutes = onSelectRequirementMinutes,
        onSaveRequirement = onSaveRequirement,
        onEditRequirement = onEditRequirement,
        onDeleteRequirement = onDeleteRequirement,
        onSelectRatio = onSelectRatio,
        onToggleActiveDay = onToggleActiveDay,
        onSelectActiveDays = onSelectActiveDays,
        onSelectAllDay = onSelectAllDay,
        onSetHours = onSetHours,
        onAddTimeWindow = onAddTimeWindow,
        onEditTimeWindow = onEditTimeWindow,
        onRemoveTimeWindow = onRemoveTimeWindow,
        onSaveTimeWindow = onSaveTimeWindow,
        onCancelTimeWindow = onCancelTimeWindow,
        onEditStartTime = onEditStartTime,
        onEditEndTime = onEditEndTime,
        onSaveRule = onSaveRule,
        onCancel = onCancel
    )
}


@Composable
fun EditorSection(
    title: String,
    helperText: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = helperText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
fun ProductiveAppSection(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedProductivePackage: String,
    pickerOpen: Boolean,
    search: String,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectApp: (String) -> Unit
) {
    val selectedName = apps.firstOrNull { it.packageName == selectedProductivePackage }?.name
        ?: if (rule.productivePackage == selectedProductivePackage) rule.productiveName else "None selected"
    Text(text = "Selected: $selectedName", style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Choose Productive App")
    }
    if (pickerOpen) {
        AppSearchField(value = search, onValueChange = onSearchChange)
        AppPickerList(
            apps = apps.filteredBy(search),
            selectedPackages = setOf(selectedProductivePackage),
            onClickApp = onSelectApp
        )
        Button(onClick = onClosePicker, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Done")
        }
    }
}
@Composable
fun BlockedAppsSection(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedBlockedPackages: Set<String>,
    pickerOpen: Boolean,
    search: String,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleApp: (String) -> Unit
) {
    val selectedNamesByPackage = rule.blockedApps.associate { it.packageName to it.name } +
        apps.associate { it.packageName to it.name }
    val selectedNames = selectedBlockedPackages.mapNotNull { selectedNamesByPackage[it] }
    val previewText = if (selectedNames.isEmpty()) {
        "No blocked apps selected"
    } else {
        selectedNames.take(3).joinToString(", ") + if (selectedNames.size > 3) " +${selectedNames.size - 3} more" else ""
    }
    Text(text = "Selected: ${selectedBlockedPackages.size} app${if (selectedBlockedPackages.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge)
    Text(text = previewText, style = MaterialTheme.typography.bodyMedium)
    Button(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Choose Blocked Apps")
    }
    if (pickerOpen) {
        AppSearchField(value = search, onValueChange = onSearchChange)
        Button(onClick = onClosePicker, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Done")
        }
        Text(text = "Selected: ${selectedBlockedPackages.size}")
        AppPickerList(
            apps = apps.filteredBy(search),
            selectedPackages = selectedBlockedPackages,
            onClickApp = onToggleApp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}
@Composable
private fun AppSearchField(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = "Search apps") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun AppPickerList(
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedPackages: Set<String>,
    onClickApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        apps.forEach { app ->
            Button(onClick = { onClickApp(app.packageName) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = if (app.packageName in selectedPackages) "${app.name} *" else app.name)
            }
        }
    }
}

@Composable
fun DayButtons(selectedActiveDays: Set<Int>, onToggleActiveDay: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EarnItRuleStore.allDays.take(4).forEach { day ->
            DayButton(day = day, selectedActiveDays = selectedActiveDays, onToggleActiveDay = onToggleActiveDay)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EarnItRuleStore.allDays.drop(4).forEach { day ->
            DayButton(day = day, selectedActiveDays = selectedActiveDays, onToggleActiveDay = onToggleActiveDay)
        }
    }
}

@Composable
private fun DayButton(day: Int, selectedActiveDays: Set<Int>, onToggleActiveDay: (Int) -> Unit) {
    Button(onClick = { onToggleActiveDay(day) }) {
        Text(text = EarnItRuleStore.dayShortName(day).take(1))
    }
}

private fun List<EarnItRuleStore.LaunchableApp>.filteredBy(query: String): List<EarnItRuleStore.LaunchableApp> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return this
    return filter { it.name.contains(trimmedQuery, ignoreCase = true) }
}

private fun formatDuration(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    return if (minutes > 0L) "${minutes}m ${seconds}s" else "${seconds}s"
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    val previewEntitlement = EntitlementState.Free
    val previewController = object : DebugEntitlementController {
        override fun simulate(state: EntitlementState) = Unit
        override fun reset() = Unit
    }
    val previewPurchaseProvider = LocalPurchaseProvider(
        SubscriptionConfig.Placeholder,
        previewController,
        simulationEnabled = true
    )
    val rule = EarnItRuleStore.Rule(
        id = "preview",
        productivePackage = "com.duolingo",
        productiveName = "Duolingo",
        blockedApps = listOf(EarnItRuleStore.RuleApp("com.instagram.android", "Instagram")),
        rewardSecondsPerProductiveSecond = 1,
        activeDays = EarnItRuleStore.allDays.toSet(),
        startMinute = 0,
        endMinute = 1_440,
        enabled = true
    )
    EarnitV2Theme {
        Dashboard(
            ruleStates = listOf(RuleDashboardState(rule, 735, 180)),
            editingRule = null,
            apps = emptyList(),
            appsLoading = false,
            selectedProductivePackage = "com.duolingo",
            selectedProductivePackages = setOf("com.duolingo"),
            selectedBlockedPackages = setOf("com.instagram.android"),
            selectedRequirements = emptyList(),
            requirementPickerOpen = false,
            requirementSearch = "",
            selectedRequirementPackage = null,
            selectedRequirementMinutes = 10,
            editingRequirementIndex = null,
            selectedRatio = 1,
            selectedActiveDays = EarnItRuleStore.allDays.toSet(),
            selectedStartMinute = 0,
            selectedEndMinute = 1_440,
            selectedTimeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440)),
            scheduleWindowEditorOpen = false,
            editingScheduleWindowIndex = null,
            scheduleEditorStartMinute = 9 * 60,
            scheduleEditorEndMinute = 17 * 60,
            productivePickerOpen = false,
            blockedPickerOpen = false,
            productiveSearch = "",
            blockedSearch = "",
            usageAccessGranted = true,
            usageStatusMessage = "Tracking enabled rules today.",
            ruleStatusMessage = "1 rule saved. Enabled.",
            accessibilityServiceEnabled = true,
            showDeveloperTools = true,
            manageRulesOpen = false,
            pauseExpirations = emptyMap(),
            strictModeOpen = false,
            strictModeState = StrictModeState(),
            globalStrictModeConfiguration = null,
            pendingStrictModeAction = null,
            chargerSession = null,
            chargingState = ChargingState(false, false),
            selectedRuleDetailId = null,
            settingsOpen = false,
            analyticsOpen = false,
            franklinDashboardOpen = false,
            franklinCalendarOpen = false,
            analyticsRange = AnalyticsRange.SevenDays,
            analyticsSelectedDate = LocalDate.now(),
            analyticsState = AnalyticsUiState.Loading,
            analyticsInsights = emptyList(),
            analyticsAppPackage = null,
            ruleTypeSelectionOpen = false,
            unavailableRuleType = null,
            deepWorkSession = DeepWorkSession(),
            deepWorkSetupOpen = false,
            entitlementState = previewEntitlement,
            featurePolicy = FeatureAccessPolicy(previewEntitlement),
            purchaseState = PurchaseState.Idle,
            purchaseProvider = previewPurchaseProvider,
            subscriptionConfig = SubscriptionConfig.Placeholder,
            proFlowState = null,
            onProFlowChange = {},
            onCloseProFlow = {},
            onPurchasePlan = {},
            onRestorePurchases = {},
            onSimulateEntitlement = {},
            onResetEntitlement = {},
            premiumSimulatorOpen = false,
            onOpenPremiumSimulator = {},
            onClosePremiumSimulator = {},
            onOpenDeepWork = {},
            onDismissDeepWorkSetup = {},
            onStartDeepWork = {},
            onActivateDeepWork = {},
            onContinueDeepWork = {},
            onFinishDeepWork = {},
            onOpenUsageAccessSettings = {},
            onOpenAccessibilitySettings = {},
            onContinueSetup = {},
            onOpenSettings = {},
            onOpenAnalytics = {},
            onCloseAnalytics = {},
            onAnalyticsRangeChange = {},
            onAnalyticsDateChange = {},
            onOpenAnalyticsApp = {},
            onBackFromAnalyticsApp = {},
            onCloseSettings = {},
            onOpenBenjaminFranklin = {},
            onCloseBenjaminFranklin = {},
            onOpenBenjaminFranklinCalendar = {},
            onCloseBenjaminFranklinCalendar = {},
            pendingFeedbackCount = 0,
            shakeToReportEnabled = false,
            shakeSettingError = null,
            onOpenFeedback = {},
            onShakeToReportChange = {},
            onDebugShakeFeedback = {},
            onClearDebugFeedback = {},
            onReplayOnboarding = {},
            onOpenStrictMode = {},
            onCloseStrictMode = {},
            onSaveStrictModeConfiguration = {},
            onBeginStrictModeActivation = { _, _, _ -> },
            onCancelStrictModeActivation = {},
            onBeginStrictModeDeactivation = {},
            onConfirmChargerStrictModeDeactivation = { PendingActionValidation.Invalid("Unavailable") },
            onCancelStrictModeDeactivation = {},
            onConfirmStrictModeDeactivation = {},
            onKeepStrictModeActive = {},
            onStrictModeTick = {},
            onAuthorizeCharger = {},
            onVerifyPin = { PinVerificationResult.Rejected("Unavailable") },
            onConfirmProtectedAction = {},
            onCancelProtectedRequest = {},
            onRequestStrictModeMethodChange = { _, _, _ -> },
            onStrictModeBlockedAction = {},
            onOpenEarnApp = {},
            onAddRule = {},
            onBackFromRuleTypeSelection = {},
            onBackFromUnavailableRuleType = {},
            onSelectRuleType = {},
            onEditRule = {},
            onToggleRuleEnabled = {},
            onToggleDailyCommitment = { _, _ -> },
            onPauseRuleFor = { _, _, _ -> },
            onResumeRule = {},
            onPauseTimerTick = {},
            onDeleteRule = {},
            onToggleManageRules = {},
            onOpenRuleDetail = {},
            onBackFromRuleDetail = {},
            onCancelEditingRule = {},
            onOpenProductivePicker = {},
            onCloseProductivePicker = {},
            onOpenBlockedPicker = {},
            onCloseBlockedPicker = {},
            onDismissBlockedPicker = {},
            onProductiveSearchChange = {},
            onBlockedSearchChange = {},
            onSelectProductiveApp = {},
            onOpenRequirementPicker = {},
            onCloseRequirementPicker = {},
            onDismissRequirementAppPicker = {},
            onUseRequirementApp = {},
            onRequirementSearchChange = {},
            onSelectRequirementApp = {},
            onSelectRequirementMinutes = {},
            onSaveRequirement = {},
            onEditRequirement = {},
            onDeleteRequirement = {},
            onToggleBlockedApp = {},
            onSelectRatio = {},
            onToggleActiveDay = {},
            onSelectActiveDays = {},
            onSelectAllDay = {},
            onSetHours = {},
            onAddTimeWindow = {},
            onEditTimeWindow = {},
            onRemoveTimeWindow = {},
            onSaveTimeWindow = {},
            onCancelTimeWindow = {},
            onEditStartTime = {},
            onEditEndTime = {},
            builderStep = RuleBuilderStep.Earn,
            onBuilderStepChange = {},
            onSaveRule = {}
        )
    }
}
