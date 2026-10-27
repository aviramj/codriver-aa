# CoDriver AI

A hands-free AI voice assistant for **Android Auto**. Tap once, ask a question
out loud, and it reads the answer back — then you can keep asking follow-ups,
with the conversation remembering what came before. The AI backend is any
**OpenAI-compatible** API, so it talks to ChatGPT today and anything else
(Azure OpenAI, OpenRouter, a local model, …) by changing one setting.

> This is a personal, sideload-first project. Please read **"Running in a real
> car"** below before you expect it to show up on your dashboard — Google
> restricts which apps Android Auto will load in an actual vehicle, and that
> affects how you install it.

---

## How it works

```
  You tap "Speak"  ─▶  record car mic ─▶  Whisper (speech→text)
                                               │
                                               ▼
                         conversation history + system prompt
                                               │
                                               ▼
                       Chat Completions (ChatGPT / your model)
                                               │
                                               ▼
     Text-to-Speech (device or OpenAI)  ◀──  reply text  ──▶ shown on screen
                                               │
                                     tap "Ask follow-up" to continue
```

Why this shape:

- **Speech-to-text goes to the cloud (Whisper).** On Android Auto the
  microphone is only reachable through the Car App Library's `CarAudioRecord`;
  the normal Android `SpeechRecognizer` can't use it. So the app records raw
  audio and sends it to a transcription endpoint. Using OpenAI's Whisper means
  one API key covers both transcription and chat.
- **Text-to-speech is your choice.** On-device TTS (default) is free, low-latency,
  and routes over the car speakers. Flip one switch to use **OpenAI's cloud voice**
  instead — nicer sounding, at a small per-character cost and a little more latency.
  If the cloud voice can't be fetched (bad key/model/voice or no signal), it falls
  back to the device voice so a reply is never lost.
- **One screen, one turn at a time.** Staying on a single Car App template and
  refreshing it keeps the UI within Android Auto's driver-distraction limits.

## Project layout

```
codriver/
├── app/src/main/
│   ├── AndroidManifest.xml            CarAppService + launcher activity + mic perm
│   ├── java/com/aviramj/codriver/
│   │   ├── MainActivity.kt            phone: settings + "test & speak"
│   │   ├── Settings.kt                encrypted storage of key/model/prompt
│   │   ├── ai/OpenAiClient.kt         chat completions + Whisper transcription
│   │   ├── ai/ConversationManager.kt  shared conversation history
│   │   ├── audio/CarMicRecorder.kt    CarAudioRecord + audio focus + endpointing
│   │   ├── audio/WavUtil.kt           PCM → WAV for upload
│   │   ├── audio/Speaker.kt           on-device TextToSpeech wrapper
│   │   └── car/
│   │       ├── AiCarAppService.kt     Android Auto entry point
│   │       ├── AiSession.kt
│   │       └── ConversationScreen.kt  the state machine (IDLE→LISTEN→THINK→SPEAK)
│   └── res/…                          phone layout, theme, launcher icon
├── setup-toolchain.sh                 one-time Android SDK install (CLI route)
├── build-apk.sh                       assemble the debug APK
└── gradlew                            Gradle 8.9 wrapper
```

## Prerequisites

- A Galaxy phone (or any Android 10+ phone) with the **Android Auto** app.
- An **OpenAI API key** (platform.openai.com). Chat + Whisper are pay-as-you-go
  and cheap for personal use, but they are **not free** — see *Costs* below.
- To build: **JDK 17+** and the **Android SDK** (Android Studio is easiest).

## Build

### Option A — Android Studio (recommended)
1. `File ▸ Open…` and select this repository's root folder.
2. Let it sync, then `Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s)`.
3. Or press ▶ with your phone connected over USB to install directly.

### Option B — command line
```bash
./setup-toolchain.sh     # one-time: installs the SDK under ~/android-toolchain
./build-apk.sh           # produces codriver.apk
adb install -r codriver.apk
```

## Configure (on the phone)

Open **CoDriver AI** on the phone and fill in:

| Field | Default | Notes |
|-------|---------|-------|
| API base URL | `https://api.openai.com/v1` | change to use another provider |
| OpenAI API key | — | voice input (Whisper) + OpenAI chat models; stored encrypted, on-device |
| Anthropic API key | — | only needed when you pick a Claude model |
| Chat model | dropdown | latest 3 OpenAI + latest 3 Claude, or **Custom** to type any id; provider auto-detected |
| Transcription model | `whisper-1` | OpenAI's speech-to-text |
| Use OpenAI voice (cloud TTS) | off | on = cloud voice; off = on-device voice |
| OpenAI TTS model | `gpt-4o-mini-tts` | e.g. `gpt-4o-mini-tts`, `tts-1`, `tts-1-hd` |
| OpenAI TTS voice | `alloy` | e.g. `alloy`, `echo`, `nova`, `shimmer`, `onyx` |
| System prompt | *(driving-friendly preset)* | tune the assistant's style |

Tap **Save**, then **"Send test & speak"** to confirm the key/model work and that
you can hear the reply — all without a car.

## Running it

### In the Desktop Head Unit (works with a sideloaded APK)
This is the fastest way to try the full car experience.

1. On the phone: **Settings ▸ Apps ▸ Android Auto ▸ Additional settings in the
   app**. Scroll to **About**, tap the **version** row ~10 times to unlock
   **Developer mode**.
2. In Android Auto's overflow menu, enable developer options and
   **"Start head unit server"**.
3. On a computer with the Android SDK, run the DHU:
   ```bash
   adb forward tcp:5277 tcp:5277
   $ANDROID_SDK_ROOT/extras/google/auto/desktop-head-unit
   ```
4. CoDriver AI appears in the DHU launcher. (The DHU uses the computer's
   microphone for `CarAudioRecord`.)

### In a real car  ⚠️ important
Android Auto **will not load a sideloaded Car App Library app in a real
vehicle**. Google's testing docs are explicit: the "Unknown sources" developer
toggle *"applies to media, messaging notifications, and parked apps but doesn't
apply to apps built using the Android for Cars App Library."* In a car, the app
must come from a **trusted source**.

In practice that means a one-time trip through Google Play's **Internal testing**
track (no public review, and *not* subject to the "12 testers / 14 days" rule —
that only gates production access):

```bash
./make-upload-key.sh   # once: creates your upload key
./build-aab.sh         # -> codriver-release.aab  (the file you upload)
```

Then create a Play developer account, upload the AAB to Internal testing, add
your own Google account as a tester, and install from the opt-in link — now it's
a "trusted source" and shows up on your car's Android Auto screen.

**See [PLAY_CONSOLE.md](PLAY_CONSOLE.md) for the full click-by-click guide.**
There is no code-level workaround for the trusted-source rule — it's a platform
safety requirement.

## Customizing the AI ("any model you like")

Everything is OpenAI-compatible, so point the **base URL** (and model) at:

- **OpenAI / ChatGPT** — `https://api.openai.com/v1` (default)
- **Azure OpenAI** — your `…/openai/deployments/<name>` base + key
- **OpenRouter** — `https://openrouter.ai/api/v1`, pick any model id
- **Local (Ollama/llama.cpp)** — e.g. `http://<server>:11434/v1`
  (a plain-HTTP/LAN endpoint also needs a network-security-config tweak, since
  cleartext is blocked by default on Android 9+)

If a provider has no Whisper-style `audio/transcriptions` endpoint, keep the
transcription base URL on OpenAI and only change the chat side. (Splitting the
two endpoints is a small, obvious extension of `Settings` + `OpenAiClient`.)

## Costs

Each spoken turn makes two billed OpenAI calls: Whisper transcription (~$0.006
per minute of audio) and one chat completion (depends on the model and how much
conversation history has accumulated). `gpt-4o-mini` keeps this to fractions of
a cent per exchange; larger models cost more. On-device text-to-speech (the
default) is free; switching on the OpenAI voice adds a third billed call per
turn, priced per character of the reply.

## Limitations & notes

- **Not published / not for distribution** as-is. The declared Android Auto
  category (`POI`) is a placeholder so the host will load it — there is no
  "AI assistant" category, so this wouldn't pass Play's car-app review for public
  release. Internal testing (above) is the intended path.
- **Drive responsibly.** Keep interactions short; the whole point of the preset
  system prompt is brief, spoken-friendly answers.
- **Latency** is network-bound (record → upload → transcribe → chat → speak),
  typically a couple of seconds.
- **Minimum Car API level 5** is declared because `CarAudioRecord` requires it.

## Troubleshooting

- *"Open CoDriver AI on your phone and add your API key"* on the car screen →
  the key isn't saved; set it in the phone app.
- *"I couldn't reach the microphone"* → grant the phone's microphone permission
  to CoDriver AI (Settings ▸ Apps ▸ CoDriver AI ▸ Permissions).
- *HTTP 401 / 403 in the test* → wrong or unentitled API key.
- *Doesn't appear in a real car* → see "Running in a real car"; sideloaded
  builds only work in the DHU.
