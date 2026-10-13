# Solune

A small, open-source Android music player with a clean, minimalist UI. It streams from the
**YouTube Music** catalogue. You don't need an account, an API key or ads.

- **Home**: Jump back in, Made for you mood mixes, "Because you like …" radio, Trending now.
- **Search** songs, albums and artists, plus a Browse all grid of mixes.
- **Artist and album pages** with Play, Shuffle and Download all.
- **Your Library**: Liked Songs, Downloads, Recently played and your own **playlists** (create, rename, delete).
- **⋯ song menu**: Like, Play next, Add to queue, Add to playlist, Song radio, Download, Go to artist, Share.
- **Autoplay**: similar songs keep playing when your queue ends. You can turn it off in Library.
- **Synced lyrics** from [LRCLIB](https://lrclib.net). Tap a line to jump to it.
- **Sleep timer**: 5–60 minutes or end of song. It runs in the playback service, so it works even if you close the app.
- **Queue editing**: drag to reorder, × to remove.
- **Offline downloads**: saved songs play from storage, with no network needed.
- **Player**: an arch-shaped album card with a curved seek bar. While music plays the card breathes, the ring turns, the sun drifts and rings pulse outward.
- Background playback, a media notification, and light, dark or follow-system themes.

## Download

Get the latest APK from **[Releases](https://github.com/Zyuael/solune/releases/latest)**, open it on your Android
phone (Android 8.0+) and allow "Install unknown apps" when asked.

Official builds are signed by ZYUAEL. Certificate SHA-256 fingerprint:

```
FE:ED:12:07:8C:6B:69:80:50:95:4F:E4:96:42:9A:60:55:88:8D:A1:12:CF:BD:99:47:7D:DC:9C:C8:39:DE:27
```

If an APK with this name has a different fingerprint, it did not come from this project.

## How it works

| Layer | What |
|---|---|
| Catalogue | [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) searches with the YouTube Music `music_songs` filter and extracts audio streams |
| Playback | Media3 ExoPlayer inside a `MediaSessionService`. Each queue item is a `music.youtube.com/watch?v=…` URI. A `ResolvingDataSource` swaps it for a real stream URL only when bytes are needed (`StreamResolver`). |
| UI | Jetpack Compose, with Coil for artwork |

```
app/src/main/java/com/zyuael/solune/
├── data/        YouTubeMusic, StreamResolver, Library, Taste, Downloads, Lyrics, Song
├── playback/    PlaybackService (ExoPlayer, cache, media session)
├── ui/          HomeScreen, PlayerScreen, Theme, components/ (ArchShape, ArcSeekArtwork, controls)
└── MainViewModel.kt, MainActivity.kt, SoluneApp.kt
```

## Build

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # live smoke test: search → extract → fetch audio
```

If playback breaks after a YouTube change, bump the NewPipeExtractor version in
`app/build.gradle.kts` first.

## Author

**Solune** was created by **ZYUAEL**.

Copyright © 2026 ZYUAEL

## License

Solune is free software under the **GNU General Public License v3.0 or later** (see [LICENSE](LICENSE)).
You're welcome to use, study, share and modify it. If you distribute a modified version, the GPL
requires you to:

- keep the original copyright notice ("Copyright © 2026 ZYUAEL") and this license,
- release your changes under the same license with their source code, and
- state clearly that you changed it.

The name "Solune" and its logo identify the official app by ZYUAEL. Please give forks a different
name and icon.

### Third-party

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor): GPL-3.0
- [AndroidX Media3](https://github.com/androidx/media), [Jetpack Compose](https://developer.android.com/jetpack/compose), [OkHttp](https://square.github.io/okhttp/), [Coil](https://coil-kt.github.io/coil/): Apache-2.0
- Lyrics from [LRCLIB](https://lrclib.net)

Solune is an independent project. It is not affiliated with or endorsed by YouTube, Google or Spotify.
