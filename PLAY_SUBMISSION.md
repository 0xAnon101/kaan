# Play Console submission pack for Kaan

Ready-to-paste text for each form Kaan triggers in Play Console. Form labels change over time, so match by meaning if a label differs. Checked against Google's help pages on 16 November 2026.

## Before you start

1. Host the privacy policy. In your GitHub repository open Settings, Pages, choose "Deploy from a branch", branch `main`, folder `/docs`. The policy then lives at `https://YOUR-USERNAME.github.io/YOUR-REPO/privacy-policy`. Add your contact email to `docs/privacy-policy.md` first.
2. Record one screen video, about 40 seconds, and upload it to YouTube as unlisted. Script:
   - Open Kaan and show the Today screen.
   - Open Settings and switch "Background tracking" on.
   - Pull down the notification shade and show the Kaan line.
   - Play music through earphones, go to the home screen, wait a few seconds.
   - Open Kaan again and show that the percentage rose.
   - Open Settings, switch "Precise mode" on, accept the permission prompt, and show the line "Kaan is reading the signal".
   - Switch "Background tracking" off and show that the notification disappears.

## Store listing

**App name**

Kaan: hearing budget

**Short description** (80 characters or fewer)

See how much safe earphone listening you have left today

**Full description**

Earphones can damage hearing slowly, and you only notice once it is permanent. Kaan gives your ears a daily budget and shows how much of it you have used.

How it works
Kaan reads your volume level and which earphones are connected, estimates the sound level at your ear, and adds it up through the day. Loud listening uses the budget fast. Quiet listening barely touches it.

What you get
- Today's budget as one clear percentage
- The safe time left at your current volume
- A warning at 80 percent and at 100 percent, with one tap to lower the volume
- Calls on earphones counted along with music
- Bluetooth speakers and car stereos left out of the count

Kaan Pro
- 7 and 30 day history
- Calibration for each pair of earphones
- Ear rest reminders
- Automatic volume drop when the budget runs out
- Home screen widget

Private by design
Kaan never listens to the room and keeps no audio. It has no account and no server. Your data stays on your phone.

Optional precise mode
Switch it on and Kaan reads how loud the audio signal from your phone is, for a closer estimate. Android asks for the record audio permission for this. Kaan still records and stores nothing.

Based on a public standard
The budget follows the WHO and ITU safe listening standard: 40 hours a week at 80 dB for adults, or at 75 dB for children and sensitive ears.

Kaan is a wellness guide, not a medical device. It estimates sound exposure from your volume setting and does not measure sound in your ear. It does not diagnose, treat or prevent any condition. See an audiologist or doctor if you have concerns about your hearing.

**Category**

Health and fitness

## App content: privacy policy

Paste the GitHub Pages URL from step 1.

## App content: data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types | No |
| Is all of the user data collected by your app encrypted in transit | Not applicable, no data is collected |
| Do you provide a way for users to request that their data is deleted | Not applicable. Data stays on the device and is removed by uninstalling |

Purchase history is handled by Google Play Billing, not collected by the app.

Precise mode reads the output signal level on the device and discards it. Nothing leaves the phone, so it does not count as collection under Play's definition. If the form asks why the app requests the microphone permission, use the text in the next section.

## App content: record audio permission

> Kaan requests RECORD_AUDIO only for its optional "Precise mode", which the user switches on in Settings. Android requires this permission to attach a Visualizer to the device's own audio output. Kaan uses the Visualizer only to read the RMS level of the audio being played, so it can estimate headphone sound exposure more closely. Kaan never opens the microphone, never records or stores audio, and sends nothing off the device. Every other feature works without this permission.

## App content: health apps declaration

Google requires this form from every app. For Kaan:

- **Health features:** select the Health and fitness option that covers wellness guidance. If the list includes a hearing or "other" wellness option, select that.
- **Medical device:** No. Kaan is not regulated as a medical device.
- **Health Connect or health data access:** None.

If the form has a free text box:

> Kaan is a general wellness app. It estimates headphone sound exposure from the device's media volume setting and compares it with the WHO and ITU safe listening guideline. It does not measure sound, does not use the microphone, does not access Health Connect or any health data, and is not a medical device. The app and its store listing state that it does not diagnose, treat or prevent any condition.

## App content: foreground service permissions

Kaan declares `FOREGROUND_SERVICE_SPECIAL_USE` and `FOREGROUND_SERVICE_MICROPHONE`. Google asks about each one. The special use text comes first. The microphone text follows it.

**Which task does this service perform**

Choose "Other" under special use.

**Describe the use**

> Kaan estimates the user's headphone sound exposure through the day. The foreground service reads the media volume level and the active audio output every five seconds while the user listens, and adds the estimated exposure to a daily total. This must continue while the user is in other apps, because listening happens in music, video and call apps, not in Kaan. The user starts the service from the app with the "Background tracking" switch and can stop it with the same switch at any time. A persistent notification shows the current daily total while the service runs. The service does not use the microphone, location, camera or network.

**Why no other foreground service type fits**

> The task is not media playback, because Kaan plays no audio. It is not a health sensor task, because Kaan reads no body sensors and no activity data. It is not data sync, because nothing is transferred. Work scheduled with WorkManager cannot do this job, because it does not run continuously and would miss most listening time.

**User impact if the task is deferred or interrupted**

> Listening that happens while the service is stopped is not counted. The user would see a daily total that is too low and would not receive the warnings at 80 and 100 percent, which defeats the purpose of the app.

**Video link**

Paste the unlisted YouTube link from step 2.

### Microphone type

**Describe the use**

> Only when the user has switched on "Precise mode" and granted RECORD_AUDIO, the same foreground service keeps an audio Visualizer attached to the device's output mix and reads its RMS level a few times a second. Android ties this to the microphone service type because the Visualizer sits behind the RECORD_AUDIO permission. The service never opens the microphone and never records or stores audio. The user starts it in the app, sees a persistent notification while it runs, and can stop it with the "Precise mode" or "Background tracking" switch.

Use the same video link.

## Monetize: subscriptions

Create two subscriptions, each with one base plan.

| Product ID | Name | Billing period |
|---|---|---|
| `kaan_pro_monthly` | Kaan Pro monthly | 1 month |
| `kaan_pro_yearly` | Kaan Pro yearly | 1 year |

**Benefits text for both**

- 7 and 30 day history
- Earphone calibration
- Ear rest reminders and automatic volume drop
- Home screen widget

## Content rating questionnaire

Category: Utility, productivity, communication or other. Answer "No" to every content question. Kaan has no user-generated content, no ads and no sharing.

## Target audience

Select 13 and over. Selecting younger age groups brings the Families policy into play and adds review steps.

## Ads

No ads.

## Release steps

1. Build a signed bundle in Android Studio: Build, Generate Signed App Bundle. Keep the keystore file and its passwords safe. Losing them blocks future updates.
2. Upload the `.aab` to the Closed testing track.
3. A personal developer account created after 13 November 2023 needs 12 testers opted in for 14 days in a row before Google allows a production release.
4. Apply for production access and answer the questions about your test.

## Claims to avoid in the listing and screenshots

- Do not say Kaan "measures" the sound in your ear. Say "estimates". Precise mode measures the signal, not the sound.
- Do not say it "prevents hearing loss". Say it "helps you keep track".
- Do not show a percentage next to words like "accurate" or "clinical".
