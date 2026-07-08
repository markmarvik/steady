# Steady

Evidence-based habit tracker for consistency, energy, focus and longevity. Native Android (Kotlin + Jetpack Compose).

Groups, flexible logging (checkbox / counter / duration / scale / notes), configurable reminders, and a dedicated home-screen widget for time-of-day prompts — designed from real usage patterns in TrackAndGraph.

**New**: Onboarding for first-time users, in-app color scheme settings, rich weekly progress tracker (with trend arrows and per-group expandable circles), reliable export (works even with empty data), archived items view + restore in Manage, and the floating action button has been removed (use Manage for deliberate habit/group creation).

## Key Features
- **Grouping by time-of-day / routine**: Morning Routine, Focus & Work, Evening / Wind Down, Mindset & Review (fully customizable in Manage tab).
- **Richer habit types**: 
  - Checkbox (tap to toggle)
  - Counter (reps, doses, liters)
  - Duration (minutes)
  - Scale 1-5 (energy/mood)
  - Note/journal (gratitude, wins, reflections)
- **Today screen**: Grouped sections, current period highlighted, quick log for non-binary habits.
- **History**: 14-day completion bars + streak tracking.
- **Reminders & alarms**: Per-group (or daily review) — set time + weekdays directly in the app. Notifications prompt you.
- **Dedicated Widget**: Shows current time-of-day habits at a glance. Tap to open app. Time-aware.
- **Customization**: Add/edit/delete groups + habits, change type, set targets/units. Reorder via Manage.
- **Motivation**: Current streak (🔥), progress ring + weekly tracker with trend arrow, expandable per-group circles, rich notes preserved in history.
- **Onboarding / Help**: Friendly first-run guide + anytime "Run guided tour / onboarding" (header ⓘ button and in Settings). The interactive tour walks through Today, History, Manage, progress, reminders, widget and key gestures. Welcome guide replays the quick-start bullets.
- **Settings**: Choose color scheme (green/blue/orange/purple/slate) — live update.
- **Backup**: Export button now fully works (even with minimal/empty data) via JSON backup.
- **Manage**: Drill-down editing, reminders, CSV/backup export (fully functional, works on empty projects), and an Archived section to view + restore items.
- **All local**: DataStore + JSON. No accounts, no ads.

Default starter set includes the original 7 high-ROI habits plus several patterns from real tracking (Box Breathing, supplements, strength, NSDR, wins, reflections).

## Using Steady
- **First run**: You'll see a short onboarding explaining groups, the widget, and key flows.
- **Today tab**: Tap habits to log. The header shows today's completion circle. It includes:
  - A 7-day weekly tracker row.
  - Trend arrow (▲ / ▼) comparing to yesterday.
  - Tap the card to expand and see weekly % circles for every group.
- **Settings**: Tap the gear icon (top right) to switch color schemes instantly (Green / Blue / Orange / Purple / Slate).
- **Help / Tour**: Tap the ⓘ icon next to the gear (or Settings → "Guided tour" / "Welcome guide") to run the interactive onboarding that takes you around the app: explains the progress bar, switches tabs for you, details every screen, gestures (tap/log/long-press), reminders, widget, and backup.
- **Manage tab**: Create/edit groups and habits here (including defaults + units for counters/durations). Set per-group reminders. Use "Export Backup" to save a full JSON (groups + habits + history). Scroll to the Archived section to restore anything.
- **No quick + button**: We removed the floating action button. Add things deliberately from Manage.
- **Widget**: Add from your launcher. It shows the current time-of-day group, missed items from the previous, and a peek at the upcoming group. Taps open the app (or toggle simple checkboxes directly in some cases).

Tip: Everything is logged with exact timestamps. Export regularly for backup or analysis.

## Build & Run (Artix Linux CLI, no Android Studio)

```bash
# 1. Packages
sudo pacman -S --needed jdk17-openjdk unzip wget curl git

# 2. SDK (one time)
./scripts/setup-android-sdk.sh

# 3. Build
./build.sh clean assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Install:
```bash
./build.sh installDebug   # (device via adb)
```

After install:
- Grant notification permission when prompted.
- For exact alarms (Android 12+): if a toast or card appears, go to system Settings → Apps → Steady → Alarms & reminders → Allow.
- Add the Steady widget from your launcher’s widget picker for the best experience.
- The first time you open the app you'll see onboarding.
- Use the gear icon for color themes. Tap the ⓘ (info) icon for the interactive guided tour / onboarding anytime. All adds/edits happen in the Manage tab. Export your data from Manage anytime.

## Widget + Reminders
- Widget automatically shows the active period group (morning before noon, etc.).
- Reminders configured in Manage fire notifications that deep-link into the app.
- Boot-safe (reschedules on restart).

## Future iOS / Portability
Core models (Group, Habit, HabitEntry, Reminder, AppData) and the `HabitRepository` interface are intentionally simple and serializable. Date handling uses kotlinx-datetime. The Android-specific pieces (AlarmManager, widgets, DataStore) live only in the Android impl.

When ready, a KMP `:shared` module can be extracted in <1 day.

## Tips for best results (matching evidence-based + user patterns)
- Use groups for time-of-day to keep cognitive load low.
- Use notes on Mindset/Review habits — they become powerful when you look back.
- Set realistic reminders and actually use the widget for 1-tap access.
- Streaks are forgiving (≥60% or a few items logged counts).

Built to be the simple, motivating daily driver that TrackAndGraph was missing.
