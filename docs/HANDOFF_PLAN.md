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

0 → 1 device checklist → 2 (delete gate) → 3 → 5 can run in parallel with any of them → 6.
4 starts when the iOS repo exists.

## Needed from Kaleel

1. Push the iOS project to GitHub.
2. Decide: unpause the Supabase project and apply the sync migration?
3. Decide finding 2 (how a recreated Rule should earn).
4. Confirm the cloud sync decisions above, or say which to change.
