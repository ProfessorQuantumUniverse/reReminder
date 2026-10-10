# Changelog

All notable changes to reReminder are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [4.0] — 2026-10-10

More ways to repeat, a real alarm mode, sounds that finally do what you pick, and a core
rebuilt to fire reliably.

### Added

- **Custom repeat (#7).** Besides minutes and hours, a reminder can repeat every N days,
  weeks or months. Weekly reminders can pick their weekdays ("every 2 weeks on Mon and
  Thu"), monthly ones their days of the month ("every 3 months on the 15th"; the 29th–31st
  fall on the last day of shorter months). Calendar repeats fire at the start time's time of
  day and stay there across daylight saving changes.
- **Several time windows per reminder (#7),** e.g. Mon–Fri 09:00–17:00 plus Sat 10:00–12:00.
- **Early alerts (#11).** Up to three heads-ups before each event (5 min to 1 day ahead).
  The notification says what is coming and when ("In 10 min · 10:30").
- **Alarm mode (#10).** Per reminder, choose Notification or Alarm. An alarm covers the lock
  screen and keeps ringing and vibrating until you dismiss or snooze it, and goes quiet
  after five minutes with a "missed" note. The snooze length is set in Settings.
- **A sound per reminder (#13):** the default, a tone of its own, the message read aloud, or
  silence — with a preview. Whether a reminder speaks is now its own choice rather than one
  app-wide setting.
- **Sound, vibrate or mute in one tap:** a button above the list switches every reminder,
  alarms included, between sounding as set, vibrating only and staying silent.
- **Speak at the volume of (#9):** spoken reminders can follow the notification, media or
  alarm volume. New installs use notification volume; existing users keep media.
- **List tools (#8):** swipe a reminder away to delete it (with Undo), sort by next alert or
  drag your own order, and an optional compact list that shows only the name and when it is
  next due.
- A quiet "missed" notification when an alarm or a dated reminder could not ring because the
  phone was off.

### Changed

- **The sound you pick is the sound you hear (#12).** Android fixes a notification
  channel's sound when the channel is created, so reReminder now keeps one channel per sound
  and switches channels when you pick another one; unused channels are removed again. Changes
  take effect immediately, a reminder set to silent really is silent, and sound and vibration
  no longer play twice. (Playing the sound from the app itself is not an option: Android 17
  mutes audio that background apps start.)
- "Ringtone" in Settings is now "Notification sound", with a separate "Alarm sound"; these are
  what a reminder's "Default" sound plays. The global "Sound enabled" and "Sound or
  text-to-speech" settings are gone — reminders that relied on them were switched to speech or
  silence on update, so nothing sounds different.
- `{next}` names the next event and includes the day when it isn't today (#11).
- Alerts respect Do Not Disturb, silent and vibrate mode (Android itself plays the tone), and
  stay quiet when notifications are blocked.
- Spoken reminders duck other audio instead of talking over it.

### Fixed

- Reminders without a start date no longer drift later with every late alarm; every reminder
  now runs on a fixed grid.
- Daily reminders kept their 09:00 across daylight saving changes only by luck; calendar
  repeats now compute in local time.
- Reminders fire after a reboot even before the phone is unlocked (data now lives in
  device-protected storage).
- Allowing exact alarms again re-arms reminders straight away instead of on the next app start.
- Sound and speech can no longer be cut off mid-way when Android reclaims the app, and two
  reminders firing together no longer silence each other.
- A damaged reminders file is kept aside instead of being replaced by an empty list.

## [3.0] — 2026-07-27

The single-reminder limit is gone. reReminder now runs as many independent loops as you
need, each with its own schedule and message.

### Added

- **Multiple reminders.** Create any number of reminders, each with its own interval, name,
  message and accent colour. Every reminder counts down independently — toggling or editing
  one never restarts another.
- **Schedules.** Restrict a reminder to specific weekdays and to a time window such as
  08:00–17:00. Windows may cross midnight (e.g. 22:00–06:00). Outside its window a reminder
  stays quiet and shows when it will next start. The schedule is off by default and lives
  behind a single switch, so simple reminders stay simple.
- **Message variables.** Insert `{time}`, `{date}`, `{day}`, `{name}`, `{interval}` or
  `{next}` into a reminder's text and it is filled in the moment the reminder fires. The
  editor shows a live preview.
- **Master switch.** Pause every reminder at once without losing each reminder's own
  on/off state, and see which reminder is up next and when.
- **Reliability check** in the settings: live status for exact-alarm permission and battery
  optimisation, each linking straight to the relevant system screen, plus the
  dontkillmyapp.com guide for manufacturer restrictions.
- **Per-reminder sound and vibration** toggles, on top of the app-wide alert settings.
- **German translation**, with per-app language support on Android 13+.
- **Made with passion in Europe** 🇪🇺 badge on the settings and start screens.

### Changed

- Rebuilt the reminder list around independent reminder cards with live countdowns,
  schedule summaries and an empty state.
- Rebuilt the editor around what actually defines a reminder: name and interval first,
  everything else quiet below. Variables and the schedule fold away until you need them.
- Notifications now use the reminder's name as the title and support long text, so several
  reminders can be visible at once instead of overwriting each other.
- Text-to-Speech follows the device language instead of always speaking German.
- Refreshed the Material 3 theme: full light/dark colour scheme, dynamic colour on
  Android 12+, edge-to-edge layout and a complete type scale.
- Updated the toolchain to Gradle 9.6, Android Gradle Plugin 9.3, Kotlin 2.4 and
  Compose BOM 2026.06, targeting Android SDK 37.
- Release builds are now minified and resource-shrunk.

### Fixed

- **Reminders could stop forever after a single failure.** The next alarm is now armed
  before the notification is posted, so a broken ringtone, a dead TTS engine or the process
  being killed mid-alert can no longer break the loop.
- **Reminders were lost on reboot.** `BootReceiver` was declared in the manifest but the
  class did not exist, and its intent filter required a data URI that boot broadcasts never
  carry — so reminders silently stopped after every restart.
- **Clock and timezone changes** now trigger a full reschedule; time windows are local-time
  based and were left pointing at the old wall clock.
- Exact-alarm permission is checked before scheduling; if it has been revoked, reminders
  fall back to inexact alarms instead of disappearing.
- Removed a dangling `<service>` declaration for a class that did not exist, and dropped
  the unused `FOREGROUND_SERVICE` and `WAKE_LOCK` permissions.
- Fixed the status-bar icon contrast being inverted in the previous theme.

### Removed

- Unused dependencies: Room, KSP, WorkManager, Navigation, Preference, ConstraintLayout and
  data/view binding, along with the dead XML layouts left over from the pre-Compose version.

### Migration

Your existing reminder is carried over automatically and becomes the first entry in the new
list, keeping its interval, message and alert settings.

## [2.0] — 2025

A major update with a complete rewrite of the app.

### Added

- Text-to-Speech, vibration and sound settings.
- "Don't Kill My App" integration: a one-time warning on devices with aggressive background
  process management.

### Changed

- Complete redesign using Jetpack Compose.
- More reliable alarms and notifications.

[3.0]: https://github.com/ProfessorQuantumUniverse/reReminder/releases/tag/v3.0
[2.0]: https://github.com/ProfessorQuantumUniverse/reReminder/releases/tag/v2.0
