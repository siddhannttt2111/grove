# Grove

Grove is a friends-only Android autopilot. Pick one song and it keeps a set in that vibe.

The phone library plays inside Grove, with a short fade between songs. Spotify Premium plays inside the Spotify app. Grove searches and queues a similar session there. It does not download, analyze, or mix Spotify audio.

YouTube and Amazon Music do not offer a way for another app to keep a set going, so they are not in this build.

## Run it

Open the `DJ` folder in Android Studio, or build from this folder:

```
gradlew.bat assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Share that file with friends and install it on the phone. Android will ask for permission to install an app that did not come from the Play Store.

On first launch, allow music access. Songs already stored on the phone show up under Discover and Library. Tap one to start the set. Calmer, Harder, and Ban artist reshape what comes next. Phone fade is in Settings.

## Spotify, for up to five people

1. Create an app at [Spotify for Developers](https://developer.spotify.com/dashboard).
2. Add the redirect URI `grove://callback`.
3. Paste the client id into Grove Settings, or into `local.properties` as `SPOTIFY_CLIENT_ID=` before you build.
4. In the dashboard, add each friend under Users. Development mode allows 5 accounts, and the app owner needs Spotify Premium.
5. Each person needs the Spotify app and Premium. Open Spotify once, then connect it in Grove Settings and start a song from the Spotify search tab.

A new Spotify app cannot ask Spotify for audio features or automatic recommendations. Grove continues a Spotify set from the same artist and nearby titles.
