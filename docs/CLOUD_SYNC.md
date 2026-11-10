# Cloud Sync

Opt-in backup and sync of Rules through the existing EarnIt Supabase project. Nothing is
uploaded until the user signs in from Settings → Cloud Sync.

This file is the contract. The Android client implements it in `CloudSync*.kt`; the iOS client
should implement the same table, payload and merge rules.

## What syncs

| Synced | Stays on the device |
|---|---|
| Rule terms: type, earn apps, blocked apps and websites, ratio, schedule, requirements, daily-commitment flag | Whether a Rule is enabled or paused, entitlement state, Strict Mode |
| | Reward Time balances, usage history, analytics, Deep Work, daily commitments |

Enabled/paused state is deliberately local. A pause lives with an expiry on one device, and a
second device must never be able to switch a Rule off remotely.

## Accounts

Supabase Auth, email one-time code. No password, no SDK:

1. `POST /auth/v1/otp` `{ "email", "create_user": true }`
2. `POST /auth/v1/verify` `{ "type": "email", "email", "token" }` → `access_token`, `refresh_token`, `user.id`
3. `POST /auth/v1/token?grant_type=refresh_token` `{ "refresh_token" }` when the access token is within a minute of expiry

Tokens are stored in the `earnit_cloud_sync` SharedPreferences file, which is excluded from
Android backup and device transfer.

## Table

`public.sync_records`, primary key `(user_id, collection, record_id)`. Row-level security limits
every row to its owner. See `supabase/migrations/202611100001_cloud_sync.sql`.

| Column | Meaning |
|---|---|
| `collection` | `rules` (the only collection so far) |
| `record_id` | The Rule id |
| `platform` | `android` or `ios`. A client only merges records of its own platform |
| `payload` | The Rule as JSON (below). Kept on tombstones for recovery |
| `updated_at_ms` | Client clock of the last write. Last writer wins |
| `deleted` | Tombstone. Rows are never hard-deleted by clients |
| `device_id` | Random per-install id, breaks timestamp ties |

Rules are per platform because the app identifiers are not portable: Android stores package
names, iOS stores FamilyControls tokens that only mean something on the device that created them.
Android ignores `ios` rows and leaves them untouched, and the reverse should hold.

## Rule payload (version 1)

```json
{
  "v": 1,
  "type": "EarnRewardTime | CompleteToUnlock | ScheduledBlock",
  "earnApps": [{ "id": "com.duolingo", "name": "Duolingo" }],
  "blockedApps": [{ "id": "com.instagram.android", "name": "Instagram" }],
  "blockedDomains": ["youtube.com"],
  "ratio": 2,
  "activeDays": [1, 2, 3, 4, 5],
  "windows": [{ "start": 540, "end": 1020 }],
  "requirements": [{ "id": "org.lichess.mobileV2", "name": "Lichess", "seconds": 600 }],
  "requiresDailyCommitment": false
}
```

- `ratio` is reward minutes per 10 productive minutes (the app's `rewardSecondsPerProductiveSecond`).
- Days are 1 = Monday … 7 = Sunday. Minutes are 0–1439 for `start`, 1–1440 for `end`; `start > end` is an overnight window.
- A client ignores a payload whose `v` is higher than it understands and does not overwrite it. Any change to this shape must bump `v`.
- Content is compared as canonical JSON (object keys sorted), because Postgres `jsonb` does not preserve key order.

## Merge rules

One pass (`CloudSyncEngine.sync`):

1. Diff local Rules against the journal (what this device last knew). A changed payload is a local edit stamped `now`; a Rule that disappeared is a tombstone stamped `now`. No save path has to record anything.
2. Fetch the server's rows for the collection.
3. Per record, the higher `updated_at_ms` wins; ties go to the higher `device_id`. Identical content is a no-op.
4. Push local winners, apply remote winners locally, save the journal.

The server repeats rule 3 in a trigger, so a stale push can never replace a newer row.

Strict Mode: while Strict Mode is protecting on a device, a remote edit or delete of a Rule that
exists there is not applied. The local Rule is kept and re-pushed as the newest write, so the
protected device wins everywhere. New Rules from other devices are still accepted.

A Rule pulled from the server arrives enabled, then the free-plan active-Rule limit is applied.

## When sync runs

- Right after sign-in, and on "Sync now".
- When the app comes to the foreground, if the last successful sync is older than 15 minutes.
- Every 6 hours in the background (WorkManager, network required).

## Known limits

- Clocks: last-writer-wins trusts each device's clock. A device with a clock set far ahead wins conflicts until the others catch up.
- Rules do not cross between Android and iOS (see above).
- There is no in-app account deletion yet. Deleting the user in Supabase Auth cascades to their rows.

## Backend setup

1. Apply the migration: `npx supabase db push`.
2. Supabase Dashboard → Authentication → Email Templates → Magic Link: include `{{ .Token }}` so the email carries the 6-digit code.
3. The app reads `FEEDBACK_SUPABASE_URL` / `FEEDBACK_SUPABASE_ANON_KEY` (same project as feedback). Without them the Cloud Sync screen says sync is unavailable and no request is made.
