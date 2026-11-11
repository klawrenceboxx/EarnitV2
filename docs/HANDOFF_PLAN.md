# EarnIt handoff plan (2026-11-10)

Written for the agent executing the work. Read `CLAUDE.md`, `docs/PRODUCT_CONTEXT.md` and
`docs/CURRENT_PRIORITIES.md` first, then this.

Branch with work in progress: `claude/reliability-audit-cloud-sync` (this branch, off `main`).

## Ground rules

- Work on branches and open PRs. Do not push to `main` in either repo.
- Run on a machine that can build Android (`./gradlew test assembleDebug`). The session that wrote
  this branch could not reach Google's Maven or the Android SDK, so **nothing on this branch has
  been through Gradle**. Workstream 0 exists to close that gap. Do not start anything else first.
- Do not apply migrations to, restore, or otherwise change the live Supabase project
  (`EarnitV2`, ref `rvyffrhtjpxrumlrmbyr`) without Kaleel saying so. It is currently **paused**
  (status INACTIVE), which means feedback upload and the website waitlist are down right now.
- Report what was verified and how. "Compiles" and "tested on a device" are different claims.

## Status at handoff

| Workstream | State |
|---|---|
| 0. Build verification of this branch | Not started. Blocks everything |
| 1. Core reliability audit (roadmap 01) | Logic fixes written and unit-tested off-Gradle. Device checks and several findings open |
| 1A. Strict Mode charger lockout | Diagnosed, not fixed. Do this right after workstream 0 |
| 1B. Full-app audit | Scoped below. Only the blocking engine and ledger have been audited so far |
| 1C. Pickup notifications | Diagnosed, not fixed. Needs two product answers from Kaleel |
| 2. Anti-bypass friction (roadmap 03) | Two bypasses fixed, edit gate set to 15s. Delete path and others open |
| 3. Cloud sync | Client, tests, migration, docs written. Not built, not run against Supabase |
| 4. iOS | Blocked: the iOS code is not on GitHub |
| 5. Website animations | Not started |
| 6. Deep Work 2.0 (roadmap 02) | Not started |
| 7. Roadmap 04–07 | Out of scope for this handoff |

### How the branch was verified

- Pure logic (ledger maths, usage clock, sync JSON/codec/planner, Supabase request shapes) was
  compiled with the Kotlin 2.0.21 compiler against hand-written Android stubs and run under JUnit:
  45 tests passed, including an in-memory two-device sync simulation and end-to-end ledger tests.
  Three of those tests were also run against `main` and failed there, confirming the bugs are real.
- The new tests under `app/src/test` (`RewardLedgerReliabilityTest`, `CloudSyncCoreTest`,
  `CloudSyncBackendTest`) are pure and should run under `./gradlew test` unchanged.
- The migration was applied to a throwaway Postgres 16 with a mocked `auth` schema: newest-write
  trigger, tombstones keeping their payload, per-user isolation, forged `user_id`, client delete
  and anon access were all exercised.
- Everything touching Android APIs, WorkManager or Compose got a **syntax check only**. That is
  `EarnItAccessibilityService.kt`, `ProtectionStatusNotifier.kt`, `CloudSyncWorker.kt`,
  `CloudSyncScreen.kt`, and the edits to `MainActivity.kt`, `EarnItSetup.kt`, `MainApplication.kt`,
  `EarnItRuleDetail.kt`. Expect a few compile errors there.

## Workstream 0: make the branch build

1. `./gradlew test assembleDebug`. Fix compile errors in the files listed above without changing behaviour.
2. All existing tests plus the three new test classes must pass.
3. Install on a device and run the device checklist in workstream 1.

Done when: the build is green and the checklist is filled in on the PR.

## Workstream 1: core reliability (roadmap 01)

### Fixed on this branch

| # | Bug | Fix | Where |
|---|---|---|---|
| R1 | Reward time was truncated on every credit. At 10:1, crediting 10 minutes one second at a time earned 0 instead of 60s; credit runs on every window change, so busy productive apps lost real earnings | Issue from cumulative totals | `RewardLedger.incrementalRewardSeconds` |
| R2 | Editing a Rule reset consumed time to zero while usage was re-credited from UsageStats: a full refund of the day's spent time. It also wiped Deep Work reward | Same-day reset keeps consumed time and Deep Work credit | `RewardLedger.ledgerResetPlan` |
| R3 | Screen off inside a reward app: the 1s tick stopped during sleep, then charged the whole gap on wake | Stop the clock on `ACTION_SCREEN_OFF`; cap a single tick at 10s | service `unlockReceiver`, `ProtectedUsageClock.tick` |
| R4 | After unlock, service restart or app update, whatever was in front was not evaluated until its next window change (free use) | `enforceCurrentForeground()` on connect and on `USER_PRESENT` | `EarnItAccessibilityService` |
| R5 | Accessibility switched off meant silent fail-open | Notification from `onUnbind` when enabled Rules exist | `ProtectionStatusNotifier` |
| R6 | Processed Deep Work session ids grew without bound | Cleared on day rollover | `RewardLedger.currentPrefs` |

### Device checklist (none of this has been run)

- [ ] R1: 10:1 Rule, use the earn app for 10 minutes with lots of in-app navigation, confirm 1:00 earned.
- [ ] R2: spend all reward time, edit the Rule (add a blocked app), confirm balance stays 0.
- [ ] R3: open a reward app with 5 minutes banked, lock for 10 minutes, unlock, confirm about 5 minutes remain.
- [ ] R4: lock inside a blocked app and unlock straight back into it; confirm the block screen appears. Repeat after toggling the Accessibility service off and on while the app is in front.
- [ ] R4 regression: confirm no block-screen flicker or loop on unlock with an allowed app in front, and that browser blocking still works after unlock.
- [ ] R5: disable Accessibility with a Rule enabled; confirm the notification, and that it clears on re-enable. Confirm no notification with zero enabled Rules.
- [ ] Schedules: overnight window (22:00–06:00) across midnight; day boundary at 00:00 with an EarnRewardTime Rule mid-spend.
- [ ] Permission loss: revoke Usage Access; confirm Rules fail closed and the block screen explains why.
- [ ] Restart: reboot with a Rule active; confirm blocking resumes without opening EarnIt.

### Open findings, in priority order

1. **Deleting a Rule has no friction** unless Strict Mode is on. `EarnItRuleDetail.kt` (`onDelete`),
   `EarnItHome.kt` and the confirm dialog in `MainActivity.kt` all delete immediately, which makes
   the 15s edit gate pointless. Route delete through the same countdown gate. (Roadmap 03.)
2. **Delete and recreate refunds the day.** A new Rule id starts a fresh ledger and UsageStats
   re-credits the whole day. Needs a product decision from Kaleel: either a new Rule only earns
   from the moment it is created, or consumed time is tracked per blocked app per day.
3. **Clock tampering.** Pause expiry and the ledger day key use wall-clock time. Setting the clock
   back extends a pause; changing the date resets the ledger. Anchor pauses to
   `elapsedRealtime` plus a boot counter and treat a backwards clock jump as "no reset".
4. **Force-stop** disables the Accessibility service without `onUnbind`, so R5 does not fire.
   Add a periodic WorkManager check that posts the same notification.
5. **Performance.** `creditLatestProgress` runs a full-day `UsageEvents` scan per package per Rule
   on every window change and every second while spending. Cache per package for a few seconds
   and scan once for all packages.
6. **Midnight undercount.** `activeAppUsageSecondsToday` drops a session that started before
   midnight until the app is next resumed. Query from a little before midnight and clamp.
7. **Deep Work after reboot.** `DeepWorkSession.elapsedSeconds` falls back to wall clock after a
   reboot, then silently switches back to the monotonic clock once uptime passes the stored start
   value, making elapsed time jump backwards. Store boot count with the session. `DeepWork.kt`
   must be reformatted before editing (see `CLAUDE.md`).
8. **Picture-in-picture and split screen** may keep a blocked app visible behind or beside the
   block screen. Untested; check YouTube PiP and split screen on a device.
9. **Privacy policy accuracy.** Section 2 says the Accessibility service "reads only the package
   name of the foreground app", but website blocking reads the browser address bar. Fix the
   wording before Play review. The hosted copy at boxxautomations.space also needs the Cloud Sync
   section added on this branch.
10. Repo hygiene: the file named `for local settings and supabase temp…` in the repo root is a
    stray `git diff` dump (no secrets found); delete it. `isMinifyEnabled = false` in release is
    still undecided.

## Workstream 1A: Strict Mode charger lockout (reported by Kaleel)

Symptom: with Charger protection, plugging the phone in still doesn't let you end Strict Mode.

Cause: every gate checks `ChargingState.isActivelyCharging`, which is
`plugged != 0 && status == BATTERY_STATUS_CHARGING` (`Charger.kt`). A plugged-in phone often
reports a different status:

- `STATUS_FULL` at 100%.
- `STATUS_NOT_CHARGING` when charging is paused: Samsung "Protect battery" (stops at 80–85%),
  Pixel Adaptive Charging, OEM optimised charging, or a weak USB port.
- `STATUS_DISCHARGING` for a moment right after plugging in, before the next battery broadcast.

In all of these, the "Disable Strict Mode" button stays disabled ("Connect charger to
deactivate"), the confirm dialog closes itself (`EarnItStrictMode.kt`, the `LaunchedEffect` on
`isActivelyCharging`), and `confirmGlobalChargerDeactivation` / `authorizeCharger` /
`beginOrRestoreCharger` in `StrictModeFoundation.kt` reject the attempt. On a phone with
battery protection on and the battery above the limit, Strict Mode can't be ended at all.
`ChargerTest.acUsbWirelessAndOtherSourcesRequireActiveCharging` asserts this behaviour, so it
was deliberate. But the friction the feature promises is "go and plug in your phone", not
"the battery must be taking current".

Fix:

1. Gate on `isPlugged` everywhere the charger is used as authorization: `StrictModeFoundation.kt`
   (`confirmGlobalChargerDeactivation`, `authorizeCharger`, `beginOrRestoreCharger`),
   `EarnItStrictMode.kt` (button state, dialog auto-close, authorize button) and
   `chargerDeactivationButtonUi`. Keep `isActivelyCharging` only for display, if at all.
2. Update `ChargerTest`: plugged plus `NOT_CHARGING`, `FULL` or `DISCHARGING` must authorize;
   unplugged must not.
3. `AndroidChargingStateObserver.observe` only runs between `onStart` and `onStop`. Confirm the
   state is re-read when the user plugs in while EarnIt is in the background and then returns
   (`onStart` calls `observe`, which reads `currentState()`, so this should hold; verify it).
4. Device test: battery full; Samsung Protect battery on above 85%; Pixel Adaptive Charging;
   wireless pad; a USB-C cable on a laptop port. Each must allow deactivation while plugged in
   and block it when unplugged.

Also confirm no other Strict Mode method can lock the user out: PIN forgotten (is there any
recovery?), countdown across a reboot, and a deactivation request expiring mid-flow.

## Workstream 1C: pickup notifications (reported by Kaleel)

Symptom: the app is meant to notify you as your phone pickups add up, but the notifications
mostly don't come.

Cause, in `UnlockMilestoneStore.kt`:

1. **The count never resets.** It's a lifetime total with milestones at 5, 10, 25, 50, 100 and
   200, and each fires once ever. Most people unlock their phone 50–100 times a day, so all six
   fire within the first day or two of install and then never again. That matches "lacking".
2. **Wrong channel.** It posts on the Benjamin Franklin reminders channel (`bf_reminders`).
   Turning those reminders off silences pickups too, and pickups can't be controlled on their own.
3. **Wrong message.** The copy congratulates ("You're building a habit", "You're one of the few
   who sticks with it") for picking the phone up, which is the behaviour EarnIt exists to reduce.
4. **Only counted while the Accessibility service is running** (the receiver is registered in
   `EarnItAccessibilityService.onServiceConnected`). Pickups while it's off or restarting are lost.
   Acceptable, but say so on whatever screen shows the number.
5. **Not tested.** No unit test covers it.

Fix:

1. Count per local day, reset at midnight (store the date with the count, as `RewardLedger` does).
   Handle time zone changes by using the stored date, not elapsed time.
2. Notify at each step of a fixed increment during the day, once per step per day. Default
   proposal: every 25 pickups (25, 50, 75, 100…). See questions for Kaleel.
3. Its own channel, "Pickup check-ins", created in `MainApplication`, plus an on/off switch and
   increment picker in Settings. Default on.
4. Neutral, factual copy in the app's voice: "That's 50 pickups today." with today's reward time
   used in the body if it's cheap to get. No praise, no alarm.
5. Pure-Kotlin counter logic with unit tests: increments, once-per-step, midnight reset, a day
   with no unlocks, clock moved backwards.
6. Device check: lock/unlock 25 times and get one notification; cross midnight and confirm the
   count resets; disable the channel and confirm Benjamin Franklin reminders still arrive.
7. Check whether `ACTION_USER_PRESENT` fires on a phone with no screen lock. If not, count
   `ACTION_SCREEN_ON` instead when no keyguard is set.

Questions for Kaleel before building: what increment (every 10, 25, 50, or chosen by the user)?
And should it say something different once you're past your usual daily count?

## Workstream 1B: full-app audit

Only the blocking engine, reward ledger and a few bypass paths have been audited. Workstream 1A
is the kind of bug the rest of the app still hides. Audit every file, area by area, in the order
below. About 23,700 lines of Kotlin in 63 files.

### Method, per area

1. Read every file in the area in full. For the large files (`MainActivity.kt` 2.8k lines,
   `EarnItRuleBuilder.kt` 2.2k, `EarnItStrictMode.kt` 1.6k, `EarnItRuleDetail.kt` 1.4k,
   `StrictModeFoundation.kt` 1.3k), read in sections and keep notes.
2. Check against the questions for that area below, plus these for every area:
   - Can the user get stuck, with no way forward or back (the 1A pattern)?
   - Can the user get round a Rule or Strict Mode, by order of operations, timing, process death
     or the system clock?
   - What happens after process death, reboot, app update and clearing the app from recents?
   - Does every error path tell the user something true, or fail silently?
   - Is state written with `commit()` where a crash right after would lose it?
3. Record each finding in the table below the moment it's found: id, area, file:line, severity,
   what happens, how to reproduce, proposed fix. Severity: **S1** blocks the user or breaks a
   promise (lockout, bypass, data loss, crash); **S2** wrong result or broken flow with a
   workaround; **S3** polish.
4. Fix S1s as found, each in its own commit with a regression test. Batch S2s per area. List S3s
   and leave them for Kaleel to triage.
5. Spawn the `code-reviewer` agent on each area's fixes before moving on.

### Areas

| # | Area | Files | Specific questions |
|---|---|---|---|
| A1 | Blocking engine | `EarnItAccessibilityService`, `RuleAccessEvaluator`, `ProtectedTarget`, `BrowserPage`, `BlockedDomain`, `TrackedAppHandoff`, `AppPackages` | Done in part (workstream 1). Remaining: findings 3–8, Chrome UI changes, other browsers (Firefox, Samsung Internet, Brave), incognito, Chrome custom tabs inside other apps |
| A2 | Ledger and pauses | `RewardLedger`, `EarnItPauseStore`, `UnlockMilestoneStore` (see 1C) | Done in part. Remaining: findings 2, 3, 6; pause expiry while the phone is off; milestone counts across days |
| A3 | Block screen | `BlockedActivity` | Back, Home, Recents, gesture nav, split screen; does any button lead into Settings or EarnIt where a Rule can be weakened; Daily Commitment screen can't be skipped |
| A4 | Strict Mode | `StrictModeFoundation`, `StrictModeStore`, `StrictModePolicy`, `StrictModeCommitment`, `StrictModePin`, `Charger`, `EarnItStrictMode` | 1A first. Then: every lockout path for each method; legacy-to-global migration on upgrade; what Strict Mode actually protects (edit, pause, delete, Accessibility off, uninstall); whether the 7-day commitment can be shortened |
| A5 | Rules UI | `EarnItRuleBuilder`, `EarnItRuleDetail`, `EarnItRuleTypeSelection`, `EarnItHome`, `EarnItAppPicker`, `EarnItAppIcon`, `RuleTypePresentation`, `CompleteToUnlockProgressUiState`, `EarnItUiState` | Invalid Rules (no blocked app, zero-length window, blocking EarnIt or the launcher or the dialer); uninstalled apps in a Rule; the edit gate and pause countdown can't be skipped; free-plan limit enforced at every entry point |
| A6 | Deep Work | `DeepWork` | Reformat first. Finding 7; DND permission denied or revoked mid-session; ending early; standalone vs linked sessions |
| A7 | Benjamin Franklin Mode | `BenjaminFranklinMode`, `BenjaminFranklinScreen`, `BenjaminFranklinNotificationScheduler`, `BenjaminFranklinReceiver` | Exact-alarm permission denied (Android 14 denies by default); time zone and DST changes; streak across a missed day; review after midnight |
| A8 | Entitlement and Pro | `Entitlement`, `RuleEntitlementPolicy`, `PurchaseProvider`, `SubscriptionConfig`, `EarnItPro` | Can a release build reach the simulator or grant Pro; downgrade with more than 2 Rules picks the right ones; nothing pretends billing works |
| A9 | Onboarding and setup | `EarnItOnboarding`, `EarnItOnboardingState`, `EarnItSetup` | Permission denied or granted out of order; returning from Settings; replay doesn't change Rules; the legacy setup flow is unreachable or removed |
| A10 | Analytics | `AnalyticsScreen`, `AnalyticsModels`, `AnalyticsRepository`, `AnalyticsInsights` | Numbers match the ledger; day boundaries and DST; no Usage Access; insights never claim cause and effect (a recent commit changed wording toward that, check it) |
| A11 | Feedback and crash reporting | `Feedback*`, `ProductAnalytics`, `MainApplication` | Offline queue survives restarts and doesn't grow forever; screenshot EXIF stripped; nothing sensitive in diagnostics; PostHog missing-key crash is debug-only |
| A12 | Cloud Sync | `CloudSync*` | New code; covered by workstream 3 |
| A13 | Navigation and app shell | `MainActivity`, `ui/theme/*` | Back stack can't strand the user; state survives rotation and process death; deep links from notifications land in the right place |
| A14 | Manifest, permissions, build | `AndroidManifest.xml`, `res/xml/*`, `app/build.gradle.kts`, `proguard-rules.pro` | Exported components; `QUERY_ALL_PACKAGES` and Accessibility declarations Play will ask about; backup rules; release minify decision |
| A15 | Privacy policy vs behaviour | `docs/PRIVACY_POLICY.md` | Every statement true of the code, finding 9 included |

### Findings log

Keep it in `docs/AUDIT_FINDINGS.md` (create it). Seed it with the open findings from workstream 1
and 1A. One row per finding:

| ID | Area | Sev | File:line | What happens | Repro | Fix | Status |
|---|---|---|---|---|---|---|---|

Done when: every area above has been read in full and marked done in the log, every S1 is fixed
with a test, and the log is linked from the PR.

## Workstream 2: anti-bypass friction (roadmap 03)

Done on this branch: edit gate raised from 10s to 15s (`EDIT_GATE_COUNTDOWN_SECONDS`); R2 above;
and the block debounce no longer drops a block (reopening a blocked app within 2s of being blocked
used to leave it open until its next window change; it now re-checks after the debounce).

To do: findings 1–4 above, then "safer, deliberate access exceptions" from the roadmap, which
needs a short spec agreed with Kaleel before any code.

## Workstream 3: cloud sync

Design and contract: `docs/CLOUD_SYNC.md`. Summary: opt-in, Supabase email one-time-code sign-in,
one `sync_records` table with row-level security, last-writer-wins per Rule, tombstones for
deletes. Only Rule terms sync; enabled/paused state, balances and usage stay on the device. While
Strict Mode protects a device, remote edits and deletes are held back and the local Rule wins.

Decisions already taken that Kaleel may want to reverse: email code rather than Google sign-in;
Rules only (no balances, commitments or settings); sync is a backup/multi-device feature for
Android, not Android-to-iOS, because app identifiers are not portable between the platforms.

To do:

1. Get it compiling (workstream 0).
2. With Kaleel's go-ahead: unpause the Supabase project, `npx supabase db push`, and add
   `{{ .Token }}` to the Magic Link email template so the email shows the 6-digit code.
3. Two-device test from `supabase/README.md` → Cloud Sync. Also: sign out and back in, airplane
   mode, expired session, Strict Mode on one device with a delete on the other, free-plan limit
   when a third Rule arrives.
4. Check `CloudSyncScreen` against `docs/DESIGN_SYSTEM.md`; it was written without rendering it.
5. Add in-app account deletion (Play requires it for apps with accounts) via a Supabase edge function.
6. Publish the updated privacy policy before the feature ships.

## Workstream 4: iOS

Blocked. Kaleel's Swift port (domain layer, 45 tests) lives only on his Mac. First step is his:
push it to GitHub. Until then do not write iOS code; a second, parallel port would have to be
thrown away.

Once it is available:

1. Port the R1 and R2 ledger fixes and their tests to Swift.
2. Implement `docs/CLOUD_SYNC.md` with `platform = "ios"`.
3. FamilyControls/ManagedSettings/DeviceActivity work needs a paid Apple Developer account and the
   Family Controls entitlement. Kaleel has chosen to defer that cost; build UI and domain only
   until he says otherwise.

## Workstream 5: website animations (`earnit-website`)

Read that repo's `CLAUDE.md` and `docs/SITE_CONTEXT.md` first. Constraints that matter: no new
dependencies, CSS plus small client islands, colour tokens only (rule colours are fixed: earn
green, unlock blue, scheduled amber), `prefers-reduced-motion` respected, Lighthouse ≥ 95,
`lint` + `typecheck` + `build` green. No download CTAs or invented numbers.

Brief: make "do the work, earn the time" visible. Proposed, in order of value:

1. **Hero time bank.** A ring fills in green while a "focus" counter runs, then the earned
   minutes pour into a locked app tile that opens. Loops slowly; static final frame under reduced motion.
2. **How it works exchange.** On scroll into view, 30 productive minutes tick down while reward
   minutes tick up at the Rule's ratio. This is the product's mechanic, so use real ratios (10:1, 5:1, 2:1).
3. **Rule type cards.** One short motion per type in its own colour: earn fills, unlock ticks
   off requirements then opens, scheduled sweeps a clock arc.
4. **Outcome.** A day timeline where grey scrolling blocks are replaced by earned blocks.

Check at 375, 768 and 1280 wide and with reduced motion on. Run the `seo-a11y-auditor` agent on the result.

## Workstream 6: Deep Work 2.0 (roadmap 02)

Not started and not specified beyond the roadmap line (intentions, daily goal visibility,
Pomodoro, distraction checks, manual-session integrity, end-of-session accounting). Write a
one-page spec and get Kaleel's sign-off before building. Reformat `DeepWork.kt` in its own commit
first, and fix finding 7 as part of "manual-session integrity".

## Suggested order

0 → 1A → 1C → 1 device checklist → 1B (full audit, which absorbs 2) → 3 → 6. 5 can run in parallel with any of them.
4 starts when the iOS repo exists.

## Needed from Kaleel

1. Push the iOS project to GitHub.
2. Decide: unpause the Supabase project and apply the sync migration?
3. Decide finding 2 (how a recreated Rule should earn).
4. Confirm the cloud sync decisions above, or say which to change.
5. Pickup notifications: what increment, and any change in tone past your usual count (workstream 1C).
