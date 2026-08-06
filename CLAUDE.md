# EarnItV2 — CLAUDE.md

Android app that gates access to distracting apps/websites behind productive app usage or task completion.
Package: `com.kaleel.earnitv2`

> **Note:** Package was renamed from `com.example.earnitv2` → `com.kaleel.earnitv2` in commit ac6613d. All source files use the new package; the manifest app ID remains `com.example.earnitv2` (per build config app ID suffix logic).

---

## Required project context
Before planning or implementing substantial work, read:
- `docs/PRODUCT_CONTEXT.md`
- `docs/CURRENT_PRIORITIES.md`
- `docs/DESIGN_SYSTEM.md` — before building or modifying any UI

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material3
- **Min SDK:** 26 | **Target/Compile SDK:** 36 (minor API 1)
- **Persistence:** SharedPreferences only — no Room, no SQLite
- **Background work:** WorkManager (feedback upload)
- **Analytics:** PostHog (events + screen views) + Firebase Crashlytics (crash reporting)
- **Feedback backend:** Supabase (URL/key in `local.properties` or env vars)
- **No DI framework** — Context passed manually everywhere
- **No third-party networking lib** — plain HTTP for Supabase

**Key dependencies (via version catalog):**
`core-ktx`, `lifecycle-runtime-ktx`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`,
`work-runtime-ktx`, `exifinterface`, Compose BOM (UI + Material3), JUnit4, `org.json` (test),
`posthog-android`, `firebase-crashlytics`

**Build variants:**
| Variant | App ID suffix | Entitlement simulator | Beta entitlement |
|---|---|---|---|
| `debug` | — | on | off |
| `internal` | `.internal` | on | **on** (grants Pro) |
| `release` | — | off | off |

> Note: `isMinifyEnabled = false` in release — ProGuard not active despite config being present.

---

## Architecture

### Data flow for blocking

```
AccessibilityEvent (window change)
  → EarnItAccessibilityService
      → EarnItRuleStore.getRules(context)          // reads SharedPrefs
      → RuleAccessEvaluator.evaluate(rules, pkg, day, minute, runtimeState)
           runtimeState = { rule ->
               remainingRewardSeconds  = RewardLedger.snapshot(...)
               requirementProgressSeconds = RewardLedger.completionProgress(...)
               hasDailyCommitment = BenjaminFranklinStore.today(...) != null
           }
      → if denied → startActivity(BlockedActivity)
      → if allowed + EarnRewardTime → consumeRunnable ticks every 1s via Handler
            → RewardLedger.consumeRewardSeconds(...)
```

### Key files

All paths relative to `app/src/main/java/com/kaleel/earnitv2/` unless noted.

**Core blocking engine**

| File | Role | Key classes/functions |
|---|---|---|
| `EarnItAccessibilityService.kt` | Central event loop. Handles app/browser foreground, Deep Work blocking, reward consumption tick | `EarnItAccessibilityService`, `consumeRunnable` |
| `EarnItRuleStore.kt` | Rule CRUD + serialization. Object singleton | `EarnItRuleStore`, `Rule`, `RuleType`, `LaunchableApp`, `RuleApp`, `TimeWindow` |
| `RuleAccessEvaluator.kt` | Pure evaluation logic (zero Android deps). Returns `Result(denials, spendRule)` | `RuleAccessEvaluator`, `RuleRuntimeState`, `DenialReason` |
| `RewardLedger.kt` | Earn/consume reward seconds. Reads `UsageStatsManager` for productive usage | `RewardLedger`, `LedgerSnapshot` |
| `ProtectedTarget.kt` | `ProtectedTarget` sealed class (App/Website). `ProtectedTargetResolver` picks app vs domain. `ProtectedUsageClock` tracks foreground ticks | `ProtectedTarget`, `ProtectedTargetResolver`, `ProtectedUsageClock` |

**UI — Activities & screens**

| File | Role | Key composables/classes |
|---|---|---|
| `MainActivity.kt` | All main navigation + UI (~1500+ lines). Very large — read in sections | Navigation host, settings, rule list, rule detail routing |
| `BlockedActivity.kt` | Fullscreen block gate. All `BlockedScreen` UI + `DailyCommitmentScreen` | `BlockedActivity`, `BlockedScreen`, `DailyCommitmentScreen` |
| `EarnItHome.kt` | Home screen composable showing rule cards + permission state | `EarnItHome`, `HomeRuleUiState` |
| `EarnItRuleDetail.kt` | Rule detail/management screen. Includes pause dialog with 10-second countdown | `EarnItRuleDetail`, `PauseOptionsCard`, `EditRuleGateCard` |
| `EarnItRuleBuilder.kt` | Multi-step rule creation wizard (Earn → Reward → Exchange → Schedule → Review) | `RuleBuilderStep`, `EarnItRuleBuilder` |
| `EarnItRuleTypeSelection.kt` | Screen to pick rule type before entering builder | `EarnItRuleTypeSelection`, `RuleTypeOption` |
| `EarnItStrictMode.kt` | Strict Mode setup/management UI | `StrictModeSetupState`, strict mode composables |
| `EarnItSetup.kt` | First-launch setup flow (legacy, pre-onboarding) | `FirstLaunchStep` |
| `EarnItOnboarding.kt` | 8-step onboarding flow (Value → Example → Permissions → Ready) | `EarnItOnboarding` composable |
| `EarnItPro.kt` | Pro/subscription flow: Gate, Intro, Plans, Compare, Restore, PurchaseStatus | `ProRoute`, `ProFlowState`, plan selection UI |
| `BenjaminFranklinScreen.kt` | BF history + streak screen, commitment review dialog | `BenjaminFranklinHistoryScreen`, `CommitmentReviewDialog` |
| `FeedbackScreen.kt` | User feedback form (category, message, screenshot, email) | `FeedbackScreen` |

**Analytics**

| File | Role | Key classes/functions |
|---|---|---|
| `AnalyticsScreen.kt` | All analytics UI. Today + 7-day tabs | `AnalyticsScreen`, `AnalyticsHourlyBarChart`, `AnalyticsHeatmap`, `AnalyticsDailyBarChart`, `PeakDistractionSection`, `RewardActivitySection` |
| `AnalyticsModels.kt` | Data classes + aggregation logic + helper fns | `AnalyticsSummary`, `DailyUsageSummary`, `AppUsageSummary`, `AnalyticsPeriods`, `AnalyticsAggregator`, `detectPeakWindow`, `analyticsDuration`, `formatHour` |
| `AnalyticsRepository.kt` | Loads `UsageEvents` from system, builds `AnalyticsSummary`. Also contains `AnalyticsEventStore` for blocked-attempt tracking | `AnalyticsRepository`, `AnalyticsEventStore` |
| `AnalyticsInsights.kt` | Generates ranked insight cards from summary data; tracks display history | `AnalyticsInsightEngine`, `AnalyticsInsightHistoryStore`, `InsightCandidate`, `InsightType` |

**Stores & state**

| File | Role | Key classes/functions |
|---|---|---|
| `EarnItPauseStore.kt` | Temporary rule pausing with expiry timestamps | `EarnItPauseStore` |
| `BenjaminFranklinMode.kt` | `BenjaminFranklinStore` + `DailyCommitment` data class + `BfReminderPreferences` | `BenjaminFranklinStore`, `DailyCommitment`, `CompletionStatus` |
| `DeepWork.kt` | Deep Work session + store. Focus mode with DND integration. **Heavily minified — reformat before editing** | `DeepWorkStore`, `DeepWorkSession` |
| `StrictModeStore.kt` | Strict Mode state persistence | `StrictModeStore`, `StrictModeState`, `StrictModeLifecycleState` |
| `Entitlement.kt` | `FeatureAccessPolicy`, `EntitlementState`, `SharedPreferencesEntitlementRepository`, `DebugEntitlementController` | Premium feature gating |
| `EarnItOnboardingState.kt` | Onboarding step machine + `EarnItOnboardingStore` | `OnboardingStep`, `EarnItOnboardingStore`, step navigation fns |

**Policy & business logic**

| File | Role | Key classes/functions |
|---|---|---|
| `RuleEntitlementPolicy.kt` | Rule activation limits, downgrade reconciliation, expired-pause resolution | `RuleEntitlementPolicy`, `RuleActivationResult`, `RuleInactiveReason` |
| `StrictModePolicy.kt` | Whether strict mode protects a given rule | `StrictModePolicy`, `isStrictModeProtecting()`, `StrictModeRuleProtectionSummary` |
| `StrictModeCommitment.kt` | Commitment preset helpers (1h, 24h, 7d, custom) for strict mode setup | `StrictModeCommitmentPreset`, `selectCommitmentPreset()` |
| `StrictModeFoundation.kt` | Strict Mode base data classes and lifecycle types | `StrictModeProtectionMethod`, `StrictModeDurationType` |
| `StrictModePin.kt` | PIN-based strict mode deactivation | PIN verification logic |

**UI state & adapters**

| File | Role | Key classes/functions |
|---|---|---|
| `EarnItUiState.kt` | Shared UI state data classes + formatting. `EarnItUiStateAdapters` builds all card/draft/permission states | `RuleCardUiState`, `RuleDetailUiState`, `RuleDraftUiState`, `PermissionSetupUiState`, `EarnItUiStateAdapters`, `EarnItUiFormatters` |
| `CompleteToUnlockProgressUiState.kt` | Progress computation for CompleteToUnlock rules | `CompleteToUnlockRuleProgressUiState`, `CompleteRequirementUiState`, `completeToUnlockProgressUiState()` |
| `RuleTypePresentation.kt` | Rule type icon + label metadata; custom Canvas icons | `RuleTypePresentation`, `RuleTypeIcon`, `RuleTypeBadge` |

**Shared UI components**

| File | Role | Key composables/classes |
|---|---|---|
| `EarnItAppIcon.kt` | App icon composable with LRU bitmap cache (80 entries); fallback initial-letter tile | `EarnItAppIcon`, `EarnItAppInitialTile`, `EarnItAppIconCache` |
| `EarnItAppPicker.kt` | App picker filtering/classification by category or keyword | `AppPickerCategory`, `filterLaunchableApps`, `classifyLaunchableApp` |

**Feedback system**

| File | Role | Key classes/functions |
|---|---|---|
| `FeedbackModels.kt` | All feedback data types, validation, serialization | `FeedbackCategory`, `FeedbackSubmission`, `FeedbackDiagnostics`, `QueuedFeedback`, `FeedbackValidation` |
| `FeedbackViewModel.kt` | AndroidViewModel for feedback form | `FeedbackViewModel`, `FeedbackUiState`, `FeedbackPhase` |
| `FeedbackRepository.kt` | HTTP submission to Supabase + offline queue | `FeedbackRepository` |
| `FeedbackUploadWorker.kt` | WorkManager task for retry queue | `FeedbackUploadWorker` |
| `FeedbackDiagnosticsCollector.kt` | Gathers device/app diagnostics snapshot | `FeedbackDiagnosticsCollector`, `CrashMarkerStore` |
| `FeedbackImageProcessor.kt` | Screenshot capture + EXIF stripping for feedback | `FeedbackImageProcessor` |
| `FeedbackLifecycle.kt` | Shake-to-report detection, crash handler install, `EarnItCrashHandler` | `CrashMarkerStore`, shake detection |

**Notifications & receivers**

| File | Role | Key classes/functions |
|---|---|---|
| `BenjaminFranklinNotificationScheduler.kt` | AlarmManager scheduling for morning/evening BF reminders | `BenjaminFranklinNotificationScheduler` |
| `BenjaminFranklinReceiver.kt` | `BroadcastReceiver` for morning/evening notifications + BOOT_COMPLETED rescheduling | `BenjaminFranklinReceiver` |

**Analytics/product instrumentation**

| File | Role | Key classes/functions |
|---|---|---|
| `ProductAnalytics.kt` | Thin wrapper around PostHog + Firebase Crashlytics. Call `identify()` at startup, `capture()` anywhere | `ProductAnalytics.identify()`, `ProductAnalytics.capture()` |

**Infrastructure**

| File | Role | Key classes/functions |
|---|---|---|
| `MainApplication.kt` | App class: PostHog init, notification channel creation (`bf_reminders`) | `MainApplication` |
| `Charger.kt` | Charging state observation (BroadcastReceiver-based) for strict mode charger-protection method. Also contains `StrictModeProtectionStrengthPolicy` | `AndroidChargingStateObserver`, `ChargingState`, `StrictModeProtectionStrengthPolicy` |
| `PurchaseProvider.kt` | `PurchaseProvider` interface + `LocalPurchaseProvider` (mock/debug, no real Play Billing yet) | `PurchaseProvider`, `LocalPurchaseProvider`, `PurchaseState` |
| `SubscriptionConfig.kt` | Plan config + pricing (`$6.99/mo`, `$39.99/yr` placeholders). No Google Play Billing wired yet | `SubscriptionConfig`, `SubscriptionPlan` |
| `AppPackages.kt` | Package name constants (Duolingo, Instagram, Lichess) | `AppPackages` |

**Browser blocking**

| File | Role |
|---|---|
| `BrowserPage.kt` | Accessibility-based URL/hostname extraction from Chrome address bar |
| `BlockedDomain.kt` | Domain matching + normalization logic |
| `TrackedAppHandoff.kt` | Special-case logic for apps like Gemini launching through Google app |

**Theme**

| File | Role |
|---|---|
| `ui/theme/Color.kt` | Color tokens: `WarmCoral`, `WarmInk`, `WarmSurface`, `WarmSurfaceRaised`, `WarmOutline`, `WarmText`, `WarmTextMuted`, `WarmSuccess`, `CreamBackground`, etc. |
| `ui/theme/Theme.kt` | Material3 dark theme setup |
| `ui/theme/Type.kt` | Typography scale |

---

### Rule types

```kotlin
enum class RuleType { EarnRewardTime, CompleteToUnlock, ScheduledBlock }
```

- **EarnRewardTime** — use productive app to earn reward seconds; blocked app consumes them
- **CompleteToUnlock** — blocked app unlocks when all requirement durations are met for the day
- **ScheduledBlock** — hard block during configured days/time windows; no earn mechanic

### Day/time conventions (non-obvious)

- Days: 1=Mon, 2=Tue ... 6=Sat, 7=Sun — **NOT** `Calendar.DAY_OF_WEEK` order
- Minutes of day: 0–1439 for start, 1–1440 for end (1440 = midnight = "all day")
- Overnight windows (start > end) are supported and handled in `isActiveAt()`

### Persistence key spaces

| SharedPrefs name | Contents |
|---|---|
| `earnit_rule` | All rules (custom URL-encoded serialization) |
| `earnit_reward_ledger` | Per-rule earn/consume balances |
| `deep_work` | Deep Work session state |
| `benjamin_franklin_mode` | Daily commitment records keyed by `LocalDate` |
| `earnit_pause` | Active rule pauses with expiry |
| `earnit_debug_entitlement` | Simulated entitlement state (debug/internal only) |
| `earnit_analytics_events` | Blocked attempt counts keyed by `blocked\|date\|ruleId` |
| `earnit_analytics_insights` | Insight display history (type, relatedId, timesShown, lastShownEpochDay) |
| `earnit_setup` | Onboarding: `onboarding_seen`, `onboarding_step`, `onboarding_replay_active` |
| `feedback_identity` | `installation_id` UUID (shared by PostHog and feedback diagnostics) |
| `feedback_crash_marker` | Pending crash diagnostics from previous session (consumed once on next launch) |

### Rule serialization

Rules are serialized as URL-encoded fields joined by `\u001F`, records by `\u001E`.
Field order is **positional** — adding new fields must append to end (field 16 = `requiresDailyCommitment`).
Do not reorder or insert fields.

---

## Building & Running

```bash
# Debug build + install
./gradlew installDebug

# Internal build (Pro entitlement granted)
./gradlew installInternal

# Unit tests
./gradlew test

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

**Required setup for feedback:**
Add to `local.properties` (or set as env vars):
```
FEEDBACK_SUPABASE_URL=...
FEEDBACK_SUPABASE_ANON_KEY=...
POSTHOG_PROJECT_TOKEN=...
POSTHOG_HOST=...
```
If Supabase keys are missing, feedback upload is a no-op. If PostHog keys are missing, analytics silently drops events (crash in debug).

**Required permissions on device:**
1. Accessibility Service — Settings > Accessibility > EarnIt
2. Usage Access — Settings > Apps > Special app access > Usage access

---

## Coding Conventions

- **Object singletons** for all stores (`EarnItRuleStore`, `BenjaminFranklinStore`, `DeepWorkStore`, `RewardLedger`, `AnalyticsEventStore`). No injected dependencies.
- **Activity-level `mutableStateOf`** for UI state in `BlockedActivity` (no ViewModel there). `MainActivity` uses ViewModels via `viewModels()`.
- **UI state data classes** follow naming pattern `*UiState`, `*Presentation`, `*Screen`.
- **Pure Kotlin for business logic** — `RuleAccessEvaluator` has zero Android imports, making it unit-testable without instrumentation.
- **Unit tests** use plain builder functions (`earnRule(...)`, `completeRule(...)`) — no Mockito.
- **Composable previews** included in most UI files.
- `internal` visibility modifier used for navigation enums and implementation details within the same module.
- `commit()` (synchronous) used for SharedPrefs writes in the accessibility service path; `apply()` elsewhere.

---

## Benjamin Franklin Mode — Status & Rough Edges

`BenjaminFranklinMode.kt` contains `BenjaminFranklinStore`, `DailyCommitment` data class, and `BfReminderPreferences`.

**What works:**
- Store: `saveToday()`, `reviewToday()`, `get(date)` — all functional
- Evaluator integration: `hasDailyCommitment` fed into `RuleRuntimeState`; `DailyCommitmentMissing` denial reason implemented
- Block gate UI: `DailyCommitmentScreen` in `BlockedActivity.kt` — user sets commitment before unlock
- Serialization: `requiresDailyCommitment` field (field 16) persists correctly, only valid on `CompleteToUnlock` rules
- Toggle UI: `EarnItRuleDetail.kt` has a `Switch` for `requiresDailyCommitment` on `CompleteToUnlock` rules
- End-of-day review: `CommitmentReviewDialog` in `BenjaminFranklinScreen.kt` — calls `reviewToday()`. Surfaced via "Review today's commitment" button in `CompleteToUnlockDetailCard`
- History/streak screen: `BenjaminFranklinHistoryScreen` in `BenjaminFranklinScreen.kt` — shows streak count + last 60 days
- Notifications: morning commitment prompt + evening review reminder via `BenjaminFranklinNotificationScheduler` + `BenjaminFranklinReceiver`

**No known missing pieces** — the feature is end-to-end usable.

---

## Other Known Rough Edges

- **`DeepWork.kt` is heavily minified** — single-line functions, no whitespace. Do not edit without reformatting first; the logic is complex (DND integration, session credit, monotonic clock fallback).
- **`MainActivity.kt` is very large** (~1500+ lines) — likely needs splitting. Approach large edits carefully; read in sections.
- **`FeatureAccessPolicy.current(context)`** creates a new `SharedPreferencesEntitlementRepository` on every call (not cached). Works correctly but slightly wasteful.
- **`isMinifyEnabled = false`** in release build — dead code not removed. Intentional or oversight, unclear.
- **Browser blocking** requires exact placeholder visibility detection via accessibility tree inspection — fragile if Chrome updates its UI. `MAX_WEBSITE_REDIRECT_ATTEMPTS = 10` with 200ms retries is the current mitigation.
- **Gemini/Google app tracking** (`TrackedAppMatchPolicy`) has special-case logic for Gemini launching through the Google app — evidence-based heuristic, not guaranteed.
- **No real Play Billing** — `PurchaseProvider` is backed by `LocalPurchaseProvider` (mock). `SubscriptionConfig` has placeholder prices. Pro flow UI exists but no payment integration yet.
- **PostHog init crash in debug** if `POSTHOG_PROJECT_TOKEN` or `POSTHOG_HOST` are missing from `local.properties` — intentional to surface misconfiguration early.

---

## Active work-in-progress

Things mid-change or recently discussed — do not assume these are stable architecture:

- **Analytics UI polish:** `AnalyticsHourlyBarChart` (reward activity, Today tab) and `AnalyticsHeatmap` (peak distraction times, 7-day tab) are being visually improved. Reference images in `app/image_reference/` show target look.
- **Quick-pause "Pause now" button removal:** The 10-second countdown pause dialog (`EarnItRuleDetail.kt:211–234`) was recently updated to add a 10-second countdown. A "Pause now" confirmButton was added alongside it but is being removed — the intent is that users must wait the full countdown or cancel.
- **Package rename:** All source is `com.kaleel.earnitv2` but the git history shows the rename happened recently (commit ac6613d). Any copy referencing `com.example` is outdated.
