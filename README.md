# Work Points Tracker

A cross-platform system that gamifies work sessions by converting hours worked into points. Tracks sessions automatically on your Linux PC (based on active apps and idle detection) and manually on Android, with a shared cloud database.

## Architecture

```
┌──────────────┐                                         ┌──────────────┐
│ Android App  │────► REST API (Spring Boot/Kotlin) ◄────│ PC Daemon    │
│ (Kotlin)     │      deployed on AWS                    │ (Kotlin/JVM) │
└──────────────┘               │                         │ + Web UI     │
                      ┌────────▼─────────┐               └──────────────┘
                      │  AWS RDS         │
                      │  (PostgreSQL)    │
                      └──────────────────┘
```

Both clients share a single cloud database via the REST API — no sync logic needed.

## Features

- **Auto-tracking on PC**: Sessions start/stop automatically based on which apps are open
- **Auto-pause on idle**: Pauses after 5 min idle, ends after 30 min paused
- **Points system**:
  - Day job hours (Mon-Fri, 9am-4pm): 0.25 pts/hr
  - Side work: 1 pt/hr
  - Early morning (5am-8am): 1.5 pts/hr
  - First hour bonus: +0.5 pts
  - Streak bonuses: 3-day (10%), 7-day (15%), 30-day (20%)
- **Wishlist**: Add items with prices, track progress, redeem with points
- **History**: View stats by day/week/month/year
- **Streak tracking**: Automatic streak counting with 36-hour grace period
- **Badges**: 12 achievement badges (Early Bird, Marathon Runner, etc.)
- **Web dashboard**: Live status at `http://127.0.0.1:8742`

## Project Structure

```
streak-pay/
├── app/                  # Android app (Kotlin/Jetpack Compose)
├── shared/               # Shared business logic (platform-agnostic Kotlin)
│   └── src/main/kotlin/
│       ├── PointsCalculator.kt
│       ├── StreakManager.kt
│       ├── BadgeCalculator.kt
│       ├── FormatUtils.kt
│       └── models/       # Plain data classes
├── api/                  # Spring Boot REST API
│   └── src/main/kotlin/
│       ├── controller/   # REST endpoints
│       ├── entity/       # JPA entities
│       ├── repository/   # Spring Data repositories
│       ├── service/      # Business logic services
│       └── config/       # Security, CORS
├── pc-client/            # Linux PC daemon + web dashboard
│   └── src/main/kotlin/
│       ├── daemon/       # WindowMonitor, IdleDetector, SessionManager
│       ├── api/          # HTTP client for REST API
│       ├── web/          # Ktor web dashboard
│       └── config/       # YAML config loading
├── docker-compose.yml    # PostgreSQL + API for local dev
└── build.gradle.kts      # Multi-module Gradle build
```

## Quick Start

### Prerequisites

- JDK 17+ (JDK 21 recommended)
- Docker & Docker Compose (for local database)

### 1. Start the API locally

```bash
docker compose up -d
```

This starts PostgreSQL and the Spring Boot API on `http://localhost:8080`.

### 2. Run the PC daemon

```bash
# Build
./gradlew :pc-client:shadowJar

# Create config
mkdir -p ~/.config/workpointsd
cp pc-client/config.example.yaml ~/.config/workpointsd/config.yaml

# Run
java -jar pc-client/build/libs/workpointsd.jar
```

Or use the installer for systemd auto-start:

```bash
./pc-client/install.sh
systemctl --user start workpointsd
```

Dashboard: `http://127.0.0.1:8742`

### 3. Android app

Open in Android Studio, set `API_BASE_URL` and `API_KEY` in `local.properties`, then build and run.

### CLI options

```
workpointsd --help              # Show usage
workpointsd --status            # Print today's stats and exit
workpointsd --config <path>     # Use custom config file
```

## Configuration

Edit `~/.config/workpointsd/config.yaml`:

```yaml
api:
  base_url: "http://localhost:8080"
  api_key: "dev-api-key-change-in-production"

poll_interval_seconds: 2
idle_timeout_seconds: 300          # 5 min idle → auto-pause
end_after_paused_seconds: 1800     # 30 min paused → end session
non_tracked_grace_seconds: 120     # 2 min on non-tracked app → pause

web_ui:
  enabled: true
  port: 8742

tracked_apps:
  - name: "VS Code"
    wm_class: "code"
  - name: "IntelliJ IDEA"
    wm_class: "jetbrains-idea"
  - name: "Terminal (GNOME)"
    wm_class: "Gnome-terminal"
  # ... see config.example.yaml for full list
```

### Configuring Tracked Apps

The daemon auto-starts a session when a tracked app is in focus, and auto-pauses when you switch away. Each entry supports two matching strategies:

**`wm_class`** — matches the window's X11 WM_CLASS (exact match, case-insensitive). Use this for native desktop apps:

```yaml
- name: "VS Code"
  wm_class: "code"
```

**`title_contains`** — matches a substring in the window title (case-insensitive). Use this for PWAs or browser-based apps where the WM_CLASS is just the browser:

```yaml
- name: "Microsoft Teams"
  title_contains: "Microsoft Teams"
- name: "Outlook"
  title_contains: "Outlook"
```

Both fields can be set on a single entry — the window matches if **either** condition is true.

#### Finding the WM_CLASS for your apps

Open the app you want to track, then run:

```bash
# Get the active window's WM_CLASS
xprop -id $(xdotool getactivewindow) WM_CLASS
```

This prints something like:

```
WM_CLASS(STRING) = "gnome-terminal-server", "Gnome-terminal"
```

Use the **second** quoted value (the class name) in your config. For PWAs running in Chrome/Brave/Edge, the class is usually the browser name — use `title_contains` instead to distinguish different PWAs.

#### Default tracked apps

If no `tracked_apps` section is in the config, these defaults are used:

| App | Match type | Value |
|-----|-----------|-------|
| VS Code | wm_class | `code` |
| IntelliJ IDEA | wm_class | `jetbrains-idea` |
| PyCharm | wm_class | `jetbrains-pycharm` |
| Android Studio | wm_class | `jetbrains-studio` |
| Microsoft Teams | wm_class | `teams-for-linux` |
| Microsoft Teams (PWA) | title_contains | `Microsoft Teams` |
| Outlook (PWA) | title_contains | `Outlook` |
| Slack | wm_class | `slack` |
| Zoom | wm_class | `zoom` |
| Obsidian | wm_class | `obsidian` |
| Notion | title_contains | `Notion` |
| Terminal (GNOME) | wm_class | `Gnome-terminal` |
| Kitty | wm_class | `kitty` |
| Alacritty | wm_class | `Alacritty` |
| Firefox | wm_class | `firefox` |
| Chrome | wm_class | `google-chrome` |

## API Endpoints

All endpoints require `X-API-Key` header.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/sessions` | Create session |
| PUT | `/api/sessions/{id}` | Update session (pause/resume/end) |
| GET | `/api/sessions` | List sessions (optional `startDate`/`endDate`) |
| GET | `/api/sessions/{id}` | Get session |
| DELETE | `/api/sessions/{id}` | Delete session |
| GET | `/api/stats/today` | Today's summary |
| GET | `/api/stats/points` | Total points |
| GET | `/api/stats/streak` | Streak info |
| GET | `/api/settings` | App settings |
| PUT | `/api/settings` | Update settings |
| GET | `/api/goals` | Daily goals |
| PUT | `/api/goals` | Update goals |
| GET | `/api/wishlist` | List wish items |
| POST | `/api/wishlist` | Add wish item |
| PUT | `/api/wishlist/{id}` | Update wish item |

## PC Daemon State Machine

```
IDLE ──── tracked app active + not idle ────► ACTIVE
                                                │
                              idle 5min ◄───────┤
                        or non-tracked 2min     │
                                │               │
                                ▼               │
                             PAUSED ────────────┘
                                │     activity + tracked app
                                │
                         paused 30min
                                │
                                ▼
                     END (< 15min → discard)
```

## Technical Stack

| Component | Technology |
|-----------|-----------|
| Shared logic | Kotlin (JVM) |
| Android app | Kotlin, Jetpack Compose, Room, Material3 |
| REST API | Spring Boot, Spring Data JPA, PostgreSQL |
| PC daemon | Kotlin/JVM, OkHttp, Ktor (web dashboard) |
| Window detection | xdotool + xprop (X11), gdbus (GNOME Wayland), swaymsg, hyprctl |
| Idle detection | xprintidle (X11), GNOME Mutter IdleMonitor |
| Build | Gradle multi-module, Shadow JAR |

## Building

```bash
# All modules
./gradlew build

# Individual modules
./gradlew :shared:build
./gradlew :api:bootJar
./gradlew :pc-client:shadowJar
```

## Deploying

### API (Docker)

Build the boot JAR, rebuild the Docker image, and restart the container:

```bash
./gradlew :api:bootJar
docker compose up -d --build api
```

Verify it's running:

```bash
docker compose ps
```

### PC Daemon (systemd)

Build the shadow JAR, copy it to the install location, and restart the service:

```bash
./gradlew :pc-client:shadowJar
cp pc-client/build/libs/workpointsd.jar ~/.local/lib/workpointsd.jar
systemctl --user restart workpointsd
```

Verify it's running:

```bash
systemctl --user status workpointsd
```

View logs:

```bash
journalctl --user -u workpointsd -f
```

### Both at once

```bash
./gradlew :api:bootJar :pc-client:shadowJar
docker compose up -d --build api
cp pc-client/build/libs/workpointsd.jar ~/.local/lib/workpointsd.jar
systemctl --user restart workpointsd
```

## Key Rules

- Sessions under 15 minutes are discarded
- Only side work and early morning hours count toward streaks
- Day job hours never receive streak bonuses
- Weekend work at any time counts as side work
- Streak requires 60+ qualifying minutes per day
- Grace period: 36 hours between work days before streak breaks

## Roadmap

### Input Activity Tracking
Replace the binary "idle or not" detection with per-app input activity monitoring. Instead of just checking `xprintidle`, track event types (keystrokes, clicks, scrolls, mouse movement) per focused window every poll interval.

**Why**: The current idle detector can't distinguish "actively coding in IntelliJ" from "IntelliJ focused while scrolling phone." Input activity tracking solves this by measuring *what kind* of input is happening and *how much*.

**Activity signals**:
| Input | Signal | Example |
|-------|--------|---------|
| Keystrokes | Strong | Coding, writing messages |
| Mouse clicks | Strong | Navigating code, reviewing PRs |
| Mouse scroll | Medium | Reading code/docs, browsing chat |
| Mouse movement only | Weak | Possibly distracted |

**What this enables**:
- **App-to-session-type mapping** — Assign session types per app (e.g., Teams/Outlook → DAY_JOB, IntelliJ → SIDE_WORK) instead of relying purely on time of day. Activity tracking makes this meaningful because it proves the app is actually being used.
- **Multi-app tracking per session** — Record which apps were actively used during a session and for how long (e.g., "45min IntelliJ, 15min Terminal, 10min Chrome"). Stored as a per-app breakdown in the session.
- **Smarter auto-pause** — Pause when input activity drops below a threshold (e.g., <1 click/scroll per 30s) even if the tracked app is focused, rather than waiting for full idle timeout.
- **Title exclude patterns** — Track browsers but exclude non-work titles (YouTube, Netflix, Reddit). Combined with activity data, gives accurate work-vs-browsing time.
- **Activity insights** — Surface productivity patterns in the Android app and web dashboard (activity heatmaps, per-app time breakdowns).

**Implementation approach**: Poll `/dev/input` or `xinput` to categorize input events by type, paired with the focused window from `xprop`. Store `{window, keystrokes, clicks, scrolls, movement}` per poll interval. On Wayland, fall back to compositor-level idle signals (GNOME Mutter IdleMonitor).

### Goal Progress Notifications
Push notifications on Android when approaching wishlist item milestones ("You're 80% of the way to that new keyboard!") or daily goals ("15 more minutes to hit today's target"). Configurable thresholds. Keeps motivation high without needing to open the app.

### Weekly/Monthly Reports
Auto-generated summary screens in the Android app: total hours worked, points earned, streak status, busiest days, most-used apps (once activity tracking lands), points-per-week trend. Could also support email digest via the API.

### Session Notes and Tags
Add a short note or tags to any session from the Android app or web dashboard. Examples: "Fixed auth bug", "#feature", "#meeting", "#bugfix". Makes session history useful for time reporting, retrospectives, or filtering by project.

### Time Blocking / Scheduled Sessions
Define recurring work blocks in the config or Android app (e.g., "Mon-Fri 6:00-7:30 = early morning coding"). The app sends reminders to start, and the dashboard shows planned vs actual hours. Helps build consistent habits.

### Break Reminders
After a configurable duration of continuous work (e.g., 90 minutes), nudge the user to take a break via Android notification or web dashboard alert. Resets when the session is paused or the user acknowledges. Supports Pomodoro-style workflows.

### Points Decay
Optional mechanic: points slowly decay if you skip days (separate from streak breaking). Creates gentle ongoing motivation beyond the streak system. Configurable decay rate and grace period. Off by default — opt-in for users who want extra pressure.

### Timesheet Export
Export session history as CSV from the web dashboard or Android app. Include date, start/end times, duration, app used, session type, tags, and points. Future integration targets: Toggl, Clockify, Jira time logging.

### Multi-User / Leaderboard
Add user accounts to the API and support a shared leaderboard comparing points, streaks, and hours. Household or team competition. Requires auth (API keys per user or OAuth), a users table, and a leaderboard API endpoint + UI in both clients.

### Android Home Screen Widget
A glanceable widget showing current streak, today's points, and active session timer (with pause/resume controls). Quick access without opening the app. Uses Android's RemoteViews or Glance (Jetpack Compose widgets).

### Customization and Theming
- Dark/light theme toggle in the Android app
- Custom point currency name ("coins", "gems", "credits")
- Wishlist item images (upload or URL)
- User avatar/profile
- Configurable color accents

Small personalization features that make the app feel more like a game and less like a time tracker.

### Resilient Sync (Offline Queue + Retry)
Currently Android uses fire-and-forget API calls — if the API is down, session data is lost. Add an offline queue that stores pending API operations in Room and retries them when connectivity is restored. Include a sync status indicator in the UI so the user knows if sessions are pending upload. Also add retry with exponential backoff for transient failures.

### Multi-User Support
The current system assumes a single user (one API key, no user table, shared state). Add a users table, per-user API keys or OAuth authentication, and scope all sessions/points/streaks/wishlist to a user ID. This is a prerequisite for the leaderboard feature and would also allow household/team usage without data collision.

### Points Economy Balancing
Points accumulate indefinitely with no feedback mechanism to adjust rates. Add admin controls to tune point rates without breaking historical data: a settings screen to adjust multipliers per session type, a "recalculate" option that retroactively applies new rates, and optional point caps or diminishing returns for very long sessions to prevent gaming.

### Android Auto-Tracking
The app's best feature (auto-detection) only works on PC. Explore Android equivalents: Android's UsageStatsManager can report which app is in the foreground, AccessibilityService can detect app switches in real-time, and the existing idle detection could use screen on/off state. This would let the Android app auto-start sessions when you open tracked apps (e.g., a mobile IDE, Slack, or Jira) without manual start/stop.
