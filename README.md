# Friends Vibe

Pick one song. The set continues.

There are two sessions, and only one plays at a time:

- **Phone library.** Reads audio on the device, ranks the rest from artist, album, genre, folder, and a simple tempo when tags are missing, then crossfades the queue. Pause, skip, calmer, harder, and ban artist are on the set screen.
- **Spotify.** Allowlisted Premium listeners, with the Spotify app installed. The app searches and queues track URIs through Spotify App Remote and the Auth library. Spotify keeps the audio. Nothing from Spotify is downloaded, analyzed, or mixed.

Sharing a debug APK, the 5-person Development Mode allowlist, the redirect URI, and the debug SHA-1 are written up in [docs/FRIEND_SHARE.md](docs/FRIEND_SHARE.md).

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

The debug APK is `app\build\outputs\apk\debug\app-debug.apk`. Put `spotify.client.id` in `local.properties` before building a Spotify session. See `local.properties.example`.
