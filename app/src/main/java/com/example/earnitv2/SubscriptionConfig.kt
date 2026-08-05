package com.kaleel.earnitv2

import java.math.BigDecimal
import java.math.RoundingMode

data class SubscriptionPlan(
    val id: String,
    val displayName: String,
    val price: BigDecimal,
    val billingPeriod: BillingPeriod,
    val trialDays: Int? = null
) {
    val formattedPrice: String get() = "$${price.setScale(2, RoundingMode.HALF_UP)}"
}

enum class BillingPeriod { Monthly, Annual }

/**
 * Placeholder catalog until Google Play supplies localized ProductDetails.
 * Annual trial support is modeled here but intentionally disabled for the pre-Billing build.
 */
data class SubscriptionConfig(
    val monthly: SubscriptionPlan,
    val annual: SubscriptionPlan,
    val annualTrialEnabled: Boolean,
    val entitlementGracePeriodMillis: Long,
    val termsUrl: String? = null,
    val privacyUrl: String? = null
) {
    val yearlySavingsPercent: Int
        get() {
            val fullYear = monthly.price.multiply(BigDecimal(12))
            val savings = BigDecimal.ONE.subtract(annual.price.divide(fullYear, 8, RoundingMode.HALF_UP))
            return savings.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toInt()
        }

    val annualMonthlyEquivalent: BigDecimal
        get() = annual.price.divide(BigDecimal(12), 2, RoundingMode.HALF_UP)

    companion object {
        val Placeholder = SubscriptionConfig(
            monthly = SubscriptionPlan(
                id = "earnit_pro_monthly_placeholder",
                displayName = "Monthly",
                price = BigDecimal("6.99"),
                billingPeriod = BillingPeriod.Monthly
            ),
            annual = SubscriptionPlan(
                id = "earnit_pro_annual_placeholder",
                displayName = "Annual",
                price = BigDecimal("39.99"),
                billingPeriod = BillingPeriod.Annual,
                trialDays = 7
            ),
            annualTrialEnabled = false,
            entitlementGracePeriodMillis = 72L * 60L * 60L * 1_000L
        )
    }
}
