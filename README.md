# SafeSphere — Emergency Orchestration Platform
**Version 2.0 · Two-app architecture — Citizen App (M1) + Professional App (M5)**
**Lead Architect:** Katukuri Saketh
**Institution:** Chaitanya Bharathi Institute of Technology
---
## Table of Contents
1. Executive Summary
2. Problem Restatement & Scope Boundaries
3. Key Concepts You Must Get Right
4. Prior Art — What Already Exists (and What We Reuse vs. Build)
5. High-Level Architecture
6. Core Data Model (Emergency Capsule Schema)
7. Module-by-Module Design (M1–M6)
8. Emergency State Machine Taxonomy
9. Resource Conservation Model (Survival Mode)
10. Capability Matching Mapping
11. Technology Stack Summary
12. Data Flow
13. Team Structure & Role Allocation
14. 26-Hour Sprint Plan
15. Demo Script & Success Metrics
16. Risks & Mitigations
17. Roadmap Beyond the Hackathon
18. Appendix — Sample Capsule JSON, Trigger Pseudocode
19. Development Kickoff Prompts (one per module)

---
## 1. Executive Summary
SafeSphere is not just another SOS button; it is an intelligent, resilient emergency-orchestration platform. It detects crises through sensor fusion, gathers contextual telemetry, preserves device evidence via cryptography, conserves remaining phone resources, and coordinates dispatch between people, responders, devices, and future AI agents.

The output format is an encrypted, structured **Emergency Capsule** containing role-based data payloads, seamlessly distributed to verified volunteers, family members, and professional 112 dispatchers. Building this requires a strict separation of concerns: AI is used for interpretation (computer vision hazard detection), while a deterministic rules engine handles safety-critical actions.

There are **two distinct end-user applications**, each its own codebase/executable:
- **The Citizen App (M1)** — the platform's main/flagship, most-installed app. Every everyday person uses this one app in either of two modes: **Need Help** (trigger and manage your own SOS — this absorbs what used to be a separate Victim app) and **Help Nearby** (receive and respond to nearby incidents as a verified volunteer — this absorbs what used to be a separate Volunteer app). The same person naturally switches modes: they might trigger an SOS today and respond to someone else's tomorrow. In Help Nearby mode, this app only ever receives the victim's name, age, gender, and live location — no medical, contact, or hazard data is ever serialized into that payload.
- **The Professional App (M5)** — a completely separate, verified-official-only application for police/EMS and control-room dispatch staff. It has two modes too: **Command Desk** (the control-room queue, live incident timeline, and map — this absorbs what used to be a separate Dispatcher Dashboard) and **Field Mode** (activated once a specific official is assigned to an incident — this absorbs what used to be a separate Official Responder app, receiving the full unredacted capsule and the OTP-silence mechanism).

This document gives the team a scoped architecture, a precise data model, a 6-module build plan, mathematical rules for battery survival and responder matching, a realistic 26-hour sprint calendar for a 3-person team, and copy-paste-ready prompts to kick off implementation.

---
## 2. Problem Restatement & Scope Boundaries
The problem statement demands an upgrade from basic alert tools to a robust orchestration layer. A full enterprise-grade version (live 112 API integration, deep OS-level battery kernel hooks, physical IoT wearables) is a multi-quarter engineering program. **Scope deliberately, and say so explicitly in your pitch.**

### In scope for the hackathon MVP
- **Citizen App — Need Help mode:** Java Swing desktop client simulating an Android environment (SOS triggers, hardware simulation sliders, silent confirmation windows).
- **Citizen App — Help Nearby mode:** Same app, a second mode/tab for verified volunteers — receives an incident push showing *only* the victim's name, age, gender, and live location. Accept/Decline/Mark Arrived buttons; no medical data, contacts, or hazard notes ever included in this payload.
- **Threat Detection:** Simulated accelerometer spikes backed by an actual Python/YOLOv8 microservice analyzing webcam frames for hazard verification.
- **State Machine Engine:** Deterministic Java FSM managing the escalation lifecycle.
- **Survival Mode:** Software-level battery conservation logic (exponential UI and GPS throttling based on simulated telemetry), embedded in the Citizen App's Need Help mode.
- **Professional App — Command Desk mode:** Java Swing dispatcher interface with an active queue and auto-scrolling live incident timeline, used by control-room staff.
- **Professional App — Field mode:** Activated for the specific official assigned to an incident. Receives the full unredacted Emergency Capsule (exact live location, medical profile, hazard notes) and lets the responder enter a one-time password (OTP) on arrival to silence the victim's repeating alert/siren and confirm on-scene handoff.
- **Relational Matching:** SQLite-backed matching of incidents to verified volunteers/officials based on capabilities and proximity, routing to whichever app/mode fits.

### Explicitly out of scope for the MVP (call these out as "Roadmap")
- Live, authenticated API integration with India's ERSS/112 services.
- Actual Android/iOS deployment (simulated via Swing for rapid 26-hour iteration).
- Physical hardware/wearable integrations (simulated via REST APIs).
- Unrestricted AI dispatching (AI is strictly bounded to hazard classification).

---
## 3. Key Concepts You Must Get Right
Every teammate should be able to explain these in one sentence to a judge.

| Concept | One-line explanation |
|---|---|
| **Emergency Orchestration** | We don't replace 112; we provide the intelligent, resilient layer around the person in distress that feeds context to existing infrastructure. |
| **Survival Mode** | An emergency-specific resource-management system that dynamically trades nonessential phone functionality (UI, frequent GPS) for emergency communication lifetime. |
| **Emergency Capsule** | A structured, AES-encrypted incident package that distributes different data (location, medical, audio) according to recipient permissions (RBAC). |
| **Deterministic Safety** | The principle that AI (like YOLO) is used for interpreting hazards, but deterministic state machines execute safety-critical actions like dispatching police. |
| **Capability Matching** | Moving beyond simple distance-based location sharing by evaluating a volunteer's specific skills (CPR, vehicle access) against the incident's requirements. |
| **Store-and-Forward Mesh** | A communication fallback where nearby devices receive a minimal emergency packet via Bluetooth/Wi-Fi Direct and forward it when internet connectivity is reached. |
| **Mode vs. App** | "Mode" (Need Help / Help Nearby, Command Desk / Field) is a UI state within one of the two apps. It is never a permissions boundary — the payload restriction between Citizen and Professional is enforced by two separate data contracts, not by which mode is showing. |

---
## 4. Prior Art — What Already Exists (and What We Reuse vs. Build)
Do **not** reinvent a basic SOS-to-SMS pipeline. Acknowledge the existing ecosystem:
- **Android / Apple Personal Safety:** Both offer crash detection, satellite SOS, and medical IDs. *Weakness:* Primarily individual-device features, not extensible emergency platforms, and Google's SOS fails when standard Battery Saver is on.
- **Life360 & Noonlight:** Offer dispatch and family circles. Noonlight proves dispatch APIs work.
- **112 India (ERSS):** Already handles dispatch, GIS management, and verified volunteers.

**Our differentiated value-add (worth stating explicitly in the pitch)** is:
1. An **Adaptive Survival Mode** that intelligently degrades app behavior based on precise battery telemetry, which standard apps do not do.
2. A cryptographic **Evidence Vault** and **Emergency Capsule** ensuring an immutable chain of custody with Role-Based Access Control.
3. A **Network Survival Layer** (simulated store-and-forward mesh).
4. **Agent API Extensibility**, turning the emergency response into a consumable infrastructure API.
5. A **hard app-level trust boundary**: the general public (whether triggering or volunteering) and professional officials use two entirely separate applications, so sensitive fields are stripped from the payload itself before it ever reaches the Citizen App — it's not just hidden behind a mode toggle, it was never sent.

---
## 5. High-Level Architecture
```text
                        ┌───────────────────────────────────────────┐
                        │              DETECTION LAYER              │
                        │ (SOS / Voice / Simulated Sensors / CV)    │
                        └───────────────────────┬───────────────────┘
                                                │
            ┌───────────────────────────────────┼────────────────────────────────────┐
            ▼                                   ▼                                    ▼
 ┌─────────────────────────┐      ┌──────────────────────────┐         ┌──────────────────────────┐
 │  M1  Citizen App        │      │  M2  Computer Vision     │         │  M3  Survival Engine     │
 │  (Need Help / Help      │      │  (Python + YOLOv8)       │         │  (Battery/Network Rules, │
 │   Nearby modes)         │      │                          │         │   embedded in M1)        │
 └──────────┬───────────────┘      └─────────────┬────────────┘         └─────────────┬────────────┘
            │ raw signals                       │ confidence scores                  │ telemetry limits
            └────────────────────────┬──────────┴──────────────────┬─────────────────┘
                                     ▼                             ▼
                        ┌───────────────────────────────────────────────────┐
                        │   M4  Core Orchestrator & State Machine           │
                        │   (Java FSM, AES-256 Crypto, Agent Gateway)       │
                        └───────────────────────┬───────────────────────────┘
                                                ▼ (Encrypted Capsule)
                                     ┌───────────┴────────────┐
                                     ▼                        ▼
                        ┌───────────────────────────────────────────────────┐
                        │   M6  Relational Capability Matcher (SQLite)      │
                        └───────────────────────┬───────────────────────────┘
                                                │
                 ┌──────────────────────────────┴───────────────────────────────┐
                 ▼ name/age/gender/location only                                ▼ full capsule + OTP
 ┌─────────────────────────────────────┐                        ┌─────────────────────────────────────┐
 │  M1  Citizen App — Help Nearby mode │                        │  M5  Professional App               │
 │  (same app/build as Need Help mode) │                        │  (Command Desk + Field modes,        │
 │                                     │                        │   own separate build from M1)        │
 └─────────────────────────────────────┘                        └─────────────────────────────────────┘
```
6. Core Data Model (Emergency Capsule Schema)
The system relies on the Emergency Capsule, an encrypted DTO passed across the EventBus.

6.1 Raw Encrypted Payload (Generated by M4)
JSON
{
  "capsule_id": "CR-8924",
  "timestamp": "2026-09-26T22:28:40Z",
  "fsm_state": "ACTIVE_EMERGENCY",
  "telemetry": {
    "latitude": 17.3850,
    "longitude": 78.4867,
    "battery_level": 12,
    "network_quality": "WEAK"
  },
  "medical_summary": "Blood: O+, Allergies: Penicillin",
  "encrypted_evidence": "[AES-256-GCM-PAYLOAD]"
}

6.2 Citizen App — Help Nearby Mode View
When the nearest verified volunteer is pushed an incident inside the Citizen App's Help Nearby mode, M6/M4 strip everything except four fields before the payload is even serialized — this is not a UI-level mask, the other fields are never generated for this payload:
JSON
{
  "capsule_id": "CR-8924",
  "fsm_state": "ACTIVE_EMERGENCY",
  "victim_name": "Jane Doe",
  "victim_age": 24,
  "victim_gender": "Female",
  "location": {
    "latitude": 17.3850,
    "longitude": 78.4867,
    "updated_at": "2026-09-26T22:29:10Z"
  }
}
No `medical_summary`, `hazard_notes`, `emergency_contacts`, or `silence_otp` field ever exists in this object — even a decompiled Citizen App build could not recover them, because they were stripped upstream, not merely omitted from the view.

6.3 Professional App — Field Mode View
Once M6 assigns an incident to a verified professional responder (a **different application from the Citizen App, with its own install and its own verified-official login**), the official's Field mode grants the highest access tier: the complete, unredacted capsule plus a live location stream and a one-time silence code.
JSON
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
The `silence_otp` is generated by M4's `EvidenceVault`/FSM the moment an official responder is assigned. It is single-use and time-boxed. Entering it correctly in the Professional App's Field mode is the only action that stops the recurring alert notification/siren on the Citizen App (Need Help mode) and clears the incident from other volunteers' Help Nearby queues — it is deliberately never sent to the Citizen App at all, only to the assigned professional's separate app, to prevent an unverified party from silencing a real emergency.

7. Module-by-Module Design
M1 — Citizen App (Java Swing, MAIN APP)
Objective: One installable app for the general public, covering both calling for help and helping others.
Design: A single Java Swing client with two modes, switched by a tab or toggle:
- **Need Help mode:** a prominent SOS button, a "Simulated Hardware" panel (sliders for battery %, GPS signal, crash impact), a silent 10-second confirmation timer, and the "I Can't Speak" questionnaire (Yes/No buttons for mobility/threat proximity). This mode embeds M3's Survival Engine directly.
- **Help Nearby mode:** subscribes to `SafeSphereEventBus` for incidents matched to this verified volunteer, rendering ONLY `victim_name`, `victim_age`, `victim_gender`, and `location` (Section 6.2) with a map pin. Accept/Decline buttons; on Accept, a single "Mark Arrived" button. No field, panel, or code path in this mode ever handles medical data, hazard notes, contact info, or an OTP entry box.
Output: Raw SOS event signals → M4 (Need Help mode). Accept/Decline/Arrived events → M4 (Help Nearby mode).

M2 — Threat Verification (Python / YOLOv8)
Objective: Provide AI-backed context to ambiguous sensor data.
Design: A Python microservice running OpenCV and Ultralytics YOLOv8. When M1's Need Help mode triggers a simulated accelerometer spike, M2 captures a webcam frame, detects hazards (fire, weapons), and returns a confidence score. High confidence skips the silent confirmation window and escalates immediately.
Output: Confidence JSON → M4.

M3 — SafeSphere Survival Engine (Java Threads, embedded in M1)
Objective: Dynamically preserve device life.
Design: A background daemon monitoring battery state, running inside the Citizen App's Need Help mode. As battery drops, it executes the decay model (Section 9). It handles UI dark-mode repainting and network fallback simulation (dropping packets to a local WSL socket when simulated cellular fails).
Output: Telemetry throttling commands, applied within M1.

M4 — Core Orchestrator & State Machine (Java Core)
Objective: Maintain deterministic safety and package evidence.
Design: A strict Finite State Machine (FSM) enforcing valid transitions. Contains the Evidence Vault which bundles audio, video, and medical IDs into the JSON schema, and encrypts it using javax.crypto AES-256. Also runs a Python FastAPI gateway exposing POST /api/v1/agent/trigger for external IoT triggers.
Output: Encrypted Emergency Capsule, routed by M6 to whichever app/mode fits.

M5 — Professional App (Java Swing, SEPARATE APP)
Objective: One installable app for verified officials, covering both control-room oversight and on-scene field response.
Design: A standalone Java Swing client — its own codebase and build, never bundled with M1 — with two modes:
- **Command Desk mode:** dual-pane Swing interface displaying active alerts, the incident queue (JTable), and the auto-scrolling Live Incident Timeline (JTextArea). Integrates a JFXPanel to render an interactive Python/Folium HTML map. Used by control-room staff who are not necessarily the one physically dispatched.
- **Field mode:** activated for the specific official assigned to an incident. Renders the full-access payload from Section 6.3 — victim name, blood type, allergies, known conditions, emergency contacts, hazard notes, and a live-updating map pin refreshed on `stream_interval_seconds`. A dedicated "Enter OTP to Silence Alert" panel accepts the 6-digit `silence_otp`; on correct entry before `otp_expires_at`, it publishes a `SILENCE_ACK` event that (a) stops the repeating siren/notification loop on M1's Need Help mode, (b) removes the incident from other volunteers' Help Nearby queues, and (c) advances the FSM. A wrong or expired OTP is rejected locally and logged, and does not touch FSM state.
Output: Real-time situational awareness (Command Desk mode). `SILENCE_ACK` event + on-scene timestamp → M4 (Field mode).

M6 — Relational Capability Matcher (SQLite)
Objective: Match incidents to volunteers/officials using precise logic.
Design: A normalized SQLite database storing verified responders (both volunteers and officials) and their certifications. Executes aggregate queries utilizing formulas from Section 10 to rank responders by ETA and capability, not just raw distance. Its output branches to whichever app/mode fits the match: the minimal-data payload to M1's Help Nearby mode for a community volunteer, or the full capsule + OTP to M5's Field mode for a professional official.
Output: Assigned responder ID, routed to M1 (Help Nearby mode) or M5 (Field mode).

8. Emergency State Machine Taxonomy
State	Trigger Condition	Auto-Escalation Rule
SAFE	Baseline. App is idle.	None.
SUSPICIOUS	
Sensor anomaly (route deviation, crash spike).

Initiates 10-second silent confirmation.

CHECKING	
User triggered SOS; waiting for confirmation window.

If YOLOv8 detects weapon/fire, bypass to EMERGENCY.

EMERGENCY	Timer expired or high-confidence threat confirmed.	
Ping Level 1 (Trusted Contacts) and push to nearest verified volunteers via M1's Help Nearby mode.

VOLUNTEER_ASSIGNED	A pushed volunteer accepts in M1's Help Nearby mode.	
Update Live Timeline; show that volunteer only the name/age/gender/location payload (Section 6.2).

ESCALATING	No volunteer/contact response within window.	
Ping Level 3 (112 / Verified Officials) via M5's Field mode.

RESPONDER_ASSIGNED	M6 SQL match successful (Official).	
Update Live Timeline; generate `silence_otp` and push full-access capsule to M5's Field mode.

ON_SCENE	Responder enters correct `silence_otp` in M5's Field mode before expiry.	
Publish `SILENCE_ACK`; stop siren/notifications on M1's Need Help mode and clear incident from other volunteers' Help Nearby queues.

RESOLVED	Dispatcher closes incident from M5's Command Desk mode.	
Generate final audit record.

9. Resource Conservation Model (Survival Mode)
The architecture must mathematically adapt to device constraints.

Plaintext
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
This is a core differentiator. It proves the app adapts to crisis realities.

10. Capability Matching Mapping
Standard apps match by nearest distance. SafeSphere matches by weighted capability.

SQL
-- Conceptual SQL weighting for M6
SELECT responder_id, 
       ( (1 / distance_km) * 0.4 ) + 
       ( first_aid_cert * 0.3 ) + 
       ( vehicle_access * 0.3 ) AS match_score
FROM verified_volunteers
WHERE status = 'ONLINE'
ORDER BY match_score DESC
LIMIT 1;

11. Technology Stack Summary
Layer	Choice	Why
Citizen App (M1)	Java Swing	Fast to build a single, lightweight, most-installed executable; both its modes share one build so there's only one thing for the public to install.
Professional App (M5)	Java Swing + JFXPanel/Folium	Standalone build, kept deliberately separate from M1 so an official-only field (medical data, OTP) can never ship inside a public install; OTP generation/verification reuses M4's `javax.crypto` utilities so there is only one source of truth for secrets.
Core Orchestrator (M4)	Java 17+ (Core)	Strict typing, robust concurrent event bus (java.util.concurrent), native AES cryptography.
Threat CV (M2)	Python + YOLOv8 + OpenCV	Industry standard for object detection; easily invoked via Java ProcessBuilder.
Relational Matching (M6)	SQLite + JDBC	Zero-config database; supports complex relational matching logic directly out of the box.
Agent API Gateway	Python FastAPI	
Async-native, instantly provides Swagger/OpenAPI docs for simulated IoT payloads.

Mapping Engine	Python Pandas + Folium	
Generates beautiful HTML interactive maps that Java Swing can easily render via WebViews, used by both M1's Help Nearby mode and M5's two modes.

12. Data Flow
Code snippet
flowchart LR
    A[Simulated Sensors/User] --> B(M1 Citizen App — Need Help mode)
    B --> C(M2 YOLOv8 CV Microservice)
    C --> D[M4 Java Core FSM]
    B --> E(M3 Survival Daemon, embedded in M1)
    E --> D
    D --> F[(AES Encryption)]
    F --> G(EventBus / Sockets)
    G --> I[(M6 SQLite Matcher)]
    I --> H1(M1 Citizen App — Help Nearby mode: name/age/gender/location only)
    I --> H2(M5 Professional App — Command Desk + Field modes: full capsule)
    H2 --> K{OTP correct in Field mode?}
    K -->|Yes| L[Silence alert on M1 Need Help mode + clear Help Nearby queues]
    K -->|No| H2

13. Team Structure & Role Allocation (3 Developers)
Role	Owns	Primary skills needed
Backend & Core Lead	M4, M5 Field mode (full logic + OTP), FastAPI Gateway, Cryptography	Java Core, concurrency, AES, Python/FastAPI.
Edge UI & CV Engineer	M1 (both Need Help and Help Nearby modes), M2, M3	Java Swing, UI Thread management, Python YOLOv8.
Dispatch & Data Engineer	M5 Command Desk mode, M6	Java Swing (Tables/WebViews), SQLite/JDBC, Python Pandas.

*M1 is the flagship app and now carries both modes, so the Edge UI & CV Engineer's schedule should treat Help Nearby mode as equal priority to Need Help mode, not an afterthought. M5's two modes are still split by developer — Field mode (with the OTP secret) stays entirely with the Backend & Core Lead so that payload split is enforced in one place, while Command Desk mode (the queue/timeline/map) stays with the Dispatch & Data Engineer.
