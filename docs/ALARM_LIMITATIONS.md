# Alarm limitations & volume behaviour (ALM-07, AC-04)

This file documents what the app does, and what it deliberately does not do, so nobody claims more than
Android allows.

## What the app does

| Situation | Behaviour |
|---|---|
| Alarm scheduling | `AlarmManager.setAlarmClock()` (exempt from Doze). If exact alarms are unavailable it falls back to `setAndAllowWhileIdle()` **and the readiness screen says timing isn't guaranteed**. |
| Sound | `MediaPlayer` with `USAGE_ALARM` on the alarm stream. The alarm stream is set to **exactly** the user's target level, never above it, ramped from 15 % player gain, and restored afterwards. |
| Volume keys while the alarm screen is focused | `AlarmActivity.dispatchKeyEvent` consumes VOLUME_UP/DOWN/MUTE, so they are neither Snooze nor Dismiss (AC-03). Presses are counted per session. |
| Volume lowered anyway (screen off, OEM overlay, settings) | If "حماية مستوى الصوت" is on, the service checks every 1.5 s and restores the alarm stream to the user's target (counted as `volumeRestores`). It never goes above the target. |
| Phone call | While `AudioManager.mode` is ringtone/in-call/in-communication, sound and vibration pause, then resume. |
| Do Not Disturb | Respected. Alarms follow the user's DND "alarms" setting. If DND blocks volume changes we don't force them. |
| Ringing too long | Safety cap of 30 minutes, then the session ends as `CANCELLED (ring_timeout)`. |
| Reboot / time change / zone change / app update | `BootReceiver` re-registers alarms, task reminders and pending wake checks. One-time alarms missed while the phone was off are disabled and logged. |
| Process killed while ringing | `AlarmService` is `START_STICKY` and resumes the persisted ringing session if it fired less than 30 minutes ago. |

## What it can't do

* Ring while the phone is powered off.
* Ring after **Force Stop** from system settings, until the app is opened again (Android cancels its alarms).
* Guarantee full-screen launch over the lock screen on Android 14+ without the *full-screen intent* permission.
  The readiness screen shows its real state.
* Guarantee that volume keys can't change the alarm volume on every device. Some OEM skins handle keys
  before the app sees them. We measure this instead of claiming otherwise.
* Override system safety features or force the volume above what the user chose.

## AC-04 measurement sheet

Run the onboarding **test alarm** on each device and fill this in. `volumeKeyPresses` and `volumeRestores`
are shown in the test result and in the diagnostics log.

| Device / Android | Screen state | Vol-down changes alarm volume? | Restored to target? | Notes |
|---|---|---|---|---|
| Pixel (stock) 14/15 | Alarm screen focused | | | |
| Pixel | Screen off / locked | | | |
| Samsung One UI 6 | Alarm screen focused | | | |
| Samsung | Screen off / locked | | | |
| Xiaomi HyperOS / MIUI | Alarm screen focused | | | |
| Xiaomi | Screen off / locked | | | |
| OPPO ColorOS | Alarm screen focused | | | |
| OPPO | Screen off / locked | | | |
