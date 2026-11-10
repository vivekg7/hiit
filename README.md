# HIIT Timer

An interval timer for HIIT, Tabata, EMOM, AMRAP and circuits, for Android 12 and
later. Beeps, voice and vibration cues, and a timer that stays exact in the
background and with the screen off.

It exists because the interval timers on the Play Store are full of ads, and an ad
popping up between rounds is the last thing you want mid-workout. This one is free
and open source (MIT), with **no ads, no tracking, no analytics, no account and no
internet permission**.

## Features

- **Presets:** Tabata (20 s work / 10 s rest × 8), EMOM (a 60 s interval per round),
  AMRAP (one countdown) and a sample bodyweight circuit.
- **Custom workouts:** get-ready time, work, rest, rounds, sets, rest between sets and
  cool-down, plus optional exercise names per round (a shorter list repeats). Save,
  edit, duplicate and delete. The total time is shown on every workout and in the
  editor.
- **Running screen:** full-screen phase colour, huge remaining time with a progress
  ring, round x/y, set x/y, what's next, overall progress. Pause/resume, skip,
  previous (restarts the phase, or goes back one if it has only just begun) and stop
  with confirmation. The screen stays on.
- **Cues:** beeps on the last 3 seconds and at each phase change; optional spoken
  "Work", "Rest", exercise name, "Halfway", "Last round"; vibration. Each can be
  switched off in Settings.
- **Background:** an ongoing notification shows the phase, round and a live
  countdown, with pause/resume and skip.

## Permissions

| Permission                                        | Why                                                                                                                                                                           |
| ------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH` | Keeps the timer running while the app is in the background or the screen is off.                                                                                              |
| `HIGH_SAMPLING_RATE_SENSORS`                      | Android requires a `health` foreground service to hold one of a fixed set of permissions; this is the only one granted at install without a prompt. The app reads no sensors. |
| `POST_NOTIFICATIONS`                              | The ongoing notification with the controls. Asked for on Android 13+ when you first start a workout; the timer works without it.                                              |
| `WAKE_LOCK`                                       | Keeps the CPU awake while a workout is running (released on pause and at the end), so cues fire on time with the screen off.                                                  |
| `VIBRATE`                                         | Vibration cues.                                                                                                                                                               |

There is **no `INTERNET` permission**: the app cannot reach the network at all. See
[PRIVACY.md](PRIVACY.md).

## Design decisions

- **Deadlines, not delays.** `TimerEngine` holds an absolute deadline on
  `SystemClock.elapsedRealtime()` and each phase's deadline is the previous one plus
  the phase length. Ticks only _observe_ the clock, so a late or missed tick can delay
  a beep but can never make the workout drift; after the CPU sleeps it lands in
  exactly the right phase. The UI redraws from the same deadline every frame, and the
  notification uses a system countdown chronometer, so neither needs per-second
  updates from the service.
- **`health` foreground service type.** Android 14+ needs a declared type. `health`
  is the one Android defines for fitness apps ("exercise trackers"). `mediaPlayback`
  would misdescribe the app, `dataSync` and `mediaProcessing` have a 6-hour daily
  cap, `shortService` stops after about 3 minutes, and `specialUse` is for cases no
  type covers.
- **Cue audio uses `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`** with a transient
  "may duck" focus request. That is the usage Android built for short prompts over
  music: music dips instead of pausing, and the volume follows the media volume you
  already have up. Focus is held across the 3-2-1 beeps and the announcement that
  follows so the music dips once per change, not every second.
- **Beeps are synthesised at runtime** (`ToneSynth`) into WAV files in the cache and
  played with `SoundPool`, so no binary audio is checked in and beeps share the
  voice's audio attributes.
- **Vibration uses alarm usage**: a timer cue is one, and Android filters some other
  usages for apps that aren't on screen.
- **Fixed phase colours**, not Material You: work and rest must always be obviously
  different hues at a glance. The rest of the app uses dynamic colour.
- **No Stop button in the notification**: a pocket or lock-screen tap must not end a
  workout without the confirmation the app shows.
- **Storage:** workouts are a JSON list in a Preferences DataStore. A handful of small
  records doesn't need a database, and one value makes every edit atomic. Built-in
  presets are only defaults until you change the list; edit or delete them freely.
- **Cue settings apply to the next workout** started, so changing them can never
  disturb one in progress.

## Building

Requires the Android SDK (compileSdk 37). This machine has no system JDK; use the
JBR bundled with Android Studio:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug testDebugUnitTest   # app + JVM tests
./gradlew assembleRelease                   # R8-minified release
```

The release build is unsigned unless a `keystore.properties` (git-ignored) exists in
the project root with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

The timing logic (`core/`: timeline builder, timer state machine, tone synthesis) is
plain Kotlin covered by JVM unit tests in `app/src/test`.

## License

MIT, see [LICENSE](LICENSE).
