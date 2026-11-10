# Privacy Policy — EarnIt

**Effective date:** August 6, 2026
**Last updated:** November 10, 2026

---

## Overview

EarnIt is a productivity app that helps you manage your screen time by requiring productive app usage before accessing distracting apps. This policy explains what data EarnIt collects, why, and how it is handled.

**The short version:** Almost everything EarnIt does stays on your device. We do not sell your data. We do not require an account. The only data that ever leaves your device is anonymous crash reports, anonymous product analytics, feedback you choose to submit, and, only if you turn on Cloud Sync, your Rules.

---

## 1. Data We Collect

### 1.1 Data stored on your device only

The following data is created and stored entirely on your device. It is never transmitted to any server, with one exception: your Rules are uploaded if you choose to turn on Cloud Sync (section 1.6).

| Data | Purpose |
|---|---|
| Your Rules (earn apps, blocked apps, schedules) | Core app functionality — blocking and unblocking apps |
| Reward time balances | Tracking how much reward time you have earned and used |
| App usage duration (via Android Usage Access) | Calculating productive time earned; powering the Analytics screen |
| Daily commitments (Benjamin Franklin Mode) | Storing the commitments you write for yourself each day |
| Deep Work session state | Managing Deep Work focus sessions |
| Onboarding and setup progress | Remembering whether you have completed setup |
| Analytics history | Populating the Today and 7-day analytics views |

**We do not upload your app usage history, your commitments, or your personal usage patterns to any server. Your Rules are uploaded only if you turn on Cloud Sync.**

### 1.2 Anonymous installation identifier

When you first open EarnIt, a random UUID is generated and stored on your device. This identifier:

- Is not linked to your name, email, Apple ID, Google account, or any personally identifying information
- Is used only to de-duplicate crash reports and analytics events from the same device across sessions
- Is shared with PostHog (analytics) and Firebase Crashlytics (crash reporting) as described below

You can reset this identifier by clearing the app's data in Android Settings.

### 1.3 Anonymous product analytics (PostHog)

EarnIt uses [PostHog](https://posthog.com) to collect anonymous usage events. These events help us understand how the app is used so we can improve it.

Examples of events collected:
- App opened, screens viewed
- Rule created or deleted
- Feedback submitted
- Onboarding steps completed

**What is NOT collected in analytics:** the names of your apps, the content of your commitments, your personal usage amounts, or any content you type.

PostHog's privacy policy: [https://posthog.com/privacy](https://posthog.com/privacy)

Data is associated only with your anonymous installation ID. PostHog is configured with `captureScreenViews = true` and automatic error tracking.

### 1.4 Crash reports (Firebase Crashlytics)

EarnIt uses [Firebase Crashlytics](https://firebase.google.com/products/crashlytics) to automatically report app crashes. Crash reports include:

- The exception type and a sanitized stack trace
- Your anonymous installation ID
- Device model, Android version, and app version
- The screen or route where the crash occurred

Crash reports do **not** include your Rules, usage data, commitments, or any other personal content.

Firebase's privacy policy: [https://firebase.google.com/support/privacy](https://firebase.google.com/support/privacy)

### 1.5 Feedback you voluntarily submit

If you choose to submit feedback through the in-app feedback form, we collect:

- Your feedback category (Bug, Suggestion, or Other)
- The message you write
- Your email address, if you choose to provide it (optional)
- A screenshot, if you choose to attach one (optional)
- Technical diagnostics: app version, Android version, device model, active rule count (not rule content), permission status, whether the device was online, your anonymous installation ID

Feedback is submitted to our backend (Supabase) and used only to understand and respond to your report. If you provide an email address, we may reply to your feedback. We will not use your email for marketing.

Feedback data is stored securely and deleted when no longer needed for support purposes.

### 1.6 Cloud Sync (optional, off by default)

Cloud Sync backs up your Rules and keeps them matched across your devices. It is off until you sign in from Settings > Cloud Sync. If you never sign in, nothing in this section applies to you.

When Cloud Sync is on, we store on our backend (Supabase):

- Your email address, used only to sign you in with a one-time code
- Your Rules: the apps you earn with, the apps and websites you block (app identifiers, app names, and website domains), exchange rate, schedule, and requirements
- A random device identifier and the time each Rule was last changed, used to merge changes between devices

Cloud Sync does **not** upload your app usage history, Reward Time balances, analytics, Deep Work sessions, or daily commitments.

Only your signed-in account can read or change your synced Rules. We do not use your email for marketing. Signing out stops syncing and leaves your Rules on your device. To have your account and synced Rules deleted, contact us at the email below.

---

## 2. Permissions We Request

| Permission | Why we need it |
|---|---|
| **Accessibility Service** | Required to detect which app is in the foreground so EarnIt can enforce your Rules. EarnIt reads only the package name of the foreground app — it does not read the content of any app. |
| **Usage Access** (`PACKAGE_USAGE_STATS`) | Required to measure how long you spend in your productive (Earn) apps, so we can calculate Reward Time earned. This data stays on your device. |
| **Post Notifications** | Used to send optional daily commitment reminders for Benjamin Franklin Mode. You can disable these at any time in your device's notification settings. |
| **Receive Boot Completed** | Used to reschedule Benjamin Franklin Mode notification reminders after your device restarts. |
| **Schedule Exact Alarm** | Used to deliver Benjamin Franklin Mode reminders at your chosen times. |
| **Internet** | Used to submit feedback, send anonymous analytics events and crash reports, and sync your Rules if you turn on Cloud Sync. |

---

## 3. Data Sharing

We do not sell your data. We do not share your data with advertisers or data brokers.

Data is shared only with the following service providers, and only to the extent described in this policy:

| Provider | Purpose | Data shared |
|---|---|---|
| PostHog | Anonymous product analytics | Anonymous events, installation ID |
| Firebase (Google) | Crash reporting | Crash diagnostics, installation ID |
| Supabase | Feedback storage; optional Cloud Sync | Feedback submissions (only when you submit feedback); your email and Rules (only if you turn on Cloud Sync) |

All providers are used solely to operate and improve EarnIt.

---

## 4. Data Retention

| Data type | Retention |
|---|---|
| On-device data (Rules, usage, commitments) | Kept until you clear the app's data or uninstall |
| Anonymous analytics events | Retained by PostHog per their data retention settings |
| Crash reports | Retained by Firebase Crashlytics per their data retention settings |
| Feedback submissions | Retained as long as needed for support purposes, then deleted |
| Cloud Sync account and synced Rules | Kept until you ask us to delete your account |

---

## 5. Children's Privacy

EarnIt is not directed at children under the age of 13. We do not knowingly collect personal information from children under 13. If you believe a child under 13 has submitted personal information through the feedback form, please contact us and we will delete it promptly.

---

## 6. Your Choices and Controls

- **Analytics:** Anonymous analytics are enabled by default. If you wish to opt out, contact us at the email below and we will provide instructions.
- **Crash reports:** Crash reporting is enabled by default to help us fix bugs. If you wish to opt out, contact us.
- **Feedback:** Submitting feedback is entirely optional. Email and screenshot fields are optional within the form.
- **Cloud Sync:** Off by default. Sign out at any time in Settings > Cloud Sync; contact us to delete your account and synced Rules.
- **Notifications:** You can disable Benjamin Franklin Mode reminders at any time in Android Settings > Apps > EarnIt > Notifications.
- **App data:** You can clear all on-device data by going to Android Settings > Apps > EarnIt > Storage > Clear Data.

---

## 7. Security

We take reasonable technical measures to protect the data we process:

- Feedback submissions are transmitted over HTTPS
- Analytics and crash reporting services use encrypted transport
- On-device data is protected by Android's standard app sandboxing

---

## 8. Changes to This Policy

We may update this policy from time to time. When we do, we will update the "Last updated" date at the top. If changes are significant, we will note them in the app's release notes. Continued use of EarnIt after changes constitutes your acceptance of the updated policy.

---

## 9. Contact

If you have questions about this privacy policy or want to make a data request, contact us at:

**Email:** [your-support-email@example.com]

---

*EarnIt is built by an independent developer. This policy applies to the EarnIt Android application.*
