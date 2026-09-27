# SafeSphere — Professional App Contract (M5)
### Frozen Interface Specification
**Version 4.0 · REST + WebSocket wire contract against `safesphere-client/backend`**
**Parent project:** SafeSphere — Emergency Orchestration Platform

This is the contract the Professional App builds against, regardless of
what native stack it's eventually written in (still open — see
`SafeSphere.md` §2, §11), and regardless of which branch (`mobile/` or
`desktop/`) is consuming it. It defines exactly what this app sends and
receives in each of its two modes — nothing else exists on either side of
the wire. This app has its own repo, its own build, and its own
verified-official login; it shares no code with the Citizen App or with
`backend/` — only this contract.

The backend this contract targets now lives inside the `safesphere-client`
repo (in `backend/`) instead of a separate `safesphere-backend` repo, but
it is still a standalone Java process reached over HTTPS/WSS at a
configurable base URL. Nothing below changes based on where the backend's
source sits in git.

---
## Table of Contents
1. Shared Enums & Constants
2. Endpoints & Channels
3. Command Desk Mode — Inbound (WebSocket): `IncidentQueueView`
4. Command Desk Mode — Outbound: `POST /api/v1/dispatch/decision`
5. Field Mode — Inbound (WebSocket): `OfficialIncidentView`
6. Field Mode — Outbound: `POST /api/v1/field/silence-ack`
7. Branches: `mobile/` vs. `desktop/`
8. Change Process

---
## 1. Shared Enums & Constants
```
FsmState: SAFE | SUSPICIOUS | CHECKING | EMERGENCY | VOLUNTEER_ASSIGNED
        | ESCALATING | RESPONDER_ASSIGNED | ON_SCENE | RESOLVED
```
Command Desk mode observes every state — it's the full situational-
awareness view for control-room staff. Field mode only ever observes
`RESPONDER_ASSIGNED` and `ON_SCENE`. Neither mode implements transition
logic; `backend/` (M4) owns the state machine entirely.

---
## 2. Endpoints & Channels
| Route | Direction | Payload |
|---|---|---|
| `WSS /ws/v1/incidents/queue` | backend → this app (Command Desk), subscribe | `IncidentQueueView` messages |
| `POST /api/v1/dispatch/decision` | this app (Command Desk) → backend | `DispatchDecisionEvent` request body |
| `WSS /ws/v1/incidents/official?official_id={id}` | backend → this app (Field), subscribe | `OfficialIncidentView` messages |
| `POST /api/v1/field/silence-ack` | this app (Field) → backend | `SilenceAckEvent` request body |

Field mode's subscription is filtered server-side by `official_id`; Command
Desk mode subscribes to the full active queue unfiltered, since
control-room staff need the whole picture.

---
## 3. Command Desk Mode — Inbound (WebSocket): `IncidentQueueView`
```json
{
  "capsule_id": "CR-8924",
  "fsm_state": "ESCALATING",
  "latitude": 17.3850,
  "longitude": 78.4867,
  "task_requirement": "Immediate First Aid Required. Victim cannot speak.",
  "last_updated": "2026-09-27T10:16:00Z"
}
```
Populates the Active Incident list and the Live Incident Timeline. A
summary view, not the full medical capsule — Command Desk mode is for
oversight, not treatment; the full capsule is Field mode's payload only
(§5). Identical on both the `mobile/` and `desktop/` branches — the
schema does not change per platform, only the rendering does.

---
## 4. Command Desk Mode — Outbound: `DispatchDecisionEvent`
**Request**
```
POST /api/v1/dispatch/decision
Content-Type: application/json
```
```json
{
  "capsule_id": "CR-8924",
  "staff_id": "DISP-03",
  "action": "CLOSE_INCIDENT",
  "target_responder_id": null
}
```
| Field | Type | Notes |
|---|---|---|
| `action` | enum | `FORCE_MATCH` \| `CLOSE_INCIDENT` |
| `target_responder_id` | string, nullable | Only populated for `FORCE_MATCH`. |

`CLOSE_INCIDENT` transitions the FSM to `RESOLVED` and generates the final
audit record.

---
## 5. Field Mode — Inbound (WebSocket): `OfficialIncidentView`
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
    "updated_at": "2026-09-27T10:15:30Z",
    "stream_interval_seconds": 5
  },
  "hazard_notes": null,
  "silence_otp": "482913",
  "otp_expires_at": "2026-09-27T10:45:30Z"
}
```
This is the only message in the whole platform carrying medical data,
contacts, or the OTP. It exists on this type because giving officials the
full picture is Field mode's entire purpose — the privacy boundary is
enforced by which app the data goes to (a separate install, a separate
verified login), not by hiding fields inside a shared type.

**Field mode is mobile-only.** It is not implemented on the `desktop/`
branch (§7) — an assigned official is, by definition, physically at the
scene, not at a control-room desk.

`hazard_notes` is reserved for the roadmap CV service (see `SafeSphere.md`
§18) and is always `null` for now — render it conditionally, don't assume
it's present.

---
## 6. Field Mode — Outbound: `SilenceAckEvent`
**Request**
```
POST /api/v1/field/silence-ack
Content-Type: application/json
```
```json
{
  "capsule_id": "CR-8924",
  "official_id": "OFC-07",
  "on_scene_at": "2026-09-27T10:30:02Z",
  "entered_otp": "482913"
}
```
Rules:
- Send this request **only** after the entered value matches
  `silence_otp` exactly (case-sensitive) and the current time is before
  `otp_expires_at`. Verify client-side before sending, but the backend
  re-verifies on receipt regardless — never trust the client's check
  alone.
- A wrong or expired entry is rejected locally with an inline error;
  nothing is sent, no shared state is touched, retry is allowed until
  expiry.
- `entered_otp` is included on the wire only for the backend's
  re-verification — the backend never echoes `silence_otp` back or logs it
  beyond the comparison that consumes it.
- On a valid ack, the backend stops the siren on the Citizen App's Need
  Help mode, clears the incident from the Citizen App's Help Nearby queue,
  and transitions the FSM to `ON_SCENE`.

---
## 7. Branches: `mobile/` vs. `desktop/`
Both branches build against this exact same contract — there is no
schema difference between them. What differs:

| | `mobile/` (MVP, build first) | `desktop/` (stretch, build only if time remains) |
|---|---|---|
| Command Desk mode | Yes | Yes |
| Field mode | Yes | **No — not implemented on this branch** |
| Frontend UI stack | Not yet decided (§11 of `SafeSphere.md` still applies: real native/OS-level, not a webview) | Not yet decided; desktop-native (e.g. JavaFX, since the backend is already Java) is a reasonable default since §11's mobility-specific hardware requirements (GPS, background service) don't apply to a desk console |
| Depends on `mobile/` existing first? | N/A | Yes — do not start `desktop/` before `mobile/` and Field mode are demo-ready |

---
## 8. Change Process
- This contract is **frozen** once the team agrees on it. A field
  rename/add/remove after that requires:
  1. Posting the exact diff in the team channel before touching code.
  2. The Citizen App owner and the Backend Lead acknowledging it — the
     shared `FsmState` enum and route-naming pattern are common ground.
  3. Updating this file in the same commit/PR as the backend change (in
     `safesphere-client/backend`) — this file is the source of truth, not
     a comment in the code.
- No field gets added "just in case" mid-sprint, in either mode, on either
  branch.
