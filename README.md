# Work Points Tracker

A personal Android app that gamifies work sessions by converting hours worked into points.

## Features

### MVP 1 (Current)

- **Timer**: Start/Pause/Stop work sessions with foreground service
- **Points System**:
  - Day job hours (Mon-Fri, 9am-3pm): 0.25 pts/hr
  - Side work: 1 pt/hr
  - Early morning (5am-8am): 1.5 pts/hr
  - First hour bonus: +0.5 pts
  - Streak bonuses: 3-day (10%), 7-day (15%), 30-day (20%)
- **Wishlist**: Add items with images, track prices, redeem with points
- **History**: View stats by day/week/month/year with progress bars
- **Streak Tracking**: Automatic streak counting for consecutive work days

## Technical Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM
- **Database**: Room
- **Image Loading**: Coil
- **Background Tasks**: Foreground Service

## Project Structure

```
app/
├── data/
│   ├── local/database/    # Room database, DAOs, converters
│   ├── repository/        # Data repositories
│   └── model/            # Data models
├── domain/
│   ├── usecase/          # Business logic (PointsCalculator, StreakManager)
│   └── service/          # TimerService
├── ui/
│   ├── home/            # Timer and recent sessions
│   ├── wish/            # Wishlist with tabs
│   ├── history/         # Statistics and charts
│   ├── about/           # App info
│   ├── navigation/      # Navigation graph
│   └── theme/           # Compose theme
└── util/                # Utilities (ImageUtils, FormatUtils)
```

## Building the Project

1. Clone the repository
2. Open in Android Studio Arctic Fox or later
3. Sync Gradle
4. Run on Android device/emulator (minSdk 26)

## Key Rules

- Minimum 1 hour required for first session of each day
- Subsequent sessions can be any length
- Only side work counts toward streaks
- Day job hours never receive bonuses
- Weekend work at any time counts as side work

## Future Features (MVP 2+)

- Friend list with points/streaks visibility
- "Working now" online status
- Streak freeze power-ups
- Advanced statistics and charts
- Notifications and reminders
