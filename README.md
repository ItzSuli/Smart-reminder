# Smart Reminder

An Android app for people who hate writing reminders properly.

You type (or say) something quick and messy like

```
HW englisj read page 32 till teusday 10.10
```

and the app turns it into

> 📚 **English homework: read page 32**
> Read page 32 for English. Due Saturday, October 10.

…adds it to your list, and then nags you about it during the day with small 3-second pop-ups,
without ever telling you *when* the next one is coming.

**[⬇ Download the latest APK from Releases](https://github.com/ItzSuli/Smart-reminder/releases/latest)** · Android 8.0 (API 26) or newer.

---

## What it does

- **Type it like a text message.** Typos, abbreviations, German or English, `10.10`, `tmrw`, `at 5pm`, `every morning` – it figures it out.
- **Say it instead.** Mic button in the app, a "Speak" shortcut on the launcher icon, a "Speak" button on the widget.
- **AI clean-up, for free.** Your note is rewritten into a clear title and sentence with the right date, time and icon by one of:
  - **Gemini Nano on the phone** – no key, no internet, private. Only on phones that ship Google's AICore (Pixel 9 and newer, Galaxy S25 and newer, …). The app detects it automatically.
  - **Gemini API with a free key** – 2 minutes to set up at [aistudio.google.com](https://aistudio.google.com), no card needed.
  - **Claude API** – optional, paid, if you already have a key.
  - **Offline parser** – always there as a safety net, handles dates and typos on its own.
- **Three nag levels, named exactly like this:** `meh` · `whatever` · `A LOT`. `meh` is the gentlest, `A LOT` the strongest. The app deliberately never shows you how often that is.
- **Deadlines get louder on their own.** The closer the due date, the more often you hear about it. A timed deadline also gets one guaranteed "final call" shortly before.
- **Unpredictable by design.** Reminder moments are randomised and mixed with a secret number generated on your phone, so you can't learn the pattern and start ignoring it.
- **Two kinds of reminders.**
  - **Deadlines** – homework, exams, appointments, errands. Tick them off once.
  - **Daily** – supplements, water, stretching, meds. Come back every day, can be tied to morning / midday / evening, and keep a 🔥 streak.
- **Pop-ups, not notifications.** A small card slides in at the top of whatever you're doing, stays for 3 seconds (2–8, your choice) and disappears by itself. One tap on **Done** ticks the thing off. Screen off? It quietly tries again a little later. Notifications exist only as an opt-in setting.
- **Active hours.** Nothing fires outside the window you set (default 08:00–22:00).
- **Home-screen widget** with your next deadlines and one-tap Add / Speak.
- **Quick Settings tile** – swipe down, tap, start typing.
- **Import deadlines** from your phone's calendar or a timetable file (.ics).
- **Backup & restore** everything as one file.
- **Share to add.** Select text in any app → Share → Smart Reminder.
- **Nice to look at.** Material 3, light and dark mode, optional wallpaper colours on Android 12+.

## Install

1. Grab the APK from the **[latest release](https://github.com/ItzSuli/Smart-reminder/releases/latest)** on your phone.
2. Open it. Android asks you to allow installs from your browser – allow it once.
3. Open the app and go through the **Finish setup** card:
   - **Pop-ups over other apps** – needed for the 3-second card.
   - **No battery limits** – so Android doesn't silence the alarms.
4. **Samsung (One UI):** also open *Settings → Battery → Background usage limits* and make sure Smart Reminder is **not** in "Sleeping apps" or "Deep sleeping apps" (put it in "Never sleeping apps"). One UI otherwise puts apps to sleep after a few days, and the pop-ups stop.

Every release is signed with the same key, so a newer APK installs as an update.

## Set up the free AI (2 minutes)

1. Go to [aistudio.google.com](https://aistudio.google.com) and sign in with a Google account.
2. Click **Get API key** → **Create API key**. It's free; no credit card.
3. In the app: **Settings → AI clean-up → Gemini API key** → paste → **Test connection**.

Leave the engine on **Automatic**. If your phone ever supports Gemini Nano (the on-device one), the app will use that first; otherwise it uses your Gemini key; if that fails it falls back to the offline parser and tells you so in the preview.

Only the text of the note you're cleaning up is sent to Google. The key is stored on your phone only.

## How reminding works (spoiler-free)

| You choose | The app does |
|---|---|
| `meh` | a light touch |
| `whatever` | a normal amount |
| `A LOT` | a lot – but still spread over the day and only inside your active hours |

On top of that, deadlines automatically ramp up as the date approaches and keep nagging for a while if you miss it. Daily things stay at their level every day and stop for the day once you tap **Done today**.

If you really want to see the numbers, they live in
[`Cadence.kt`](app/src/main/java/com/itzsuli/smartreminder/schedule/Cadence.kt) – the file starts with a spoiler warning for a reason.

## Project layout

```
app/src/main/java/com/itzsuli/smartreminder/
├── MainActivity.kt          entry point; share-to-add, widget/tile/shortcut actions
├── SmartReminderApp.kt      Application: wires up storage + notification channels
├── data/                    models, JSON storage, settings, streaks
├── ai/
│   ├── Prompts.kt           prompt text + JSON handling shared by all engines
│   ├── NanoEngine.kt        Gemini Nano on the phone (ML Kit GenAI Prompt API)
│   ├── GeminiParser.kt      Gemini API (free key), JSON mode with a schema
│   ├── ClaudeParser.kt      Claude API (optional)
│   ├── LocalParser.kt       offline parser (dates, weekdays, typos, abbreviations)
│   └── ReminderParser.kt    picks the engine, falls back gracefully
├── schedule/
│   ├── Cadence.kt           ⚠ how often things fire (spoilers)
│   ├── Scheduler.kt         keeps one AlarmManager alarm armed for the next moment
│   ├── FireEngine.kt        pop-up vs "try again later" vs notification
│   ├── Popup.kt             the 3-second overlay card
│   └── Notifier.kt, *Receiver.kt
├── io/                      calendar + .ics import, backup file
├── widget/                  home-screen widget, quick-settings tile
└── ui/                      Jetpack Compose screens (home, add/edit, import, settings) + theme
app/src/test/                unit tests: parser, cadence, streaks, .ics reader
keystore/                    signing key (see its README)
.github/workflows/build.yml  tests + builds on every push; a v* tag publishes a release
```

## Build it yourself

Requires JDK 17+ and the Android SDK (platform 36, build-tools 36).

```bash
./gradlew testReleaseUnitTest      # unit tests
./gradlew assembleRelease          # → app/build/outputs/apk/release/app-release.apk
```

Or push a tag like `v1.1.0` and GitHub Actions builds the APK and attaches it to a new release.

## Permissions, explained

| Permission | Why |
|---|---|
| Display over other apps | the pop-up card |
| Exact alarms | fire at the planned minute instead of "sometime soon" |
| Ignore battery optimisations | so Android doesn't kill the alarms |
| Run at boot | re-arm the alarm after a restart |
| Read calendar | only when you use "Import from calendar" |
| Notifications | only if you switch a reminder type to notifications |
| Internet | only for the optional Gemini / Claude call |

## Privacy

Everything lives on your phone in a small JSON file. Nothing is uploaded anywhere unless you add a Gemini or Claude key, in which case only the text of the note you're cleaning up is sent to that service. Backups never contain your keys.
