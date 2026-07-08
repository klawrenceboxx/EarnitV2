package com.example.earnitv2

enum class EarnItPermissionStatus {
    Granted,
    NeedsAttention
}

data class EarnItAppUiState(
    val packageName: String,
    val name: String
)

data class RuleCardUiState(
    val ruleId: String,
    val earnAppName: String,
    val earnAppPackage: String,
    val rewardApps: List<EarnItAppUiState>,
    val rewardAppCount: Int,
    val availableRewardTimeLabel: String,
    val productiveUsageLabel: String?,
    val scheduleStatusLabel: String,
    val enabled: Boolean,
    val paused: Boolean,
    val attentionLabel: String?
)

data class RuleDetailUiState(
    val card: RuleCardUiState,
    val ruleAgreementSummary: String,
    val scheduleSummary: String,
    val scheduleExplanation: String,
    val canEdit: Boolean,
    val canPause: Boolean,
    val canDelete: Boolean
)

data class RuleDraftUiState(
    val selectedEarnApp: EarnItAppUiState?,
    val selectedRewardApps: List<EarnItAppUiState>,
    val exchangeSelection: Int,
    val activeDays: Set<Int>,
    val startMinute: Int,
    val endMinute: Int,
    val canReview: Boolean,
    val canSave: Boolean,
    val reviewSummary: String
)

data class PermissionSetupUiState(
    val earningProgressStatus: EarnItPermissionStatus,
    val appBlockingStatus: EarnItPermissionStatus,
    val isReady: Boolean,
    val needsAttention: Boolean,
    val repairTargetLabels: List<String>
)

object EarnItUiStateAdapters {
    fun ruleCard(
        rule: EarnItRuleStore.Rule,
        productiveUsageSeconds: Long?,
        remainingRewardSeconds: Long,
        usageAccessGranted: Boolean,
        appBlockingEnabled: Boolean,
        isActiveNow: Boolean
    ): RuleCardUiState {
        return RuleCardUiState(
            ruleId = rule.id,
            earnAppName = rule.productiveName,
            earnAppPackage = rule.productivePackage,
            rewardApps = rule.blockedApps.map { it.toUiState() },
            rewardAppCount = rule.blockedApps.size,
            availableRewardTimeLabel = EarnItUiFormatters.rewardTimeAvailability(remainingRewardSeconds),
            productiveUsageLabel = productiveUsageSeconds?.let { EarnItUiFormatters.productiveUsage(it) },
            scheduleStatusLabel = EarnItUiFormatters.scheduleStatus(rule, isActiveNow),
            enabled = rule.enabled,
            paused = !rule.enabled,
            attentionLabel = attentionLabel(usageAccessGranted, appBlockingEnabled)
        )
    }

    fun ruleDetail(
        card: RuleCardUiState,
        rule: EarnItRuleStore.Rule
    ): RuleDetailUiState {
        return RuleDetailUiState(
            card = card,
            ruleAgreementSummary = EarnItUiFormatters.exchangeAgreement(rule),
            scheduleSummary = EarnItUiFormatters.scheduleSummary(rule),
            scheduleExplanation = EarnItUiFormatters.scheduleExplanation(rule),
            canEdit = true,
            canPause = rule.enabled,
            canDelete = true
        )
    }

    fun ruleDraft(
        selectedEarnApp: EarnItRuleStore.LaunchableApp?,
        selectedRewardApps: List<EarnItRuleStore.RuleApp>,
        exchangeSelection: Int,
        activeDays: Set<Int>,
        startMinute: Int,
        endMinute: Int
    ): RuleDraftUiState {
        val validExchange = exchangeSelection in EarnItRuleStore.allowedRatios
        val validSchedule = activeDays.any { it in EarnItRuleStore.allDays } &&
            startMinute in 0..1_439 &&
            endMinute in 1..1_440
        val ready = selectedEarnApp != null &&
            selectedRewardApps.isNotEmpty() &&
            validExchange &&
            validSchedule
        val earnApp = selectedEarnApp?.let { EarnItAppUiState(it.packageName, it.name) }
        val rewardApps = selectedRewardApps.map { it.toUiState() }

        return RuleDraftUiState(
            selectedEarnApp = earnApp,
            selectedRewardApps = rewardApps,
            exchangeSelection = exchangeSelection,
            activeDays = activeDays.filter { it in EarnItRuleStore.allDays }.toSet(),
            startMinute = startMinute.coerceIn(0, 1_439),
            endMinute = endMinute.coerceIn(1, 1_440),
            canReview = ready,
            canSave = ready,
            reviewSummary = EarnItUiFormatters.draftReviewSummary(
                earnAppName = earnApp?.name,
                rewardAppNames = rewardApps.map { it.name },
                exchangeSelection = exchangeSelection,
                activeDays = activeDays,
                startMinute = startMinute,
                endMinute = endMinute
            )
        )
    }

    fun permissionSetup(
        usageAccessGranted: Boolean,
        appBlockingEnabled: Boolean
    ): PermissionSetupUiState {
        val repairTargets = buildList {
            if (!usageAccessGranted) add("Earning progress")
            if (!appBlockingEnabled) add("App blocking")
        }
        val ready = usageAccessGranted && appBlockingEnabled
        return PermissionSetupUiState(
            earningProgressStatus = if (usageAccessGranted) {
                EarnItPermissionStatus.Granted
            } else {
                EarnItPermissionStatus.NeedsAttention
            },
            appBlockingStatus = if (appBlockingEnabled) {
                EarnItPermissionStatus.Granted
            } else {
                EarnItPermissionStatus.NeedsAttention
            },
            isReady = ready,
            needsAttention = !ready,
            repairTargetLabels = repairTargets
        )
    }

    private fun attentionLabel(
        usageAccessGranted: Boolean,
        appBlockingEnabled: Boolean
    ): String? {
        return when {
            usageAccessGranted && appBlockingEnabled -> null
            !usageAccessGranted && !appBlockingEnabled -> "Earning progress and app blocking need attention"
            !usageAccessGranted -> "Earning progress needs attention"
            else -> "App blocking needs attention"
        }
    }

    private fun EarnItRuleStore.RuleApp.toUiState(): EarnItAppUiState {
        return EarnItAppUiState(packageName = packageName, name = name)
    }
}

object EarnItUiFormatters {
    fun rewardTimeAvailability(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0L)
        if (safeSeconds == 0L) return "No Reward Time"
        return "${formatWholeMinutes(safeSeconds)} available"
    }

    fun productiveUsage(totalSeconds: Long): String {
        return "${formatWholeMinutes(totalSeconds.coerceAtLeast(0L))} productive today"
    }

    fun exchangeAgreement(rule: EarnItRuleStore.Rule): String {
        return "Every 1 min in ${rule.productiveName} earns " +
            "${rule.rewardSecondsPerProductiveSecond} min of Reward Time."
    }

    fun exchangeSummary(exchangeSelection: Int): String {
        return "Every 1 min earns $exchangeSelection min Reward Time"
    }

    fun scheduleStatus(rule: EarnItRuleStore.Rule, isActiveNow: Boolean): String {
        return when {
            !rule.enabled -> "Rule paused"
            isActiveNow -> "Active now"
            else -> "Unrestricted right now"
        }
    }

    fun scheduleSummary(rule: EarnItRuleStore.Rule): String {
        return rule.scheduleLabel
    }

    fun scheduleExplanation(rule: EarnItRuleStore.Rule): String {
        return if (rule.activeDays == EarnItRuleStore.allDays.toSet() &&
            rule.startMinute == 0 &&
            rule.endMinute == 1_440
        ) {
            "This Rule applies every day, all day."
        } else {
            "Outside these times, your Reward Apps are unrestricted by this Rule."
        }
    }

    fun draftReviewSummary(
        earnAppName: String?,
        rewardAppNames: List<String>,
        exchangeSelection: Int,
        activeDays: Set<Int>,
        startMinute: Int,
        endMinute: Int
    ): String {
        val parts = mutableListOf<String>()
        if (!earnAppName.isNullOrBlank()) {
            parts.add("When I use $earnAppName")
        }
        if (exchangeSelection in EarnItRuleStore.allowedRatios) {
            parts.add(exchangeSummary(exchangeSelection))
        }
        if (rewardAppNames.isNotEmpty()) {
            parts.add("For ${rewardAppNames.joinToString(", ")}")
        }
        val validDays = activeDays.filter { it in EarnItRuleStore.allDays }.toSet()
        if (validDays.isNotEmpty() && startMinute in 0..1_439 && endMinute in 1..1_440) {
            parts.add(scheduleLabel(validDays, startMinute, endMinute))
        }
        return parts.joinToString("\n")
    }

    private fun scheduleLabel(activeDays: Set<Int>, startMinute: Int, endMinute: Int): String {
        val dayLabel = if (activeDays == EarnItRuleStore.allDays.toSet()) {
            "Every day"
        } else {
            activeDays.sorted().joinToString(" ") { EarnItRuleStore.dayShortName(it) }
        }
        return "$dayLabel ${EarnItRuleStore.formatMinute(startMinute)}-${EarnItRuleStore.formatMinute(endMinute)}"
    }

    private fun formatWholeMinutes(totalSeconds: Long): String {
        val minutes = totalSeconds / 60L
        return if (minutes > 0L) "${minutes} min" else "<1 min"
    }
}
