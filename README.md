# SkipperKit

[![CI](https://github.com/foodlbs/skipperkit/actions/workflows/ci.yml/badge.svg)](https://github.com/foodlbs/skipperkit/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![Platform: Android](https://img.shields.io/badge/Platform-Android%2024%2B-3DDC84.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg)

An Android app that automatically taps buttons you've approved, so you don't have
to — using only the Android **Accessibility** APIs. Its flagship use is streaming:
**Skip Intro**, **Skip Recap**, and **Next Episode** in Netflix, Prime Video,
Disney+, Crunchyroll, HBO Max, Hulu, Paramount+, Peacock, and Apple TV. The same
teach-and-approve mechanism works for other repetitive buttons in apps you
explicitly add — and taught apps can be exported and shared as small JSON files.

It behaves exactly like a user reaching over and tapping the button the app
already shows. It never looks at video, never captures the screen, and never
touches DRM-protected content.

---

## How it works (and why this approach)

A streaming app exposes its on-screen controls — including the "Skip Intro" button —
as nodes in an **accessibility tree**, the same data a screen reader uses. SkipperKit
runs an `AccessibilityService` that:

1. wakes only for the supported packages (scoped in the service config),
2. searches the current window's node tree for a configured target,
3. walks from the matched node up to the first **clickable** ancestor,
4. performs `ACTION_CLICK` (falling back to a synthesized tap gesture if no
   clickable ancestor exists),
5. debounces so a burst of UI updates produces at most one tap.

### Why Accessibility APIs are sufficient

The buttons we care about are **real, interactive UI controls**. The OS already
publishes their text, content-description, view id, bounds, and clickability through
the accessibility tree, and lets an authorized service activate them with
`ACTION_CLICK`. That is everything needed to "press Skip Intro." No pixels required.

On real devices each app even exposes a **stable identifier** on the button:

| App | Toolkit | Skip-button identifier |
|---|---|---|
| Netflix | Jetpack Compose | `skipCreditsButtonTestTag` (testTag) |
| Prime Video | Compose | `test_tag:…:contextualButton_skip_intro_Button` |
| Disney+ | native views | `com.disney.disneyplus:id/skipIntro` |

These ids are locale-independent, so matching is robust across languages. Visible
text (e.g. `SKIP INTRO`) is kept only as a case-insensitive fallback.

### Why screen capture / OCR / ML vision are intentionally avoided

- **Unnecessary.** The button is already a structured, clickable node. Reading pixels
  to find something the OS hands us as text would be strictly worse.
- **Privacy-invasive.** `MediaProjection`/screenshots would expose the entire screen
  (including everything else on it) to the app. The accessibility tree exposes only
  UI semantics for the foregrounded supported app.
- **Blocked anyway.** Streaming playback surfaces are DRM-protected; screen capture of
  them yields black frames. Any pixel-based approach would fail precisely where it'd
  be needed.
- **Heavy and non-deterministic.** OCR/ML inference on a hot callback path would drain
  battery and could mis-click. A string/id match is deterministic and cheap.

**SkipperKit therefore uses no `MediaProjection`, no screenshots, no OCR, no image
recognition, no ML vision, no root, and no ADB automation.**

---

## Supported apps

Built-ins come in two tiers. **Verified** apps match on stable view-ids captured
from a real device. **Label-only** apps match on visible button text — functional,
but a redesign or a non-English locale can break them until someone captures their
view-ids (contributions welcome, see [CONTRIBUTING.md](CONTRIBUTING.md)).

| Service | Package | Tier |
|---|---|---|
| Netflix | `com.netflix.mediaclient` | ✅ Verified |
| Prime Video | `com.amazon.avod.thirdpartyclient` | ✅ Verified |
| Disney+ | `com.disney.disneyplus` | ✅ Verified |
| Crunchyroll | `com.crunchyroll.crunchyroid` | 🏷️ Label-only |
| HBO Max | `com.wbd.stream` | 🏷️ Label-only |
| Hulu | `com.hulu.plus` | 🏷️ Label-only |
| Paramount+ | `com.cbs.app` | 🏷️ Label-only |
| Peacock | `com.peacocktv.peacockandroid` | 🏷️ Label-only |
| Apple TV | `com.apple.atve.androidtv.appletv` | 🏷️ Label-only |

You can also **add other apps** yourself (Settings → *Add an app*). The accessibility
service only ever observes the built-ins above **plus the apps you explicitly add** —
never anything else. Added apps are read the same way (accessibility text only, no
capture), discovery proposes their skip buttons for you to approve, and you can remove
any of them at any time.

### TV is explicitly **not** supported

> TV applications generally expose little or no accessible node tree, making reliable
> accessibility automation impossible.

So the Fire TV / Android TV / `com.netflix.ninja` variants are out of scope by design.
SkipperKit targets phone/tablet builds only.

---

## Install

### Option 1 — prebuilt APK (recommended)

Download `skipperkit-<version>.apk` from the
[**Releases**](https://github.com/foodlbs/skipperkit/releases) page (a `.sha256`
checksum is attached to each release) and sideload it:

```bash
adb install -r skipperkit-v0.1.0.apk
# or just open the APK on the phone and confirm the install prompt
```

> **Why not the Play Store?** Google Play restricts the AccessibilityService API
> to apps whose core purpose is assisting users with disabilities, so utilities
> like this one are distributed as direct APKs instead.

### Option 2 — build from source

Requirements: Android Studio (Ladybug+) or a JDK 17–21 and the Android SDK
(compileSdk 35). Kotlin 2.0, AGP 8.7, Gradle 8.14 (wrapper included).

```bash
# from the project root
./gradlew assembleDebug          # build the APK
./gradlew installDebug           # build + install to a connected device
# APK path: app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and Run. (First sync generates the Gradle
wrapper jar if it isn't present.)

To sideload an existing APK manually:

```bash
adb install -r app-debug.apk
```

### Building a signed release

Release signing reads from a **gitignored** `keystore.properties` at the repo root —
no keystore or secret is ever committed. Create a keystore and the properties file:

```bash
keytool -genkey -v -keystore skipperkit-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias skipperkit
```

```properties
# keystore.properties (do not commit)
storeFile=skipperkit-release.jks
storePassword=********
keyAlias=skipperkit
keyPassword=********
```

Then `./gradlew assembleRelease`. If `keystore.properties` is absent, the
release APK is simply built unsigned.

### Continuous integration & releases

`.github/workflows/ci.yml` runs `testDebugUnitTest`, `lintDebug`, and `assembleDebug`
on every push to `main` and on pull requests. Pushing a `v*` tag runs
`.github/workflows/release.yml`, which builds a **signed** APK (keystore supplied via
repository secrets, never committed) and attaches it to a GitHub Release.

---

## Accessibility setup (required, one time)

An accessibility service **cannot be enabled programmatically** — only the user can
turn it on. In the app, tap **Open Accessibility settings**, then:

1. Find **SkipperKit Skip Assistant** under *Installed apps* / *Downloaded apps*.
2. Toggle it **On** and accept the permission dialog.

### Restricted Settings (Android 13+ / Samsung)

Sideloaded apps are blocked from accessibility until you unlock them:

1. **Settings → Apps → SkipperKit → ⋮ (top-right) → Allow restricted settings**, confirm
   with your PIN/biometric.
2. Return to **Settings → Accessibility → SkipperKit Skip Assistant** and the toggle
   will now work.

The in-app status card surfaces this: it shows **SERVICE OFF** with the explanation
and the settings shortcut until the service is actually running.

---

## Using it

Open SkipperKit and you get a Material 3 settings screen:

- **Status card** — service ON/OFF, plus a health line ("Last activity: 2 min ago",
  or a warning when no events have been seen recently).
- **Enable SkipperKit** — master switch gating every app.
- **Per-app cards** — each app has an Enable switch and **Skip Intro / Skip Recap /
  Auto-play Next Episode** toggles. Toggles grey out when the app (or master) is off.

Then just watch a supported app — when a skip button appears, SkipperKit taps it.

### Auto-play Next Episode support

| App | Skip Intro / Recap | Auto-next |
|---|---|---|
| Netflix | ✅ | ✅ (end-of-episode card) |
| Disney+ | ✅ | ✅ (`upNextLiteButton`) |
| Prime Video | ✅ | ✅ ("Next up:" prefix match on the end card) |
| Label-only apps | ✅ (by text) | ❌ — needs a verified end-card id first |

Where auto-next is unsupported the toggle is shown **disabled** ("Not available in
this app"): without a verified identifier there is no way to tell an end-of-episode
card from an always-present control-bar button, and SkipperKit refuses to guess.
Auto-next is **off by default** everywhere.

### Sharing taught apps

Every app you've added yourself has a **Share** action on its card (built-ins
ship with everyone already): it exports the app's
approved skip buttons as a small JSON snippet via the system share sheet. Another
user pastes it under *Add an app → Import shared* and gets the same buttons —
after their own explicit import, and only for the one package the file names.
Shared files can only ever describe **Skip Intro / Skip Recap** buttons; auto-next
can never arrive via import.

### Beyond streaming

Skip buttons are the flagship, but the teach-and-approve loop is general: any
repetitive, user-visible button in an app you add — a recurring "rate this app"
nag, a daily check-in confirmation, a "still watching?" prompt — can be taught,
approved once, and tapped for you from then on. The same constraints always hold:
the app must be explicitly added, every button explicitly approved, and matching
stays accessibility-tree-only. This also makes SkipperKit useful as an assistive
tool for users for whom repeated precise taps are physically costly.

---

## Configuration (data-driven + remote)

All labels and identifiers live in **data**, never in matching logic
(`config/DefaultConfigs.kt`). Matching only ever compares against configured values.

SkipperKit can also pull an updated config over HTTPS, so identifiers can be fixed
**without an app release** if a streaming app changes its UI:

- On startup it restores saved toggles, applies the **cached** config, then fetches a
  **fresh** one. Any failure falls back cached → bundled. The fetch is HTTPS-only, with
  8 s timeouts and a 512 KB cap, and never blocks the UI.
- The default URL is this project's own
  [skipperkit-config](https://github.com/foodlbs/skipperkit-config) repo (changes land
  via reviewed PRs). Forks should point `SettingsStore.DEFAULT_REMOTE_CONFIG_URL` at
  their own trusted host. The config can only change which nodes get tapped within the
  already-scoped apps; **trust the host accordingly.**

Config JSON schema:

```json
{
  "version": 1,
  "apps": [
    {
      "packageName": "com.netflix.mediaclient",
      "locale": "en",
      "skipIntroViewIds": ["skipCreditsButtonTestTag"],
      "skipIntroLabels": ["Skip Intro"],
      "skipRecapViewIds": ["skipCreditsButtonTestTag"],
      "skipRecapLabels": ["Skip Recap"],
      "nextEpisodeViewIds": ["NextEpisodeButtonTestTag"],
      "nextEpisodeLabels": ["Next Episode"],
      "enabled": true,
      "autoNextEnabled": false
    }
  ]
}
```

User toggles persist across restarts via Preferences **DataStore**.

---

## Architecture

The matching core is Android-free so it's unit-testable against fake trees:

```
matching/NodeView        – minimal node interface (text, desc, viewId, clickable, parent, children, click, boundsCenter)
matching/NodeMatcher     – view-id-first, case-insensitive text/desc fallback
matching/TreeSearch      – DFS findFirst + walk-to-clickable-ancestor
matching/SkipEngine      – orchestration + package-aware 1500 ms debounce (injected clock)
config/AppConfig         – per-app data; ConfigRepository (bundled⊕remote); DefaultConfigs (verified)
settings/SettingsRepository – user toggles → configFor() applies them over the base config
service/AccessibilityNodeView – adapts a real AccessibilityNodeInfo to NodeView
service/SkipAccessibilityService – binds events → runs the engine on a worker thread
ui/settings/             – Material 3 Compose settings screen
```

`onAccessibilityEvent` does no tree work itself — it posts to a worker thread, so the
framework callback never blocks (no ANRs).

### Debug Node Inspector

A debug-only build flag (`BuildConfig.DEBUG_NODE_INSPECTOR`) dumps the live
accessibility tree (text / desc / view-id / clickable / bounds) under Logcat tag
`SkipperKitInspector`. This is how each app's stable identifiers above were captured.
It is fully disabled in release builds.

---

## Testing

```bash
./gradlew testDebugUnitTest
```

Unit tests cover label/view-id matching, ancestor traversal, debounce (incl. per-package
and gesture-handoff), config gating, settings overlay, and remote-config parsing —
all against fake node trees, no device required.

---

## Limitations

- **Mobile apps only.** TV apps are unsupported (see above).
- **Labels and ids vary by locale and app version.** SkipperKit prefers stable ids and
  keeps text as a fallback, but a streaming-app redesign can still break matching until
  the (bundled or remote) config is updated.
- **Label-only apps are English-only for now** and more fragile than the verified
  tier — device-verified view-ids for them are a welcome contribution.
- It only acts on the **foregrounded** supported app, exactly like a user tap.

---

## Privacy

SkipperKit reads the accessibility node tree of the supported apps and clicks buttons.
That's all. Specifically, it does **not**:

- capture or record the screen (no `MediaProjection`, no screenshots),
- run OCR or any image/ML recognition,
- analyze video frames or audio,
- intercept, decrypt, or access DRM-protected media,
- use root or ADB,
- send your viewing data anywhere. (The only network call is an outbound HTTPS GET to
  fetch the button-configuration file; nothing about what you watch is transmitted.)

It interacts solely with the accessibility tree and native UI controls — the same
mechanism assistive technologies use every day.

**Scope is user-controlled.** The service is scoped (via `packageNames`) to the
built-in apps listed above plus any app you explicitly add in Settings — it cannot
observe apps you didn't add. Adding or removing an app widens or narrows that scope
immediately; removing an app also deletes everything SkipperKit learned about it.

---

## Responsible use & trademarks

SkipperKit only automates a tap on a button the streaming app already shows to the
user — the same action you'd take yourself. It does not bypass ads, DRM, paywalls, or
any access control. Use it in accordance with the terms of the apps you use it with.

SkipperKit is an independent project and is **not affiliated with, endorsed by, or
sponsored by** Netflix, Amazon (Prime Video), The Walt Disney Company (Disney+), or any
other streaming provider. All product names, logos, and brands are property of their
respective owners and are used only to identify the apps SkipperKit interoperates with.

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for how to build,
test, and add support for new apps. Security reports: see [SECURITY.md](SECURITY.md).

The one hard rule: **accessibility-tree only** — no screen capture, OCR, ML vision,
root, or media analysis, ever.

## License

[Apache License 2.0](LICENSE) © The SkipperKit Authors. See [NOTICE](NOTICE).
