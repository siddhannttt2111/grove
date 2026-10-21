# Share a debug build with friends

Friends Vibe has two sessions that never play at the same time. The phone library reads files on the device, scores them, and crossfades on the phone. Spotify is a remote control for the Spotify app: search, queue, pause, and skip. The app does not download Spotify audio, does not analyze it, and does not mix it with itself or with files on the phone.

The Spotify session only exists for a Development Mode app. That mode is a small sandbox, not a public release.

## Who can use Spotify

From Spotify’s February 2026 Development Mode rules:

- The app owner needs an active Spotify Premium subscription. If that subscription lapses, the Spotify session stops for everyone.
- A new app allows **5 authorized users**, including you. A sixth friend can still install the APK and use the phone library. Spotify calls for that sixth account come back as HTTP 403 until someone is removed from the allowlist.
- Each person who starts a Spotify set also needs Spotify Premium. Playing a chosen track on demand is a Premium capability. The app checks that with App Remote (`canPlayOnDemand`) because Development Mode no longer returns the `product` field from `GET /me`.
- The Spotify app must be installed and logged in on the phone.
- Development Mode is for learning and personal use. It is not a store release. Extended quota is a company review, not this build.

Phone-library playback does not need a Spotify account.

## 1. Create the Spotify app

1. Sign in at [the Spotify developer dashboard](https://developer.spotify.com/dashboard) with the Premium account that will own the app.
2. Create an app. New apps start in Development Mode. You do not need the client secret in this project. The Android app uses the token from the Auth library.
3. Open the app, then **Settings**.
4. Add this redirect URI exactly:

   `com.friendsvibe.autopilot://callback`

5. Add the Android package name and the debug SHA-1:

   - Package: `com.friendsvibe.autopilot`
   - SHA-1: from the debug keystore that signs the APK you will send (next section)

6. Save.

## 2. Allowlist up to five people

Spotify’s steps, from the [quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes) page:

1. Log in to the developer dashboard.
2. Open the app.
3. Open **Settings**.
4. Open **Users Management**.
5. Choose **Add new user** and enter the friend’s name and the email on their Spotify account.
6. Send them the debug APK.

Add yourself as well if you are not already counted. An account that is not on this list may get past the Spotify login screen and then fail on the next API call with 403. The app shows that as an allowlist error.

## 3. Put the client id in the build

Copy `local.properties.example` to `local.properties` if that file does not exist yet. Set:

```
spotify.client.id=your_client_id
```

`local.properties` stays off git. The id is baked into the debug APK at build time, so change it and build again before you share. Friends do not need this file if they only install the APK.

## 4. Build the debug APK

From the project folder, in PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

The file to send is:

`app\build\outputs\apk\debug\app-debug.apk`

The first debug build creates `%USERPROFILE%\.android\debug.keystore` if it is missing. The SHA-1 of **that** keystore is the one Spotify must have.

Print it after the build:

```powershell
.\gradlew.bat :app:signingReport
```

Or:

```powershell
keytool -alias androiddebugkey -keystore "$env:USERPROFILE\.android\debug.keystore" -storepass android -keypass android -list -v
```

Copy the `SHA1:` line into the dashboard. The debug store password and key password are `android`.

Friends should install this APK. If a friend builds the project on their own computer, Android signs it with their debug keystore, the SHA-1 will not match, and Spotify login will fail until you register that fingerprint too.

## 5. Install it on a friend’s phone

Either:

- Send `app-debug.apk` and have them open it. Android will ask them to allow install from that app (Files, Chrome, or whichever app they opened it from).
- Or, with USB debugging on:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 6. What each friend does

Phone library:

1. Open Friends Vibe.
2. Choose **Phone library**.
3. Allow audio access.
4. Tap one song. The app scores the rest of the library from artist, album, genre, folder, and a simple on-device tempo when those tags are missing, then crossfades the queue.
5. Pause, skip, calmer, harder, and ban artist are on that screen. Calmer and harder rescore what comes next. They do not turn the screen into a mixer.

Spotify:

1. Install Spotify and log in with the allowlisted Premium account.
2. In Friends Vibe, choose **Spotify**, then **Connect Spotify**, and approve the request.
3. Search for one song and tap it. Spotify starts that song. Near the end of the track, this app queues one similar track inside Spotify. Similarity is search, the same artist, and saved songs in that person’s library. It is not Spotify audio analysis.
4. The same pause, skip, calmer, harder, and ban artist controls send commands to Spotify. Calmer and harder change the later search (acoustic or chill, versus remix or club). They do not overlap two Spotify streams.
5. If you want a crossfade, turn it on in Spotify’s own playback settings. This app does not fade Spotify audio.

Leaving a session pauses it. Starting the phone library pauses Spotify, and starting Spotify stops the phone player. The two sources are never mixed.

## If Spotify will not start

| What you see | What to fix |
| --- | --- |
| No client id | Set `spotify.client.id` and rebuild the APK you share. |
| Install the Spotify app | Install Spotify from the Play Store and log in. |
| Allowlist / 403 | Users Management has 5 seats. Add this account’s Spotify email, or remove someone else first. |
| Premium required | The listener’s account cannot play a chosen track on demand. |
| Login rejected, fingerprint, redirect | Redirect URI, package name `com.friendsvibe.autopilot`, and the debug SHA-1 must match this APK. |
| Spotify is in offline mode | Turn offline mode off in Spotify. |
| Asked to slow down | Development Mode quota. Wait, then search again. |

Policy reference: [Spotify developer policy](https://developer.spotify.com/policy). It forbids segue, mix, remix, or overlap of Spotify audio, including with other Spotify tracks, and forbids combining Spotify with another music service. This build keeps those sessions apart on purpose.
