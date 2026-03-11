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
- `ai.py` — Calls Gemini (gemini-3-flash) with structured output to parse free text into reminder fields (title, date, time, location)
- `auth/` — `@auth.require_user` decorator for protected routes; salted SHA256 password hashing; rotating token sessions (last 5 tokens kept per user)
- `data/` — Abstract `DataSource` class; `YamlDataSource` persists to YAML files in `NUDGE_STORE_PATH`
- `models/` — `Reminder` (title, description, time, priority, link, etc.), `Config` (email, timezone)
- `actions/` — Email notifications via Gmail App Passwords; snooze logic; priority-based dispatch
- `job/` — Reads pending reminders and fires actions; run via cron `*/10 * * * *`
- `spell/` — Autocorrects typos while preserving capitalization

### Environment Variables
- `GEMINI_API_KEY` — Required for AI reminder creation
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
- Terms of Service (`TermsOfServiceFragment`)
- Privacy Policy (`PrivacyPolicyFragment`)
- Dark mode support
- Calendar pill show/hide toggle
- Reminder cards with various fields

### Legal Screens Pattern
`TermsOfServiceFragment` and `PrivacyPolicyFragment` are built identically: programmatic `ScrollView` + `TextView`, no XML layout. Both are linked from `LoginFragment` (`tosLoginText` TextView) and `SignupFragment` (`tosCheckbox` CheckBox) using `SpannableString` + `ClickableSpan`. Login text: "By logging in you agree to the Terms of Service and Privacy Policy". Signup checkbox: "I agree to the Terms of Service and Privacy Policy". Signup validation requires the checkbox to be checked before account creation proceeds.

### Recent Work (from git log)
- Added Privacy Policy screen; linked from Login and Signup
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
- **Modify AI parsing:** edit `ai.py` (uses Gemini structured outputs)

---

## Android Critical User Journeys (CUJs)

These must remain working after any change. Verify manually or via tests.

### Online Mode
- **CUJ-O1 View list** — App opens, reminders load from network, shimmer shows then disappears, list appears sorted by time
- **CUJ-O2 Create reminder (manual)** — Hamburger → Add Event or empty-state button → fill form → save → list refreshes with new reminder
- **CUJ-O3 Create reminder (AI)** — Type natural language in AI box → parse → pre-fill fields → save → list refreshes
- **CUJ-O4 Edit reminder** — Tap edit on a card → change fields → save → list refreshes with update
- **CUJ-O5 Copy reminder** — Tap copy on a card → opens form pre-filled with same data, empty ID → save creates new entry
- **CUJ-O6 Delete reminder** — Tap delete → confirm dialog → reminder removed from list
- **CUJ-O7 Swipe to refresh** — Pull down → spinner shows → fresh data fetched

### Offline Mode
- **CUJ-F1 Enter offline** — Network fails → "You're Offline" dialog shown once → list shows cached data → status bar says Offline → copy/delete buttons hidden, edit-only
- **CUJ-F2 Create reminder offline** — Hamburger → Add Event (only enabled item) → fill form (no AI box) → save → appears in list with "(Not Backed Up)" prefix
- **CUJ-F3 Edit reminder offline** — Tap edit on existing card → modify → save → appears with "(Not Backed Up)" prefix, original ID preserved
- **CUJ-F4 Stay offline through navigation** — Navigate to ReminderFragment and back; offline mode must persist, no shimmer on return

### Reconnect
- **CUJ-R1 Auto-sync on reconnect** — Next swipe-to-refresh or background resume while online → pending reminders POST'd serially to server → "(Not Backed Up)" entries replaced with server copies

### Navigation
- **CUJ-N1 Abort edit** — Open ReminderFragment, press back without saving → no shimmer, no network call, list unchanged
- **CUJ-N2 Save and return** — Save a reminder → list refreshes once with updated data
- **CUJ-N3 Background resume** — Leave app (home/recents), return → fresh fetch triggered

---

## Building and Installing the Android App (macOS + Android Studio)

### Prerequisites
- Android Studio installed at `/Applications/Android Studio.app`
- `JAVA_HOME` set to the Android Studio bundled JDK in your shell profile:
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```
  This is required because AGP 8.4.1 needs Java 11+, but the system Java may be older.
- A physical Android device connected via USB with USB debugging enabled, **or** an emulator running
- `adb` available at `~/Library/Android/sdk/platform-tools/adb` (add to `PATH` if needed)

### Build and Install

From `client/android/`:

```bash
./gradlew installDebug
```

### Launch the App After Installing

```bash
~/Library/Android/sdk/platform-tools/adb shell am start -n "com.hyunwookshin.nudge/.MainActivity"
```

### One-liner (build, install, and launch)

```bash
./gradlew installDebug && ~/Library/Android/sdk/platform-tools/adb shell am start -n "com.hyunwookshin.nudge/.MainActivity"
```
