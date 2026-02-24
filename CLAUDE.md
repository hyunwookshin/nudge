# Nudge — CLAUDE.md

## What This Project Is

A multi-platform reminder/notification app with:
- Python Flask backend
- Android client (Kotlin + Jetpack Compose)
- Web client (vanilla HTML/JS)

Users create reminders manually or via natural language (AI-powered). The backend dispatches notifications on a cron schedule via email or SMS.

---

## Directory Structure

```
nudge/
├── server.py          # Flask REST API entry point
├── ai.py              # OpenAI GPT-4 mini integration (NL → structured reminder)
├── auth/              # Token auth: login, signup, password hashing, decorators
├── data/              # DataSource abstraction (YamlDataSource, FakeDataSource)
├── models/            # Reminder and Config dataclasses
├── actions/           # Notification channels: email, snooze, priority handling
├── job/               # Background cron job runner (fires every 10 min)
├── spell/             # Autocorrect utility for reminder text
├── store/             # YAML data files (runtime data, not committed)
├── client/
│   ├── android/       # Kotlin/Compose Android app (minSdk 31, targetSdk 34)
│   └── web/           # HTML/JS web UI
├── systemd/           # Systemd service configs for deployment
├── Makefile           # Build/run automation
└── README.md
```

---

## Backend (Python / Flask)

### Key Files
- `server.py` — All REST routes: `/login`, `/reminders`, `/add_reminder`, `/add_reminder_ai`, etc.
- `ai.py` — Calls OpenAI with structured output to parse free text into reminder fields (title, date, time, location)
- `auth/` — `@auth.require_user` decorator for protected routes; salted SHA256 password hashing; rotating token sessions (last 5 tokens kept per user)
- `data/` — Abstract `DataSource` class; `YamlDataSource` persists to YAML files in `NUDGE_STORE_PATH`
- `models/` — `Reminder` (title, description, time, priority, link, etc.), `Config` (email, timezone)
- `actions/` — Email notifications via Gmail App Passwords; snooze logic; priority-based dispatch
- `job/` — Reads pending reminders and fires actions; run via cron `*/10 * * * *`
- `spell/` — Autocorrects typos while preserving capitalization

### Environment Variables
- `OPENAI_API_KEY` — Required for AI reminder creation
- `NUDGE_STORE_PATH` — Directory for YAML data files
- `NUDGE_SECURE_KEY_PATH` — Optional path for encrypted data store key

### Deployment
```
Nginx (SSL, port 443) → Flask via Waitress (port 5000)
Cron job → job/ runner → email/SMS actions
```

---

## Android Client (Kotlin)

**Location:** `client/android/`

### Stack
- Language: Kotlin
- UI: Jetpack Compose + Material Design 3
- Networking: Retrofit2 (REST calls to backend)
- Local DB: Room (offline persistence)
- Build: Gradle (Kotlin DSL), minSdk 31, targetSdk 34

### Key Screens / Features
- Login / Signup
- Reminder list, detail, creation
- Snooze
- Terms of Service
- Dark mode support
- Calendar pill show/hide toggle
- Reminder cards with various fields

### Recent Work (from git log)
- Fixed fields in reminder cards
- Fixed checkbox bug in ToS
- Updated ToS copy
- Added Terms of Service screen
- Show/hide calendar pill

---

## Web Client

**Location:** `client/web/`

Vanilla HTML/CSS/JS. Calls the same Flask REST API. Minimal interface, secondary to the Android app.

---

## Architecture Summary

```
Android / Web Client
    ↓ HTTPS (Nginx reverse proxy)
Flask Server
    ├── @auth.require_user (token auth)
    ├── DataSource → YAML persistence
    └── ai.py → OpenAI → structured reminder fields

Cron (*/10 min)
    └── job/ → actions/ → email / SMS
```

---

## Data Model: Reminder

Key fields: `title`, `description`, `time` (datetime), `priority`, `link`, `closed` (bool), `snoozed_until`

---

## Auth Flow

1. User submits password → salted + SHA256 hashed
2. Server generates a session token (SHA256), stores last 5 per user
3. Client sends token in subsequent requests; `@auth.require_user` validates it

---

## Common Tasks

- **Run server locally:** see `Makefile` for targets
- **Add a new API endpoint:** edit `server.py`, protect with `@auth.require_user` if needed
- **Change reminder fields:** update `models/`, propagate to `data/`, Android UI, and API
- **Change notification behavior:** edit `actions/`
- **Modify AI parsing:** edit `ai.py` (uses OpenAI structured outputs)
