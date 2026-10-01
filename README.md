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

The OfflinePlanner interface is ready for an on-device model that is packaged locally or otherwise available without network access. The model will produce answers or proposed actions only.

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
- `battery`
- `open settings`
- `open chrome`
- `volume up`
- `tap Settings`
- `go home`

Commands that affect the phone are prepared for approval rather than executed silently.

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

GitHub Actions runs unit tests and produces a debug APK artifact on pushes to main.

The repository is intentionally private while the security model and device behavior are still being hardened.
