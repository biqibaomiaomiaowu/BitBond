# BitBond Android Shell

This directory contains the native Android shell for the BitBond app.

## Open

Open the `android` directory in Android Studio.

## Build

From this directory, run:

```powershell
gradle :app:assembleDebug
```

Or use the project wrapper:

```powershell
.\gradlew.bat :app:assembleDebug
```

The wrapper downloads Gradle from `services.gradle.org` on first use. If that network request times out, the globally installed `gradle` command can still build the project.

The detected SDK platform is `android-36.1`, so the app is configured with Android 16 QPR2 `compileSdk` syntax and Android Gradle Plugin 8.13.0.

## Scope

D1 is intentionally limited to a minimal Java native `Activity`. WebView, JSBridge, Supabase, and Usage Access integration are deferred to later milestones.
