# Security Model

Mobile Agent A52 is designed to fail closed.

- No INTERNET permission in the baseline application.
- Observation is separated from action execution.
- Every action passes through the security kernel.
- Sensitive actions require explicit confirmation.
- Emergency STOP blocks future actions.
- Accessibility interruption never triggers fallback automation.
- AI, if added later, is an untrusted planner and cannot bypass policy.
