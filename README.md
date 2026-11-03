# Hayate for Android

An anime player and tracker for Android, ported from the desktop app
[Hayate](https://github.com/Piyush-1927/Hayate-Multi-Platform). It keeps the same dark look and brings
discovery, schedules and playback to your phone.

> Early release. Expect rough edges.

## Features

- **Home**: spotlight banner and shelves for airing, just finished and upcoming anime
- **Weekly airing schedule**
- **Search**: AniList-powered, with ranked results
- **Details pages**: synopsis, episodes and related seasons, plus a separate page for titles that haven't aired yet
- **Built-in player**: YouTube official-channel episodes resolved with yt-dlp and played with ExoPlayer
- **Script providers**: add your own `.js` providers (QuickJS) from Settings → Import Provider…
- **Settings** that mirror the desktop app: provider, content filter and cache options, including clearing the image cache
- **Midnight theme**, matching the desktop default

## Roadmap

- Catalogue, Watch History and Library screens
- LAN media player (client only)
- AniSkip: auto-skip, skip button, Auto Next
- Proper landscape layout with auto-hiding system bars

## Install

1. Download the latest APK from [Releases](../../releases).
2. If you have an older debug or test build installed, uninstall it first. The signing key is different, so Android won't install over it.
3. Open the APK and allow installs from this source if asked.

## Build from source

You need Android Studio (or JDK 17+ and the Android SDK).

```bash
git clone https://github.com/Piyush-1927/Hayate-Android.git
cd Hayate-Android
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. If the terminal can't find Java, set `JAVA_HOME` to
the JDK bundled with Android Studio.

## Project layout

| Module | Package | What it does |
| --- | --- | --- |
| `:app` | `com.hayate.app` | UI, player, providers, settings |
| `:core` | `com.hayate.core.anilist` | Kotlin port of the desktop AniList client: parsing, search, home shelves, schedule |

## Built with

Kotlin, AniList GraphQL API, [youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp),
Media3 ExoPlayer, libmpv, QuickJS.

## Disclaimer

Hayate does not host or distribute any media. Content comes from the providers you choose to use.

## Contact

Telegram: [@Neonblade01](https://t.me/Neonblade01)
