# SafeSphere — Emergency Orchestration Platform
### Hackathon Sprint · Design & Technical Document
**Version 1.0 · Prepared for team kickoff**
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
7. Module-by-Module Design (M1–M8)
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

There are three distinct end-user applications, deliberately kept as separate codebases/executables rather than one app with a role switch: the **Victim App** (M1, triggers and manages the incident), the **Volunteer Companion App** (M8, the platform's main/flagship consumer app — this is what gets pitched and demoed first), and the **Official Responder App** (M7, a completely separate, professional-only application for verified police/EMS). The Volunteer app is deliberately minimal by design: it only ever receives the victim's name, age, gender, and live location — no medical, contact, or hazard data is ever serialized into its payload. The Official app is the only one that ever receives the full unredacted capsule and the OTP silence mechanism.

This document gives the team a scoped architecture, a precise data model, an 8-module build plan, mathematical rules for battery survival and responder matching, a realistic 26-hour sprint calendar for a 3-person team, and copy-paste-ready prompts to kick off implementation.

---
## 2. Problem Restatement & Scope Boundaries
The problem statement demands an upgrade from basic alert tools to a robust orchestration layer. A full enterprise-grade version (live 112 API integration, deep OS-level battery kernel hooks, physical IoT wearables) is a multi-quarter engineering program. **Scope deliberately, and say so explicitly in your pitch.**

### In scope for the hackathon MVP
- **Victim Interface:** Java Swing desktop client simulating an Android environment (SOS triggers, hardware simulation sliders, silent confirmation windows).
- **Threat Detection:** Simulated accelerometer spikes backed by an actual Python/YOLOv8 microservice analyzing webcam frames for hazard verification.
- **State Machine Engine:** Deterministic Java FSM managing the escalation lifecycle.
- **Survival Mode:** Software-level battery conservation logic (exponential UI and GPS throttling based on simulated telemetry).
- **Command Dashboard:** Java Swing dispatcher interface with an active queue and auto-scrolling live incident timeline.
- **Relational Matching:** SQLite-backed matching of incidents to verified volunteers based on capabilities and proximity.
- **Volunteer Companion App (main app):** A Java Swing client — the platform's flagship, most-used application — that pushes an incident to the nearest verified volunteer showing *only* the victim's name, age, gender, and live location. No medical data, contacts, or hazard notes are ever included in this payload.
- **Official Responder App (separate app):** A completely separate Java Swing client (distinct build/codebase from the Volunteer app) for verified police/EMS only. Once dispatched, it receives the full unredacted Emergency Capsule (medical profile, hazard notes, exact live location) and lets the responder enter a one-time password (OTP) on arrival to silence the victim's repeating alert/siren and confirm on-scene handoff.

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
5. A **hard app-level trust boundary**: volunteers and professional responders use two entirely separate applications (M8 vs. M7), so sensitive fields are stripped from the payload itself before it ever reaches the Volunteer app — it's not just hidden in the UI, it was never sent.

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
 ┌─────────────────────┐          ┌──────────────────────────┐         ┌──────────────────────────┐
 │  M1  Victim Client  │          │  M2  Computer Vision     │         │  M3  Survival Engine     │
 │  (Java Swing UI)    │          │  (Python + YOLOv8)       │         │  (Battery/Network Rules) │
 └──────────┬──────────┘          └─────────────┬────────────┘         └─────────────┬────────────┘
            │ raw signals                       │ confidence scores                  │ telemetry limits
            └────────────────────────┬──────────┴──────────────────┬─────────────────┘
                                     ▼                             ▼
                        ┌───────────────────────────────────────────────────┐
                        │   M4  Core Orchestrator & State Machine           │
                        │   (Java FSM, AES-256 Crypto, Agent Gateway)       │
                        └───────────────────────┬───────────────────────────┘
                                                ▼ (Encrypted Capsule)
            ┌───────────────────────────────────┴────────────────────────────────────┐
            ▼                                                                        ▼
 ┌───────────────────────────────┐                                     ┌───────────────────────────────┐
 │  M5  Dispatcher Command Desk  │◄───── incident data ──────────────►│  M6  Relational Matcher       │
 │  (Java Swing, HTML Maps)      │                                     │  (SQLite, JDBC)               │
 └───────────────────────────────┘                                     └───────────────┬───────────────┘
                                                                                        │
                                     ┌───────────────────────────────────────────────────┤
                                     ▼ name/age/gender/location only                    ▼ full capsule + OTP
                    ┌───────────────────────────────┐                    ┌───────────────────────────────┐
                    │  M8  Volunteer Companion App  │                    │  M7  Official Responder App   │
                    │  (Java Swing — MAIN APP,      │                    │  (Java Swing — SEPARATE APP,  │
                    │   minimal-data payload only)  │                    │   full medical record, OTP)   │
                    └───────────────────────────────┘                    └───────────────────────────────┘
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
6.2 Volunteer Companion App View (M8 — Main App RBAC Output)
When the nearest verified Volunteer is pushed an incident in the **main app**, M6/M4 strip everything except four fields before the payload is even serialized — this is not a UI-level mask, the other fields are never generated for this payload:
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
No `medical_summary`, `hazard_notes`, `emergency_contacts`, or `silence_otp` field ever exists in this object — a Volunteer app that was somehow decompiled or intercepted still could not recover them, because they were stripped upstream, not merely omitted from the view.

6.3 Official Responder App View (M7 — Completely Separate App)
Once M6 assigns an incident to a verified professional responder (Police/EMS role — a **different application from M8, with its own install and its own verified-official login**), the responder's role in the RBAC matrix grants the highest access tier: the complete, unredacted capsule plus a live location stream and a one-time silence code.
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
The `silence_otp` is generated by M4's `EvidenceVault`/FSM the moment an official responder is assigned. It is single-use and time-boxed. Entering it correctly in M7 is the only action that stops the recurring alert notification/siren on the victim's device and on other still-pinged volunteers' apps — it is deliberately never sent to the Volunteer app (M8) at all, only to the assigned professional Responder's separate app, to prevent an unverified party from silencing a real emergency.
7. Module-by-Module Design
M1 — Victim Interface & Edge Telemetry (Java Swing)
Objective: Capture user intent and simulate hardware sensors.
Design: Built in Java Swing. Features a prominent SOS button, a "Simulated Hardware" panel (sliders for battery %, GPS signal, crash impact), a silent 10-second confirmation timer, and the "I Can't Speak" questionnaire (Yes/No buttons for mobility/threat proximity).
Output: Raw event signals pushed to M4.   
PDF

M2 — Threat Verification (Python / YOLOv8)
Objective: Provide AI-backed context to ambiguous sensor data.
Design: A Python microservice running OpenCV and Ultralytics YOLOv8. When M1 triggers a simulated accelerometer spike, M2 captures a webcam frame, detects hazards (fire, weapons), and returns a confidence score. High confidence skips the silent confirmation window and escalates immediately.
Output: Confidence JSON → M4.

M3 — SafeSphere Survival Engine (Java Threads)
Objective: Dynamically preserve device life.
Design: A background daemon monitoring battery state. As battery drops, it executes the decay model (Section 9). It handles UI dark-mode repainting and network fallback simulation (dropping packets to a local WSL socket when simulated cellular fails).
Output: Telemetry throttling commands.   
PDF
+ 2

M4 — Core Orchestrator & State Machine (Java Core)
Objective: Maintain deterministic safety and package evidence.
Design: A strict Finite State Machine (FSM) enforcing valid transitions[cite: 1]. Contains the Evidence Vault which bundles audio, video, and medical IDs into the JSON schema, and encrypts it using javax.crypto AES-256[cite: 1]. Also runs a Python FastAPI gateway exposing POST /api/v1/agent/trigger for external IoT triggers[cite: 1].
Output: Encrypted Emergency Capsule.   
PDF

M5 — Dispatcher Command Dashboard (Java Swing)
Objective: Provide professional visualization for responders[cite: 1].
Design: Dual-pane Swing interface displaying active alerts, Medical Emergency Profiles, and the Live Incident Timeline (chronological event logging)[cite: 1]. Integrates a JFXPanel to render an interactive Python/Folium HTML map for tracking Safe Journeys and route deviations[cite: 1]. This is an internal ops tool used by 112-style staff, not by volunteers or officials in the field — those groups use M8 and M7 respectively.
Output: Real-time situational awareness.

M6 — Relational Capability Matcher (SQLite)
Objective: Match incidents to volunteers using precise logic[cite: 1].
Design: A normalized SQLite database storing verified responders and their certifications. Executes aggregate queries utilizing formulas from Section 10 to rank responders by ETA and capability, not just raw distance. Its output branches to whichever of the two field apps fits the match: the minimal-data payload to M8 for a community Volunteer, or the full capsule + OTP to M7 for a professional Official.
Output: Assigned responder ID, routed to M8 or M7.

M7 — Official Responder App (Java Swing, separate application)
Objective: Give the dispatched professional responder (police/EMS) everything needed on-scene, and a safe way to stand down the alert.
Design: A standalone Java Swing client — its own codebase and build, never bundled with M8 — simulating the official's handset. On assignment it subscribes to the EventBus for its `capsule_id` and renders the full-access payload from Section 6.3: victim name, blood type, allergies, known conditions, emergency contacts, hazard notes, and a live-updating map pin (Folium/WebView) refreshed on `stream_interval_seconds`. A dedicated "Enter OTP to Silence Alert" panel accepts the 6-digit `silence_otp`; on correct entry before `otp_expires_at`, it publishes a `SILENCE_ACK` event that (a) stops the repeating siren/notification loop on M1, (b) removes the incident from other volunteers' active queues, and (c) advances the FSM. A wrong or expired OTP is rejected locally and logged, and does not touch FSM state.
Output: `SILENCE_ACK` event + on-scene timestamp → M4.

M8 — Volunteer Companion App (Java Swing, MAIN APP)
Objective: Mobilize the nearest verified community volunteer fast, with just enough information to help — and nothing that risks the victim's privacy.
Design: The platform's flagship, most-installed application, deliberately kept as simple and lightweight as possible so adoption is easy. It receives incident pushes from M6 containing only `victim_name`, `victim_age`, `victim_gender`, and `location` (Section 6.2) — never medical data, hazard notes, contact info, or the `silence_otp`. Its Swing UI shows a map pin, the four allowed fields, and Accept/Decline buttons; on Accept, a "Mark Arrived" button logs an on-scene timestamp. It has no OTP entry field at all, since that capability is exclusive to M7.
Output: Accept/decline events + arrival timestamp → M4.

8. Emergency State Machine Taxonomy
State	Trigger Condition	Auto-Escalation Rule
SAFE	Baseline. App is idle.	None.
SUSPICIOUS	
Sensor anomaly (route deviation, crash spike)[cite: 1].

Initiates 10-second silent confirmation[cite: 1].

CHECKING	
User triggered SOS; waiting for confirmation window[cite: 1].

If YOLOv8 detects weapon/fire, bypass to EMERGENCY[cite: 1].

EMERGENCY	Timer expired or high-confidence threat confirmed.	
Ping Level 1 (Trusted Contacts) and push to nearest verified Volunteers via M8[cite: 1].

VOLUNTEER_ASSIGNED	A pushed Volunteer accepts in M8.	
Update Live Timeline; show that Volunteer only the name/age/gender/location payload (Section 6.2).

ESCALATING	No Volunteer/contact response within window.	
Ping Level 3 (112 / Verified Official Responders) via M7[cite: 1].

RESPONDER_ASSIGNED	M6 SQL match successful (Official).	
Update Live Timeline[cite: 1]; generate `silence_otp` and push full-access capsule to M7.

ON_SCENE	Responder enters correct `silence_otp` in M7 before expiry.	
Publish `SILENCE_ACK`; stop siren/notifications on M1 and clear incident from other volunteers' queues.

RESOLVED	Dispatcher closes incident.	
Generate final audit record[cite: 1].

9. Resource Conservation Model (Survival Mode)
The architecture must mathematically adapt to device constraints[cite: 1].

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
This is a core differentiator. It proves the app adapts to crisis realities[cite: 1].

10. Capability Matching Mapping
Standard apps match by nearest distance. SafeSphere matches by weighted capability[cite: 1].

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
User Interfaces (M1, M5)	Java Swing	Fast to build desktop executables; entirely decouples front-end from cloud latency risks during a hackathon.
Core Orchestrator (M4)	Java 17+ (Core)	Strict typing, robust concurrent event bus (java.util.concurrent), native AES cryptography.
Threat CV (M2)	Python + YOLOv8 + OpenCV	Industry standard for object detection; easily invoked via Java ProcessBuilder.
Relational Matching (M6)	SQLite + JDBC	Zero-config database; supports complex relational matching logic directly out of the box.
Agent API Gateway	Python FastAPI	
Async-native, instantly provides Swagger/OpenAPI docs for simulated IoT payloads[cite: 1].

Mapping Engine	Python Pandas + Folium	
Generates beautiful HTML interactive maps that Java Swing can easily render via WebViews[cite: 1].

First Responder App (M7)	Java Swing + JFXPanel/Folium	Standalone build, kept deliberately separate from M8 so an official-only field (medical data, OTP) can never ship inside a public volunteer install; OTP generation/verification reuses M4's `javax.crypto` utilities so there is only one source of truth for secrets.
Volunteer Companion App (M8)	Java Swing (separate build from M7)	The flagship app, so it's optimized for install size and simplicity over feature depth; reuses M1's minimal-UI patterns rather than M5/M7's denser dashboard layout.

12. Data Flow
Code snippet
flowchart LR
    A[Simulated Sensors/User] --> B(M1 Victim Swing UI)
    B --> C(M2 YOLOv8 CV Microservice)
    C --> D[M4 Java Core FSM]
    B --> E(M3 Survival Daemon)
    E --> D
    D --> F[(AES Encryption)]
    F --> G(EventBus / Sockets)
    G --> H[M5 Dispatcher Swing UI]
    H --> I[(M6 SQLite Matcher)]
    I --> M8(M8 Volunteer App — MAIN, name/age/gender/location only)
    I --> J(M7 Official Responder App — separate, full capsule)
    J --> K{OTP correct?}
    K -->|Yes| L[Silence alert on M1 + clear queues]
    K -->|No| J
13. Team Structure & Role Allocation (3 Developers)
Role	Owns	Primary skills needed
Backend & Core Lead	M4, M7 (Official Responder App, full logic + OTP), FastAPI Gateway, Cryptography	Java Core, concurrency, AES, Python/FastAPI.
Edge UI & CV Engineer	M1, M2, M3	Java Swing, UI Thread management, Python YOLOv8.
Dispatch & Data Engineer	M5, M6, M8 (Volunteer Companion App — main app)	Java Swing (Tables/WebViews), SQLite/JDBC, Python Pandas.

*M8 is the flagship app and gets priority in the Dispatch & Data Engineer's schedule over dashboard polish on M5, since it's what the demo and pitch lead with. M7 stays entirely with the Backend & Core Lead so the payload split (full data vs. four fields) is enforced in one place, not duplicated across two developers' code.
14. 26-Hour Sprint Plan
Phase	Core Engine (M4)	Edge & CV (M1/M2/M3)	Dispatch & Data (M5/M6)
Hours 1–3 (Scaffold)	Freeze Capsule JSON schema; Build EventBus.	Scaffold Victim Swing JFrame; build hardware sliders.	Scaffold Dispatch JSplitPane; scaffold M8 Volunteer App shell; write SQLite schema scripts.
Hours 4–8 (Logic)	Implement FSM states & transitions; Write AES encryption class; scaffold M7 Official Responder App shell (separate build).	Build YOLOv8 Python script; Implement silent confirmation timers.	Build SQLite aggregate queries for capability matching; build M8 accept/decline UI (name/age/gender/location only).
Hours 9–12 (Refining)	Build FastAPI Agent Gateway for IoT triggers; wire OTP generation into EvidenceVault.	Implement Survival Mode math loop & dark theme logic.	Build Live Incident Timeline auto-scroller; Python Folium map; add live-location pin to M8.
Hours 13–16 (Wiring)	Wire FSM events to EventBus; connect M7's full-access payload subscription.	Link YOLOv8 output to Java FSM via ProcessBuilder.	Hook SQL matcher and Folium maps to EventBus stream; wire M6 → M8 push for Volunteer matches.
Hours 17–20 (Test 1)	JUnit tests for illegal FSM transitions; Decryption integrity; M7 OTP entry validation tests.	SwingWorker EDT safety checks (prevent UI freeze).	Latency check on SQLite matching (100 mock incidents); confirm M8 payload never contains medical/OTP fields.
Hours 21–23 (Test 2)	Validate RBAC data stripping; generate & expire `silence_otp` correctly; confirm M7 and M8 are genuinely separate builds.	Failure injection: Drop battery to 5%, confirm UI swap.	Validate M8 shows only name/age/gender/location, end to end.
Hours 24–26 (Polish)	Code freeze. End-to-end rehearsal.	Rehearse demo timing.	Rehearse demo timing (lead with M8 as the flagship app, then the separate M7 "enter OTP → siren stops" beat).
15. Demo Script & Success Metrics
Suggested 5-minute live demo flow:

Start the Safe Journey tracking. Manually trigger a "Route Deviation" on the simulator.

Trigger the "Crash Impact" slider. Show the YOLOv8 script catch a hazard frame, instantly skipping the silent countdown.

Drop the battery slider to 12%. Watch the Victim UI instantly snap to black and print "GPS Throttled to 45s" to prove Survival Mode[cite: 1].

Switch to the Dispatcher Dashboard. Show the Live Incident Timeline populate[cite: 1].

Show the incident push arriving on the **M8 Volunteer Companion App** (the main app) — only the victim's name, age, gender, and a map pin are visible, nothing medical.

Click "Dispatch" and show the SQL query select an Official responder based on CPR certification, not just distance[cite: 1].

Switch to the **M7 Official Responder App** — a completely separate application — and show the full-access capsule (exact location, blood type, allergies, hazard notes) arriving, then type in the `silence_otp` to show the victim's siren stop and the incident vanish from the Volunteer app's queue.

Anticipated judge questions:

"Why not just use Android SOS?" → Android SOS disables when Battery Saver is on[cite: 1]. SafeSphere orchestrates its own survival mode.

"What if cell networks go down?" → Mention the offline store-and-forward mesh fallback[cite: 1].

16. Risks & Mitigations
Risk	Mitigation
Swing UI freezes when YOLOv8 runs	Use SwingWorker for all background CV processing. Never block the Event Dispatch Thread (EDT).
Database locks on high concurrency	Use proper JDBC connection pooling and read-only views where possible.
Merge conflicts slowing the sprint	Strict separation of concerns. The EventBus contract is frozen in Hour 1. Developers do not touch each other's UI panels.
17. Roadmap Beyond the Hackathon
Agent/API Layer: Exposing the emergency capability layer to external ride apps, wearables, and campus security apps[cite: 1].

Full 112 ERSS Integration: Officially pushing capsule payloads to government dispatch infrastructure[cite: 1].

Risk-Aware Routing: Navigation that considers historical incident density and street lighting[cite: 1].

18. Appendix
18.1 Trigger & FSM Pseudocode (for M4)
Java
// Core FSM Transition evaluation
public void evaluateSensorSpike(SensorData data) {
    if (currentState == State.SAFE) {
        currentState = State.SUSPICIOUS;
        startSilentConfirmationWindow(10);
        
        // Invoke CV
        double cvConfidence = CVService.analyzeFrame();
        if (cvConfidence > 0.85) {
            // Bypass confirmation
            currentState = State.EMERGENCY;
            escalateToLevel(1);
        }
    }
}
19. Development Kickoff Prompts
Each block below is written to be pasted directly into an AI coding assistant to bootstrap that module. Paste the relevant section(s) of this document alongside the prompt for full context.

Prompt 1 — M1 & M3: Victim Interface & Survival Mode
Plaintext
Build the Edge Telemetry & Victim Interface (M1) and Survival Mode (M3) for SafeSphere (see design doc Sections 7 and 9).
Requirements:
1. Create a Java Swing `JFrame` representing a mobile app interface.
2. Build a primary "SOS" button panel, and a "Hardware Simulator" side-panel containing: a JSlider for Battery % (0-100), a JComboBox for Network Quality, and a JButton for "Simulate Crash Impact".
3. Implement the "I Can't Speak" questionnaire (Yes/No buttons for 'Injured?' and 'Threat Nearby?').
4. Implement the Survival Mode background thread: Continuously monitor the Battery slider. If it drops below 15%, invoke a method on the Event Dispatch Thread (via SwingUtilities) that changes the entire JFrame background to Color.BLACK, hides non-essential buttons, and logs "GPS throttled to 45s heartbeat".
5. Ensure all button clicks that simulate long-running tasks use a `SwingWorker` so the UI never freezes.
Provide the complete, runnable Java class for this UI.
Prompt 2 — M4: Core Orchestrator & FSM
Plaintext
Build the Core Orchestrator and State Machine module (M4) for SafeSphere (see design doc Sections 6, 7, and 8).
Requirements:
1. Define a strict Java Enum for the FSM states (SAFE, SUSPICIOUS, CHECKING, EMERGENCY, VOLUNTEER_ASSIGNED, ESCALATING, RESPONDER_ASSIGNED, ON_SCENE, RESOLVED).
2. Create an `EmergencyStateEngine` class that manages transitions. It must reject illegal state jumps (e.g., cannot go from SAFE to RESPONDER_ASSIGNED).
3. Create the `EmergencyCapsule` record/POJO matching the JSON schema in Section 6.1.
4. Implement an `EvidenceVault` class utilizing `javax.crypto` to encrypt a sample JSON payload using AES-256-GCM.
5. Create a basic singleton `SafeSphereEventBus` using a ConcurrentHashMap to allow subscribers to listen for emitted `EmergencyCapsule` objects.
Do not implement the UI. Provide the core logic classes.
Prompt 3 — M5 & M6: Dispatch Command & Relational Matcher
Plaintext
Build the Dispatch Command Center (M5) and Relational Capability Matcher (M6) for SafeSphere (see design doc Sections 6, 7, and 10).
Requirements:
1. Create a Java Swing `JFrame` using a `JSplitPane`. The left pane is the Active Incident queue (JTable). The right pane is the Medical Profile and Auto-scrolling Live Incident Timeline (JTextArea). This is the internal ops dashboard only — it is not the Volunteer or Official app.
2. Implement a SQLite JDBC class (`DatabaseMatcher.java`). Include a method to initialize an in-memory database with a table `verified_volunteers` (id, distance_km, cpr_cert, vehicle_access, status, is_official BOOLEAN).
3. Write a SQL aggregate query method that takes incident coordinates and requirements, and returns the best match ID based on the weighted formula in Section 10, not just raw distance.
4. Implement the payload-splitting method: given a match, if `is_official = false`, serialize only `victim_name`, `victim_age`, `victim_gender`, and `location` (Section 6.2) for M8; if `is_official = true`, serialize the full capsule plus a freshly generated `silence_otp` (Section 6.3) for M7. Do this by constructing two different DTOs — never build one object and hide fields on it, since that DTO could still be serialized whole by mistake.
Provide the complete Swing UI class and the JDBC matcher class.
Prompt 4 — M7: Official Responder App (Separate Application)
Plaintext
Build the Official Responder App (M7) for SafeSphere (see design doc Sections 6.3 and 7). This is a standalone application — its own `main()` class, its own JAR — never combined with M8's code.
Requirements:
1. Create a Java Swing `JFrame` representing the assigned official responder's handset.
2. On construction, subscribe to `SafeSphereEventBus` for the given `capsule_id` and render the full-access payload: victim name, blood type, allergies, known conditions, emergency contacts, hazard notes, and a live-location panel that updates every `stream_interval_seconds` (simulate with a `javax.swing.Timer`, no real GPS needed).
3. Add an "Enter OTP to Silence Alert" panel: a masked `JTextField` (6 digits) and a "Confirm" button.
4. On Confirm, compare the entered value against the capsule's `silence_otp` (case-sensitive, exact match) and check it is before `otp_expires_at`. On success, publish a `SILENCE_ACK` event on the EventBus and update the local UI to "On Scene — Alert Silenced". On failure, show an inline error and do not touch shared state.
5. Never log or print the `silence_otp` value anywhere except the field it was generated in (M4) — this class only compares it.
Do not implement the OTP generation logic (that belongs to M4). Provide the complete Swing UI class that consumes it.
Prompt 5 — M8: Volunteer Companion App (Main App)
Plaintext
Build the Volunteer Companion App (M8) for SafeSphere (see design doc Sections 6.2 and 7). This is the platform's flagship, most-installed application — keep it lightweight and its own standalone build, entirely separate from M7's code.
Requirements:
1. Create a Java Swing `JFrame` representing a verified volunteer's handset, simpler and lighter-weight than M5 or M7.
2. On construction, subscribe to `SafeSphereEventBus` for incidents matched to this volunteer and render ONLY four fields from the minimal-data payload: `victim_name`, `victim_age`, `victim_gender`, and `location` (as a map pin via a simple WebView/Folium panel).
3. Add "Accept" and "Decline" buttons. On Accept, replace them with a single "Mark Arrived" button that, when clicked, publishes an `ARRIVED` event with a timestamp to the EventBus.
4. Do NOT include any field, panel, or code path for medical data, hazard notes, contact info, or an OTP entry box — this class must never even deserialize those fields if they somehow appeared in a payload, since they are never supposed to exist in this app's data model at all.
5. On Decline, remove the incident from this volunteer's queue and allow M6 to re-match it to the next-best volunteer.
Provide the complete, runnable Swing UI class.