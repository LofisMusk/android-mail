# Google-free build (F-Droid)

This fork removes every proprietary Google component so the app runs on de-Googled Android
(GrapheneOS, CalyxOS, LineageOS without GApps, /e/OS, …) and can be published on F-Droid.

## What changed

| Area | Before | Now |
|---|---|---|
| New-mail notifications | Firebase Cloud Messaging (FCM) push, device token registered with Proton | **Native fetch**: the Rust core's local notification engine (`MailUserSession.watchNotifications()`) plus a periodic WorkManager job that forces an event-loop poll |
| `google-services` Gradle plugin | applied, needed `google-services.json` | removed, no config file needed |
| In-app review | Google Play In-App Review API | removed (no-op) |
| Install referrer | Google Play Install Referrer API | removed (no-op data source) |
| In-app purchases | Google Play Billing (`billing-ktx`, `payment-billing-google`) | removed; Play Services always reported as missing, so the store purchase UI is never offered. Plans can be bought on the web. |
| Security key (FIDO2) 2FA | `auth-fido-play` → `play-services-fido` | removed; the binding is optional so TOTP 2FA keeps working, security keys are unavailable |
| `play-services-base` | linked | removed |
| Play Integrity / SafetyNet | not used | not used (verified with `./gradlew :app:dependencies`) |

`./gradlew :app:dependencies --configuration prodReleaseRuntimeClasspath` contains no
`com.google.android.gms`, `com.google.firebase`, `com.google.android.play`,
`com.android.billingclient` or `com.android.installreferrer` artifact.

## How native fetch works

* `NativeNotificationsWatcher` attaches one `watchNotifications()` stream per logged-in (`Ready`) account.
  The Rust core emits `NewMessage` for new mail in the Inbox / notify-enabled folders and `Dismiss` when a
  message is read elsewhere. It already filters old/read mail and de-duplicates, so the watcher only maps the
  events onto the existing notification builders (same look, same quick actions: archive, trash, mark read).
* While the app process is alive the Rust event loop polls by itself, so notifications arrive quickly.
* `NativeFetchWorker` runs every 15 minutes (the WorkManager minimum) when a network is available. It opens a
  Rust background execution scope, calls `forceEventLoopPollAndWait()` for every account and keeps the scope
  open a few seconds so the stream can deliver the events.
* Both are started by `NativeNotificationsLifecycleObserver` at process start; WorkManager keeps the periodic
  job across reboots.

Delivery is therefore "within ~15 minutes" when the app is in the background. Android may delay periodic
jobs further in Doze / battery saver; exempting the app from battery optimisation makes it more regular.

## Building

```
./gradlew assembleProdRelease
```

No `google-services.json` is required anymore.

## Remaining blockers for the official F-Droid repository

F-Droid builds everything from source. These dependencies are still consumed as prebuilt binaries from
Maven Central and would have to be built from source (or accepted by the F-Droid maintainers) before an
inclusion request in `fdroiddata`:

* `me.proton.mail.common:lib` — the Rust mail core (native `.so`, GPLv3, source in Proton's monorepo).
* `me.proton.android.payment:payment-core` — Rust payment core (native `.so`).
* Other `me.proton.core:*` libraries (FOSS, built from `external/clients-monorepo` with
  `-PbuildFromSource=true`).
* Sentry (`io.sentry:*`, MIT) is linked but only active when `SENTRY_DSN_MAIL` / `SENTRY_DSN_ACCOUNT` are
  set at build time; an F-Droid build leaves them empty. F-Droid may still flag it with the `Tracking`
  anti-feature unless it is removed completely.

The app can already be distributed through a self-hosted F-Droid repository, Obtainium or as a plain APK.
