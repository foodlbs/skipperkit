# Contributing to SkipperKit

Thanks for your interest! SkipperKit is an Android accessibility utility that taps
"Skip Intro / Recap / Next Episode" buttons in streaming apps using only the
Accessibility APIs. This guide covers how to build, test, and contribute.

## Ground rules

- **Accessibility-only.** SkipperKit must never use screen capture, OCR, image/ML
  vision, root, or any analysis of video/DRM content. It interacts solely with the
  accessibility node tree. PRs that cross this line won't be accepted. See the
  [Privacy](README.md#privacy) section for the rationale.
- **Config-driven.** App-specific labels and view-ids live in data
  (`config/DefaultConfigs.kt`), never hardcoded in matching logic.
- **Determinism first.** Keep the hot path (the accessibility callback) cheap and
  deterministic. Heavier/heuristic work belongs off-thread and throttled. We
  researched and **declined** an on-device LLM as unjustified for this use case.

## Building

Requirements: Android Studio (Ladybug+) or JDK 17–21 + the Android SDK (compileSdk 35).
The Gradle wrapper (8.14) is committed.

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew installDebug           # build + install to a connected device
```

## Testing

```bash
./gradlew testDebugUnitTest      # unit tests (no device needed)
./gradlew lintDebug              # Android lint
```

The matching core (`NodeView` / `NodeMatcher` / `TreeSearch` / `SkipEngine`) is
**Android-free on purpose** so it's unit-testable against fake node trees — please
add tests there for any matching/engine/config change. The CI workflow
(`.github/workflows/ci.yml`) runs tests + lint + assemble on every PR.

### Verifying on a device

Streaming apps require Widevine DRM and won't run on an emulator, so end-to-end
changes need a **physical device**. The debug build includes a Node Inspector
(`BuildConfig.DEBUG_NODE_INSPECTOR`) that logs the accessibility tree under Logcat
tag `SkipperKitInspector` — that's how each app's stable identifiers were captured.

## Adding or fixing an app's skip buttons

1. Enable the service, open the app, reach a Skip button.
2. Read the `SkipperKitInspector` dump and find the button's `viewIdResourceName`
   (preferred — stable) and/or visible text.
3. Add/adjust the entry in `config/DefaultConfigs.kt` (view-id first, text fallback).
4. Add a test and verify on device.

Note: end users can already add unsupported apps at runtime via **Settings → Add an
app** (on-device discovery). Code changes are for *built-in* support or engine fixes.

## Pull requests

- Branch from `main`; keep commits small and descriptive (no "WIP").
- Make sure `./gradlew testDebugUnitTest lintDebug assembleDebug` passes.
- Describe what changed, why, and how you tested it (include device + app if relevant).
- By contributing, you agree your contributions are licensed under Apache-2.0.

## Code style

Kotlin official style. Prefer clarity over cleverness. Match the surrounding code.
