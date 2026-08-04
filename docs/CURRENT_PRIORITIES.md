# EarnItV2 — Current Priorities

Owner tags: **[Claude Code]** = manual/infra work, ideal fit for what you set up.
**[Codex]** = new user-facing feature, fits your existing split. **[Manual]** = you,
by hand. **[Design]** = needs mockup/visual decisions before code.

## High priority — blocking real usage

1. **[Claude Code] Wire up feedback destination.** Currently submits into the void.
   Decision already made: build the lightweight mailto version (see PRODUCT_CONTEXT.md
   §2), not full Supabase. Verify first whether this was already built in a past Codex
   session — your own note suggests it wasn't.
2. **[Claude Code] Fix shake-to-report toggle bug.** Confirmed state-sync bug — switch UI
   doesn't reflect persisted value. Likely a local `remember{}` not observing the
   repository Flow. Small, well-scoped fix.
3. **[Claude Code] Add basic usage/product analytics.** Firebase Analytics, centralized
   `ProductAnalytics` interface, start with the onboarding + rule-creation funnels before
   the full 25-event list. This also covers tracking beta testers as a distinguishable
   cohort.

## Feature work — new functionality

4. **[Codex] Fix Benjamin Franklin Mode.** CLAUDE.md audit already found the real gap:
   the evaluator/model logic exists but there's no UI to actually set
   `requiresDailyCommitment` on a rule — feature is unreachable end-to-end. This is a
   scoped, well-understood fix, good next Codex task.
5. **[Codex] Milestone notifications.** Notify user at incremental phone-open counts:
   5 → 10 → 25 → 50 → 100 → 200. Needs: a counter (persisted), a notification channel,
   and trigger logic. Decide if this counts *phone unlocks* or *app opens* — those are
   different signals and worth being precise about before building.
6. **[Codex] Cooldown before the 5-minute pause.** Add a ~10 second wait/confirmation
   before the pause takes effect. Purpose (worth stating explicitly in the Codex prompt):
   prevents impulsive/reflexive pausing, adds friction at the moment of temptation —
   which is the whole thesis of the app, so this is on-brand, not just UX polish.
7. **[Codex, spec already exists] Today analytics redesign.** Full spec already written
   (date navigation, hourly timeline, rule activity section) — see PRODUCT_CONTEXT.md §3.
   Verify if the earlier "UX refinement pass" prompt already covered this before
   re-requesting it.

## Launch prep — parallel track, not blocking

8. **[Design → Manual] Website.** You have ChatGPT-generated hero/phone-mockup concepts
   already. Once you've picked which ones you like, this becomes a real build — worth
   deciding now whether that's a Claude Code build task (static site) or something you
   want to hand to a website-specific tool.
9. **[Manual] Remaining launch assets.** Feature graphic, app icon export, final
   analytics screenshot (7/8 done), social/Product Hunt assets.
10. **[Manual, confirmed] Play Store submission steps.** Console setup, signing,
    subscription products, internal testing, beta invites — all manual, already scoped
    in the launch roadmap table in PRODUCT_CONTEXT.md.

## Meta

11. **[In progress] Context consolidation.** Get PRODUCT_CONTEXT.md and this file into
    `EarnItV2/docs/`, reference them from CLAUDE.md the way the earlier ChatGPT thread
    outlined, and treat them as living documents — update as decisions get made rather
    than re-deriving context from scratch each session.