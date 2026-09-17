# MotoHub Android v4 FULL

Android WebView wrapper for https://motohub.relaxdev.ru/

Included:
- status/navigation bar safe insets
- animated red/black MotoHub splash
- WebView cookies and DOM storage
- file upload support
- camera permission
- Android 13+ notification permission and notification channel
- native settings screen
- offline/retry screen
- native settings button injected into the MotoHub page
- GitHub Actions APK build without gradlew
- version 1.1.0

Important:
- Real remote push notifications require a backend/Web Push or Firebase integration. This build provides the Android notification channel and a JavaScript bridge (`MotoHub.notify(title, body)`) for local notifications.
- The GitHub release update check only notifies when a newer GitHub Release tag exists; it does not silently install APKs.
