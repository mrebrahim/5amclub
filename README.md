# 5 AM — Smart Morning & Wake-Up (Android)

تطبيق أندرويد يساعدك تنام في ميعاد مناسب، تصحى بدري، وتبدأ يومك بهدف.
منبه بيقف بمسح QR، تأكيد صحيان بعد المسح، مخطط نوم، أهداف بكرة، مهام وتذكيرات، روتين صباحي، Streak وXP، ونصايح وتقارير أسبوعية. كل ده بيشتغل محليًا من غير إنترنت ولا تسجيل دخول.

> **Reliable Alarm First. Healthy Wake-Up Second. Productivity Third.**

## Stack

Kotlin 2.0 · Jetpack Compose (Material 3) · MVVM-lite with a manual DI container · Room · DataStore ·
AlarmManager `setAlarmClock()` · foreground service (`mediaPlayback`) · CameraX + ML Kit **bundled**
barcode scanner (offline) · minSdk 26, target/compile 35.

Hilt was replaced by a small manual container (`AppContainer.kt`), which the PRD allows ("Hilt or a suitable
alternative"). It keeps the alarm path free of annotation processing and reflection.

## Build

The app is built on **Codemagic** using [`codemagic.yaml`](codemagic.yaml):

| Workflow | Trigger | Output |
|---|---|---|
| `android-debug` | every push / PR | unit tests + `app-debug.apk` |
| `android-release` | git tag `v*` | signed `app-release.apk` + `.aab` |

To set it up:

1. In Codemagic, add the `mrebrahim/5amclub` repository and pick **codemagic.yaml** as the configuration.
2. For release builds, upload a keystore under *Team settings → Code signing identities → Android keystores*
   with the reference name **`fiveam_keystore`**. `app/build.gradle.kts` reads the `CM_KEYSTORE_*` variables
   Codemagic exports. Without a keystore, local release builds fall back to the debug key.
3. The version code comes from Codemagic's `BUILD_NUMBER`.

Locally (Android Studio Ladybug or newer, JDK 17):

```bash
./gradlew testDebugUnitTest assembleDebug
```

## Project layout (`app/src/main/java/com/fiveam/club`)

PRD §15.2 modules are packages in a single Gradle module for now; they can be split later without API changes.

| PRD module | Package | Key files |
|---|---|---|
| alarm-core | `alarm/core` | `AlarmScheduler`, `NextTrigger`, `AlarmState`, `AlarmSessionManager`, `BootReceiver`, `Readiness` |
| alarm-audio | `alarm/audio` | `AlarmService` (foreground), `AlarmPlayer` (alarm stream, ramp, volume protection) |
| qr-scanner | `qr` | `QrScanner` (CameraX + ML Kit), `QrValidator` (hash-only fixed QR) |
| wake-verification | `wake` | `WakeCheckScheduler`, `WakeNotifier`, `WakeCheckReceiver` |
| sleep-planner | `sleep` | `SleepPlanner`, `BedtimeScheduler`, `SleepRepository` |
| tasks-reminders | `tasks` | `TaskRepository`, `TaskReminderScheduler`, `TaskRecurrence`, `TaskReminderReceiver` |
| morning-routines | `routines` | `RoutineRepository` (20/20/20 + short routine) |
| gamification | `gamification` | `RewardEngine` (dedup via unique index), `StreakCalculator`, `ProgressRepository` |
| analytics | `gamification/ProgressRepository.weeklyReport` | weekly report |
| coach | `coach` | `TipsLibrary`, `InsightEngine` (local rules), `Motivation` |
| data-local | `data` | Room `AppDatabase`, `SettingsRepository`, `DataManager` (export/delete) |
| ui-design-system | `ui/theme`, `ui/components` | dark-first RTL theme, shared components |

## Alarm lifecycle

```
SCHEDULED → FIRING ⇄ QR_SCANNING → QR_COMPLETED → AWAITING_CONFIRMATION → CONFIRMED → ROUTINE_STARTED
              ↘ QR_FAILED (math fallback) ↗          ↘ VERIFICATION_MISSED
   any ringing state → EMERGENCY_DISMISSED | CANCELLED
```

* `AlarmSessionManager` is the only writer of session state. Every transition is checked against
  `AlarmState.canTransitionTo` and persisted before side effects, so the service can be killed and
  restored (`START_STICKY` + `recoverRinging()`).
* Only one session rings at a time; an alarm firing during another is logged as `CANCELLED (overlap)`.
* Wake checks: after QR, a notification asks "صحيت ولا لسه؟" after the configured delay (default 5 min),
  then up to N follow-ups 2 minutes apart, then `VERIFICATION_MISSED`. No endless loud chain.
* Nothing in the alarm path needs the network, login, AI, or the main screen.

## Phase status

| Phase | Status |
|---|---|
| 1. Alarm foundation | ✅ Implemented: scheduling, reboot/time/zone recovery, full-screen alarm, QR (any/fixed), math fallback, emergency stop, wake verification, readiness screen, end-to-end test alarm |
| 2. Sleep & planning | ✅ Implemented: onboarding, sleep planner, bedtime reminders, tomorrow goals, tasks with Done/Snooze/Open notification actions, recurrence |
| 3. Habits & motivation | ✅ Implemented: routine builder and runner, streak, XP, milestones, rest day, daily tips & challenges |
| 4. Insights & quality | 🟡 Weekly report, delay reasons and local insights are done. Device-matrix testing, accessibility audit and the privacy policy are still to do (see `docs/TEST_PLAN.md`). |
| 5. AI coach | ⏳ Placeholder card only. A cloud backend (e.g. Supabase) is out of scope until then. |

## Known limits

See [`docs/ALARM_LIMITATIONS.md`](docs/ALARM_LIMITATIONS.md). In short, the app **cannot** ring when the phone is off
or after a Force Stop, and it **does not claim** to fully block volume keys on every device.
