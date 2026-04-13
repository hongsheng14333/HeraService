# AGENTS.md

This file gives coding agents repository-specific guidance for `D:\AndroidStudioProjects\HeraService`.

## Project Snapshot

- Android application project using Gradle and Android Gradle Plugin `8.1.3`.
- Single module app: `:app`.
- Primary language is Java, not Kotlin.
- App namespace and package: `com.core.heraservice`.
- Compile SDK `34`, target SDK `33`, min SDK `30`.
- Main runtime behavior centers on a foreground/background service, SIM management, SMS sending, WebSocket communication, and voice recognition.
- Source layout is standard Android Gradle layout under `app/src/main/java` and `app/src/main/res`.

## Agent Priorities

- Preserve existing Java-and-Android style unless the user asks for broader refactors.
- Prefer small, targeted edits over large rewrites.
- Treat this as a system-integrated Android app with telephony, SMS, storage, and overlay behavior; avoid speculative permission or manifest changes.
- Be careful with threading, broadcast receivers, and Android lifecycle behavior.
- Do not introduce Kotlin, Compose, or architectural migrations unless explicitly requested.

## Existing Instruction Files

- No repository `AGENTS.md` existed when this file was generated.
- No `.cursorrules` file was found.
- No files were found under `.cursor/rules/`.
- No `.github/copilot-instructions.md` file was found.
- If any of those files are added later, treat them as higher-priority supplements to this document.

## Environment Notes

- Gradle wrapper exists: `gradlew` and `gradlew.bat`.
- Android Gradle Plugin `8.1.3` requires Java 17 to run Gradle tasks.
- In the current environment, Gradle task discovery failed under Java 11, so agents should expect to need JDK 17 for build/test/lint commands.
- If commands fail with an AGP/JDK mismatch, switch `JAVA_HOME` to JDK 17 or set `org.gradle.java.home` locally before retrying.

## Build Commands

Run from repository root: `D:\AndroidStudioProjects\HeraService`.

- Windows wrapper: `./gradlew.bat <task>`
- POSIX wrapper: `./gradlew <task>`

Common commands:

- Build debug APK: `./gradlew.bat assembleDebug`
- Build release APK: `./gradlew.bat assembleRelease`
- Full build: `./gradlew.bat build`
- Clean: `./gradlew.bat clean`
- Install debug APK to connected device: `./gradlew.bat installDebug`

Useful Android-specific verification commands:

- Compile app only: `./gradlew.bat :app:compileDebugJavaWithJavac`
- Package debug app: `./gradlew.bat :app:assembleDebug`
- Run all verification tasks usually wired into build: `./gradlew.bat check`

## Lint Commands

No third-party linting tools such as Checkstyle, Spotless, Detekt, or ktlint were found in Gradle files.

Use Android/Gradle lint tasks:

- Run Android lint for all modules: `./gradlew.bat lint`
- Run lint for app only: `./gradlew.bat :app:lint`
- Run debug lint only: `./gradlew.bat :app:lintDebug`
- Run release lint only: `./gradlew.bat :app:lintRelease`

If an agent only changed Java source and wants a faster sanity check, `:app:compileDebugJavaWithJavac` is often cheaper than a full build.

## Test Commands

This repository currently contains placeholder tests:

- Local unit test: `app/src/test/java/com/core/heraservice/ExampleUnitTest.java`
- Instrumented test: `app/src/androidTest/java/com/core/heraservice/ExampleInstrumentedTest.java`

Run all local JVM unit tests:

- `./gradlew.bat test`
- `./gradlew.bat :app:test`
- `./gradlew.bat :app:testDebugUnitTest`

Run one local unit test class:

- `./gradlew.bat :app:testDebugUnitTest --tests com.core.heraservice.ExampleUnitTest`

Run one local unit test method:

- `./gradlew.bat :app:testDebugUnitTest --tests com.core.heraservice.ExampleUnitTest.addition_isCorrect`

Run all instrumented tests on a connected device/emulator:

- `./gradlew.bat connectedAndroidTest`
- `./gradlew.bat :app:connectedDebugAndroidTest`

Run one instrumented test class:

- `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.core.heraservice.ExampleInstrumentedTest`

Run one instrumented test method:

- `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.core.heraservice.ExampleInstrumentedTest#useAppContext`

## When To Run What

- Small Java-only change: run `:app:compileDebugJavaWithJavac` at minimum.
- Resource, manifest, or Android wiring change: run `:app:assembleDebug` and preferably `:app:lintDebug`.
- Test-only change: run the narrowest relevant `--tests` command.
- Behavior change in telephony/SMS/service code: prefer `:app:assembleDebug`; instrumented verification may also be needed if a device is available.

## Codebase Structure

- Core app entry points: `MainActivity`, `BackgroundTaskService`, `BootReceiver`, `SmsSendReceiver`.
- Networking and protocol objects: `app/src/main/java/com/core/heraservice/network`.
- SIM helpers: `app/src/main/java/com/core/heraservice/sim`.
- SMS workflow: `app/src/main/java/com/core/heraservice/sms`.
- Utilities and device integration: `app/src/main/java/com/core/heraservice/utils`.
- Voice recognition and token/auth support: `app/src/main/java/com/core/heraservice/voice`.

## Style Guidelines

Follow the existing project style first, even where it is imperfect. Keep edits consistent with surrounding files.

### Imports

- Use explicit imports for normal classes.
- Static wildcard imports exist in tests (`import static org.junit.Assert.*;`); for production code, prefer explicit static imports unless the file already uses a wildcard.
- Keep Android and AndroidX imports grouped near the top, followed by third-party imports, then Java imports.
- The existing code does not always maintain perfect import ordering; when editing a file, improve obvious disorder only if the diff stays small.

### Formatting

- Use 4 spaces for indentation.
- Use K&R-style braces as seen in the Java files.
- Keep one statement per line.
- Preserve existing blank-line rhythm; do not aggressively reformat untouched sections.
- Avoid style-only churn.

### Types And APIs

- Prefer Java 8-compatible language features; `sourceCompatibility` and `targetCompatibility` are set to Java 8.
- Do not introduce APIs requiring a higher Java language level.
- Use concrete Android and Java types already common in the codebase: `Handler`, `HandlerThread`, `Intent`, `JSONObject`, `List`, arrays, etc.
- Match existing nullability annotation usage; AndroidX annotations are used sparingly.

### Naming Conventions

- Class names use PascalCase.
- Constants use `UPPER_SNAKE_CASE`.
- Methods generally use lowerCamelCase.
- Fields are mixed style: some use `mPrefix` (`mContext`, `mSmsManager`), some do not. In edited files, follow the local file's style instead of forcing a repo-wide rename.
- Log tags are usually `private static final String TAG = "ClassName"`.

### Error Handling

- Existing code often catches broad `Exception` and logs with `Log.e` or prints stack traces.
- For new code, prefer logging meaningful context with `Log.e(TAG, message, exception)` where possible.
- Do not swallow exceptions silently.
- If a failure can be surfaced to the calling layer or over WebSocket/result objects, do so consistently with existing flow.
- Preserve operational behavior in service and telephony paths; avoid changing failure semantics unless requested.

### Logging

- Use Android `Log` for runtime diagnostics.
- Keep log messages short and action-oriented.
- Do not log secrets, auth tokens, activation codes, access keys, or personally sensitive data.
- This matters here because some current code appears to contain sensitive configuration and verbose diagnostics.

### Concurrency And Lifecycle

- Much of the app uses raw `Thread`, `Handler`, `HandlerThread`, `CountDownLatch`, and callbacks.
- Reuse the existing concurrency model inside a file unless there is a strong reason to refactor.
- Be careful to avoid blocking the main thread.
- When adding asynchronous work, think about receiver/service lifecycle and cleanup.
- Register/unregister receivers and callbacks deliberately; leaking them is easy in Android components.

### Android-Specific Conventions

- Respect SDK gates already present, such as `Build.VERSION.SDK_INT` checks and `@RequiresApi`.
- Check runtime permissions before using protected APIs where the surrounding code expects it.
- Keep manifest changes minimal and justified.
- This app uses foreground service, boot receiver, SMS, telephony, storage, and overlay permissions; avoid changing these casually.

### Networking And Protocol Models

- `DataDef` contains many nested message/data classes used for JSON serialization.
- When extending payloads, preserve field names expected by the server.
- Gson is used in WebSocket code; org.json and fastjson also appear elsewhere. Prefer the serializer already used in the local subsystem.
- Avoid broad protocol refactors unless explicitly requested.

### Testing Expectations

- There is very little real test coverage today.
- If you add pure Java logic, add or extend local unit tests under `app/src/test/java` where feasible.
- For Android-framework-heavy code, document manual verification steps if automated coverage is impractical.

## Repository-Specific Cautions

- `app/build.gradle` contains hardcoded signing credentials and references `platform.jks`.
- `SpeechRecognize.java` and related code appear to include hardcoded external-service credentials.
- `CommonConstant.java` contains fixed server endpoints and persistent file paths.
- Agents should not expose, duplicate, rotate, or casually modify these values unless the user explicitly asks.
- Avoid committing new secrets or moving existing secret-like values into more places.

## Preferred Change Strategy

- Read the local file first and mimic its conventions.
- Change the smallest set of files necessary.
- If behavior is sensitive, add guard rails rather than broad rewrites.
- Verify with the narrowest useful Gradle command.
- If verification is blocked by missing JDK 17 or missing device/emulator, say so clearly in your final report.

## Final Checklist For Agents

- Confirm whether the change touches service startup, telephony, SMS, WebSocket, or voice code.
- Run the smallest relevant compile/build/test command.
- Mention any environment blockers, especially Java 17 requirements.
- Call out manual verification needs for device-only behavior.
- Keep diffs focused and avoid unrelated cleanup.
