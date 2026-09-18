# Spotify Content Synchronization Compliance Gate

## Status

Prototype compliance checkpoint — NOT a distribution approval.

## Scope

The project uses Spotify playback metadata and an external lyrics provider.

External lyrics must NOT be treated as a method for bypassing Spotify's content-synchronization restrictions.

## Current policy concern

Spotify's current Android SDK documentation states restrictions concerning synchronization of Spotify sound recordings with visual media and includes lyrics within that policy context.

State this as a documented policy concern.

Do not claim that the current prototype is approved by Spotify.

Do not claim that private use automatically makes the implementation authorized.

## Prototype rule

For the private prototype:

- Keep this issue explicitly documented.
- Do not implement technical mechanisms intended to evade or circumvent Spotify restrictions.
- Do not assume technical functionality means policy authorization.
- Keep policy-sensitive synchronized-lyrics behavior behind this compliance gate.

## Distribution/release gate

Before distributing or publicly releasing the application:

1. Review the current Spotify Developer Terms/Policies.
2. Review the current Spotify Android SDK documentation/policies.
3. Review the current licensing/terms of the selected lyrics provider.
4. Determine whether the exact planned lyrics experience is permitted.
5. Record the result of that review before release.

## If synchronization is not permitted

The project must switch to a compliant product design.

Do NOT document or implement workarounds intended to evade the restriction.

Possible compliant product-design changes should be described only at a high level, not as circumvention instructions.

## Decision status

`COMPLIANCE REVIEW REQUIRED BEFORE DISTRIBUTION`

Do NOT mark the project as "approved", "compliant", or "safe for release".

## Source references

- Spotify Developer Terms: https://developer.spotify.com/terms
- Spotify Developer Policy: https://developer.spotify.com/policy
- Spotify Android SDK Documentation: https://developer.spotify.com/documentation/android
- LRCLIB Terms: https://lrclib.net/

---

This document serves as the project's internal compliance gate. It must be revisited and the review completed before any distribution or public release.