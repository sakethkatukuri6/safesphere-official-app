# SafeSphere — Professional App Contract (M5)
### Frozen Interface Specification
**Version 2.0 · Covers both modes — Command Desk and Field — inside the one Professional App build**
**Parent project:** SafeSphere — Emergency Orchestration Platform

This is the contract the Professional App developer(s) build against. It defines exactly what this app receives and sends in each of its two modes — nothing else exists on either side of the wire. This app has its own build, its own JAR, and its own verified-official login; it shares no code with the Citizen App.

---
## Table of Contents
1. Shared Enums & Constants
2. EventBus Topics
3. Command Desk Mode — Inbound: `IncidentQueueView`
4. Command Desk Mode — Outbound: `DispatchDecisionEvent`
5. Field Mode — Inbound: `OfficialIncidentView`
6. Field Mode — Outbound: `SilenceAckEvent`
7. Change Process

---
## 1. Shared Enums & Constants
```java
public enum FsmState {
    SAFE, SUSPICIOUS, CHECKING, EMERGENCY,
    VOLUNTEER_ASSIGNED, ESCALATING,
    RESPONDER_ASSIGNED, ON_SCENE, RESOLVED
}
```
Command Desk mode observes every state (it's the full situational-awareness view for control-room staff). Field mode only ever observes `RESPONDER_ASSIGNED` and `ON_SCENE`. Neither mode implements transition logic — the Core Orchestrator (M4) owns the state machine.

```java
public final class Topics {
    public static final String INCIDENT_QUEUE        = "incident.queue.v1";        // M4/M6 -> this app (Command Desk)
    public static final String DISPATCH_DECISION     = "dispatch.decision.v1";     // this app (Command Desk) -> M6
    public static final String INCIDENT_OFFICIAL      = "incident.official.v1";     // M4/M6 -> this app (Field)
    public static final String OFFICIAL_SILENCE_ACK   = "official.silence_ack.v1"; // this app (Field) -> M4
}
```

---
## 2. EventBus Topics
| Topic | Direction | Payload |
|---|---|---|
| `incident.queue.v1` | M4/M6 → **this app, Command Desk mode** (subscribe) | `IncidentQueueView` |
| `dispatch.decision.v1` | **this app, Command Desk mode** → M6 (publish) | `DispatchDecisionEvent` |
| `incident.official.v1` | M4/M6 → **this app, Field mode** (subscribe) | `OfficialIncidentView` |
| `official.silence_ack.v1` | **this app, Field mode** → M4 (publish) | `SilenceAckEvent` |

Field mode subscribes filtered by `capsule_id`; Command Desk mode subscribes to the full active queue (unfiltered, since control-room staff need the whole picture).

---
## 3. Command Desk Mode — Inbound: `IncidentQueueView`
```java
public record IncidentQueueView(
    String capsuleId,
    FsmState fsmState,
    double latitude,
    double longitude,
    String taskRequirement,
    Instant lastUpdated
) {}
```
```json
{
  "capsule_id": "CR-8924",
  "fsm_state": "ESCALATING",
  "latitude": 17.3850,
  "longitude": 78.4867,
  "task_requirement": "Immediate First Aid Required. Victim cannot speak.",
  "last_updated": "2026-09-26T22:30:00Z"
}
```
This populates the Active Incident queue (JTable) and the Live Incident Timeline. It's a summary view, not the full medical capsule — Command Desk mode is for oversight, not treatment.

---
## 4. Command Desk Mode — Outbound: `DispatchDecisionEvent`
```java
public record DispatchDecisionEvent(
    String capsuleId,
    String staffId,
    DispatchAction action,   // FORCE_MATCH | CLOSE_INCIDENT
    String targetResponderId // optional, only for FORCE_MATCH
) {}

public enum DispatchAction { FORCE_MATCH, CLOSE_INCIDENT }
```
```json
{
  "capsule_id": "CR-8924",
  "staff_id": "DISP-03",
  "action": "CLOSE_INCIDENT",
  "target_responder_id": null
}
```
`CLOSE_INCIDENT` is what transitions the FSM to `RESOLVED` and generates the final audit record.

---
## 5. Field Mode — Inbound: `OfficialIncidentView`
```java
public record OfficialIncidentView(
    String capsuleId,
    FsmState fsmState,
    VictimProfile victimProfile,
    LiveLocation liveLocation,
    String hazardNotes,
    String silenceOtp,
    Instant otpExpiresAt
) {}

public record VictimProfile(
    String name,
    String bloodType,
    List<String> allergies,
    List<String> knownConditions,
    List<String> emergencyContacts
) {}

public record LiveLocation(
    double latitude,
    double longitude,
    Instant updatedAt,
    int streamIntervalSeconds
) {}
```
```json
{
  "capsule_id": "CR-8924",
  "fsm_state": "RESPONDER_ASSIGNED",
  "victim_profile": {
    "name": "Jane Doe",
    "blood_type": "O+",
    "allergies": ["Penicillin"],
    "known_conditions": ["Epilepsy"],
    "emergency_contacts": ["+91-9XXXXXXXXX"]
  },
  "live_location": {
    "latitude": 17.3850,
    "longitude": 78.4867,
    "updated_at": "2026-09-26T22:29:10Z",
    "stream_interval_seconds": 5
  },
  "hazard_notes": "YOLOv8 confidence 0.91 — possible weapon in frame",
  "silence_otp": "482913",
  "otp_expires_at": "2026-09-26T22:59:10Z"
}
```
This is the only payload in the whole platform carrying medical data, contacts, hazard notes, or the OTP. It exists on this type because giving officials the full picture is Field mode's entire purpose — the privacy boundary is enforced by which app the data goes to, not by hiding fields inside a shared one.

---
## 6. Field Mode — Outbound: `SilenceAckEvent`
```java
public record SilenceAckEvent(
    String capsuleId,
    String officialId,
    Instant onSceneAt
) {}
```
```json
{
  "capsule_id": "CR-8924",
  "official_id": "OFC-07",
  "on_scene_at": "2026-09-26T22:45:02Z"
}
```
Rules for publishing this event:
- Publish **only** after the entered value matches `silence_otp` exactly (case-sensitive) and the current time is before `otp_expires_at`.
- A wrong or expired entry is rejected locally with an inline error — never publish anything, never touch shared state, allow retry until expiry.
- The event itself carries only the official's ID and a timestamp as proof-of-verification. It never includes `silence_otp` — the secret does not travel any further than the comparison that consumed it.
- M4 reacts to a valid `SilenceAckEvent` by stopping the siren on the Citizen App's Need Help mode, clearing the incident from the Citizen App's Help Nearby queue, and transitioning the FSM to `ON_SCENE`.

---
## 7. Change Process
- This contract is **frozen** once Hour 1 ends. A field rename, addition, or removal after that requires:
  1. Posting the exact diff in the team channel before touching code.
  2. The Citizen App developer acknowledging it too, even if only this app is affected — the shared `FsmState` enum and topic-naming pattern are common ground.
  3. Updating this file in the same commit as the code change — this file is the source of truth, not a comment in the code.
- No field gets added "just in case" mid-sprint, in either mode.
