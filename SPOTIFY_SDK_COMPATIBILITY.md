# Spotify SDK Compatibility, Toolchain & Hardening Specifications

## Pinned SDK & Toolchain Versions
- **Spotify App Remote SDK**: `0.8.0` (`spotify-app-remote-release-0.8.0.aar`)
- **Android Studio Version**: `2026.1.4`
- **JDK / JBR**: `Eclipse Temurin JDK 17` (`17.0.20.101-hotspot`)
- **Gradle**: `8.14`
- **Android Gradle Plugin (AGP)**: `8.7.3`
- **Kotlin**: `2.1.0`
- **KSP**: `2.1.0-1.0.29`
- **Compile SDK**: `35`
- **Target SDK**: `35`
- **Min SDK**: `26`
- **Gson**: `2.11.0`
- **Compose BOM**: `2024.11.00`
- **Room**: `2.6.1`

## Isolation Architecture
- All Spotify SDK / App Remote calls are strictly isolated behind `SpotifyManager` and `SpotifyTrackMapper`.
- UI composables and ViewModels MUST NOT import or directly invoke `com.spotify.*` SDK APIs.

## Hardening & Security Policy
1. **Zero Secret Leakage**: `client_secret`, access tokens, refresh tokens, and Authorization headers MUST NOT be hardcoded, tracked in Git, or logged to Logcat/HealthMonitor.
2. **Network Timeouts**: HTTP connections bound to 10s connect/read timeouts with coroutine cancellation support.
3. **Cache-First Resilience**: Room cache lookup preferred before external network calls; cached lyrics remain available during network/provider downtime.
4. **Bounded Retries**: Connection attempts bounded to `MAX_RETRIES = 3`; non-transient auth failures classified to `REAUTH_REQUIRED` / `AUTH_REVOKED`.
5. **Upgrade Policy**: Review release notes before upgrading; do NOT upgrade SDK/dependencies blindly.
