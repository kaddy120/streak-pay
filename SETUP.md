# Setup Instructions

## Prerequisites

- Android Studio Arctic Fox (2020.3.1) or later
- JDK 17 or later
- Android SDK with API Level 34

## Initial Setup

1. **Open the project in Android Studio**
   ```
   File -> Open -> Select the project folder
   ```

2. **Generate Gradle Wrapper** (if needed)

   If you don't have gradlew files, run this in the project root:
   ```bash
   gradle wrapper --gradle-version 8.2
   ```

3. **Sync Gradle**

   Android Studio should automatically prompt you to sync. If not:
   ```
   File -> Sync Project with Gradle Files
   ```

4. **Build the project**
   ```
   Build -> Make Project
   ```

5. **Run on device/emulator**
   - Connect an Android device with USB debugging enabled, or
   - Start an Android emulator (API 26 or higher)
   - Click the Run button (green play icon)

## Permissions Required

The app requires the following permissions:
- `FOREGROUND_SERVICE` - For timer service
- `POST_NOTIFICATIONS` - For timer notifications (Android 13+)
- `FOREGROUND_SERVICE_SPECIAL_USE` - For timer foreground service type

These are declared in the AndroidManifest.xml and will be requested at runtime.

## Troubleshooting

### Gradle sync fails
- Ensure you have JDK 17 installed and configured in Android Studio
- Check your internet connection (Gradle needs to download dependencies)
- Try: File -> Invalidate Caches / Restart

### Build errors
- Clean the project: Build -> Clean Project
- Rebuild: Build -> Rebuild Project

### Missing gradlew
If gradlew files are missing, run in terminal:
```bash
gradle wrapper --gradle-version 8.2 --distribution-type bin
```

## Testing

The app requires a physical device or emulator with:
- Android API 26 (Oreo) or higher
- Storage permission for wish item images

## Default Settings

On first launch, the app initializes with:
- User name: "User"
- Daily goal for day job: 7.5 hours
- Daily goal for side work: 4.0 hours
- Current streak: 0 days
- Total points: 0

You can change these settings through the UI once the app is running.
