package com.example.earnitv2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MockPurchaseOutcome {
    Success, Processing, Pending, Failed, Cancelled, RestoreSuccess, RestoreNotFound
}

sealed interface PurchaseState {
    data object Idle : PurchaseState
    data object LoadingPlans : PurchaseState
    data object Processing : PurchaseState
    data object Success : PurchaseState
    data object Pending : PurchaseState
    data class Failed(val message: String) : PurchaseState
    data object Cancelled : PurchaseState
    data object RestoreSuccess : PurchaseState
    data object RestoreNotFound : PurchaseState
    data class Unavailable(val message: String) : PurchaseState
}

interface PurchaseProvider {
    val state: StateFlow<PurchaseState>
    fun loadPlans(): List<SubscriptionPlan>
    fun purchase(plan: SubscriptionPlan)
    fun restorePurchases()
    fun openManageSubscription()
}

class LocalPurchaseProvider(
    private val config: SubscriptionConfig,
    private val entitlementController: DebugEntitlementController,
    private val simulationEnabled: Boolean
) : PurchaseProvider {
    private val mutableState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    override val state: StateFlow<PurchaseState> = mutableState.asStateFlow()
    private val mutableNextPurchaseOutcome = MutableStateFlow(MockPurchaseOutcome.Success)
    private val mutableNextRestoreOutcome = MutableStateFlow(MockPurchaseOutcome.RestoreSuccess)
    val nextPurchaseOutcomeState: StateFlow<MockPurchaseOutcome> = mutableNextPurchaseOutcome.asStateFlow()
    val nextRestoreOutcomeState: StateFlow<MockPurchaseOutcome> = mutableNextRestoreOutcome.asStateFlow()
    var nextPurchaseOutcome: MockPurchaseOutcome
        get() = mutableNextPurchaseOutcome.value
        set(value) { mutableNextPurchaseOutcome.value = value }
    var nextRestoreOutcome: MockPurchaseOutcome
        get() = mutableNextRestoreOutcome.value
        set(value) { mutableNextRestoreOutcome.value = value }
    var nextOutcome: MockPurchaseOutcome
        get() = nextPurchaseOutcome
        set(value) {
            if (value == MockPurchaseOutcome.RestoreSuccess || value == MockPurchaseOutcome.RestoreNotFound) {
                nextRestoreOutcome = value
            } else {
                nextPurchaseOutcome = value
            }
        }

    override fun loadPlans(): List<SubscriptionPlan> = listOf(config.annual, config.monthly)

    override fun purchase(plan: SubscriptionPlan) {
        if (!simulationEnabled) {
            mutableState.value = PurchaseState.Unavailable("Purchases are not available in this build.")
            return
        }
        if (mutableState.value == PurchaseState.Processing) return
        mutableState.value = PurchaseState.Processing
        when (nextPurchaseOutcome) {
            MockPurchaseOutcome.Success -> {
                entitlementController.simulate(
                    EntitlementState(EntitlementStatus.Active, EntitlementSource.Purchase)
                )
                mutableState.value = PurchaseState.Success
            }
            MockPurchaseOutcome.Processing -> Unit
            MockPurchaseOutcome.Pending -> mutableState.value = PurchaseState.Pending
            MockPurchaseOutcome.Failed -> mutableState.value =
                PurchaseState.Failed("We couldn't complete your purchase. Please try again.")
            MockPurchaseOutcome.Cancelled -> mutableState.value = PurchaseState.Cancelled
            MockPurchaseOutcome.RestoreSuccess, MockPurchaseOutcome.RestoreNotFound ->
                mutableState.value = PurchaseState.Failed("Choose a purchase outcome for checkout.")
        }
    }

    override fun restorePurchases() {
        if (!simulationEnabled) {
            mutableState.value =
                PurchaseState.Unavailable("Purchase restoration will be available when billing is connected.")
            return
        }
        mutableState.value = PurchaseState.Processing
        if (nextRestoreOutcome == MockPurchaseOutcome.RestoreSuccess) {
            entitlementController.simulate(
                EntitlementState(EntitlementStatus.Active, EntitlementSource.Purchase)
            )
            mutableState.value = PurchaseState.RestoreSuccess
        } else {
            mutableState.value = PurchaseState.RestoreNotFound
        }
    }

    override fun openManageSubscription() {
        mutableState.value = PurchaseState.Unavailable(
            if (simulationEnabled) "Subscription management will open in Google Play after Billing is connected."
            else "Subscription management is not available in this build."
        )
    }
}
