# Steady

**Evidence-based habit tracker for consistency, energy, focus, and longevity.**

Native Android app built with **Kotlin** and **Jetpack Compose**. Local-first: no accounts, no ads, no cloud lock-in.

<p align="center">
  <img src="docs/app-icon.png" alt="Steady app icon" width="128" height="128" />
</p>

---

## What Steady is for

Steady helps you run a **daily routine** (sleep-anchored morning → focus → wind-down) and stay pointed at **longer-term goals** (Dreamline / Path). Log flexibly, see trends, get gentle reminders when a group starts, and keep a home-screen widget that shows *what to do right now*.

Complex capabilities — wearables, snore watch, deep work, oral hygiene, screen audit — are **special habit blocks** you place on your day, not separate mini-apps.

The planner day rolls over at **04:00 by default** (configurable), so late-night logging still counts for “today.”

---

## Design philosophy: special habit blocks

**Features are (or become) blocks / extensions** on the Today timeline or the **Enabled blocks** strip:

| Placement | Examples |
|-----------|----------|
| **Day timeline** (Morning / Work / Bedtime grids) | Standard habits, oral hygiene (AM+PM slots), sleep phone guard, Deep Work, Pomodoro, snore start/stop |
| **Enabled blocks strip** (below habits on Today) | Wearable Sync (Gadgetbridge), Screen Usage, Sensor Auto-Read, other tool-style blocks |

Logging a block runs side-effects (timers, imports, sensors) while still counting for streaks, Momentum, reminders, and the widget.

**Multi-group habits** (same habit in morning *and* evening) log **independently per section** — finishing brush in the morning does not close the evening instance.

See **Manage → Blocks** for templates.

---

## Features

### Today
- Timeline groups from your **Daily Planner** (24h schedule); past / now / next
- **Logical day** (default 4am rollover) for logs, show/hide done, and completion
- **Square habit grid** — two-finger horizontal pinch (2–4 columns); tap to log, long-press to skip
- Habit types: checkbox, counter, duration, scale, notes; stacks (“after …”); point weights
- **Priorities (MITs)** — up to **3 Most Important Tasks** per day; carry unfinished to the next logical day; promote from Work todos (long-press)
- Work **todos** as squares in the Work section
- **Deep Work** — start/finish focus sessions from the timeline; minutes + intent on the log note
- **Write** & **Journal** full screens; Chat with Grok entry
- Workout session logger for exercise routines
- **Enabled blocks** strip under the grids for tools that are not morning/evening checklists

### Path
- **Dreamline wizard** — Having / Being / Doing (6- and 12-month horizons)
- Goal cards: progress, confidence, first steps, notes
- **“Am I on path?”** alignment check-ins (vision · energy · identity)
- Mindset anchors tied to Being goals

### History
- Open via the **top progress bar** (not a main tab)
- Streaks, weekly bars, Anki-style heatmap
- **Momentum** — points, level, lifetime, 30-day chart; MIT completions add points
- Tag trends; habit squares by 30-day completion
- **Screen usage** frames when the block is on (wall-clock screen-on, capped)
- **Wearables** frames (steps / sleep / HR min·avg·max) when Gadgetbridge is enabled
- Workout history; sleep-audio nights
- **Chat with Grok** — multi-select notes/tags, time scope, stats tools, **saved presets**

### Manage (four sub-tabs)

| Sub-tab | What it does |
|--------|----------------|
| **Habits** | Catalog, tags, archive / restore, exercise routines |
| **Groups** | Timeline groups; multi-group membership; order |
| **Blocks** | Extensions: Deep Work, Pomodoro, oral hygiene, sleep phone guard, snore watch, sensors, screen usage, Gadgetbridge, ESM check-ins, local web |
| **Time** | Sleep spine, 24h timeline, reminders, sensors / auto-log, day start hour, full JSON **backup** |

### Productivity
- **Deep Work block** — default 50/90/120m sessions; start then finish on Today; status on widget
- **Daily MITs** — 1–3 outcomes, not an infinite todo list; +5 Momentum each when done
- **Pomodoro** still available for classic cycles; pairs with LAN Focus UI
- Work todos from Capture tags; long-press → MIT when room

### Momentum (scoring)
- Points for due **slots** completed (multi-group AM/PM count separately)
- Target / quality bonuses; solid day, full clear, Path check-in bonuses
- MIT completion points; optional habit **point weights**
- Soft screen-overage penalty when a Screen Usage limit is set
- Levels from lifetime points (calm titles)

### Wearables (Gadgetbridge)
- **Wearable Sync** block: pick export DB (document picker + schema validation)
- Hourly (configurable) import of steps, sleep, heart rate into unified day metrics
- History heatstrips; optional notifications (step goal, personal best, sleep/HR thresholds)
- Appears under **Enabled blocks**, not in morning habits

### Hygiene & sleep helpers
- **Oral Hygiene** — brush / floss / tongue / water / mouthwash; morning + evening (independent completions)
- **Sleep · Phone guard** — “Phone parked” (evening) + “Phone later” (morning); optional screen minutes on the note
- **Snore Watch** — activate at bedtime, stop & review in the morning; charging gate optional

### Auto-log (sensors & external)
- Opt-in per habit: Suggest or Auto-apply
- Screen time · evening screen after wind-down · bedtime light · ambient noise · phone / external steps
- Respects logical day (`dayStartHour`)

### Widget & notifications
- Current group, pending habits (slot-aware multi-group toggles), deep work remaining
- Exact alarms; smart & gentle: adaptive timing, quiet hours, daily cap, streak-risk copy
- Quotes and awareness check-ins (opt-in)
- Deep links from notifications and widget

### Local web UI (LAN)
- Foreground service; **HTTP** `:8787`, optional **HTTPS** `:8788` (self-signed)
- Desktop: Today, inbox/journal, Path, History, habits, Focus (Pomodoro), workouts
- Auto turn-off; trusted Wi‑Fi + PIN for auto-start
- Fully local — no cloud

### Appearance
- Theme packs (Nord, Catppuccin, Tokyo Night, Gruvbox, Dracula, Rosé Pine, Everforest, …)
- Accent swatches + custom HSV; guided tour from Settings

---

## Using Steady

1. **First run** — onboarding; starter high-ROI habits preloaded.
2. **Today** — set **Priorities**, log habits, run **Deep Work** if enabled; pinch to densify the grid.
3. **Path** — Dreamline for long goals; **Am I on path?** check-ins.
4. **Manage → Time** — wake/bed, 24h timeline, **day starts at** hour, reminders, backup.
5. **Manage → Blocks** — Deep Work, oral hygiene, phone guard, Gadgetbridge, LAN web, etc.
6. **Progress bar** — tap for History; long-press for quick progress details.
7. **Settings (gear)** — theme + help/tour.

Tips:
- Grant **notifications** and **exact alarms** (Android 12+) for reliable reminders.
- **Usage access** for screen / phone-guard minutes; optional location/mic for sensor & snore blocks.
- Add the **Steady widget** from your launcher.
- Archive instead of delete so history stays intact.
- Full backup: Manage → Time → Export / Import JSON.

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
  data/          # models, HabitDomain, ProductivityDomain, blocks, repository / DataStore
  extensions/    # ExtensionManager side-effects on log
  sensors/       # auto-log, screen time, Gadgetbridge import
  sleepaudio/    # snore / overnight capture
  reminders/     # AlarmManager, BootReceiver, notifications
  ui/            # Compose (Today, Path, History, Manage, Write, Journal, Grok)
  widget/        # app widget models + rendering
  web/           # LAN LocalWebServer
```

- **Schema** — versioned JSON in DataStore (v15+ fields: wearables, oral hygiene, sleep phone, deep work, MITs, day start hour).
- **Domain** — unit-tested under `app/src/test/`.

---

## Privacy

All data stays on device. No analytics SDKs, no accounts. Export is a local JSON backup you control.

---

## Portability

Core models (`Group`, `Habit`, `HabitEntry`, `Schedule`, `GoalStory`, `AppData`, MITs, wearable days) and domain helpers are platform-light. Android-specific pieces (AlarmManager, widgets, DataStore, UsageStats) sit outside pure domain so a shared core can be extracted later.

---

## License / contribution

Open development on GitHub. Issues and PRs welcome for bugs, UX polish, and evidence-backed habit defaults.

---

<p align="center">
  <img src="docs/app-icon.png" alt="Steady" width="64" height="64" /><br/>
  <sub>Stay on path. Stay steady.</sub>
</p>
