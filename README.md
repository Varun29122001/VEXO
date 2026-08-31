# VEXO

A voice assistant for Android that has no home screen. It opens as a translucent overlay, listens
for exactly one request, performs it, answers out loud, then closes itself.

Package: `com.vexo` · versionName `1.0` · versionCode `1`

## What it actually does

VEXO handles two kinds of request today:

| Request | Result |
| --- | --- |
| "open settings", "open wifi settings", "open bluetooth", "open brightness" | Launches the matching system settings panel |
| "open chrome", "launch youtube", "go to gmail" | Resolves the phrase against installed launcher activities and starts the best match |
| anything else | Speaks "I can't do that yet" and shows what was heard |

It is a command router, not a conversational agent. There is no LLM, no network call, and no
persisted state.

## Interaction lifecycle

```
LAUNCHER / ASSIST intent
        │
        ▼
MainActivity.onCreate ──► enableEdgeToEdge ──► setContent { AssistantSurface }
        │
        ├─ RECORD_AUDIO granted? ──no──► request permission
        │                                    └─ denied ──► speak "I need microphone access to listen" ──► dismiss
        ▼ yes
AssistantSession.run()  (lifecycleScope coroutine)
        │
        │  state: Listening   SpeechRecognitionManager.listen() emits
        │                     AudioLevel* → AssistantManager.updateAudioLevel → orb
        │                     then one Transcript or Failed, then completes
        │
        │  state: Processing  CommandParser.parse(transcript) → Command
        │
        │  state: Executing   ActionManager.execute(command) → ActionResult
        ▼
TextToSpeechManager.speak(result.spoken)      ← fire-and-forget, engine is app-scoped
        │
        ▼
dismiss = true ──► AssistantSurface exit animation (200 ms) ──► onClosed() ──► finish()
                                                                                  │
                                                             onDestroy ──► AssistantManager.reset()
```

The activity is declared `android:noHistory="true"` and `android:excludeFromRecents="true"`, so it
leaves no trace in the back stack or recents. Speech output survives the activity because
`TextToSpeechManager` is owned by `VexoApplication`, and the engine is warmed in
`Application.onCreate` while the user is still speaking.

## Source layout

```
app/src/main/java/com/vexo/
├── MainActivity.kt              Overlay host: permission gate, session launch, toast + finish
├── VexoApplication.kt           Lazy singletons: AssistantManager, TextToSpeechManager, AssistantSession
├── assistant/
│   ├── AssistantManager.kt      StateFlow<AssistantState> + StateFlow<Float> audioLevel (clamped 0..1)
│   ├── AssistantSession.kt      One interaction end to end: listen → parse → execute
│   └── AssistantState.kt        Idle | Listening | Processing | Executing
├── voice/
│   ├── SpeechEvent.kt           AudioLevel | Transcript | Failed(reason, code)
│   ├── SpeechRecognitionManager.kt  callbackFlow around platform SpeechRecognizer
│   └── TextToSpeechManager.kt   App-scoped TTS, offline-voice selection, pending-utterance queue
├── actions/
│   ├── Command.kt               OpenSettings(panel) | OpenApp(query) | Unknown; SettingsPanel enum
│   ├── CommandParser.kt         Phrase → Command (pure, no Android deps → unit testable)
│   └── ActionManager.kt         Command → Intent; ActionResult Performed | NotUnderstood | Failed
└── ui/assistant/
    ├── AssistantSurface.kt      Bottom-anchored container, owns enter/exit animation + dismiss gestures
    ├── VoiceOrb.kt              RuntimeShader host, per-frame uniform updates via withFrameNanos
    └── OrbShader.kt             AGSL fragment shader source (const ORB_SHADER)
```

14 Kotlin source files in `main`, 2 in `test`.

## Layer notes

### Command parsing

`CommandParser` is a pure object with no Android dependencies. It normalises to lowercase, strips
everything outside `[a-z0-9 -]`, collapses repeated spaces, then:

1. Strips an optional wake phrase — `hi vexo`, `hey vexo`, `ok vexo`, `hello vexo`, `vexo`.
2. Matches a leading verb — `open up`, `open`, `launch`, `start`, `go to`, `show me`, `show`.
   Longer variants are listed before their prefixes, so `open up` and `show me` win over `open`
   and `show`. No verb means `Command.Unknown`.
3. Trims a trailing `please`, drops a trailing `settings`/`setting`, and looks the remainder up in
   a 14-key alias map (`wifi`, `wi-fi`, `wireless`, `bluetooth`, `display`, `brightness`, `screen`,
   `sound`, `volume`, `battery`, `location`, `gps`, `apps`, `applications`) covering 8
   `SettingsPanel` values.
4. Anything left over becomes `Command.OpenApp(target)`.

The wake phrase is tolerated but never required, because the recogniser usually captures it as
part of the utterance.

### Speech recognition

`listen()` returns a `Flow<SpeechEvent>` on `Dispatchers.Main.immediate` that emits amplitude
updates and then exactly one terminal `Transcript` or `Failed` before completing. Two behaviours
worth knowing:

- **Partials are salvaged.** Platform recognisers routinely deliver a usable partial and then fail
  with `ERROR_NO_MATCH`. The last partial is retained and preferred over discarding the utterance,
  in both `onResults` and `onError`.
- **Amplitude is normalised** from the platform RMS range (`-2 dB` to `10 dB`) into `0..1`.

Silence windows are deliberately generous so the surface can animate in while the recogniser is
already live: 2000 ms complete silence, 2000 ms possibly-complete silence, 6000 ms minimum
utterance. Every `SpeechRecognizer` error code is mapped to a spoken sentence; the numeric code is
kept in `Failed.code` for the on-screen diagnostic only.

### Speech output

`TextToSpeechManager` initialises asynchronously and holds a single pending utterance until the
engine is ready. Voice selection filters to the current language, excludes network-only voices,
not-installed voices, and `legacySetLanguageVoice` alias voices (which delegate to the system
default and are often network-backed), then sorts by quality, latency, name. The effect is a
consistent offline voice across devices. Pitch `1.0`, rate `1.05`, `QUEUE_FLUSH`.

### The orb

`VoiceOrb` compiles `ORB_SHADER` into a `RuntimeShader` and drives it from a `withFrameNanos`
loop, pushing six uniforms per frame: `iResolution`, `iTime`, `hue`, `rot`, `hover`,
`hoverIntensity`. `audioLevel` accelerates rotation only above a `0.05` threshold, so at silence
the orb idles on its animated noise field alone.

`OrbShader.kt` is an AGSL port of a GLSL original. Colours, the simplex noise field, and the
two-term lighting model are unchanged; only type names and vector construction were adapted,
because SkSL is stricter than GLSL about mixing scalars and vectors.

`RuntimeShader` is why `minSdk` is 33 — that is the floor for AGSL.

### Surface and window

`AssistantSurface` is bottom-anchored, 148 dp, 32 dp above the navigation bar inset. Enter is
260 ms (slide + fade + scale from 0.85); exit is 200 ms, and `onClosed` fires only after the exit
animation finishes so the window never disappears mid-frame. Both a tap anywhere and the back
gesture set `closing`.

`Theme.Vexo` extends the platform `android:Theme.Material.NoActionBar` with a translucent,
transparent, undimmed, animation-free window. Note the app depends on Compose `foundation`,
`ui`, and `animation` only — there is no Material Compose dependency, so no `MaterialTheme`.

## Build

Toolchain versions come from `gradle/libs.versions.toml` and the wrapper:

| Component | Version |
| --- | --- |
| Gradle | 9.5.0 |
| Android Gradle Plugin | 9.3.2 |
| Kotlin / Compose compiler plugin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| compileSdk / targetSdk | 37 |
| minSdk | 33 |
| Java source/target compatibility | 11 |
| Gradle daemon JVM (foojay toolchain) | 25 |

Configuration cache is enabled in `gradle.properties`. `settings.gradle.kts` sets
`RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so repositories are declared centrally.

Commands (Windows, from the project root):

```bat
gradlew.bat assembleDebug          :: debug APK
gradlew.bat testDebugUnitTest      :: JVM unit tests
gradlew.bat installDebug           :: install to a connected device
gradlew.bat assembleRelease        :: release APK
```

`local.properties` holds the local SDK path and is gitignored; it is not checked in for a reason.

### Release configuration

The `release` build type sets `optimization { enable = false }` — R8 shrinking, obfuscation, and
resource shrinking are all off, and there is no `signingConfig` in `app/build.gradle.kts`. Before
shipping, both need attention: turn optimization on and add a signing config. The checked-in
`app/release/app-release.apk` (17.7 MB, with baseline profiles for API 28-30 and 31+) came from an
IDE signed-release export, not from `assembleRelease`.

## Tests

10 JVM unit tests, all passing:

- `CommandParserTest` (6) — wake phrase present and absent, trailing punctuation and politeness,
  panel alias resolution, app-launch fallback, and unknown phrases including empty input.
- `AssistantManagerTest` (4) — initial idle/silent state, transitions, audio-level clamping at both
  bounds, and reset.

```bat
gradlew.bat testDebugUnitTest
```

The seam that makes this testable is `CommandParser` being a pure object: all Android coupling
lives in `ActionManager`, which is not unit tested. There is no `androidTest` source set, so the
recogniser, TTS, shader, and surface animation are unverified by automated tests.

## Permissions and privacy

`RECORD_AUDIO` is the only permission requested. It is asked for at first launch, and denial is
handled by speaking an explanation rather than failing silently.

The `<queries>` block declaring the `MAIN`/`LAUNCHER` intent is required on modern Android for
`queryIntentActivities` to see installed apps — without it, "open <app>" would always fail.

Nothing is stored and nothing is sent anywhere by VEXO itself. Be aware that the platform
`SpeechRecognizer` may still route audio to a cloud service depending on the device's recognition
provider; that is outside the app's control. TTS voice selection deliberately prefers offline
voices.

## Known gaps

- `AssistantManager.state` is written on every transition but nothing observes it — the UI reads
  only `audioLevel`. The orb therefore cannot visually distinguish listening from executing.
- `SpeechRecognitionManager.isAvailable()` is public but never called; `listen()` performs its own
  availability check inline.
- `VoiceOrb`'s `hue` parameter is always the default `0f`; no caller varies it.
- No `androidTest` source set, and `ActionManager` has no test coverage.
- Release builds are unoptimized and unsigned by configuration (see above).
- The command vocabulary is a fixed alias map. Requests outside "open settings panel" and
  "open app" are answered with "I can't do that yet".
