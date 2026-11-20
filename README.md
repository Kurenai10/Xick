# Xick

**Language:** English · [Türkçe](README.tr.md)

Xick is an unofficial, lightweight Android client for [Kick](https://kick.com),
built with Kotlin. It focuses on low mobile data usage and a simple, fast UI.

> Xick is not affiliated with or endorsed by Kick. "Kick" is a trademark of its owner.

## Features

- Browse popular live channels and categories with infinite scroll
- Search channels and categories, filter by language
- Watch live streams with quality selection, fit/zoom/fill modes, fullscreen,
  and a mini player (drag down to shrink, swipe sideways to dismiss)
- Live chat with inline emotes
- Video on demand and clips for each channel
- Sign in with your Kick account (the password is typed on Kick's own page)
- Followed channels that are live right now, with follow / unfollow

## Data-saving design

- Thumbnails use the smallest suitable size on mobile data
  (`DataSaverPolicy.pickThumbnailUrl`).
- Video is capped at 480p on mobile data by default; the user can change it.
- HTTP responses are cached using the server's `Cache-Control` headers.
- Emotes are loaded when they appear in a message.
- Secondary chat data is loaded only when its tab is opened.

## Architecture

- MVVM with repositories and Paging 3
- Media3 / ExoPlayer for playback (HLS)
- Coil for images, OkHttp for network, kotlinx.serialization for JSON
- Centrifugo WebSocket for chat

## Build

Requirements: JDK 17, Android SDK (API 35).

1. Clone the repository.
2. Create `local.properties` in the project root (it is git-ignored):

   ```properties
   sdk.dir=/path/to/your/Android/Sdk
   ```

3. Build a debug APK:

   ```bash
   ./gradlew :app:assembleDebug
   ```

   On Windows use `gradlew.bat :app:assembleDebug`.

The APK is written to `app/build/outputs/apk/debug/`.

## Status

This is an early version. Playback, browsing, chat and sign-in work on test
devices. Kick's website and APIs are not documented publicly, so parts of the
app may need updating when Kick changes them.

## License

See [LICENSE](LICENSE) if present. Until a license file is added, all rights
are reserved by the author.
