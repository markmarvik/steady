# Steady

**Evidence-based habit tracker for consistency, energy, focus, and longevity.**

Native Android app built with **Kotlin** and **Jetpack Compose**. Local-first: no accounts, no ads, no cloud lock-in.

<p align="center">
  <img src="docs/app-icon.png" alt="Steady app icon" width="128" height="128" />
</p>

---

## What Steady is for

Steady helps you run a **daily routine** (sleep-anchored morning → focus → wind-down) and stay pointed at **longer-term goals** (Dreamline / Path). Log flexibly, see trends, get gentle reminders when a group starts, and keep a home-screen widget that shows *what to do right now*.

---

## Features

### Today
- Timeline groups ordered by your **Daily Planner** (24h schedule)
- Current period highlighted; past / now / next sections
- Habit types: checkbox, counter, duration, scale, notes
- Multi-group habits, stacking (“after …”), skip when needed
- Quick capture inbox + workout session logger for exercise routines

### Path
- **Dreamline wizard** — Having / Being / Doing across 6- and 12-month horizons
- Goal cards with progress, confidence, first steps, and notes
- **“Am I on path?”** alignment check-ins (vision · energy · identity)
- Mindset anchors tied to Being goals

### History
- Streaks, weekly bars, Anki-style heatmap
- Tag-based trends (Supplements, Movement, Sleep, …)
- Workout session history

### Manage
Three focused sub-tabs:

| Sub-tab | What it does |
|--------|----------------|
| **Habits** | Flat catalog: create, edit, archive habits; **tags** for History; **exercise routines** |
| **Groups** | Timeline groups (Morning, Focus, Bedtime…); **attach existing habits**, order, move primary |
| **Planner** | Sleep spine + **24h timeline**, **reminders** (aligned to schedule), **backup** export |

### Widget & notifications
- Home-screen widget: current group, missed items, what’s next
- Exact alarms for group / daily-review reminders (reschedule on boot)
- Deep links from notifications and widget into the app

### Appearance
- Background: Dark · AMOLED · Light
- Accent: Green · Blue · Orange · Purple · Slate · Teal · Red
- Guided tour + welcome guide anytime from Settings

---

## Using Steady

1. **First run** — short onboarding; starter high-ROI habits are preloaded.
2. **Today** — tap to log; expand the header for weekly trends and per-group averages.
3. **Path** — run **Start Dreamline** to define dreams; check in with **Am I on path?**
4. **Manage → Planner** — set wake/bed, edit the 24h timeline, enable reminders.
5. **Manage → Habits / Groups** — catalog habits and timeline membership.
6. **Settings (gear)** — theme only; help/tour lives there too.
7. **Export** — Manage → Backup → Export Backup (works even with empty history).

Tips:
- Grant **notifications** and **exact alarms** (Android 12+) for reliable reminders.
- Add the **Steady widget** from your launcher’s widget picker.
- Archive instead of delete so history stays intact.

---

## Build & run (CLI)

```bash
# Packages (Arch / Artix example)
sudo pacman -S --needed jdk17-openjdk unzip wget curl git

# One-time SDK setup
./scripts/setup-android-sdk.sh

# Debug APK
./build.sh clean assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Install on a connected device
./build.sh installDebug
```

Or with Gradle directly:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

---

## Project layout

```
app/src/main/java/com/steady/habittracker/
  data/          # models, HabitDomain (pure), repository / DataStore
  reminders/     # AlarmManager, BootReceiver, notifications
  ui/            # Compose screens (Today, Path, History, Manage, wizards)
  widget/        # App widget models + rendering
```

- **Schema** versioned JSON in DataStore (current schema v8: routines, goals, path checks).
- **Domain** logic is unit-tested under `app/src/test/`.

---

## Privacy

All data stays on device. No analytics SDKs, no accounts. Export is a local JSON backup you control.

---

## Portability

Core models (`Group`, `Habit`, `HabitEntry`, `Schedule`, `GoalStory`, `AppData`) and domain helpers are intentionally platform-light. Android-specific pieces (AlarmManager, widgets, DataStore) sit in Android modules so a KMP shared core can be extracted later.

---

## License / contribution

Open development on GitHub. Issues and PRs welcome for bugs, UX polish, and evidence-backed habit defaults.

---

<p align="center">
  <img src="docs/app-icon.png" alt="Steady" width="64" height="64" /><br/>
  <sub>Stay on path. Stay steady.</sub>
</p>
