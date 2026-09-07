# Smart Reminder

An Android app for people who hate writing reminders properly.

You type something quick and messy like

```
HW englisj read page 32 till teusday 10.10
```

and the app turns it into

> 📚 **English homework: read page 32**
> Read page 32 for English. Due Saturday, 10 October.

…adds it to your list, and then nags you about it during the day without ever telling you *when*
the next nudge is coming.

**[⬇ Download the APK](downloads/SmartReminder.apk)** · Android 8.0 (API 26) or newer.

---

## What it does

- **Type it like a text message.** Typos, abbreviations, German or English, `10.10`, `tmrw`, `at 5pm`, `every morning` – it figures it out.
- **AI clean-up.** With a Claude API key your note is rewritten by Claude into a clear title and sentence, with the right date, time and icon. Without a key a built-in offline parser does a simpler version of the same thing, so the app works either way.
- **Three nag levels, named exactly like this:** `meh` · `whatever` · `A LOT`.
  `meh` is the gentlest, `A LOT` the strongest. The app deliberately never shows you how often that is.
- **Deadlines get louder on their own.** The closer the due date, the more often you hear about it. A timed deadline also gets one guaranteed "final call" shortly before.
- **Unpredictable by design.** Reminder moments are randomised (and mixed with a secret number generated on your phone), so you can't learn the pattern and start ignoring it.
- **Two kinds of reminders.**
  - **Deadlines** – homework, exams, appointments, errands. Tick them off once.
  - **Daily** – supplements, water, stretching, meds. These come back every day, can be tied to morning / midday / evening, and are meant to be *not annoying*.
- **The 3-second pop-up.** Instead of a notification, a small card slides in at the top of whatever you're doing, stays for a few seconds (2–8, your choice) and disappears by itself. One tap on **Done** ticks the thing off. If the screen is off, daily things quietly try again a bit later; deadlines fall back to a normal notification (configurable).
- **Active hours.** Nothing fires outside the window you set (default 08:00–22:00).
- **Share to add.** Select text in any app → Share → Smart Reminder.
- **Nice to look at.** Material 3, light and dark mode, optional wallpaper colours on Android 12+.

## Install

1. Download [`downloads/SmartReminder.apk`](downloads/SmartReminder.apk) on your phone.
2. Open it. Android will ask you to allow installs from your browser – allow it once.
3. Open the app and go through the **Finish setup** card:
   - **Pop-ups over other apps** – needed for the 3-second card.
   - **Notifications** – used when the screen is off.
   - **No battery limits** – recommended, otherwise some phones (Xiaomi, Huawei, Samsung…) silence background apps after a while.

Every APK in `downloads/` is signed with the same key, so a newer one installs as an update.

## Using AI (optional)

1. Get an API key at [console.anthropic.com](https://console.anthropic.com) (Claude API).
2. In the app: **Settings → AI clean-up → paste the key → Test connection**.
3. Done. From now on **✨ Make it clear** sends your note to Claude.

Notes:

- The key is stored only on your phone and is only ever sent to `api.anthropic.com`.
- One note costs a fraction of a cent. The default model is `claude-opus-5`; you can switch to `claude-haiku-4-5` in Settings if you want it cheaper and faster.
- No key, no internet, or the API is down? The app silently uses its offline parser and tells you so in the preview.

## How reminding works (spoiler-free)

| You choose | The app does |
|---|---|
| `meh` | a light touch |
| `whatever` | a normal amount |
| `A LOT` | a lot – but still spread over the day and only inside your active hours |

On top of that, deadlines automatically ramp up as the date approaches and keep nagging for a while if you miss it. Daily things stay at their level every day, use the quiet pop-up by default, and stop for the day once you tap **Done today**.

If you really want to see the numbers, they live in
[`Cadence.kt`](app/src/main/java/com/itzsuli/smartreminder/schedule/Cadence.kt) – but the file starts with a spoiler warning for a reason.

## Project layout

```
app/src/main/java/com/itzsuli/smartreminder/
├── MainActivity.kt          entry point, share-to-add handling
├── SmartReminderApp.kt      Application: wires up storage + notification channels
├── data/                    models, JSON storage, settings
├── ai/
│   ├── ClaudeParser.kt      Claude Messages API call (structured JSON output)
│   ├── LocalParser.kt       offline fallback parser (dates, weekdays, typos, abbreviations)
│   └── ReminderParser.kt    picks Claude or offline
├── schedule/
│   ├── Cadence.kt           ⚠ how often things fire (spoilers)
│   ├── Scheduler.kt         keeps one AlarmManager alarm armed for the next moment
│   ├── FireEngine.kt        decides pop-up vs notification vs "try later"
│   ├── Popup.kt             the 3-second overlay card
│   └── Notifier.kt, *Receiver.kt
└── ui/                      Jetpack Compose screens (home, add/edit, settings) + theme
app/src/test/                unit tests for the parser and the cadence logic
downloads/                   the finished APK
keystore/                    signing key (see its README)
.github/workflows/build.yml  builds + tests on every push, uploads the APK as an artifact
```

## Build it yourself

Requires JDK 17+ and the Android SDK (platform 36, build-tools 36).

```bash
./gradlew testReleaseUnitTest      # parser + cadence tests
./gradlew assembleRelease          # → app/build/outputs/apk/release/app-release.apk
```

Or push to `main` and let GitHub Actions do it (the APK appears under the workflow run's artifacts).

## Permissions, explained

| Permission | Why |
|---|---|
| Display over other apps | the pop-up card |
| Notifications | fallback when the screen is off |
| Exact alarms | fire at the planned minute instead of "sometime soon" |
| Ignore battery optimisations | so Android doesn't kill the alarms |
| Run at boot | re-arm the alarm after a restart |
| Internet | only for the optional Claude call |

## Ideas for later

- Home-screen widget / quick-settings tile for one-tap adding
- Voice input ("Hey, remind me…")
- Import due dates from a school timetable or calendar
- Streaks for daily things
- Backup / restore as a file
- Wear OS pop-ups

## Privacy

Everything lives on your phone in a small JSON file. Nothing is uploaded anywhere unless you add a Claude key, in which case only the text of the note you're cleaning up is sent to Anthropic.
