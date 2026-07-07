# UpsideCharge

UpsideCharge is a small native Android app for flipping a Samsung phone into reverse portrait while it is charging and physically upside down.

It is built for this use case:

- Keep apps in portrait aspect ratio.
- Rotate the whole phone UI to 180 degree portrait.
- Avoid landscape.
- Keep working outside the app through a foreground service.

## Features

- Kotlin Android app with no heavy UI framework.
- Foreground service for background rotation control.
- USB-only or any-charging trigger.
- Filtered accelerometer detection for upside-down portrait.
- Manual "Turn Around Now" button.
- "Restore Normal" button.
- System light/dark appearance.
- Global reverse portrait guard using a tiny overlay window.
- ADB-friendly permission setup.

## Build

```powershell
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

With wireless debugging:

```powershell
adb connect PHONE_IP:PHONE_PORT
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.niko.upsidecharge/.MainActivity
```

Useful permission commands:

```powershell
adb shell appops set com.niko.upsidecharge android:write_settings allow
adb shell appops set com.niko.upsidecharge SYSTEM_ALERT_WINDOW allow
adb shell pm grant com.niko.upsidecharge android.permission.POST_NOTIFICATIONS
adb shell dumpsys deviceidle whitelist +com.niko.upsidecharge
```

## Usage

1. Open UpsideCharge.
2. Grant Modify System Settings.
3. Grant Appear on Top.
4. Enable UpsideCharge.
5. Put the phone vertically upside down while charging.

If normal and upside-down detection are swapped, enable **Invert sensor direction**.

## Package

```text
com.niko.upsidecharge
```

## Notes

Android does not give normal apps perfect global rotation authority. UpsideCharge uses the strongest non-root approach available here: system rotation settings plus a foreground-service overlay guard.
