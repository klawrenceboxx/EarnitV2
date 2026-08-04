# EarnItV2 — CLAUDE.md

Android app that gates access to distracting apps/websites behind productive app usage or task completion.
Package: `com.example.earnitv2`

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
- **Feedback backend:** Supabase (URL/key in `local.properties` or env vars)
- **No DI framework** — Context passed manually everywhere
- **No third-party networking lib** — plain HTTP for Supabase

**Key dependencies (via version catalog):**
`core-ktx`, `lifecycle-runtime-ktx`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`,
`work-runtime-ktx`, `exifinterface`, Compose BOM (UI + Material3), JUnit4, `org.json` (test)

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

| File | Role |
|---|---|
| `EarnItAccessibilityService.kt` | Central event loop. Handles app/browser foreground, Deep Work blocking, reward consumption tick |
| `EarnItRuleStore.kt` | Rule CRUD + serialization. Object singleton |
| `RuleAccessEvaluator.kt` | Pure evaluation logic (no Android deps). Returns `Result(denials, spendRule)` |
| `RewardLedger.kt` | Earn/consume reward seconds. Reads `UsageStatsManager` for productive usage |
| `BlockedActivity.kt` | Fullscreen block gate. Also houses all `BlockedScreen` UI + `DailyCommitmentScreen` |
| `MainActivity.kt` | All main navigation + UI. Very large (~1500+ lines) |
| `BenjaminFranklinMode.kt` | `BenjaminFranklinStore` only — DailyCommitment storage |
| `DeepWork.kt` | Deep Work session + store. Focus mode with DND integration |
| `EarnItPauseStore.kt` | Temporary rule pausing with expiry timestamps |
| `StrictModeStore.kt` / `StrictModePin.kt` / `StrictModeFoundation.kt` | Premium: prevents rule pausing/disabling |
| `Entitlement.kt` | `FeatureAccessPolicy`, `EntitlementState`, `SharedPreferencesEntitlementRepository` |
| `FeedbackUploadWorker.kt` | WorkManager task for Supabase feedback upload |
| Browser blocking: `BrowserPage.kt`, `BlockedDomain.kt`, `TrackedAppHandoff.kt` | Accessibility-based URL/domain reading + domain matching |

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
```
If missing, feedback upload is a no-op (empty string BuildConfig fields).

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

`BenjaminFranklinMode.kt` contains only `BenjaminFranklinStore` + `DailyCommitment` data class.

**What works:**
- Store: `saveToday()`, `reviewToday()`, `get(date)` — all functional
- Evaluator integration: `hasDailyCommitment` fed into `RuleRuntimeState`; `DailyCommitmentMissing` denial reason implemented
- Block gate UI: `DailyCommitmentScreen` in `BlockedActivity.kt` — user sets commitment before unlock
- Serialization: `requiresDailyCommitment` field (field 16) persists correctly, only valid on `CompleteToUnlock` rules

**What works:**
- Store: `saveToday()`, `reviewToday()`, `get(date)` — all functional
- Evaluator integration: `hasDailyCommitment` fed into `RuleRuntimeState`; `DailyCommitmentMissing` denial reason implemented
- Block gate UI: `DailyCommitmentScreen` in `BlockedActivity.kt` — user sets commitment before unlock
- Toggle UI: `EarnItRuleDetail.kt` has a `Switch` for `requiresDailyCommitment` on `CompleteToUnlock` rules, saved via `MainActivity.kt:1381`
- End-of-day review: `CommitmentReviewDialog` in `BenjaminFranklinScreen.kt` — calls `reviewToday()`. Surfaced via "Review today's commitment" button in `CompleteToUnlockDetailCard` when today's status is `Pending`
- History/streak screen: `BenjaminFranklinHistoryScreen` in `BenjaminFranklinScreen.kt` — shows streak count + last 60 days of commitments. Accessible via "View history ›" in rule detail

**No known missing pieces** — the feature is end-to-end usable.

---

## Other Known Rough Edges

- **`DeepWork.kt` is heavily minified** — single-line functions, no whitespace. Do not edit without reformatting first; the logic is complex (DND integration, session credit, monotonic clock fallback).
- **`MainActivity.kt` is very large** — likely needs splitting. Approach large edits carefully; read in sections.
- **`FeatureAccessPolicy.current(context)`** creates a new `SharedPreferencesEntitlementRepository` on every call (not cached). Works correctly but slightly wasteful — fine for now.
- **`isMinifyEnabled = false`** in release build — dead code not removed. Intentional or oversight, unclear.
- **Browser blocking** requires exact placeholder visibility detection via accessibility tree inspection — fragile if Chrome updates its UI. `MAX_WEBSITE_REDIRECT_ATTEMPTS = 10` with 200ms retries is the current mitigation.
- **Gemini/Google app tracking** (`TrackedAppMatchPolicy`) has special-case logic for Gemini launching through the Google app — evidence-based heuristic, not guaranteed.
