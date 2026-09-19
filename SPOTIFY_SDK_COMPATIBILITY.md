# Spotify SDK Compatibility & Isolation Policy

## Pinned SDK Version
- **Current Pinned SDK Version**: `spotify-app-remote-release-0.8.0.aar`
- **Location**: `spotify-app-remote/spotify-app-remote-release-0.8.0.aar`

## Isolation Architecture
- All Spotify SDK / App Remote calls are strictly isolated behind `SpotifyManager` and `SpotifyTrackMapper`.
- UI composables and ViewModels MUST NOT import or directly invoke `com.spotify.*` SDK APIs.

## Upgrade & Maintenance Guidelines
1. **Review Release Notes**: Before upgrading the Spotify App Remote SDK, review the official Spotify Developer changelog and release notes.
2. **Never Upgrade Blindly**: Do NOT upgrade the SDK version merely because a newer version exists.
3. **Run Compatibility Tests**: Run the full unit test suite (including `SpotifyCompatibilityTest` and `SpotifyAuthTest`) before and after any SDK upgrade attempt.
4. **Security Audit**: Ensure no client secrets, tokens, or authorization headers are logged or exposed in health monitoring or error messages.
