# P0 test plan (PRD §19)

Automated unit tests (`./gradlew testDebugUnitTest`) cover the pure logic:

| Test | Covers |
|---|---|
| `NextTriggerTest` | AC-01 trigger computation, weekday masks, timezone/DST |
| `AlarmStateTest` | §4.3 state machine, QR-08 scanner close keeps ringing |
| `QrValidatorTest` | QR-02/03, AC-05 invalid QR rejected |
| `SleepPlannerTest` | AC-17 bedtime math, SLP-05/06 |
| `StreakTest` | GAM-02/03/05, AC-20 history kept after a break |
| `TaskRecurrenceTest` | AC-11, AC-13, AC-14, AC-15 |

Reward de-duplication (AC-19) is enforced by the unique `(type, sourceEventId)` index on `reward_events`.

## Manual device tests (release gate)

Run each on Pixel, Samsung, Xiaomi and OPPO, on the lowest and highest supported Android versions.

| # | Steps | Expected |
|---|---|---|
| AC-01/02 | Set an alarm 2 min ahead, lock the screen, wait. Repeat after `adb shell dumpsys deviceidle force-idle`. | Rings on time, full-screen over the lock screen. Diagnostics shows `lateMs` < 1000. |
| AC-03/04 | While ringing, press volume down 5× with the screen on, then with it off. | No snooze/dismiss. Record the results in `ALARM_LIMITATIONS.md`. |
| AC-05 | In fixed-QR mode, scan a different code. | "ده مش الكود المحفوظ", keeps ringing. |
| AC-06 | Airplane mode, scan any QR. | Stops ringing. |
| AC-07/08 | Scan QR, ignore the follow-ups. | Check after N min, then 2-min follow-ups up to the limit, then "ماوصلناش تأكيد". Report shows a missed verification. |
| AC-09 | Deny camera, or tap "إيقاف طوارئ". | Math fallback / emergency works after the 5 s wait; reason is logged. |
| AC-10 | Set an alarm, reboot, don't open the app. | Rings. |
| AC-12 | Task reminder → tap "تم ✓" with the app closed. | Task marked done; no further reminder (AC-14). |
| AC-16 | Delete a task with a pending reminder. | No notification. |
| Kill test | While ringing: `adb shell am kill com.fiveam.club.debug`. | Ringing resumes within seconds. |
| Time change | Change the timezone, check the next alarm on Home. | Same wall-clock time in the new zone. |
| Overlap | Two alarms at the same minute. | One rings; the other is logged as overlap. |
