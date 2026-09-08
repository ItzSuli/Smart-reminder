# Changelog

Each section is used as the release notes of the matching GitHub Release.

## v1.2.0

**Events: things you just show up to**

- New third kind next to Deadlines and Daily: **Events**. Doctor's appointment, meeting, birthday, concert, flight — anything that happens at a set time and you can't "do" in advance.
- Events don't nag. You get one heads-up on the lead day (choose day before / 3 days / a week), one the day before, one on the morning of, and a final call an hour before if the event has a time. That's it.
- The AI and the offline parser recognise events on their own ("Zahnarzt Do 10:30", "dentist tuesday 3pm", "Termin", "meeting", "Geburtstag" …), and the calendar import creates events by default.
- Own **Events** tab with a date block per card; past events collect at the bottom and can be cleared.

**Pop-up moved to the upper middle of the screen**

- The card now pops in the upper middle instead of sliding in like a notification from the top. Settings → "Pop-up position" lets you pick Top, Upper middle or Center.
- Fresh look: bigger emoji tile, a small label (Reminder / Daily / Coming up), softer pop animation.

**New widget**

- Redesigned home-screen widget: gradient card, today's date, up to three upcoming deadlines and events with an emoji tile, sub-line and a colour chip (today = amber, event = teal, overdue = rose), plus round Speak and Add buttons. Follows the system corner radius on Android 12+.

## v1.1.0

**School shortcuts, in German and English**

The AI clean-up (Gemini, Claude and the on-device model) now gets a full glossary of the shortcuts you actually type, and the offline parser understands them too:

- **Subjects:** D = Deutsch, E = Englisch/English, M = Mathe/Math, F = Französisch, L = Latein, Spa = Spanisch, Bio, Ch = Chemie, Ph = Physik, Ge/Gesch = Geschichte, Ek/Geo = Erdkunde, Ku = Kunst, Mu = Musik, Sp/Spo = Sport, Rel, Eth, Pol/PoWi/Sk, Inf/IT, Wi/WiPo, NaWi, Phil, Psy, Päd, GL, Tech, DS … and the full names with typos.
- **Work:** HA/Hausi/HW = Hausaufgabe/homework, S./p. = Seite/page (`S.45`, `p32`), Nr./No./# = Nummer/number, A3/T3/Aufg. 3 = Aufgabe 3/task 3, `Nr 3-5` = tasks 3 to 5, Kap./Ch. = Kapitel/chapter, AB = Arbeitsblatt, Vok = Vokabeln, KA/Klausur/LZK/Ex = Klassenarbeit/exam, Ref/Präsi/PPT = Referat/presentation, Abg. = Abgabe, Zsf = Zusammenfassung, Wdh = Wiederholung, Lös. = Lösungen, TB/WB = textbook/workbook, lernen/lesen/üben/ausfüllen/bearbeiten …
- **Time:** bis, spätestens, Mo/Di/Mi/Do/Fr/Sa/So, heute/morgen/übermorgen, nächste Woche, in 3 Tagen, um 15 Uhr, WE/Wochenende, tmrw, nxt wk, eod …
- **Language follows the note.** `D HA S.45 Nr 3-5 bis Do` becomes *Deutsch-Hausaufgabe: Seite 45 Nr. 3–5 · Fällig am Donnerstag, 11. September*; `M T3 p32 till tmrw` becomes *Math: task 3 on page 32 · Due Tuesday, September 8*.

**Also**

- Offline parser: German output (Hausaufgabe, Seite, Aufgabe, "Fällig am …", "Jeden Tag morgens und abends"), German weekday abbreviations (`Do`, `Fr`), `um 18 Uhr`.
- More emoji rules for German words.

## v1.0.0

First release.

- Type or say a messy note, get a clean reminder (Gemini Nano on the phone, free Gemini API key, optional Claude, or the offline parser).
- Deadlines and daily things; nag levels **meh / whatever / A LOT**; unpredictable timing that ramps up as a deadline gets closer.
- 3-second pop-ups instead of notifications, active hours, retries when the screen is off.
- Home-screen widget, Quick Settings tile, launcher shortcuts, voice input.
- Import deadlines from the calendar or an .ics file, streaks for daily things, backup & restore.
