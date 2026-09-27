# SafeSphere ΓÇö Emergency Orchestration Platform
**Version 4.0 ┬╖ Two repos (backend lives inside this one) ┬╖ OS-level native apps ┬╖ UI stack TBD, backend-first**
**Lead Architect:** Katukuri Saketh
**Institution:** Chaitanya Bharathi Institute of Technology

---

## What changed from v3.0

v3.0 split the project into three repos (`safesphere-client`,
`safesphere-official-app`, and a not-yet-created `safesphere-backend`) and
made the backend pure Java. v4.0 keeps the pure-Java backend but makes
three further changes, all direct team instructions:

1. **Back to two repos.** There is no separate `safesphere-backend` repo.
   The Java backend (M4 Orchestrator, M6 Matcher, Agent Gateway) now lives
   **inside this repo**, in its own `backend/` folder, alongside the
   Citizen App's `app/` folder. It still runs as its own standalone Java
   process at deployment time ΓÇö only where its source sits in git changed.
   `safesphere-official-app` stays separate and has no backend code; it
   only talks to `backend/` over the network, same as before.
2. **Professional App's Command Desk mode now has two branches:**
   `mobile/` first (the MVP), `desktop/` later only if time allows. Field
   mode stays mobile-only in both branches. (This lives in
   `safesphere-official-app`, noted here for context.)
3. **Backend-first build order.** The team hasn't committed to a frontend
   UI framework yet ΓÇö only AI-generated concept screens exist. The plan is
   to build and fully test `backend/` first, headlessly (REST/WebSocket
   calls, no UI needed to prove it works), and choose the frontend stack
   for both apps once the backend is stable. See `SafeSphere.md` ┬º2 and
   ┬º15.

Both apps are required to be genuine **OS-level native applications with
direct hardware access** (GPS, motion sensors, battery state, background
services, camera/mic, push notifications) ΓÇö not a webview/PWA wrapper.
See `SafeSphere.md` ┬º11 for the full list and why it rules out certain
stacks.

---

## Two repos

| Repo | Owns | Status |
|---|---|---|
| `safesphere-client` | **Citizen App (M1)** frontend (`app/`, UI stack TBD) **and** the shared Java backend (`backend/`) ΓÇö M4 Orchestrator, M6 Matcher, Agent Gateway | this repo |
| `safesphere-official-app` | **Professional App (M5)** frontend, `mobile/` (MVP) and `desktop/` (stretch) branches, UI stack TBD | separate repo |

`app/` and `backend/` are separate build targets in this repo ΓÇö they do
not import each other, and `backend/` is deployed as its own process that
both this app and `safesphere-official-app` reach over HTTPS/WSS. Treat
the folder boundary with the same discipline you'd give a repo boundary.

---

## Quick architecture

```
 Citizen App (M1, native, app/)     Professional App (M5, native, other repo)
 Need Help Γöé Help Nearby            Command Desk (mobileΓåÆdesktop) Γöé Field (mobile)
      Γöé           Γöé                          Γöé                    Γöé
      ΓööΓöÇΓöÇΓöÇΓöÇΓöÇΓö¼ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÿ                          ΓööΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓö¼ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÿ
            Γöé  HTTPS (REST) + WSS (push)                Γöé
            Γû╝                                           Γû╝
      ΓöîΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÉ
      Γöé   backend/  (Java 17+ ΓÇö lives here, runs standalone)  Γöé
      Γöé   M4 FSM + AES-256 Evidence Vault                     Γöé
      Γöé   M6 SQLite Capability Matcher                        Γöé
      Γöé   Agent Gateway (Javalin REST + WebSocket)            Γöé
      ΓööΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÿ
```

**Current build phase:** `backend/` only. No frontend UI code should be
written for either app until the UI stack is chosen (`SafeSphere.md` ┬º2).
Wireframing against the existing AI-generated concept screens can continue
in parallel ΓÇö that's design work, not implementation.

See `SafeSphere.md` for the full design doc (data model, module-by-module
design, state machine, survival-mode math, hardware/OS requirements, tech
stack, backend-first sprint plan, and the multi-developer parallel-start
workflow), and `CitizenAppContract.md` / `ProfessionalAppContract.md` for
the frozen wire contract this app builds against.
