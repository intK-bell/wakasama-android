# Dependency / SDK Memo

Last updated: 2026-02-20

## SDK / Toolchain
- AGP: `8.13.2` (`android/build.gradle.kts`)
- Kotlin plugin: `2.0.21` (`android/build.gradle.kts`)
- KSP plugin: `2.0.21-1.0.28` (`android/build.gradle.kts`)
- compileSdk: `36` (`android/app/build.gradle.kts`)
- targetSdk: `36` (`android/app/build.gradle.kts`)
- minSdk: `26` (`android/app/build.gradle.kts`)
- Java target: `17`

## Secret/config handling
- `X-App-Token` is sent by `AuthInterceptor` as request header.
- Token source is `BuildConfig.APP_TOKEN` (not Settings UI input).
- Configure token via Gradle property:
  - `~/.gradle/gradle.properties` (recommended local secret)
  - Example: `APP_TOKEN=xxxxxx`

## App dependencies (current)
- `androidx.core:core-ktx:1.17.0`
- `androidx.appcompat:appcompat:1.7.1`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.10.0`
- `com.google.android.material:material:1.13.0`
- `androidx.constraintlayout:constraintlayout:2.2.1`
- `androidx.work:work-runtime-ktx:2.11.1`
- `androidx.room:room-runtime:2.8.4`
- `androidx.room:room-ktx:2.8.4`
- `androidx.room:room-compiler:2.8.4` (via KSP)
- `com.squareup.retrofit2:retrofit:2.11.0`
- `com.squareup.retrofit2:converter-moshi:2.11.0`
- `com.squareup.okhttp3:okhttp:4.12.0`
- `com.squareup.moshi:moshi-kotlin:1.15.2`

## Important decisions
- `kapt` -> `ksp` migrated for Room codegen.
- `retrofit 3.x` / `okhttp 5.x` are **not** used now:
  - This project's current Kotlin/AGP combo showed metadata incompatibility in this workspace when trying those versions.
  - Keep using `retrofit 2.11.0` + `okhttp 4.12.0` until Kotlin/AGP upgrade is planned as a set.
- Lint `NewerVersionAvailable` is disabled in app module to avoid noisy false-priority upgrades.

## Upgrade policy
- Network stack major upgrade (`retrofit`/`okhttp`) must be done together with:
  - Kotlin plugin version review
  - AGP compatibility check
  - Full `assembleDebug`, `lintDebug`, and internal test track smoke test

## Commands used for verification
- `./gradlew clean assembleDebug lintDebug`
- `./gradlew :app:dependencies --configuration debugCompileClasspath`

## Emulator network/DNS troubleshooting memo
- Symptom seen on 2026-02-20:
  - App log: `Unable to resolve host "guh3h3lma1.execute-api.ap-northeast-1.amazonaws.com": No address associated with hostname`
  - Impact: submit API fails, so email is not sent.
- Quick checks:
  - `~/Library/Android/sdk/platform-tools/adb devices -l`
  - `~/Library/Android/sdk/platform-tools/adb shell ping -c 1 8.8.8.8`
- If `no devices/emulators found`:
  - Start emulator first, then re-check `adb devices -l`.
- If network/DNS fails on emulator:
  - Cold boot emulator from AVD Manager.
  - Restart adb:
    - `~/Library/Android/sdk/platform-tools/adb kill-server`
    - `~/Library/Android/sdk/platform-tools/adb start-server`
  - Start emulator with explicit DNS:
    - `~/Library/Android/sdk/emulator/emulator -avd <AVD_NAME> -dns-server 8.8.8.8,1.1.1.1`
- Successful state example:
  - `adb devices` shows `emulator-5554 device`
  - `ping 8.8.8.8` succeeds
  - Submit flow returns success and email is delivered.
