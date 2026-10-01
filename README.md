# Mobile Agent A52

A security-first, local-first Android agent designed for the Samsung Galaxy A52.

## What this project is becoming

This is not a single-purpose macro app. The target is a personal device agent that can manage attention, answer locally, inspect the current screen, prepare tasks, and control supported mobile functions—while every device action remains policy-gated.

### Current foundation

- Kotlin + Jetpack Compose
- AccessibilityService-based screen observation
- Central security kernel
- Explicit confirmation model
- Emergency STOP
- Action rate limiting
- Local audit events
- Attention manager
- Offline answer interface with a deterministic local model
- No INTERNET permission in the baseline manifest
- GitHub Actions for tests and APK builds

### Security boundary

assistant / future local AI
→ planner
→ security kernel
→ confirmation
→ action executor
→ Android APIs

The planner is never the authority. The security kernel is.

Sensitive screen text is not persisted by the baseline observer, and password fields are excluded from the in-memory screen snapshot.

### Offline AI direction

A future on-device model can implement the LocalModel interface. The model may propose answers or actions, but it does not receive direct access to Android APIs. Network access is intentionally absent from the baseline.

### Capability roadmap

1. Reliable observation and screen understanding.
2. Safe device actions: tap, swipe, navigation, text entry, and structured target selection.
3. Attention system: reminders, tasks, follow-ups, routines, and interruption control.
4. Local command router: convert natural language into safe, reviewable intents.
5. Offline AI plugin behind the same security boundary.
6. Local memory with explicit retention controls.
7. Device utilities and system integrations where Android permits them.
8. Hardening, adversarial tests, and A52 device validation.

## Build

GitHub Actions runs unit tests and produces a debug APK artifact on pushes to main.
