# Kaan

A daily hearing budget for people who wear earphones. Made by Dronzer.

Kaan reads the media volume and the connected earphones, estimates the sound level at the ear, and shows how much of today's safe listening is used. It never uses the microphone. Everything runs on the phone, so hosting costs nothing.

## What is in the app

| Area | Behaviour |
|---|---|
| Today | Large percentage, an animated vessel that fills with the budget, the current level in dB, and the safe time left at the current volume |
| Through the day | 24 bars, one per hour |
| Alerts | One notification at 80 percent and one at 100 percent, each with a "Lower to a safe volume" action |
| History (Pro) | 7 and 30 day charts against the daily budget, days over budget, average level, listening time |
| Earphones | Kaan guesses whether a new Bluetooth device is a speaker, then asks once to confirm. Free for everyone |
| Calibration (Pro) | Set each pair to quiet, standard or loud, then fine tune in 1 dB steps against the maker's stated maximum |
| Precise mode | Optional. Reads the real signal level of what is playing, so a quiet podcast and loud music at the same volume step no longer count the same |
| Calls and speech | Calls on earphones count. Speech is modelled 5 dB below music at the same volume step |
| Ear rest (Pro) | A reminder after 30, 45, 60 or 90 minutes of unbroken listening |
| Auto volume drop (Pro) | Lowers the volume once when the budget runs out |
| Widget (Pro) | Today's budget on the home screen |
| Standards | Adult mode (80 dB for 40 hours a week) and child or sensitive mode (75 dB for 40 hours a week), from the WHO and ITU safe listening standard H.870 |

## See the Pro features in a debug build

Open Settings in the app and switch on "Unlock Pro for testing". The row exists only in debug builds. It stays on until you switch it off, and a release build never shows it.

## Build the APK online

1. Create an empty GitHub repository and push the contents of this folder to the `main` branch. Use `git push`, because the web uploader can skip the hidden `.github` folder.
2. Open the Actions tab and wait for the "Build APK" run to turn green.
3. Open the run, scroll to Artifacts and download `kaan-debug-apk`.
4. Unzip it on the phone and tap `app-debug.apk`.

Kaan asks for no accessibility, SMS or notification-reading access, so the Play Protect block that stopped Viraam should not apply. Android may still show its standard "unknown app" scan prompt.

To build on a computer instead, open the folder in Android Studio and press Run.

## Permissions

| Permission | Why |
|---|---|
| Notifications | The silent budget line and the two warnings |
| Foreground service (special use) | Keeps the volume sampler alive while audio plays |
| Run at startup | Resumes tracking after a restart |
| Record audio, change audio settings, foreground service (microphone) | Precise mode only. Android requires them to read the level of the phone's own audio output. Kaan never opens the microphone |

## How the estimate works

- Android reports how far each volume step sits below full output for the current audio device.
- Kaan adds that figure to the full output level set for the pair: 94 dB for quiet pairs, 100 dB for standard, 106 dB for loud, plus any fine tuning.
- Speech and calls are placed 5 dB below music at the same step. This is a modelling assumption, not a measurement.
- Every 3 dB above the reference level halves the safe listening time.

### Precise mode

- Kaan attaches an Android Visualizer to the phone's output mix and reads its RMS level twice a second.
- The loudness classes are defined for music at 12 dB below full scale. A stronger or weaker signal moves the result by the same number of dB. That reference is an assumption.
- Bluetooth earphones apply the volume themselves, so Kaan adds the volume step for Bluetooth. Wired and USB outputs already carry the volume in the signal.
- If the meter reads silence for 30 seconds while music plays, Kaan treats the output as unreadable and falls back to the volume estimate. It never counts less than the estimate because the meter went blind.
- Android 14 and later grant the needed service type only while the app is on screen. After a phone restart, precise mode waits until Kaan is opened once.
- Phone calls over the cellular network do not pass through the output mix. Calls always use the volume estimate.

### Why it stays an estimate

Precise mode knows the signal the phone sends. It still does not know how efficiently a given pair of earphones turns that signal into sound, which is what the loudness class and fine tune setting stand in for. Android has computed a real sound dose inside the system since Android 14, but it does not share that figure with apps. A calibration that holds an earbud against the phone microphone was considered and rejected: the tiny sealed space reads far higher than an ear canal, so the result would mislead.

## Publish on Google Play as Dronzer

`PLAY_SUBMISSION.md` holds ready-to-paste text for every Play Console form this app triggers. `docs/privacy-policy.md` is the privacy policy, ready to host with GitHub Pages.

## Competitors found on 16 November 2026

- HearAngel: an Android app that compares headphone use with a daily sound allowance.
- dbTrack: a similar tracker that is no longer on Google Play.
- iPhone has headphone exposure tracking built into the Health app.

The category is thin on Android. Kaan competes on design, clarity and the "time left at this volume" line.

## Project layout

```
app/src/main/java/com/dronzer/kaan/
  MainActivity.kt     Navigation, tab bar, first run
  Screens.kt          Today, History, Earphones, Settings, Pro
  Charts.kt           Vessel, bar chart, level meter
  Ui.kt               Buttons, switch, segmented control, rows
  TrackerService.kt   Background sampler, alerts, rest reminder
  Audio.kt            Dose maths and volume to dB estimate
  Store.kt            Settings and daily records
  KaanWidget.kt       Home screen widget
  Billing.kt          Google Play subscriptions
  Theme.kt            Colours, type, motion
app/src/main/res/
  drawable/           Custom mark and icon set (vector)
  font/               Funnel Display, Funnel Sans
```

## Fonts

Funnel Display and Funnel Sans ship under the SIL Open Font License. The licence texts sit in `licenses/`.

## Known limits

- Precise mode was written without a test device. Check on the Pixel that Settings shows "Kaan is reading the signal" while music plays, and that the dB figure on Today moves with loud and quiet passages.
- This project was syntax-checked but not compiled before delivery. The first build may show a small error. The Actions log names the file and line.
- Kaan asks once about each new Bluetooth device. A wrong answer can be changed on the Earphones screen.
- Some phone brands stop background services. Users on those phones must exempt Kaan from battery optimisation. Pixel phones do not need this.
