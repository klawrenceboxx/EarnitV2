# EarnItV2 — Product Context

Condensed from prior ChatGPT planning sessions. This is decision history and current
architecture status, not a spec to re-derive from scratch. Feed this to Claude Code
alongside CLAUDE.md before assigning feature work.

---

## 1. Premium / Paywall Architecture — BUILT (needs verification pass)

Codex implemented a full entitlement system. Status per your last note: architecture
works, but a UX refinement pass was requested on top of it. Unclear if that second
pass has landed yet — verify before assuming it's done.

**Tiers:**
- Free: 2 active Rules max, Today analytics, all core blocking/earning features
- Pro: unlimited active Rules, Deep Work, Strict Mode, 7-day analytics/insights
- Explicitly NOT gated: app blocking, website blocking, scheduled rules, existing data

**Architecture:**
- `EntitlementRepository` — single source of truth, states: FREE / PURCHASED /
  BETA / DEBUG / GRACE_PERIOD / EXPIRED / UNKNOWN
- `FeatureAccessPolicy` — central `canUse(feature)` gate; no scattered `if (isPremium)` checks
- `PremiumPurchaseGateway` — purchase abstraction; **Google Play Billing NOT yet
  connected**, currently a fake/debug-backed implementation
- Downgrade policy: never delete Rules/data. On expiry, keep the 2 most-recently-active
  Rules enabled, mark the rest "Premium inactive" (not paused, not deleted — distinct state)
- Beta entitlement: compile-time flag only, no hardcoded tester emails
- Debug entitlement simulator exists for testing all these states without real purchases

**Known follow-up requested (verify if done):**
- Premium gate dialogs — needed visual polish, feature-specific copy/icons
- Plan selection screen — hierarchy/copy refinement
- Dev simulator — needed to move off main Settings screen into its own debug screen
- Today analytics — flagged as visually incomplete (see section 3)
- 7-day tab — needed to remove crown/"Pro" text from tab label itself
- Feedback form — category selection needed to be visually obvious + validated
- **Shake-to-report toggle — confirmed real bug**: UI doesn't reflect persisted state.
  Root cause suspected: local `remember{ mutableStateOf }` not observing repository Flow.

**Next step after this:** Google Play Billing integration (separate, deliberately isolated
task) — connects real purchases into the existing `PremiumPurchaseGateway`.

---

## 2. Feedback System — NOT WIRED UP YET

Current state per your voice note: users can submit feedback, but **it goes nowhere**.
No backend is connected.

**Decision already made:** skip the full Supabase pipeline for the beta. Use a
lightweight MVP instead — feedback screen builds a pre-filled email (via `ACTION_SENDTO`
mailto intent) containing category, message, app version, Android version, device model,
and permission status. User taps send in their own email app. No backend, no DB, no
screenshot upload, no offline queue for v1.

Full spec for this lightweight version exists (category selector, validation, clipboard
fallback if no email app found, privacy disclosure copy). Confirm with Claude Code
whether this was actually implemented — your voice note today still describes feedback
as broken, so treat as **not done** until verified.

**Future upgrade path:** swap only the transport layer for Supabase later, keep the same UI.

---

## 3. Today Analytics — flagged as incomplete

Current three-bar visual doesn't convey useful info. Planned redesign:
- Date navigation (swipe/arrows, disable future dates)
- Overview: total screen time, delta from previous day, Earn/Reward/Other split
- Hourly timeline (24 bars) replacing the 3-bar chart, real data only — do not fabricate
  hourly granularity if it isn't actually stored
- Rule activity section (earned/spent/remaining/blocked attempts) — only if data exists
- Most-used apps, filtered to selected day

---

## 4. Product/Usage Analytics — NOT YET IMPLEMENTED

Distinct from the personal analytics users see in-app. This is about seeing how *you*
can observe aggregate user behavior (funnels, drop-off, feature adoption).

**Recommended approach:** Firebase Analytics, centralized behind one
`ProductAnalytics` interface — never call Firebase directly from Composables.

**Event taxonomy planned (~15-25 events):**
- Onboarding funnel: started → step viewed → permission prompt → granted/denied → completed/skipped
- Rule creation funnel: started → earn app selected → reward apps selected → exchange
  selected → schedule selected → created/abandoned
- Core behavior: reward_time_earned, reward_time_depleted, reward_app_blocked,
  blocked_screen_earn_selected, blocked_screen_dismissed, rule_paused/resumed/edited/deleted
- Feature adoption: analytics_opened, strict_mode_enabled, deep_work_started,
  website_blocking_enabled, feedback_opened

**Privacy boundary — do not track:** exact app names, package names, websites/domains,
exact timestamps tied to a person, accessibility event content, rule names, emails, PINs.
Send counts/categories, not identifying detail.

**Also wanted:** a way to identify/tag beta testers specifically in analytics so their
usage is distinguishable from future public users.

---

## 5. Launch Roadmap

| Step | Task | Status |
|---|---|---|
| 1 | Developer account | ✅ Done |
| 2 | Google identity verification | ⏳ Waiting on Google |
| 3 | Premium architecture & paywall | 🔄 Built, UX pass in progress |
| 4 | Review/fix UI issues | ⏳ |
| 5 | Google Play Billing integration | ⏳ Not started |
| 6 | Create app in Play Console | ⏳ Manual |
| 7 | Enable Play App Signing | ⏳ Manual |
| 8 | Create subscription products | ⏳ Manual |
| 9 | Build signed release .aab | ⏳ |
| 10 | Upload to Internal Testing | ⏳ Manual |
| 11 | Add license testers | ⏳ Manual |
| 12 | Test real purchases on device | ⏳ |
| 13 | Invite beta testers | ⏳ Manual |
| 14 | Fix bugs from beta feedback | ⏳ |
| 15 | Complete store listing (screenshots, description, privacy policy) | 🔄 In progress |
| 16 | Submit for Production | ⏳ Manual |

Play Store submission itself is confirmed **manual-only** — Claude Code/Codex can't
click through Play Console, and that's fine, you're doing that part by hand.

**Launch asset checklist:**
- Play Store screenshots: 7/8 generated (missing: analytics screenshot)
- Feature graphic (1024×500) — not started
- App icon export (1024×1024) — not started
- Website hero image / phone mockups — in progress (ChatGPT-generated concepts exist)
- Social/Product Hunt launch assets — not started

**Note on Figma:** original Figma files are outdated — actual app UI has evolved
significantly since those were made. Treat current app screenshots as more authoritative
than old Figma frames when generating new marketing imagery.