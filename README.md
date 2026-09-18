# Spotify Local Lyrics Player

Android companion app for Spotify playback with lyrics display.

## Compliance

**Spotify content-synchronization compliance must be reviewed before distribution or public release.**

See [docs/COMPLIANCE.md](docs/COMPLIANCE.md) for the compliance gate and review requirements.

## Architecture Overview

- **Spotify Integration**: Uses Spotify App Remote SDK (v0.8.0) for playback control and metadata
- **Lyrics Provider**: LRCLIB via provider-neutral `LyricsProvider` interface
- **UI**: Jetpack Compose with Material 3
- **Architecture**: Clean separation — SpotifyManager → PlayerViewModel → Compose UI; LyricsProvider isolated from Spotify layer

## Build

```bash
./gradlew.bat assembleDebug
```

## Testing

```bash
./gradlew.bat test
```

## Security

- Client ID supplied via `local.properties` (gitignored)
- No Client Secret in source
- No tokens/secrets committed
- See [docs/COMPLIANCE.md](docs/COMPLIANCE.md) for distribution review requirements