# VEXO

A voice assistant for Android that has no home screen. It opens as a translucent overlay, listens
for exactly one request, performs it, answers out loud, then closes itself. It can optionally be
woken by voice, and can optionally be taught to respond only to yours.

Package: `com.vexo` · versionName `1.0` · versionCode `1`

## What it actually does

VEXO handles two kinds of request today:

| Request | Result |
| --- | --- |
| "open settings", "open wifi settings", "open bluetooth", "open brightness" | Launches the matching system settings panel |
| "open chrome", "launch youtube", "go to gmail" | Resolves the phrase against installed launcher activities and starts the best match |
| anything else | Speaks "I can't do that yet" and shows what was heard |

It is a command router, not a conversational agent. There is no LLM. Requests are parsed by a fixed
alias map on-device and nothing the user says leaves the phone.

Three things are optional and off until switched on in settings:

| Feature | What it costs |
| --- | --- |
| Neural voice | 78 MiB download; VEXO speaks in a piper VITS voice instead of the system one |
| Wake word | 17 MiB download; a foreground service holds the microphone open for "Hey VEXO" |
| Voice recognition | 27 MiB download; the wake word can be ignored unless it sounds like you |

All three run entirely on-device. The only network traffic in the app's life is downloading those
models. See [Permissions and privacy](#permissions-and-privacy).

## Interaction lifecycle

```
LAUNCHER / ASSIST intent ◄── WakeWordService (optional)
        │                       │  AudioRecord 16 kHz ──► KeywordSpotter
        │                       │  match? ──► SpeakerGate.isEnrolledSpeaker (optional)
        │                       └──────────► startActivity
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
TextToSpeechManager.speak(result.spoken) : Job    ← app-scoped, survives the activity
        │
        │  await(job), capped at 8000 ms   ← surface stays up while VEXO talks, so the
        │                                    process cannot be frozen mid-sentence
        ▼
dismiss = true ──► AssistantSurface exit animation (200 ms) ──► onClosed() ──► finish()
                                                                                  │
                                                             onDestroy ──► AssistantManager.reset()
```

The activity is declared `android:noHistory="true"` and `android:excludeFromRecents="true"`, so it
leaves no trace in the back stack or recents. `TextToSpeechManager` is owned by `VexoApplication`
rather than the activity, so the speech job outlives the surface and the engine can be warmed in
`Application.onCreate` while the user is still speaking. That warm-up window is what hides neural
model load.

The surface is held open until speech finishes. That is not cosmetic: the platform engine hands an
utterance to a system service that keeps talking after VEXO exits, but neural audio is rendered by
VEXO's own `AudioTrack`, and a process with no foreground component is a candidate for Android's
cached-app freezer. Awaiting the job keeps the process foreground for the ~1-2 s of playback.
Tapping or swiping still dismisses immediately, and speech continues because the job is
application-scoped.

## Source layout

```
app/src/main/java/com/vexo/
├── MainActivity.kt              Overlay host: permission gate, session launch, toast + finish
├── SettingsActivity.kt          The only screen: permissions, wake word, voice enrolment
├── VexoApplication.kt           Lazy singletons: manager, settings, modelStore, speakerGate, tts
├── assistant/
│   ├── AssistantManager.kt      StateFlow<AssistantState> + StateFlow<Float> audioLevel (clamped 0..1)
│   ├── AssistantSession.kt      One interaction end to end: listen → parse → execute
│   └── AssistantState.kt        Idle | Listening | Processing | Executing
├── models/
│   ├── ModelStore.kt            Resumable download + atomic install; archive or single file
│   └── VexoModels.kt            The three downloads, with exact sizes and required files
├── settings/
│   └── VexoSettings.kt          Two SharedPreferences flags, exposed as StateFlow
├── speaker/
│   ├── SpeakerGate.kt           Model + profile + verification, and the "no opinion" policy
│   ├── SpeakerVerifier.kt       sherpa-onnx embedding extraction
│   ├── VoiceProfileStore.kt     Enrolled embedding on disk; averaging and cosine similarity
│   └── VoiceRecorder.kt         Fixed-length 16 kHz clips for enrolment
├── wake/
│   ├── WakeWordService.kt       Foreground microphone service; launches the overlay
│   ├── WakeWordDetector.kt      AudioRecord → KeywordSpotter, plus the audio ring buffer
│   ├── WakeWords.kt             Wake phrases and their BPE tokenisations
│   └── WakeWordBootReceiver.kt  Restores the listener after a reboot
├── voice/
│   ├── SpeechEvent.kt           AudioLevel | Transcript | Failed(reason, code)
│   ├── SpeechRecognitionManager.kt  callbackFlow around platform SpeechRecognizer
│   ├── TextToSpeechManager.kt   Facade: prefers the neural voice, falls back to the platform
│   ├── NeuralTextToSpeech.kt    sherpa-onnx OfflineTts + AudioTrack playback
│   ├── PlatformTextToSpeech.kt  Platform TTS, offline-voice selection, awaitable utterances
│   └── VoiceModel.kt            Which pack, weights file, speaker id and licence
├── actions/
│   ├── Command.kt               OpenSettings(panel) | OpenApp(query) | Unknown; SettingsPanel enum
│   ├── CommandParser.kt         Phrase → Command (pure, no Android deps → unit testable)
│   └── ActionManager.kt         Command → Intent; ActionResult Performed | NotUnderstood | Failed
└── ui/
    ├── assistant/
    │   ├── AssistantSurface.kt  Bottom-anchored container, enter/exit animation + dismiss gestures
    │   ├── VoiceOrb.kt          RuntimeShader host, per-frame uniform updates via withFrameNanos
    │   └── OrbShader.kt         AGSL fragment shader source (const ORB_SHADER)
    └── settings/
        └── SettingsScreen.kt    Stateless Compose screen: UI state in, actions out
```

30 Kotlin source files in `main`, 5 in `test`.

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

Speech output has two engines behind one facade. `TextToSpeechManager` prefers an on-device neural
voice and degrades to the platform engine.

```
Application.onCreate ──► TextToSpeechManager
                              │
                              ├─ VoiceModelStore.isInstalled(model)?
                              │       │
                              │       ├─ yes ──► NeuralTextToSpeech.create()   (~1.7-3 s)
                              │       │              warms during the listening window
                              │       │
                              │       └─ no  ──► warm-up resolves null
                              │                  + download the pack in the background
                              ▼
                        speak(text)
                              │
                              ├─ neural engine ready within 1500 ms? ──► OfflineTts.generate()
                              │                                          └─► AudioTrack (PCM float)
                              └─ otherwise ─────────────────────────────► PlatformTextToSpeech
```

**Neural path.** `NeuralTextToSpeech` wraps sherpa-onnx's `OfflineTts` over a piper VITS model and
plays the result through an `AudioTrack` at the model's native 22.05 kHz. Synthesis is one-shot
rather than streamed into the player: VEXO's sentences top out around two and a half seconds, so
the extra wait before audio starts is small, and in exchange the real-time factor is directly
measurable and a sentence can never start playing before it is known to have synthesised. The
`OfflineTts` handle wraps a single native session, so utterances are serialised behind a `Mutex`
and never run on the main thread.

**Voice pack.** The model is not in the APK. `VoiceModelStore` fetches
`vits-piper-en_US-libritts_r-medium` (82,038,311 bytes) into `filesDir/voices/<id>` on first use.
Two properties matter given VEXO is a one-shot overlay whose process can be reclaimed at any
moment:

- **The download is resumable.** It issues an HTTP range request against whatever is already on
  disk, so each launch continues where the last stopped instead of restarting an 78 MiB transfer.
  A server that ignores the range header and answers `200` restarts cleanly rather than corrupting
  the file. This is not theoretical — during testing the first attempt was frozen at 50 % when the
  activity finished, and the next launch resumed from 46,164,963 bytes.
- **Installation is atomic.** The archive expands into `<id>.staging` and is only renamed into
  place once complete, so a kill mid-extract cannot leave a half-populated pack that
  `isInstalled()` would accept. The trade-off is that extraction is *not* resumable, so a process
  killed during it repeats the work.

The pack is only fetched on an unmetered network — 78 MiB is not something to put on someone's
cellular data unasked. That is what `ACCESS_NETWORK_STATE` is for.

**Platform path.** `PlatformTextToSpeech` is the original implementation, unchanged. It initialises
asynchronously and holds a single pending utterance until the engine is ready. Voice selection
filters to the current language, excludes network-only voices, not-installed voices, and
`legacySetLanguageVoice` alias voices (which delegate to the system default and are often
network-backed), then sorts by quality, latency, name. Pitch `1.0`, rate `1.05`, `QUEUE_FLUSH`. The
neural path uses the same `1.05` rate so the two sound alike.

It answers on first run, whenever the pack is missing, whenever the model fails to load, and
whenever synthesis throws. It is a real code path, not a formality — see the measurements below.

### Measured synthesis cost

Measured on the running emulator (`sdk_gphone16k_x86_64`, x86_64, 4 threads), read from
`VexoNeuralTts` logcat. Frame counts were cross-checked against
`AudioTrack: stop(N): called with <frames> delivered`, which matched the synthesised sample count
exactly on every run. `peak` is the largest absolute sample, confirming output is speech rather
than silence.

| Utterance | Audio | Synthesis | RTF | Peak |
| --- | --- | --- | --- | --- |
| "I need a network connection to understand you" | 2124 ms | 1487 ms | 0.700 | 0.737 |
| "I didn't catch that" | 1114 ms | 777 ms | 0.697 | 0.598 |
| "I didn't catch that" | 1184 ms | 865 ms | 0.731 | 0.756 |
| "I didn't catch that" | 1091 ms | 998 ms | 0.915 | 0.556 |
| first synthesis after install (cold) | 1021 ms | 1299 ms | 1.272 | — |

Model load, same device, across seven fresh processes: **1658, 1765, 2080, 2272, 2960, 3003, and
11728 ms**. Load time falls as the 75 MiB `.onnx` settles into the page cache; the 11.7 s outlier
was the launch immediately after reinstalling the APK.

Two things follow. First, an emulator is a pessimistic proxy — sherpa-onnx's own benchmark table
puts this model at RTF 0.357 on a Raspberry Pi 4 with 4 threads, and a real arm64 phone should sit
below the ~0.70 measured here. Second, **load time regularly exceeds the 1500 ms warm-up budget**,
which is why the platform fallback matters. That budget is only the margin *after* the listening
window: warm-up starts in `Application.onCreate` and overlaps a recognition pass that cannot finish
in under 2000 ms, so a 2-3 s load is normally hidden. The 11.7 s cold load was not, and that run
correctly fell back to the platform engine — observed as 72,513 frames delivered by the system TTS
process instead of VEXO's own.

### Wake word

`WakeWordService` is the only part of VEXO that is always running, and it exists because a wake word
cannot work any other way. It is a `microphone`-typed foreground service with an ongoing
notification, off unless switched on in settings.

`WakeWordDetector` reads 16 kHz mono float PCM from `VOICE_RECOGNITION` in 100 ms chunks and feeds
them straight into sherpa-onnx's streaming `KeywordSpotter` — a 3.3 M parameter zipformer
transducer, small enough to run on one thread. Audio is consumed in-process and discarded. A rolling
two-second `AudioRing` is kept for one purpose only: handing the utterance that triggered a wake-up
to the speaker check.

**Wake phrases are supplied as BPE tokens, not spelling.** The spotter is a transducer over
byte-pair pieces, so each phrase must be tokenised with the `bpe.model` shipped inside the pack.
"Vexo" is not an English word, so this could not be assumed — the tokenisations were generated and
every piece verified against the pack's `tokens.txt`:

```
HEY VEXO    ->  ▁HE Y ▁ VE X O
HI VEXO     ->  ▁HI ▁ VE X O
OK VEXO     ->  ▁O K ▁ VE X O
HELLO VEXO  ->  ▁HE LL O ▁ VE X O
```

These mirror the phrases `CommandParser` already tolerates at the front of a request.

Two details worth knowing:

- **The model must be the full pack, not `-mobile`.** The smaller `-mobile` pack ships no float
  joiner, which forces a float encoder to be paired with an int8 one. That combination aborts the
  process inside onnxruntime on the first decode with a reshape mismatch at `/downsample/Reshape_1`
  (`Input shape:{17,1,128}, requested shape:{8,2,1,128}`). The file names now live in one
  `WakeWordFiles` object so the download's required-file list and the spotter's configuration cannot
  drift apart again. The 2 MiB saving was not worth a native crash.
- **The service is `START_NOT_STICKY`.** A native fault kills the process, and sticky restart turns
  that into an endless respawn loop. Failing off is better than failing forever.

Launching the overlay from the background needs `SYSTEM_ALERT_WINDOW`; Android blocks background
activity starts without it. The settings screen links to the system page that grants it.

**Only one component may record at a time, so the microphone is handed over explicitly.**
`MainActivity.onCreate` sets `AssistantManager.sessionActive`, and the detector watches it: while a
request is in flight it releases `AudioRecord` entirely and polls every 250 ms, then reacquires it
when the overlay closes. On a detection the release happens *before* `startActivity`, so the ordering
is deterministic rather than a race with the recogniser. The spotter itself is never unloaded, so
resuming costs only the cost of opening the audio device.

Without this the listener and the platform recogniser compete for the microphone, and the symptom is
nasty: the wake word fires, the overlay appears, and then VEXO never hears the request. Measured on
the emulator, going from listening to overlay to listening again:

```
Listening for a wake phrase        mic ACTIVE
Released the microphone            mic IDLE     ← overlay open, recogniser has it
Listening for a wake phrase        mic ACTIVE   ← overlay closed
```

`MainActivity.onDestroy` also restarts the service if the wake word is enabled. Because the service
is `START_NOT_STICKY`, a crash or force-stop would otherwise leave the listener dead until the next
reboot; this way any use of VEXO brings it back.

### Voice recognition

Speaker verification gates the wake word, not the whole app. That is not a compromise but a
consequence of where the audio is: the platform `SpeechRecognizer` never hands back raw PCM, so the
only utterance VEXO owns samples of is the wake phrase itself. `WakeWordDetector` already has it
buffered, so it is free to check.

```
enrol:  VoiceRecorder ──► 3 × 2.5 s clips ──► SpeakerVerifier.embed ──► averageEmbedding
                                                                            └─► VoiceProfileStore
verify: wake utterance ──► SpeakerVerifier.embed ──► cosineSimilarity(profile) ≥ 0.5 ?
```

`SpeakerVerifier` wraps sherpa-onnx's `SpeakerEmbeddingExtractor` over a CAM++ model, which turns an
utterance into a fixed-length embedding. Enrolment averages three takes and normalises the result,
which is less sensitive to one noisy recording than a single sample. Verification is cosine
similarity against a `0.5` threshold. The averaging, normalising and similarity maths are pure
functions, so they are unit tested without the native extractor.

`SpeakerGate` is deliberately reluctant to say "not you": it returns null — meaning no opinion, so
do not block — whenever the model is missing, no profile is enrolled, or the audio is under half a
second. A wake word that silently stops working is worse than one that occasionally answers someone
else, because **this is personalisation, not authentication.** A recording of your voice defeats it.
It is not a lock and the settings screen says so.

The profile is a normalised float vector in a versioned binary file in `filesDir`. It is never
uploaded, and Delete removes it.

Only one component may hold the microphone, so enrolling or testing stops the wake service first and
restarts it afterwards.

### Settings

`SettingsActivity` is VEXO's only screen and is deliberately not a launcher entry — the icon opens
the assistant. It is reached from the icon's long-press shortcut (`res/xml/shortcuts.xml`) or by
tapping the listening notification. `SettingsScreen` is a stateless composable taking a
`SettingsUiState` and a `SettingsActions`, so the activity owns all the state and the screen stays
a pure function of it.

This is the one place VEXO uses Material 3. The assistant overlay is still shader plus `foundation`.

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
| sherpa-onnx (JitPack) | v1.13.5 |
| commons-compress | 1.28.0 |

`settings.gradle.kts` adds `https://jitpack.io`, because sherpa-onnx publishes its Android AAR
there and nowhere else. `commons-compress` is present for one reason: the model packs ship as
`.tar.bz2` and Android has no bzip2 decoder. Compose Material 3 is used by the settings screen only.

Configuration cache is enabled in `gradle.properties`. `settings.gradle.kts` sets
`RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so repositories are declared centrally.

### Downloaded models

None of these are in the APK. `ModelStore` fetches them into `filesDir/models/<id>` on first use,
only on an unmetered network, and only for features that are switched on.

| Model | Download | Purpose |
| --- | --- | --- |
| `vits-piper-en_US-libritts_r-medium` | 82,038,311 B | Neural voice, 19.5 M params |
| `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01` | 17,626,723 B | Wake word, 3.3 M params |
| `3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx` | 28,281,164 B | Speaker embedding |

With everything enabled that is ~128 MiB on disk on top of the APK. Sizes are exact because
`ModelStore` uses them to resume a partial transfer and to reject a truncated one.

### APK size

sherpa-onnx dominates the APK. Per ABI the native libraries are:

| ABI | `lib/` total | of which `libonnxruntime.so` |
| --- | --- | --- |
| arm64-v8a | 29.9 MiB | 20.7 MiB |
| x86_64 | 33.9 MiB | — |
| armeabi-v7a | 20.9 MiB | — |
| x86 | 35.0 MiB | — |

Shipping all four in one APK produced a 144 MiB debug build. `splits.abi` now emits one APK per
ABI and drops the 32-bit ones, since every device on `minSdk 33` is 64-bit:

```
app-arm64-v8a-debug.apk              54.6 MiB   ← install this on a phone
app-x86_64-debug.apk                 58.6 MiB   ← emulator
app-arm64-v8a-release-unsigned.apk   49.3 MiB
app-x86_64-release-unsigned.apk      53.3 MiB
```

`assembleRelease` output is **unsigned** and cannot be installed — there is no `signingConfig` in
`app/build.gradle.kts`. Use the debug APK for on-device testing, or add a signing config first.

Note `splits.abi` and `defaultConfig.ndk.abiFilters` are mutually exclusive — AGP fails
configuration if both are set. A release should ship an App Bundle, which performs the same split
automatically. On top of the APK, the voice pack adds ~75 MiB in `filesDir`.

Commands (Windows, from the project root):

```bat
gradlew.bat assembleDebug          :: debug APKs, one per ABI
gradlew.bat testDebugUnitTest      :: JVM unit tests
gradlew.bat installDebug           :: install to a connected device
gradlew.bat assembleRelease        :: release APKs
```

`local.properties` holds the local SDK path and is gitignored; it is not checked in for a reason.

### Release configuration

The `release` build type sets `optimization { enable = false }` — R8 shrinking, obfuscation, and
resource shrinking are all off, and there is no `signingConfig` in `app/build.gradle.kts`. Before
shipping, both need attention: turn optimization on and add a signing config. The checked-in
`app/release/app-release.apk` (17.7 MB, with baseline profiles for API 28-30 and 31+) came from an
IDE signed-release export, not from `assembleRelease`.

## Tests

32 JVM unit tests, all passing:

- `CommandParserTest` (6) — wake phrase present and absent, trailing punctuation and politeness,
  panel alias resolution, app-launch fallback, and unknown phrases including empty input.
- `AssistantManagerTest` (6) — initial idle/silent state, transitions, audio-level clamping at both
  bounds, reset, and that the microphone claim starts clear and is released by reset (a reset that
  left it set would silently kill the wake word).
- `ModelStoreTest` (5) — archive entry sanitising: stripping the pack's top-level directory,
  skipping the directory entry itself, rejecting `..` traversal, normalising redundant separators
  and backslashes, and the required-file list for single-file models.
- `SpeakerMathTest` (8) — normalisation to unit length, the zero-vector edge case, averaging
  identical and opposing embeddings, cosine similarity for parallel/orthogonal/opposed vectors, a
  different speaker scoring below threshold, and rejection of mismatched dimensions.
- `WakeWordTest` (7) — the audio ring before and after wrap-around, partial writes, clearing, and
  that every wake phrase has a non-blank tokenisation carrying the sentencepiece word-start marker
  and the shared `VE X O` stem.

```bat
gradlew.bat testDebugUnitTest
```

The seam that makes this testable is keeping logic pure. `CommandParser` is an object with no
Android dependencies; the archive path rules live in a free function `packRelativePath`; the
embedding maths are free functions in `VoiceProfileStore.kt`; and `AudioRing` is a plain class with
no audio device. The traversal check is duplicated as a canonical-path comparison at write time, as
defence in depth.

Everything Android-coupled is untested: `ActionManager`, the recogniser, both TTS engines, the
download and extract paths, the wake service, the shader, and the surface animation. There is no
`androidTest` source set — those paths were exercised by hand on the emulator instead, as recorded
above.

## Permissions and privacy

| Permission | Why |
| --- | --- |
| `RECORD_AUDIO` | The microphone. Requested at first launch; denial is answered out loud rather than failing silently. |
| `INTERNET` | Downloading the three models, once each. |
| `ACCESS_NETWORK_STATE` | Checking the connection is unmetered before spending ~128 MiB of it. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | The wake word listener, so the fact that the microphone is open is visible in the shade. |
| `POST_NOTIFICATIONS` | Showing that ongoing notice. |
| `RECEIVE_BOOT_COMPLETED` | Restoring the listener after a reboot, if it was on. |
| `SYSTEM_ALERT_WINDOW` | Opening the overlay from the background when a wake phrase fires. Granted from the settings screen, never at install. |

The `<queries>` block declaring the `MAIN`/`LAUNCHER` intent is required on modern Android for
`queryIntentActivities` to see installed apps — without it, "open <app>" would always fail.

**The wake word changes what this app can honestly claim, so read this part.** With it switched on,
VEXO holds the microphone open continuously and a foreground notification says so. That audio is fed
straight into the keyword spotter in-process, matched, and discarded; it is never written to disk and
never transmitted. The rolling buffer is two seconds long and exists only to check the wake utterance
against your voice profile. Nonetheless, an always-open microphone is a meaningful thing to accept,
which is why the feature is off by default, requires an explicit toggle, and is visible while running.

VEXO makes exactly one kind of network request: HTTP `GET` for a model from
`github.com/k2-fsa/sherpa-onnx/releases`. No request the user speaks is ever transmitted, there is no
analytics or telemetry, and the voice profile stays in app-private storage.

The voice print is biometric-adjacent data. It is a normalised float vector, not recoverable audio,
it never leaves the device, and Delete in settings removes it.

The unchanged caveat: the platform `SpeechRecognizer` — used for the actual request after wake-up —
may route captured audio to a cloud service depending on the device's recognition provider. That is
outside the app's control, and it is the remaining reason VEXO is not fully offline. The wake word and
speaker check, by contrast, are entirely local.

## Licensing

Worth reading before shipping this. The neural path pulls in two separate licence questions, and
neither is visible from the Gradle file.

**espeak-ng is GPL-3.0.** Piper voices do not carry a pronunciation lexicon; they phonemise through
espeak-ng, which sherpa-onnx links into its native libraries via `piper-phonemize`. The sherpa-onnx
maintainers acknowledge this directly in issue #3731: espeak-ng "is licensed under GPL, it
introduces license constraints that are incompatible with the Apache-2.0 license of sherpa-onnx."
For a closed-source or Play-distributed VEXO this needs a decision, not a shrug. The usual escape
is a fork such as `piper-plus`, which ships an MIT-licensed grapheme-to-phoneme implementation and
no espeak-ng dependency.

**Voice weights carry their training data's licence.** This is why the default voice is not the one
every sherpa-onnx example uses:

| Voice | Dataset licence | Provenance |
| --- | --- | --- |
| `en_US-lessac-medium` | Blizzard 2013 **research licence** | trained from scratch |
| `en_US-ryan-high` | CC BY-**NC**-SA 4.0 | trained from scratch |
| `en_US-libritts_r-medium` ← **default** | CC BY 4.0 | fine-tuned from lessac |
| `en_US-joe-medium` | CC0 | fine-tuned from lessac |
| `en_US-kathleen-low` | CC0 | fine-tuned from ryan |

The Lessac licence is the sharp edge: it grants use for "Research Purposes" only and expressly
excludes "using the Materials for any commercial purpose, including the development, marketing,
commercialisation, sale or licencing of voice synthesis ... products or services." That rules out
the most commonly demoed voice.

`libritts_r` was chosen because LibriTTS-R itself is CC BY 4.0 and it has the best RTF-per-megabyte
in sherpa-onnx's benchmark table. Note the honest caveat recorded in `VoiceModel`: those weights
were still *fine-tuned from* the Lessac voice, and the same is true of most permissively labelled
piper voices. Whether a fine-tune is a derivative work of its base model's training data is
unsettled, so a legal review should not treat "CC BY 4.0" on the dataset as the end of the enquiry.

Swapping voices is a one-line change: `VoiceModel.LibriTtsR` is data, and `TextToSpeechManager`
takes the model as a constructor parameter.

**The two new models need the same scrutiny, which they have not had.** The wake word spotter is
trained on GigaSpeech and the speaker model is 3D-Speaker's CAM++; both are redistributed by the
sherpa-onnx project, and neither carries a licence file inside its pack. Before shipping, their
training-data terms should be checked the same way the voice's were — do not assume that "published
in a sherpa-onnx release" means "cleared for your use".

## Known gaps

Wake word and voice recognition — **none of this has been verified on real hardware.** The emulator
has no usable microphone, so what was confirmed there is that the model downloads, the spotter loads,
`AudioRecord` opens, exactly one record track is held, and stopping releases it. Detection accuracy,
false-accept and false-reject rates, and battery cost are all unmeasured.

- **The wake word may not fire, or may fire too often.** `keywordsScore` and `keywordsThreshold` are
  left at the library defaults (`1.5` and `0.25`) and have not been tuned. "Vexo" is not an English
  word, so the spotter is working from a BPE spelling of a name it never saw in training.
- **The speaker threshold is a guess.** `0.5` cosine similarity was chosen on general knowledge of
  CAM++ embeddings, not from measurements on your voice. Expect to change it.
- **Battery cost is unknown.** A zipformer on one thread plus an open microphone is cheap in
  principle, but "cheap" has not been quantified on a phone.
- **Enrolment records a fixed 2.5 s three times** with no silence trimming and no quality check
  beyond "was any speech found". A noisy enrolment quietly produces a bad profile.

Neural TTS:

- **Installing a pack fights the one-shot lifecycle.** Downloads resume by HTTP range, but bzip2
  extraction is not resumable and restarts if the process is frozen. On the emulator the voice pack
  only completed because the cached-app freezer was disabled for the test
  (`device_config put activity_manager_native_boot use_freezer false`, since restored). The wake word
  pack, being 17 MiB rather than 78 MiB, installed in one go. A production build wants `WorkManager`
  or Play Asset Delivery.
- **Audio focus is requested but not monitored.** VEXO ducks other audio while speaking, but does not
  listen for focus loss, so it will not stop early if something more important starts talking.
- **The speaker id is unvalidated.** `libritts_r` has 904 speakers and `speakerId = 109` was picked
  without listening to the alternatives. It is one number in `VoiceModel`.
- **Only measured on an emulator.** Every timing above came from `sdk_gphone16k_x86_64`.

Pre-existing:

- `AssistantManager.state` is written on every transition but nothing observes it — the UI reads
  only `audioLevel`. The orb therefore cannot visually distinguish listening from executing.
- `SpeechRecognitionManager.isAvailable()` is public but never called; `listen()` performs its own
  availability check inline.
- `VoiceOrb`'s `hue` parameter is always the default `0f`; no caller varies it.
- No `androidTest` source set, and `ActionManager` has no test coverage.
- Release builds are unoptimized and unsigned by configuration (see above).
- The command vocabulary is a fixed alias map. Requests outside "open settings panel" and
  "open app" are answered with "I can't do that yet".
