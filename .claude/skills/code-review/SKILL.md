# Code Review Skill

Review the staged changes (or files specified by the user) against EarnItV2's conventions and architecture. Be direct — flag real issues, skip generic praise.

## How to invoke

`/code-review` — reviews `git diff HEAD` (staged + unstaged changes)
`/code-review <file>` — reviews a specific file

## Review checklist

### Correctness
- [ ] Day-of-week convention: 1=Mon through 7=Sun (NOT Calendar.SUNDAY=1). Flag any raw `Calendar.DAY_OF_WEEK` without conversion.
- [ ] Minutes-of-day: start in 0–1439, end in 1–1440 (1440 = "all day midnight"). Off-by-one here silently breaks schedules.
- [ ] Overnight time windows (startMinute > endMinute) must use the `previousDay()` path in `isActiveAt()` — not just a simple range check.
- [ ] SharedPrefs rule serialization is **positional/append-only** (field index 0–16). Never insert or reorder fields — only append.
- [ ] New rule fields must also be added to `encodeRules()` and `decodeRules()` in `EarnItRuleStore.kt`.

### Persistence
- [ ] Use `commit()` (synchronous) in the AccessibilityService path — `apply()` can be lost if the process is killed.
- [ ] Use `apply()` (async) elsewhere to avoid blocking the main thread.
- [ ] New SharedPrefs key spaces need a name added to the "Persistence key spaces" table in CLAUDE.md.

### Architecture
- [ ] Store logic belongs in `object` singletons (EarnItRuleStore, RewardLedger, etc.) — not in Activities or Composables.
- [ ] `RuleAccessEvaluator` must stay free of Android imports. If you need Android context for evaluation, pass the computed value in via `RuleRuntimeState`.
- [ ] `BlockedActivity` holds UI state in `mutableStateOf` fields on the Activity — no ViewModel there. Don't add one without discussion.
- [ ] `FeatureAccessPolicy.current(context)` is re-instantiated per call. Fine for now, but don't cache it long-term without accounting for entitlement changes.

### Premium / entitlement gating
- [ ] New premium features need a `PremiumFeature` enum entry.
- [ ] Check that `FeatureAccessPolicy.canUse(feature)` is called before allowing access — not just `entitlement.grantsPremium` directly.
- [ ] Free tier: max 2 active rules (`FREE_ACTIVE_RULE_LIMIT = 2`). Rule activation must go through `RuleEntitlementPolicy.save()` / `activate()`.

### Benjamin Franklin Mode
- [ ] `requiresDailyCommitment` is only valid on `CompleteToUnlock` rules — the decoder enforces this but new code paths should too.
- [ ] Any new block-screen branching on `DailyCommitmentMissing` must handle the "commit → recheck → open app" flow like `BlockedActivity.onCommit` does.
- [ ] `BenjaminFranklinStore.today()` returns null if no commitment set — treat null as "not committed", not as an error.

### Deep Work (extra caution)
- [ ] `DeepWork.kt` is heavily minified — reformat before editing, verify against tests after.
- [ ] Deep Work session credit must go through `RewardLedger.creditDeepWork()` with a `sessionId` for idempotency.
- [ ] DND filter restoration: `previousInterruptionFilter` must be persisted and restored in `finish()`.

### Browser / domain blocking
- [ ] Domain comparison must use `DomainMatcher.matches()` — not string equality. Subdomains of a blocked domain should also match.
- [ ] `DomainNormalizer.normalize()` can return null — always null-check.
- [ ] Website redirect logic has a guard timer (`WEBSITE_REDIRECT_GUARD_MILLIS = 2500ms`) — don't bypass it.

### Tests
- [ ] New evaluator logic → add a unit test in `RuleAccessEvaluatorTest` or a dedicated `*Test.kt`.
- [ ] Use builder functions (`earnRule(...)`, `completeRule(...)`) instead of constructing `Rule(...)` inline in tests.
- [ ] Pure logic (no Android deps) → `src/test/`. Android deps required → `src/androidTest/`.

### General
- [ ] No new dependencies without discussion — check `libs.versions.toml` first.
- [ ] Compose previews for any new composable screens.
- [ ] `internal` visibility for navigation enums and implementation details shared only within the module.

## Output format

For each issue found:

**[SEVERITY]** `File.kt:line` — description of problem and what the correct behavior should be.

Severity levels: `CRITICAL` (correctness/data loss), `WARN` (convention violation, likely bug), `STYLE` (minor, low priority).

End with a short summary: total issues by severity, and one sentence on overall state.
