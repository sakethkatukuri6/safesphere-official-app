
## Table of Contents
1. Executive Summary
2. Problem Restatement & Scope Boundaries (Current Phase: Backend-First)
3. Key Concepts You Must Get Right
4. Prior Art — What Already Exists (and What We Reuse vs. Build)
5. High-Level Architecture & Repo Layout
6. Core Data Model (Emergency Capsule Schema)
7. Module-by-Module Design
8. Emergency State Machine Taxonomy
9. Resource Conservation Model (Survival Mode)
10. Capability Matching Mapping
11. Hardware & OS-Level Requirements
12. Technology Stack Summary
13. Data Flow
14. Team Structure & Modular, Order-Independent Workflow
15. Sprint Plan (Backend-First)
16. Demo Script & Success Metrics
17. Risks & Mitigations
18. Roadmap Beyond the MVP
19. Appendix — Sample Payloads, FSM Pseudocode
20. Development Kickoff Prompts (one per module)

---
## 1. Executive Summary
SafeSphere is an intelligent, resilient emergency-orchestration platform,
not another SOS button. It detects crises, gathers contextual telemetry,
preserves evidence via cryptography, conserves remaining device resources,
and coordinates dispatch between people, responders, and devices.

The output format is an encrypted, structured **Emergency Capsule**
containing role-based data payloads, distributed to verified volunteers,
family members, and professional dispatchers. AI-based hazard
interpretation stays deferred to the roadmap (§18); for the MVP, all
escalation decisions are deterministic, driven by trigger type and elapsed
time, not by a confidence score from a model.

### What's new in this revision (v4.0)
Three changes from the previous revision, all instructions from the team:
1. **Two repos, not three.** There is no separate `safesphere-backend`
   repo. The Java backend (M4 Orchestrator, M6 Matcher, Agent Gateway)
   now lives **inside `safesphere-client`**, as its own module/folder,
   alongside the Citizen App frontend. It is still a standalone,
   independently-running Java process — just version-controlled in the
   same repo instead of a third one. `safesphere-official-app` stays a
   separate repo with no backend code of its own; it only talks to the
   backend over the network, exactly as before. See §5 for the layout.
2. **Command Desk mode now targets two branches.** Mobile first (this is
   the MVP build), desktop later as a stretch goal if time allows, as two
   branches inside `safesphere-official-app`. Field mode stays mobile-only
   in both branches — it's inherently a "responder physically at the
   scene" mode.
3. **Backend-first build order, UI stack intentionally undecided.** The
   team has AI-generated concept screens only — no committed frontend
   framework yet. Rather than block on that decision, the plan is to build
   and fully test the Java backend headlessly first (REST/WebSocket calls,
   fixture JSON, no UI required to prove it works), and pick the UI stack
   for both apps once the backend is stable. §2 and §15 reflect this
   ordering explicitly.

There are still **two distinct native, OS-level end-user apps**, each its
own codebase and install:
- **The Citizen App (M1)** — the platform's flagship, most-installed app.
  Two modes: **Need Help** (trigger and manage your own SOS) and **Help
  Nearby** (respond to nearby incidents as a verified volunteer, seeing
  only the victim's name, age, gender, and live location — no medical,
  contact, or hazard data is ever serialized into that payload). Its repo
  also hosts the standalone Java backend (§5).
- **The Professional App (M5)** — a separate, verified-official-only app
  for police/EMS and control-room staff. Two modes: **Command Desk**
  (control-room queue and live incident timeline — mobile now, desktop
  later) and **Field Mode** (activated once an official is assigned,
  receiving the full unredacted capsule and the OTP-silence mechanism).

Both apps are required to be **genuine OS-level native applications with
direct hardware access** (GPS, motion sensors, battery state, background
services, camera/mic, push notifications) — not a simplified webview or
PWA wrapper. See §11 for the full list of hardware/OS capabilities this
implies and why it rules out certain stacks.

This document gives the team a scoped architecture, a precise wire-level
data model, a module build plan, mathematical rules for battery survival
and responder matching, a backend-first sprint calendar, and a workflow
that lets developers start on any module without waiting on any other
module to exist first.

---
## 2. Problem Restatement & Scope Boundaries (Current Phase: Backend-First)
A full enterprise-grade version (live 112/ERSS API integration, deep
OS-level battery kernel hooks, physical IoT wearables, production
hazard-detection ML) is a multi-quarter engineering program. **Scope
deliberately, and build in the order that doesn't block on undecided
things.**

### Current phase: backend only, UI deferred
The team has not committed to a frontend framework — what exists today is
a set of AI-generated concept screens, useful for *what the screens should
communicate*, not yet an implementation spec for *how they're built*.
Rather than guess and rebuild later, the current build phase is:
- Build and fully test `safesphere-client`'s backend module (M4, M6, Agent
  Gateway) end-to-end, driven by REST calls and WebSocket messages only —
  Postman/curl/a small Java test harness stands in for both frontends.
- Do **not** start production frontend code for either app until the UI
  stack is chosen. Wireframing against the AI-generated screens can
  continue in parallel — that's design work, not implementation.
- Once the backend is stable and the UI stack is picked, §15's later
  phases pick up both frontends against a real, already-working backend
  instead of a mock.

### In scope for the MVP (once frontend work starts)
- **Citizen App — Need Help mode:** native app, SOS trigger, the "I Can't
  Speak" questionnaire, a silent confirmation window before escalation.
- **Citizen App — Help Nearby mode:** same app, second mode for verified
  volunteers — incident push showing *only* victim name, age, gender, live
  location. Accept/Decline/Mark Arrived. No medical data, contacts, or
  hazard notes ever included in this payload.
- **State Machine Engine (backend, in `safesphere-client/backend`):**
  deterministic Java FSM managing the escalation lifecycle — see §8 for
  the CV-free bypass rule.
- **Survival Mode:** on-device battery/GPS conservation logic in the
  Citizen App's Need Help mode, using real OS battery/location APIs and a
  background service (§11) so it keeps running while backgrounded.
- **Professional App — Command Desk mode (mobile branch, MVP):** active
  queue + live incident timeline for control-room staff, on a mobile
  device.
- **Professional App — Command Desk mode (desktop branch, stretch):** same
  data, a desktop-native UI, built only if time remains after the mobile
  branch and Field mode are done.
- **Professional App — Field mode:** full unredacted Emergency Capsule
  (exact live location, medical profile) plus OTP entry to silence the
  victim's repeating alert and confirm on-scene handoff. Mobile only.
- **Relational Matching (backend):** SQLite-backed matching of incidents
  to verified volunteers/officials by capability and proximity.
- **Backend API:** Java REST + WebSocket service both apps call — no
  in-process coupling between the two apps, even though the backend's
  source lives inside the Citizen App's repo.

### Explicitly out of scope for the MVP (see §18 — Roadmap)
- CV-based hazard verification (Python/OpenCV/YOLOv8) — dropped for now.
- Live, authenticated API integration with India's ERSS/112 services.
- Physical hardware/wearable integrations beyond the phone's own sensors.
- Unrestricted AI dispatching.
- Command Desk desktop branch, unless MVP timeline allows it.

---
## 3. Key Concepts You Must Get Right
| Concept | One-line explanation |
|---|---|
| **Emergency Orchestration** | We don't replace 112; we provide the intelligent, resilient layer around the person in distress that feeds context to existing infrastructure. |
| **Survival Mode** | An emergency-specific resource-management system that trades nonessential device functionality (UI, frequent GPS) for emergency communication lifetime. Runs on-device, inside the Citizen App, as a background service — the backend cannot throttle hardware it doesn't have access to. |
| **Emergency Capsule** | A structured, AES-256-GCM-encrypted incident package that distributes different data (location, medical, audio) according to recipient permissions (RBAC), generated and held server-side only, inside `safesphere-client`'s backend module. |
| **Deterministic Safety** | Every safety-critical decision (escalation, dispatch, silence) is made by the backend's FSM from typed, versioned inputs — never inferred by a model. |
| **Capability Matching** | Matching by a volunteer's specific skills (CPR, vehicle access) and proximity, not just raw distance. |
| **Mode vs. App** | "Mode" (Need Help / Help Nearby, Command Desk / Field) is a UI state within one native app. It is never a permissions boundary — the payload restriction between Citizen and Professional is enforced by two separate backend response types, not by which mode is showing. |
| **Repo vs. Deployment** | The backend's *source code* lives in `safesphere-client`'s repo, but at runtime it is a standalone Java process both apps reach over HTTPS/WSS — no different from a third repo, deployment-wise. Nothing in either app imports the other's or the backend's internals directly. |
| **Contract vs. Implementation** | The backend is Java. The two frontend apps' UI stack is **not yet decided** — the wire contract (`CitizenAppContract.md` / `ProfessionalAppContract.md`) is language-neutral JSON so that decision can be made later without touching the backend. |

---
## 4. Prior Art — What Already Exists (and What We Reuse vs. Build)
- **Android / Apple Personal Safety:** crash detection, satellite SOS,
  medical IDs — individual-device features, not extensible platforms;
  Google's SOS fails when standard Battery Saver is on.
- **Life360 & Noonlight:** dispatch and family circles; Noonlight proves
  dispatch APIs work.
- **112 India (ERSS):** already handles dispatch, GIS, verified
  volunteers.

**Our differentiated value-add:**
1. An **Adaptive Survival Mode** that degrades app behavior based on real
   battery telemetry, on-device, as a genuine background OS service — not
   a foreground-only simulation.
2. A cryptographic **Evidence Vault** and **Emergency Capsule** with an
   immutable, RBAC-governed chain of custody — held server-side, never on
   either client.
3. **Capability-weighted matching**, not just nearest-distance.
4. **Agent API Extensibility** — the same Java backend that serves the two
   native apps also exposes an endpoint for external IoT/agent triggers.
5. A **hard app-level trust boundary**: sensitive fields are stripped from
   the payload *before the backend ever sends it* — a decompiled Citizen
   App build cannot recover data it was never sent, regardless of what
   native stack it's eventually written in.
6. **Full OS-level integration** (§11): background location, motion-based
   crash detection, and always-on push — not a webview shell that stops
   working the moment the screen locks.

---
## 5. High-Level Architecture & Repo Layout
```text
                    ┌────────────────────────────┐
                    │      DETECTION LAYER        │
                    │ (device sensors, manual SOS) │
                    └──────────────┬───────────────┘
                                   │
                                   ▼
                ┌──────────────────────────────────┐
                │   M1  Citizen App (native, OS-level)│
                │   Need Help / Help Nearby modes   │
                │   embeds M3 Survival Engine        │
                └───────────────┬────────────────────┘
                                │ HTTPS (REST) + WSS (push) — localhost in dev, real host in prod
                                ▼
   ┌───────────────────────────────────────────────────────────┐
   │      backend/  (Java 17+ — lives inside safesphere-client, │
   │      runs as its own standalone process, not the app)      │
   │  M4 Core Orchestrator: FSM, AES-256 Evidence Vault          │
   │  M6 Relational Capability Matcher (SQLite)                  │
   │  Agent/API Gateway (Javalin: REST + WebSocket)               │
   └───────────────────────────┬───────────────────────────────┘
                                │ HTTPS (REST) + WSS (push)
                ┌───────────────┴────────────────────┐
                ▼ name/age/gender/location only       ▼ full capsule + OTP
   ┌─────────────────────────────┐      ┌─────────────────────────────────┐
   │  M1 — Help Nearby mode       │      │  M5 — Professional App (native)  │
   │  (same app as Need Help)     │      │  Command Desk (mobile→desktop)   │
   │                              │      │  + Field (mobile only)           │
   └─────────────────────────────┘      └─────────────────────────────────┘
```
Both native apps are peers of the backend, not of each other — the Citizen
App never calls the Professional App and vice versa. Everything routes
through the backend, which is what makes the payload-stripping guarantee
in §6.2 enforceable regardless of either app's implementation language —
and regardless of which repo the backend's source happens to sit in.

### Repo layout (two repos)
```
safesphere-client/                 (this repo hosts BOTH of these)
├── app/                           M1 Citizen App — frontend, UI stack TBD (§2, §11)
│   └── (Need Help + Help Nearby modes, M3 Survival Engine embedded)
├── backend/                       M4 + M6 + Agent Gateway — Java 17+, Javalin
│   └── (own build file, own tests, runs as its own process — see below)
└── README.md, SafeSphere.md, CitizenAppContract.md

safesphere-official-app/
├── mobile/                        M5 Professional App — mobile branch (MVP)
│   └── (Command Desk + Field modes, UI stack TBD)
├── desktop/                       M5 Professional App — desktop branch (stretch)
│   └── (Command Desk mode only — Field stays mobile-only)
└── README.md, SafeSphere.md, ProfessionalAppContract.md
```
`app/` and `backend/` are separate build targets inside one repo (e.g. two
Gradle/Maven modules, or two independent projects in two top-level
folders — pick whichever your native stack's tooling makes cleanest once
that stack is chosen). They **do not** import each other's code. `app/`
talks to `backend/` exactly the way `safesphere-official-app` does: over
HTTPS/WSS, against the same contract file. Splitting them into a third
repo later, if the team changes its mind, is a copy-and-point-the-URL
change, not a rearchitecture — the module boundary already exists, only
the git boundary moved.

---
## 6. Core Data Model (Emergency Capsule Schema)
The backend is the only thing that ever constructs a full Emergency
Capsule. Both apps receive a **role-scoped view** of it over the wire
(JSON — see the contract files for the authoritative schema and
REST/WebSocket routes).

### 6.1 Internal Capsule (backend-only, never sent to either app whole)
```json
{
  "capsule_id": "CR-8924",
  "timestamp": "2026-09-27T10:15:00Z",
  "fsm_state": "EMERGENCY",
  "telemetry": {
    "latitude": 17.3850,
    "longitude": 78.4867,
    "battery_level": 12,
    "network_quality": "WEAK"
  },
  "medical_summary": "Blood: O+, Allergies: Penicillin",
  "encrypted_evidence": "[AES-256-GCM-PAYLOAD]"
}
```

### 6.2 Citizen App — Help Nearby View (exactly four data fields + IDs)
```json
{
  "capsule_id": "CR-8924",
  "fsm_state": "EMERGENCY",
  "victim_name": "Jane Doe",
  "victim_age": 24,
  "victim_gender": "Female",
  "location": { "latitude": 17.3850, "longitude": 78.4867, "updated_at": "2026-09-27T10:15:30Z" }
}
```
The backend never generates `medical_summary`, `hazard_notes`, or
`silence_otp` on this response type — not omitted, structurally absent.
This holds no matter what the Citizen App's frontend is written in,
because the stripping happens inside `backend/` before serialization, not
by trusting the client to ignore fields it received.

### 6.3 Professional App — Field Mode View (full access)
```json
{
  "capsule_id": "CR-8924",
  "fsm_state": "RESPONDER_ASSIGNED",
  "victim_profile": {
    "name": "Jane Doe", "blood_type": "O+", "allergies": ["Penicillin"],
    "known_conditions": ["Epilepsy"], "emergency_contacts": ["+91-9XXXXXXXXX"]
  },
  "live_location": { "latitude": 17.3850, "longitude": 78.4867, "updated_at": "2026-09-27T10:15:30Z", "stream_interval_seconds": 5 },
  "hazard_notes": null,
  "silence_otp": "482913",
  "otp_expires_at": "2026-09-27T10:45:30Z"
}
```
`hazard_notes` is retained in the schema as a nullable field so the
roadmap CV service (§18) can populate it later without a breaking schema
change — it is simply always `null` for the MVP.

---
## 7. Module-by-Module Design
### M1 — Citizen App (native, MAIN APP, frontend UI stack TBD)
One installable app for the general public, covering both calling for
help and helping others. Lives in `safesphere-client/app`. Frontend
framework is not yet decided (§2, §11) — the AI-generated screens
describe intent, not implementation.
- **Need Help mode:** SOS trigger, the "I Can't Speak" questionnaire
  (Yes/No for mobility/threat proximity), a silent 10-second confirmation
  timer before escalating. Embeds M3 directly (on-device).
- **Help Nearby mode:** subscribes over WebSocket to incidents matched to
  this verified volunteer, rendering only the four fields in §6.2 with a
  map pin. Accept/Decline; on Accept, a single "Mark Arrived" button. No
  field, screen, or code path in this mode ever handles medical data,
  hazard notes, contact info, or an OTP entry box.
- **Output:** REST call on trigger (Need Help) → backend. Accept/Decline/
  Arrived events (Help Nearby) → REST call → backend.

### M3 — Survival Engine (native, embedded in M1, on-device)
Dynamically preserves device life using real battery/location APIs — this
cannot live in the backend, since the backend has no access to the
device's actual battery state. Implements the decay model in §9. Runs as
a background service (§11) so throttling continues even when the app
isn't in the foreground. Reports `battery_level` and `network_quality`
upward on each SOS/telemetry call so the backend's capsule reflects real
device state, but the throttling decision itself (dark UI, reduced GPS
polling) executes locally.

### M4 — Core Orchestrator & State Machine (Java, in `safesphere-client/backend`)
Maintains deterministic safety and packages evidence, as a standalone
Java process. Physically checked into `safesphere-client`'s repo, but
built, run, and deployed independently of the `app/` frontend module —
neither depends on the other at compile time.
- Strict FSM enforcing valid transitions only (§8).
- `EmergencyCapsule` record matching §6.1, encrypted via `javax.crypto`
  AES-256-GCM (Evidence Vault).
- Exposes `POST /api/v1/agent/trigger` for external IoT/agent triggers,
  served by the same Java process as the rest of the API.
- Pushes role-scoped views (§6.2, §6.3) to each app over WebSocket.

### M5 — Professional App (native, SEPARATE APP, SEPARATE REPO, frontend UI stack TBD)
One installable app for verified officials, own login, own build — lives
entirely in `safesphere-official-app`, shares no code with M1, only the
backend API. Two build branches, both against the same contract:
- **`mobile/` (MVP):**
  - **Command Desk mode:** active-incident queue and auto-scrolling Live
    Incident Timeline, fed by WebSocket. Used by control-room staff who
    are not necessarily the one dispatched.
  - **Field mode:** activated for the specific assigned official. Renders
    the full-access payload (§6.3) plus a live-updating map pin refreshed
    on `stream_interval_seconds`. An "Enter OTP to Silence Alert" input
    accepts the 6-digit `silence_otp`; correct + unexpired entry publishes
    a `SILENCE_ACK` (REST call). Wrong/expired entries are rejected
    locally and never touch backend state.
- **`desktop/` (stretch, only if time remains):** Command Desk mode only,
  reimplemented for a desk-bound console. Field mode is intentionally
  **not** built for desktop — an official on scene is, by definition, not
  at a desk.
- **Output:** situational awareness (Command Desk, either branch).
  `SILENCE_ACK` + on-scene timestamp → backend (Field mode, mobile only).

### M6 — Relational Capability Matcher (Java, in `safesphere-client/backend`, SQLite)
Matches incidents to volunteers/officials using weighted capability, not
just distance (§10 formula). A normalized SQLite database of verified
responders and certifications. Its output branches to whichever app/mode
fits the match: the minimal-data payload to M1's Help Nearby mode, or the
full capsule + OTP to M5's Field mode — two different response DTOs are
constructed, never one object with fields hidden.

---
## 8. Emergency State Machine Taxonomy
| State | Trigger Condition | Auto-Escalation Rule |
|---|---|---|
| `SAFE` | Baseline. App idle. | None. |
| `SUSPICIOUS` | Sensor anomaly (route deviation) or manual SOS press. | Initiates 10-second silent confirmation. |
| `CHECKING` | Waiting out the confirmation window. | If `trigger_type == CRASH_DETECTED` (native hardware crash detection — no CV involved), bypass immediately to `EMERGENCY`. |
| `EMERGENCY` | Timer expired, or `CRASH_DETECTED` bypass, or a second manual press during `CHECKING` ("I need help now"). | Ping Level 1 (trusted contacts) and push to nearest verified volunteers via M1's Help Nearby mode. |
| `VOLUNTEER_ASSIGNED` | A pushed volunteer accepts. | Update Live Timeline; show only the §6.2 payload. |
| `ESCALATING` | No volunteer/contact response within window. | Ping Level 3 (112 / verified officials) via M5's Field mode. |
| `RESPONDER_ASSIGNED` | M6 match successful (official). | Generate `silence_otp`; push full-access capsule (§6.3) to M5's Field mode. |
| `ON_SCENE` | Responder enters correct `silence_otp` before expiry. | Publish `SILENCE_ACK`; stop siren on M1 Need Help; clear other volunteers' queues. |
| `RESOLVED` | Dispatcher closes incident from M5's Command Desk mode. | Generate final audit record. |

`hazard_notes` stays `null` until §18 ships a hazard-verification service;
the confirmation-bypass path depends only on `trigger_type`, which the
device itself determines via its own motion sensors (§11) — no CV
dependency anywhere in the MVP.

---
## 9. Resource Conservation Model (Survival Mode)
Runs on real OS battery/GPS APIs, inside a background service (§11), not
a simulated slider:
```
IF Battery > 50%:
  GPS_Polling_Interval = 5 seconds
  UI_Theme = Standard
  Evidence_Capture = High-Res Video + Audio

IF 15% < Battery <= 50%:
  GPS_Polling_Interval = 15 seconds
  UI_Theme = Standard
  Evidence_Capture = Compressed Audio Only

IF Battery <= 15%:
  GPS_Polling_Interval = 45 seconds (Heartbeat only)
  UI_Theme = Pitch Black (OLED conservation)
  Evidence_Capture = Disabled (Network preserved for SOS)
```
Each tier's `battery_level`/`network_quality` is still reported to the
backend (§6.1 telemetry) so the capsule and the Command Desk view reflect
real device state — the throttling itself is purely local.

---
## 10. Capability Matching Mapping
Unaffected by the repo/stack changes — runs in `safesphere-client/backend`
against SQLite:
```sql
SELECT responder_id,
       ( (1 / distance_km) * 0.4 ) +
       ( first_aid_cert * 0.3 ) +
       ( vehicle_access * 0.3 ) AS match_score
FROM verified_volunteers
WHERE status = 'ONLINE'
ORDER BY match_score DESC
LIMIT 1;
```

---
## 11. Hardware & OS-Level Requirements
Both apps are **full OS-level native applications**, not a simplified
webview/PWA wrapper — this is a hard project requirement, independent of
whichever UI framework eventually gets chosen. Whatever stack is picked
for `app/` (M1) and `mobile/` (M5) must give genuine access to all of the
following, including while the app is backgrounded:

| Capability | Used by | Why it can't be a simple web wrapper |
|---|---|---|
| **Foreground + background location (GPS)** | M3 Survival Mode, Need Help trigger, Field mode's live-tracking pin | Need Help must keep reporting location after the screen locks; a backgrounded browser tab is routinely killed by the OS. |
| **Motion/orientation sensors (accelerometer, gyroscope)** | `trigger_type == CRASH_DETECTED` (§8) | Crash detection is a continuous, low-latency sensor read — not something a web page can poll reliably in the background. |
| **Battery status API** | M3's tiering (§9) | Needs the OS's real battery percentage and charging state, not a browser's limited/deprecated Battery Status API. |
| **Background service / foreground service** | M3 (keeps throttling and location reporting alive while backgrounded), Help Nearby & Field mode's incident push | The whole point of Survival Mode is that it works when the app isn't actively open. |
| **Push notifications / wake-on-message** | Help Nearby incident push, Field mode assignment push | An incident can arrive at any time; the device must be able to wake and notify without the app being open. |
| **Camera & microphone** | Evidence Capture tiers in §9 | Needs OS-level media capture, not just a `<input type="file">` picker. |
| **Local secure storage / credential storage** | Verified volunteer/official login, OTP handling | Should not depend on browser storage that's cleared or inspectable via dev tools. |
| **Network state / connectivity manager** | `network_quality` field, offline handling | Needs to distinguish weak vs. offline vs. strong at the OS level to report it honestly. |

**Practical implication:** the eventual UI stack decision (§2, still open)
must be a real native or hybrid-native framework with first-class access
to the above — e.g. native Android/Kotlin or Java, native iOS/Swift, or a
cross-platform framework that ships native modules for background
location/sensors/notifications (Flutter, React Native with native
modules). A plain web app, a bare WebView wrapper around a website, or
anything that can't run a background service is ruled out regardless of
how the rest of this document evolves.

---
## 12. Technology Stack Summary
| Layer | Choice | Why |
|---|---|---|
| Citizen App frontend (M1) | **Not yet decided** — must satisfy §11; only AI-generated concept screens exist today | Backend-first build order (§2) means this decision is deliberately deferred, not accidentally undecided. |
| Professional App frontend (M5, `mobile/`) | **Not yet decided**, same constraint as M1 | MVP branch; same reasoning. |
| Professional App frontend (M5, `desktop/`, stretch) | **Not yet decided** — desktop-native (e.g. JavaFX, since the rest of the stack is already Java, or any desktop-native framework) | Command Desk only; no hardware/mobility requirements from §11 apply here since it's a desk console. |
| Backend — Orchestrator (M4) | Java 17+ | Strict typing, `java.util.concurrent`, native AES via `javax.crypto`. Non-negotiable — Java, not Kotlin, by team instruction. |
| Backend — API Gateway | Java + **Javalin** (REST + WebSocket) | Thin, fast to stand up, keeps the backend 100% Java, gives WebSocket support natively for the push-based incident/queue/field views. |
| Backend — Relational Matching (M6) | SQLite + JDBC | Zero-config, supports the weighted-match SQL directly. |
| Mapping | Native map SDK per frontend platform, once chosen | A native app renders a native map — decided alongside the UI stack. |
| Threat CV (M2) | **Deferred** — see §18 | Dropped from the MVP per current scope decision. |

**Backend language is fixed regardless of frontend decisions:** Java, not
Kotlin — this applies to everything in `safesphere-client/backend`, by
explicit team instruction, independent of whatever the frontends end up
using.

---
## 13. Data Flow
```
Citizen App (Need Help) --REST: SOS trigger--> backend/ (M4 FSM)
backend/ (M4) --AES-256 encrypt--> Emergency Capsule (held server-side)
backend/ (M4) --> M6 SQLite Matcher
M6 --WSS: §6.2 view--> Citizen App (Help Nearby, matched volunteer)
M6 --WSS: §6.3 view--> Professional App (Field mode, matched official)
Professional App (Field) --REST: SilenceAckEvent (OTP verified)--> backend/ (M4)
backend/ (M4) --WSS: stop alert--> Citizen App (Need Help)
backend/ (M4) --WSS: clear queue--> Citizen App (Help Nearby, other volunteers)
```
This is identical to the previous revision's data flow — moving the
backend's source into `safesphere-client`'s repo changes where the code
lives in git, not how any message travels at runtime.

---
## 14. Team Structure & Modular, Order-Independent Workflow
### Repo/module ownership
| Owner | Repo | Folder | Modules |
|---|---|---|---|
| Backend & Core Lead | `safesphere-client` | `backend/` | M4, M6, Agent Gateway, AES/OTP logic |
| Citizen App Engineer | `safesphere-client` | `app/` | M1 (both modes), M3 |
| Professional App Engineer | `safesphere-official-app` | `mobile/` (MVP), `desktop/` (stretch) | M5 (both modes on mobile; Command Desk only on desktop) |

Two people can both work inside `safesphere-client` without stepping on
each other, because `app/` and `backend/` are separate build targets with
separate dependency graphs — the same rule that made three repos
order-independent still applies to two folders in one repo. Treat the
folder boundary with the same discipline as a repo boundary: no importing
across it, no shared mutable state, only the contract.

### Why development order doesn't matter
Every module only ever calls the backend's contract — never another
frontend directly — so nobody is blocked waiting for someone else's app
to exist or even compile:
- The **Backend Lead** can build and test M4/M6 entirely against fixture
  JSON matching the contract files, with no UI running anywhere — and, per
  §2, this is exactly what the current phase is.
- The **Citizen App Engineer** can prototype the Help Nearby UI against a
  local mock server (a stub HTTP+WS server returning canned §6.2 JSON)
  once a UI stack is picked — no real backend, no Professional App,
  needed to finish the screen.
- The **Professional App Engineer** does the same against canned §6.3
  JSON for Field mode, and can start the `mobile/` branch independently of
  whether `desktop/` ever gets built.

### Integration checkpoints
Because nobody depends on anyone's running code, you don't need continuous
integration between the two repos — you need a small number of scheduled
checkpoints (see §15) where both frontends point at one real running
backend instance instead of their local mocks, and confirm the fixtures
they built against match what the backend actually emits.

### Change process for the contract
- Frozen once the team agrees on it. A field rename/add/remove after that
  requires: (1) posting the exact diff before touching code, (2) both app
  owners acknowledging it, (3) updating the contract file(s) in the same
  PR as the backend change. No field gets added "just in case" mid-sprint.

---
## 15. Sprint Plan (Backend-First)
Adjust hour counts to your actual timeline — the phase order is what
matters, and it's deliberately backend-first this revision.

| Phase | Backend Lead (`safesphere-client/backend`) | Citizen App Engineer (`safesphere-client/app`) | Professional App Engineer (`safesphere-official-app`) |
|---|---|---|---|
| **Backend Build** | Freeze capsule JSON schema; stand up Javalin skeleton with the routes from both contract files (returning fixtures); implement FSM states/transitions; AES-256 evidence vault; SQLite schema; weighted-match SQL. | Wireframe against AI-generated concept screens; do **not** start production UI code yet (§2). Research/shortlist native stacks against §11's hardware requirements. | Same as Citizen App Engineer, but for Command Desk/Field wireframes, and evaluate `desktop/` feasibility for later. |
| **Backend Hardening** | Full REST + WebSocket surface working against curl/Postman/a Java test client; failure injection (low battery payloads, dropped connections, expired OTPs); this phase's exit criterion is "backend demoable with no UI at all." | UI stack decision finalized for `app/`; scaffold the real project. | UI stack decision finalized for `mobile/`; scaffold the real project. |
| **Frontend — Need Help / Command Desk** | Available to unblock frontend integration questions; no new backend features unless a contract gap surfaces. | Implement Need Help mode against the now-working real backend (skip the mock — it already exists); implement Survival Mode battery/GPS throttling as a background service (§11). | Implement Command Desk `mobile/` against the real backend's WebSocket queue. |
| **Frontend — Help Nearby / Field** | — | Implement Help Nearby accept/decline/arrived flow; verify the payload never contains medical/OTP fields end to end. | Implement Field mode UI + OTP entry; verify the OTP flow end to end against real `silence_otp`/`otp_expires_at`. |
| **Integration** | Stand up backend for the team to point at continuously. | Full Need Help → Help Nearby loop against real backend, on-device (real GPS/battery/sensors, not emulator defaults). | Full Command Desk → Field handoff loop; start `desktop/` branch only if this phase finishes early. |
| **Test** | RBAC stripping tests; illegal-transition tests; OTP expiry tests. | Failure injection: low battery, dropped network, backgrounded app (confirm Survival Mode's background service actually survives). | Confirm SILENCE_ACK actually clears the Citizen App's Help Nearby queue live. |
| **Polish** | Freeze. End-to-end rehearsal. | Rehearse demo timing. | Rehearse Command Desk → Field handoff beat; if `desktop/` exists, rehearse that branch too. |

---
## 16. Demo Script & Success Metrics
1. Trigger a manual SOS in the Citizen App's Need Help mode; show the
   10-second silent confirmation window.
2. Trigger a simulated "crash detected" event (real accelerometer read, or
   a debug trigger if rehearsing indoors); show the immediate bypass to
   `EMERGENCY` with no confirmation window.
3. Drop the device battery below 15% (or simulate it); show the UI snap
   to dark theme and GPS polling drop to a 45-second heartbeat — and that
   it keeps running with the app backgrounded.
4. On a second device/app instance in Help Nearby mode, show the incident
   push arriving with only name/age/gender/location, even with the app
   backgrounded (push notification wakes it).
5. Switch to the Professional App's Command Desk mode; show the queue and
   timeline populate; dispatch to an official based on capability, not
   just distance.
6. Switch to Field mode on a **separate app install**; show the full
   capsule arrive, then enter the `silence_otp` and show the siren stop
   and the incident clear from the Help Nearby queue live.
7. If the backend-only phase is what's being demoed (before frontends
   exist), steps 1–6 can be shown as raw REST/WebSocket calls against
   `backend/` from Postman or a terminal client — the FSM transitions,
   payload stripping, and matching are all independently demoable without
   any UI.

**Anticipated questions:**
- *"Why two apps instead of one with a role switch?"* — A single app with
  a role switch means the sensitive fields exist somewhere in that app's
  data model even when hidden. Two apps, two backend response types: the
  Citizen App's build genuinely cannot deserialize medical data or the
  OTP.
- *"Why does the backend live inside the client repo instead of its own?"*
  — Team call to keep the project at two repos instead of three; the
  backend is still a standalone Java process at runtime, deployed and run
  independently of the Citizen App's frontend — only its source location
  in git changed.
- *"Why isn't there CV hazard detection yet?"* — Deliberately deferred
  (§18) so the MVP's escalation logic stays fully deterministic and
  auditable; the schema already reserves a slot (`hazard_notes`) for it.
- *"Why no UI yet?"* — The team hasn't committed to a frontend framework;
  building the backend first means that decision doesn't block progress,
  and it can be made carefully against §11's hardware requirements instead
  of under deadline pressure.

---
## 17. Risks & Mitigations
| Risk | Mitigation |
|---|---|
| Accidentally coupling `app/` and `backend/` since they share a repo | Enforce the module boundary in build tooling (separate Gradle/Maven modules or separate top-level projects), not just convention; code review checks for cross-imports. |
| Network unreliability between native apps and backend | WebSocket reconnect-with-backoff; REST calls idempotent by `capsule_id`; Survival Mode's degraded-network tier already assumes flaky connectivity. |
| Contract drift between backend Java DTOs and each app's hand-written parsing (frontend stack undecided) | Contract files are the source of truth, reviewed in the same PR as any backend change; revisit whether to reintroduce a shared `safesphere-contracts` module once a JVM-compatible frontend stack (if any) is chosen. |
| UI stack decision keeps getting delayed and blocks the whole MVP | Backend-first plan (§15) means there's a demoable, working system with zero frontend risk; set a hard decision deadline before the "Frontend" phases begin. |
| Hardware permission complexity (background location, sensors, notifications) once a stack is chosen | Budget explicit time for permission-flow UX (especially background location, which most OSes gate behind extra prompts) — don't treat it as a footnote. |
| Merge conflicts / cross-team blocking | Strict separation of concerns; nobody calls another team's code directly, only the frozen contract (§14). |
| Backend concurrency under load | `java.util.concurrent`, JDBC connection pooling, read-only views where possible. |
| Command Desk `desktop/` branch scope creep | It is explicitly a stretch goal (§2); do not start it before the mobile branch and Field mode are demo-ready. |

---
## 18. Roadmap Beyond the MVP
- **CV-based hazard verification (M2):** reintroduce as a pluggable
  service behind the already-reserved `hazard_notes` field — language/
  framework TBD when this is picked back up; it does not require a schema
  change to add.
- **Agent/API Layer:** exposing the emergency capability layer to external
  ride apps, wearables, and campus security apps via the same Java Agent
  Gateway.
- **Full 112 ERSS Integration:** officially pushing capsule payloads to
  government dispatch infrastructure.
- **Risk-Aware Routing:** navigation considering historical incident
  density and street lighting.
- **Command Desk `desktop/` branch**, if not finished within the MVP
  timeline.
- **Re-evaluate a shared contracts module** once both frontend stacks are
  chosen, if either turns out to be JVM-compatible.

---
## 19. Appendix
### 19.1 Trigger & FSM Pseudocode (backend, M4)
```java
// Core FSM transition evaluation — no CV dependency
public void evaluateTrigger(SosTriggerEvent event) {
    if (currentState == FsmState.SAFE) {
        currentState = FsmState.SUSPICIOUS;
        startSilentConfirmationWindow(10);

        if (event.triggerType() == TriggerType.CRASH_DETECTED) {
            // Native hardware crash detection bypasses confirmation directly
            currentState = FsmState.EMERGENCY;
            escalateToLevel(1);
        }
    }
}
```

### 19.2 Sample REST call (Citizen App -> Backend, Need Help mode)
```
POST /api/v1/sos/trigger
Content-Type: application/json

{
  "device_id": "DEV-4471",
  "trigger_type": "CRASH_DETECTED",
  "battery_level": 42,
  "network_quality": "WEAK",
  "cannot_speak": true,
  "threat_nearby": false,
  "timestamp": "2026-09-27T10:14:52Z"
}
```

### 19.3 Illustrative Android manifest permissions (once a native Android stack is picked)
Not a commitment to Android — illustrative only, to make §11 concrete:
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

---
## 20. Development Kickoff Prompts
Paste the relevant section(s) of this document alongside each prompt.
Prompts 1 and 3 are for **once the UI stack is chosen** — until then, only
Prompt 2 (backend) should be actively worked.

**Prompt 1 — M1: Citizen App (native, `safesphere-client/app`) + M3: Survival Mode**
```
Build the Citizen App (M1) for SafeSphere in [your chosen native stack —
must satisfy §11's hardware/OS requirements], with Survival Mode (M3)
embedded (see §7 and §9), inside the `app/` folder of `safesphere-client`
(do not import anything from `backend/`).
1. Two modes, switchable via a tab/toggle: "Need Help" and "Help Nearby".
2. Need Help mode: an SOS trigger, the "I Can't Speak" questionnaire, a
   10-second silent confirmation timer before calling POST /api/v1/sos/trigger.
   Implement Survival Mode as a background service: monitor real battery
   level; below 15%, switch the whole UI to a dark theme and drop location
   polling to a 45s heartbeat, and keep doing so while backgrounded.
3. Help Nearby mode: open a WebSocket subscription for incidents matched to
   this volunteer; render ONLY victim_name, victim_age, victim_gender, and
   location (§6.2) with a map pin. Accept/Decline; on Accept, a single "Mark
   Arrived" button. No field or screen for medical data, hazard notes,
   contacts, or an OTP entry box anywhere in this mode.
4. Build and test this module against the real `backend/` (it should
   already exist and be running per §15's backend-first phase) — a local
   mock server is only a fallback if the backend isn't reachable yet.
```

**Prompt 2 — M4/M6: Backend (Java, Javalin, `safesphere-client/backend`) — build this first**
```
Build the SafeSphere backend in Java 17+ using Javalin, covering M4 (Core
Orchestrator) and M6 (Relational Matcher) (see §6, §7, §8, §10), inside
the `backend/` folder of the `safesphere-client` repo, as its own
independent build target (no dependency on `app/`). Java only — do not use
Kotlin anywhere in this module, by explicit team instruction.
1. FsmState enum and a strict EmergencyStateEngine rejecting illegal
   transitions (e.g. SAFE -> RESPONDER_ASSIGNED directly).
2. EmergencyCapsule record matching §6.1; an EvidenceVault using
   javax.crypto for AES-256-GCM encryption.
3. REST endpoints: POST /api/v1/sos/trigger, POST /api/v1/volunteer/response,
   POST /api/v1/dispatch/decision, POST /api/v1/field/silence-ack.
4. WebSocket channels pushing the §6.2 view to matched volunteers and the
   §6.3 view to the matched official, per the topic names in the contract
   files.
5. SQLite JDBC matcher implementing the weighted query in §10, and a
   payload-splitting method that constructs two distinct response DTOs
   (never one object with fields hidden) depending on whether the match is
   a volunteer or an official.
Do not implement any UI. This is the module to build right now — the
frontend UI stack for both apps is intentionally undecided (§2); this
service should be fully testable via REST/WebSocket calls alone.
```

**Prompt 3 — M5: Professional App (native, `safesphere-official-app`)**
```
Build the Professional App (M5) for SafeSphere in [your chosen native
stack — must satisfy §11] — its own repo, own build, own login, sharing no
code with M1 (see §6, §7).
1. Start with the `mobile/` branch (this is the MVP). Two modes: "Command
   Desk" (active incident list + auto-scrolling Live Incident Timeline, fed
   by a WebSocket subscription to the full queue) and "Field" (activated
   once this specific official is assigned).
2. Field mode (mobile only): render the full-access payload from §6.3
   (victim profile, live location updating every stream_interval_seconds).
   An OTP entry field; on submit, POST /api/v1/field/silence-ack ONLY if
   the entered value matches silence_otp exactly and the current time is
   before otp_expires_at. On success, show "On Scene — Alert Silenced". On
   failure, show an inline error and send nothing.
3. Only after `mobile/` is demo-ready, and only if time remains: start a
   `desktop/` branch with Command Desk mode reimplemented for a desk-bound
   console. Do not build Field mode for desktop.
4. Build and test this module against the real `safesphere-client/backend`
   (it should already exist per §15's backend-first phase).
```
