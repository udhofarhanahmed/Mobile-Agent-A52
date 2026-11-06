# Mobile Agent A52

A security-first, local-first Android personal agent designed for the Samsung Galaxy A52.

## Current capabilities

- Local chat / command interface
- Persistent attention items
- Timed reminders using local Android alarms
- Local notification reminders
- Screen observation through AccessibilityService
- Password-field exclusion from observation
- Tap, swipe, text entry and navigation
- Open Android Settings
- Volume up, volume down and mute
- Launch supported apps by command
- Emergency STOP
- Explicit approval UI for device actions
- Action rate limiting and loop protection
- Local audit events
- Persistent local memory foundation
- GitHub Actions unit tests and debug APK builds

## Security architecture

```
User
  ↓
Chat / Command Router
  ↓
Offline Planner / optional local AI
  ↓
Security Kernel
  ↓
Confirmation Gate
  ↓
Action Executor
  ↓
Android APIs / Accessibility
  ↓
Audit Log
```

The planner is never the authority.

Any future local AI model is treated as an untrusted planner. Its proposals are sanitized so device actions remain unconfirmed until the user explicitly approves them. The baseline application does not declare the INTERNET permission.

## Offline AI

The current planner is a deterministic offline command router; this release does not include a general-purpose language model. It supports common local commands and simple arithmetic, and it explains its limits when a request is not supported. It cannot answer arbitrary questions or safely control every installed app.

The `OfflinePlanner` interface can host an on-device model that is packaged locally or otherwise available without network access. Any future model will produce answers or proposed actions only.

The project intentionally separates intelligence from authority:

- AI can suggest.
- The policy engine decides.
- The user approves sensitive actions.
- The executor performs only approved actions.
- The audit layer records the result.

## Attention model

The agent is becoming a personal attention layer rather than a simple macro:

- tasks
- reminders
- follow-ups
- alerts
- priority ordering
- completion
- persistent storage
- scheduled local notifications

Example local commands:

- `remember buy milk`
- `remind me to call Ali in 10 minutes`
- `what is 25% of 80`
- `what is 12 plus 5`
- `what time is it`
- `what is the date`
- `battery`
- `open settings`
- `open chrome`
- `volume up`
- `tap Settings`
- `go home`

Commands that affect the phone are prepared for approval rather than executed silently.

Relative alarms and reminders accept times from one minute to one year in the future. Command matching is case-insensitive while note and reminder text keeps the capitalization you entered.

## Roadmap

### Phase 1 — deterministic agent core
Security kernel, accessibility observation, action primitives, confirmation, emergency stop, audit.

### Phase 2 — personal assistant layer
Persistent attention, reminders, local memory, device state, command routing, app launch and everyday controls.

### Phase 3 — deeper device intelligence
Better screen understanding, multi-step plans, action verification, retry rules, safe workflows, notification handling, and broader Android integrations.

### Phase 4 — offline AI
Optional on-device model behind OfflinePlanner. No network authority. No direct Android API access.

### Phase 5 — hardening
Adversarial tests, data-retention controls, crash recovery, permission inventory, A52 device validation, and release signing.

## Build

Use JDK 17, Android SDK 35, and Gradle 8.10. The repository's GitHub Actions workflow runs unit tests, Android lint, and a debug APK build for pull requests to `main`, pushes to `main`, and manual workflow runs.

The current project does not include the Gradle Wrapper. On a machine with Gradle 8.10 installed, run `gradle test`, `gradle lint`, and `gradle assembleDebug` from the repository root.

The repository is intentionally private while the security model and device behavior are still being hardened.
