# Safety Model

**This document describes the logic implemented in the app. It is not a medical protocol. All thresholds and escalation rules must be reviewed by a qualified clinician before any pilot deployment.**

## Heat Index Thresholds

| Heat Index | Risk Level | Rationale |
|---|---|---|
| < 27°C (80°F) | SAFE | Below NOAA caution threshold |
| 27–32°C (80–90°F) | CHECK | NOAA "Caution" range; possible fatigue on extended exposure |
| 32–41°C (90–103°F) | CHECK → ACTION* | NOAA "Extreme Caution" to "Danger" range |
| 41–52°C (103–125°F) | ACTION | NOAA "Danger"; heat cramps/exhaustion likely |
| > 52°C (125°F) | EMERGENCY | NOAA "Extreme Danger"; heatstroke imminent |

*The transition from CHECK to ACTION within 32–41°C is governed by the physiological signals and risk profile, not the heat index alone.

Direct sun exposure adds approximately 8°C to the effective heat index before threshold comparison.

## Symptom Severity Tiers

| Symptoms | Minimum level forced |
|---|---|
| Thirst, headache, nausea, dizziness, weakness, heavy sweating | CHECK |
| Confusion, slurred speech, fainting, seizure, cannot stand | EMERGENCY |
| Stopped sweating in hot conditions | EMERGENCY |
| `MANUAL_EMERGENCY` button pressed | EMERGENCY |
| `NO_RESPONSE` to symptom prompt + environment ≥ ACTION | EMERGENCY |

Symptoms take effect regardless of the environmental heat index.

## Escalation Engine Cooldown Design

The `EscalationEngine` suppresses repeat notifications to prevent alert fatigue:

| Action | Default cooldown |
|---|---|
| `NOTIFY_USER` | 30 minutes |
| `PROMPT_SYMPTOM_CHECK` | 60 minutes |
| `ALERT_VOLUNTEER` | (no cooldown; subject to `NOTIFY_USER` cooldown) |
| `SUGGEST_DIAL_CONTACT` | EMERGENCY bypasses all cooldowns |
| `LOG_EMERGENCY_EVENT` | EMERGENCY bypasses all cooldowns |

At `EMERGENCY` level, all five actions fire unconditionally regardless of cooldown state.

`suppressedByAntiSpam = true` in `EscalationPlan` signals that at least one notification was suppressed in the current cycle. The UI can use this to show a "Suppressed" indicator.

## Required Pre-Pilot Clinical Review Checklist

Before deploying to real users:

- [ ] Confirm heat index thresholds against WHO/NOAA/local health authority guidelines for the deployment region (Kashmir climate may differ from NOAA CONUS reference)
- [ ] Review symptom severity tiers with a physician or emergency medicine specialist
- [ ] Confirm "stopped sweating" detection logic (currently requires explicit symptom report — passive detection is not implemented)
- [ ] Review escalation cooldown durations with a public health or humanitarian response specialist
- [ ] Confirm that `SUGGEST_DIAL_CONTACT` + `ACTION_DIAL` intent is appropriate in the deployment context (may not be suitable if emergency services are not reliably reachable)
- [ ] Add a visible disclaimer in the app UI: "This app provides decision support, not medical diagnosis"
- [ ] Review with legal/IRB whether the app constitutes a medical device under applicable jurisdiction
