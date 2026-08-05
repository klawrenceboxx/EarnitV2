package com.kaleel.earnitv2

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
    val earnApps: List<EarnItAppUiState>,
    val rewardApps: List<EarnItAppUiState>,
    val rewardAppCount: Int,
    val availableRewardTimeLabel: String,
    val productiveUsageLabel: String?,
    val scheduleStatusLabel: String,
    val enabled: Boolean,
    val paused: Boolean,
    val attentionLabel: String?,
    val websiteCount: Int = 0
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
    val selectedEarnApps: List<EarnItAppUiState>,
    val selectedRewardApps: List<EarnItAppUiState>,
    val exchangeSelection: Int,
    val activeDays: Set<Int>,
    val startMinute: Int,
    val endMinute: Int,
    val timeWindows: List<EarnItRuleStore.TimeWindow>,
    val canReview: Boolean,
    val canSave: Boolean,
    val reviewSummary: String,
    val selectedBlockedDomains: List<String> = emptyList()
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
        val earnApps = rule.earnApps.map { it.toUiState() }
        return RuleCardUiState(
            ruleId = rule.id,
            earnApps = earnApps,
            rewardApps = rule.blockedApps.map { it.toUiState() },
            rewardAppCount = rule.blockedApps.size,
            availableRewardTimeLabel = EarnItUiFormatters.rewardTimeAvailability(remainingRewardSeconds),
            productiveUsageLabel = productiveUsageSeconds?.let { EarnItUiFormatters.productiveUsage(it) },
            scheduleStatusLabel = EarnItUiFormatters.scheduleStatus(rule, isActiveNow),
            enabled = rule.enabled,
            paused = !rule.enabled,
            attentionLabel = attentionLabel(usageAccessGranted, appBlockingEnabled),
            websiteCount = rule.normalizedBlockedDomains.size
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
        selectedEarnApps: List<EarnItRuleStore.LaunchableApp>,
        selectedRewardApps: List<EarnItRuleStore.RuleApp>,
        exchangeSelection: Int,
        activeDays: Set<Int>,
        timeWindows: List<EarnItRuleStore.TimeWindow>,
        selectedBlockedDomains: List<String> = emptyList()
    ): RuleDraftUiState {
        val validExchange = exchangeSelection > 0
        val validDays = activeDays.filter { it in EarnItRuleStore.allDays }.toSet()
        val normalizedWindows = EarnItRuleStore.normalizeTimeWindows(timeWindows)
        val validSchedule = validDays.isNotEmpty() && normalizedWindows.isNotEmpty()
        val earnApps = selectedEarnApps.distinctBy { it.packageName }.map { EarnItAppUiState(it.packageName, it.name) }
        val rewardApps = selectedRewardApps.distinctBy { it.packageName }.map { it.toUiState() }
        val ready = earnApps.isNotEmpty() &&
            (rewardApps.isNotEmpty() || selectedBlockedDomains.isNotEmpty()) &&
            validExchange &&
            validSchedule
        val firstWindow = normalizedWindows.first()

        return RuleDraftUiState(
            selectedEarnApps = earnApps,
            selectedRewardApps = rewardApps,
            exchangeSelection = exchangeSelection.coerceAtLeast(1),
            activeDays = validDays,
            startMinute = firstWindow.startMinute,
            endMinute = firstWindow.endMinute,
            timeWindows = normalizedWindows,
            canReview = ready,
            canSave = ready,
            reviewSummary = EarnItUiFormatters.draftReviewSummary(
                earnAppNames = earnApps.map { it.name },
                rewardAppNames = rewardApps.map { it.name },
                exchangeSelection = exchangeSelection,
                activeDays = validDays,
                timeWindows = normalizedWindows
            ),
            selectedBlockedDomains = selectedBlockedDomains.mapNotNull(DomainNormalizer::normalize).distinct().sorted()
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
        return exchangeAgreement(
            earnAppNames = rule.earnApps.map { it.name },
            exchangeSelection = rule.rewardSecondsPerProductiveSecond
        )
    }

    fun exchangeAgreement(earnAppNames: List<String>, exchangeSelection: Int): String {
        return "Every 10 min ${earnAppContext(earnAppNames)} earns " +
            "${exchangeSelection.coerceAtLeast(1)} min Reward Time."
    }

    fun earnAppContext(earnAppNames: List<String>): String {
        return when (earnAppNames.size) {
            0 -> "in your Earn App"
            1 -> "in ${earnAppNames.single()}"
            else -> "across selected Earn Apps"
        }
    }

    fun compactAppNames(appNames: List<String>): String {
        val visibleNames = appNames.take(2).joinToString(", ")
        val remainingCount = (appNames.size - 2).coerceAtLeast(0)
        return if (remainingCount > 0) "$visibleNames +$remainingCount more" else visibleNames
    }

    fun exchangeSummary(exchangeSelection: Int): String {
        return "Every 10 min earns ${exchangeSelection.coerceAtLeast(1)} min Reward Time"
    }

    fun savedRewardTime(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0L)
        return if (safeSeconds == 0L) {
            "No Reward Time saved today"
        } else {
            "${formatWholeMinutes(safeSeconds)} Reward Time saved"
        }
    }

    fun remainingRewardTime(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0L)
        return if (safeSeconds == 0L) {
            "No Reward Time remains today"
        } else {
            "${formatWholeMinutes(safeSeconds)} Reward Time remains today"
        }
    }

    fun scheduleStatus(rule: EarnItRuleStore.Rule, isActiveNow: Boolean): String {
        return when {
            rule.inactiveReason == RuleInactiveReason.PremiumExpired -> "Premium inactive"
            !rule.enabled -> "Rule paused"
            rule.type == EarnItRuleStore.RuleType.ScheduledBlock && isActiveNow -> "Block active now"
            rule.type == EarnItRuleStore.RuleType.ScheduledBlock -> "Outside block schedule"
            rule.type == EarnItRuleStore.RuleType.CompleteToUnlock && isActiveNow -> "Requirements active now"
            rule.type == EarnItRuleStore.RuleType.CompleteToUnlock -> "Apps unrestricted right now"
            isActiveNow -> "Active now"
            else -> "Unrestricted right now"
        }
    }

    fun scheduleSummary(rule: EarnItRuleStore.Rule): String {
        return rule.scheduleLabel
    }

    fun scheduleExplanation(rule: EarnItRuleStore.Rule): String {
        if (rule.effectiveTimeWindows.size == 1 &&
            rule.effectiveTimeWindows.first() == EarnItRuleStore.TimeWindow(0, 1_440) &&
            rule.activeDays == EarnItRuleStore.allDays.toSet()
        ) {
            return ""
        }
        return when (rule.type) {
            EarnItRuleStore.RuleType.EarnRewardTime -> "Outside these times, Reward Apps are unrestricted by this Rule."
            EarnItRuleStore.RuleType.CompleteToUnlock -> "Outside these times, Apps to Unlock are unrestricted by this Rule."
            EarnItRuleStore.RuleType.ScheduledBlock -> "Outside these times, Blocked Apps are unrestricted by this Rule."
        }
    }

    fun draftReviewSummary(
        earnAppNames: List<String>,
        rewardAppNames: List<String>,
        exchangeSelection: Int,
        activeDays: Set<Int>,
        timeWindows: List<EarnItRuleStore.TimeWindow>
    ): String {
        val parts = mutableListOf<String>()
        if (earnAppNames.isNotEmpty()) {
            parts.add("When I use ${compactAppNames(earnAppNames)}")
        }
        if (exchangeSelection > 0) {
            parts.add(exchangeSummary(exchangeSelection))
        }
        if (rewardAppNames.isNotEmpty()) {
            parts.add("For ${compactAppNames(rewardAppNames)}")
        }
        val validDays = activeDays.filter { it in EarnItRuleStore.allDays }.toSet()
        if (validDays.isNotEmpty()) {
            parts.add(EarnItRuleStore.scheduleSummary(validDays, timeWindows))
        }
        return parts.joinToString("\n")
    }

    private fun formatWholeMinutes(totalSeconds: Long): String {
        val minutes = totalSeconds / 60L
        return if (minutes > 0L) "${minutes} min" else "<1 min"
    }
}
