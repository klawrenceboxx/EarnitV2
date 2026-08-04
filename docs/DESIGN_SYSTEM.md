# EarnIt — Design System Reference

Extracted from real app screenshots and the marketing site mockup. Values are close
visual estimates, not pixel-sampled — treat as a strong starting point, not gospel.
**For exact hex/spacing values**, pull `src/styles/theme.css` and
`src/styles/tailwind.css` from the Figma Make prototype
(figma.com/make/KCreg6zdRWxjK9p2aNwldK) and replace the estimates below.

Paste this whole file into any LLM (Claude Code, Codex, ChatGPT, etc.) before asking
it to build or modify UI — it's written to be tool-agnostic.

---

## Color Palette

| Role | Approx. value | Usage |
|---|---|---|
| Background | `#1A1210` (near-black, warm brown-black — not pure `#000`) | App background, dominant surface |
| Card surface | `#1F1815` (marginally lighter than bg) | Rounded outlined cards |
| Card border | `#3A2E28` (thin, warm dark outline) | Card/container edges |
| Primary text | `#F5EDE4` (warm off-white, not pure white) | Headlines, primary content |
| Secondary text | `#A89A8E` (muted warm gray) | Subtitles, descriptions, metadata |
| Primary accent (CTA) | `#F5B8A8` (peach/pink) | Primary buttons, active tab indicator, key highlights |
| Success / active green | `#7CB87C`–`#8FCB8F` | "Rule active," Earn Reward Time icon/text, OK status ring |
| Info blue | `#5B9BD5`-ish | "Complete to Unlock" feature accent |
| Warning amber | `#D4A24C`-ish | "Scheduled Block" feature accent, clock icon |
| Alert / inactive rose | `#E8A79C`-ish (softer than the primary peach) | "Not blocking now," "Premium inactive," expired states |

**Pattern:** each Rule *type* owns a consistent accent color across the whole app —
green = Earn Reward Time, blue = Complete to Unlock, amber = Scheduled Block. This
color-coding is load-bearing for scannability; don't reassign these per-screen.

**Status color logic:** green ring/text = healthy/active. Rose/pink ring+text =
needs attention (paused, inactive, outside schedule, premium-gated) — but rose is
*never* used for the primary accent CTA color, so it stays visually distinct from
"upgrade" prompts.

---

## Typography

- Sans-serif, rounded/geometric character (reads like SF Pro / system font, not a
  display face)
- Headlines: bold weight, tight leading, primary text color
- Body/description: regular weight, secondary text color, slightly smaller
- Card titles: medium-bold, colored per feature-type accent (see palette)
- No italic usage observed
- Numbers/timers (e.g. "10:2", "2 min available") get their own visual weight —
  often larger or bolder than surrounding body text, since they're the app's core
  "at a glance" info

---

## Components

### Cards
- Rounded corners (large radius, ~16-20px equivalent)
- Thin 1px outlined border, not shadow-based elevation
- Padding generous — content breathes, not cramped
- Status cards (active/paused/inactive) get a **colored border matching their
  status**, not just an icon — the whole card outline shifts color (green border
  when active, rose border when inactive/paused)

### Buttons
- **Primary CTA:** full-width, pill-shaped (fully rounded ends), filled peach
  background, dark text on top — high contrast, unmistakably "the button to tap"
- **Secondary/inline actions** (e.g. "Open" next to an app): outlined pill, no
  fill, peach text/border on transparent background — clearly secondary weight
- No ghost/text-only buttons observed for primary actions; even secondary actions
  keep a visible outline

### Icons
- App icons: circular, full-color, recognizable brand icons (Instagram, Duolingo,
  etc.) — not desaturated or reskinned
- Feature icons (Rule type icons): colored to match their accent (green sprout for
  Earn, blue lock for Complete to Unlock, amber clock for Scheduled Block), on a
  softly-tinted circular background matching the same hue at low opacity

### Status/progress indicators
- Onboarding dots: small circle row, active dot filled peach, inactive dots
  muted/outlined
- Toggles (switches): peach fill when on, muted/outlined when off — matches
  primary accent, not a separate toggle color

---

## Layout Conventions

- Single-column, generous vertical spacing between cards
- Status bar / header content stays minimal — page title centered, back
  chevron in accent peach color (not white/gray)
- Website mirrors the app's exact palette and card style — this is intentional
  brand consistency, not a separate marketing theme. Phone mockups are shown at
  an angle (not flat-on) with soft ambient lighting/shadow, dark background
  matching the app's own near-black

---

## Voice / Copy Patterns

- Short, second-person, direct: "Do the work. Earn the time." / "Choose one or
  more Earn Apps where productive time should count."
- Status copy is plain-language, not technical: "Premium inactive" + "This Rule
  is saved, but Free supports up to 2 active Rules." — states the fact, then
  explains the reason, then offers the action ("Resume now")
- Never uses alarming/red-flag language for non-error states (e.g. "Not blocking
  now" instead of "Inactive" or "Off")

---

## What NOT to do (anti-patterns)

<!-- TODO: this section was cut off — fill in from screenshots or design review -->
