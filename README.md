# S.H.A.M.I.A. — Android Companion App (Kotlin + Jetpack Compose)

**Smart Human-aligned Assistant & Mindful Interactive Aid**  
**Exclusive Author & Owner:** Md Shabbir Mia · `shabbirmia520@gmail.com`

---

## Overview

S.H.A.M.I.A. is a native Android personal assistant built in **100% Kotlin + Jetpack Compose** with Material Design 3 (`compileSdk = 36`, `minSdk = 26`). It pairs an animated **Quantum Core** voice interface with strict **zero-surprise Permission Vault governance**, offline English fluency coaching, 4-4-6-2 mindful breathing resets, polyphonic focus audio synthesis, a private Routine & University Syllabus planner, and **strict per-model dynamic AI routing**.

---

## Key Features

### 1. Strict Per-Model Routing & Zero Default Fallback
- **Dynamic Model Dispatch:** Every selection in the top bar dropdown or **Models Hub** updates `selectedModelId` in `ShamiaViewModel` and routes directly to that exact model's `ollamaTag` (local Termux/LAN Ollama) or distinct `cloudModelId` (Open Cloud router), bypassing cache via unique request nonces/seeds and `Cache-Control: no-cache, no-store`.
- **Isolated Context Per Model:** Switching models prevents prior-model replies from leaking style into the newly chosen model (`sessionId` + `modelIdForTurn` filtering).
- **Verifiable Metadata & Logs:** Every assistant response card renders a **Model Verified** telemetry bar (`requestedModelId`, `executedModelId`, `providerEngine`, `Cache Bypassed`) and logs full routing details under tag `ShamiaModelRouter`.
- **New Chat:** Dedicated **New Chat** action in the top bar and Models Hub starts an isolated conversation session with the currently selected model.

### 2. Supported Open-Weight & Voice Models (14 LLMs + 4 Voice Models + Custom Loader)
- **Cloud + Real-Time Search:** `Gemma 4 · Real-Time Search` (`gemma3:4b` / `gemini-search`), `GPT-OSS 20B` (`gpt-oss:20b` / `openai-large`)
- **Under-10B & Chinese Open-Weight Flagships (4nm / 6nm Ready):** `Qwen 3 8B`, `Qwen 3 4B`, `MiniCPM-o 2.6 8B`, `GLM-4 9B Chat`
- **Mobile & Edge Models:** `Gemma 3`, `Llama 3.2 1B`, `Qwen 2.5 1.5B`, `Phi-3.5 Mini`, `SmolLM2 1.7B`, `Qwen 2.5 0.5B`, `SmolLM2 360M`
- **Built-In Offline Core:** `DeepSeek 2B · S.H.A.M.I.A. Offline Core` (`deepseek-r1:1.5b` + built-in offline companion engine)
- **Offline Voice Catalog:** `Whisper Tiny.en`, `Whisper Base.en`, `Whisper Small.en` (STT), `Kokoro 82M` (TTS)
- **Multi-Model Ensemble:** Combine up to 3 selected models concurrently with optional live search grounding.

### 3. Permission Vault & Safe Device Execution
All hardware/device requests pass through `IntentEngine` and `PermissionVault` (`ASK` / `ALWAYS` / `DENY`) with inline **Authorize Once**, **Always Allow**, and **Deny** controls:
- Phone Dialer (`ACTION_DIAL` — never auto-places calls)
- Verified App Launcher (`Spotify`, `YouTube`, `YouTube Music`, `Google Maps`, `Gmail`, `Google Calendar`, `WhatsApp`, `GitHub` via allowlisted `https://`)
- Device Controls: Wi-Fi Panel (`Settings.Panel.ACTION_WIFI`), Bluetooth Settings, Do Not Disturb state, Rear Camera Torch (`CameraManager.setTorchMode`), Live Window Brightness (`25%–100%`)
- Countdown Alarms & Focus Timers with `AudioTrack` 3-note chime
- Polyphonic Ambient Focus Audio Synthesizer (`MediaBar` with 3 tracks, Play/Pause, Next, Stop, Volume slider)
- Real Device Telemetry (`BatteryManager` & `ConnectivityManager`)

### 4. Private On-Device Routine, University Syllabus & Mindful Coaching
- **Daily Routine & University Syllabus Planner:** Full local `SharedPreferences` CRUD persistence (`RoutineSyllabusStore`) + 1-tap **Build Study Plan** generator.
- **Spoken English Fluency Coach:** Grammar & natural phrasing extraction rendered as inline `Natural English Tip` cards.
- **Mindful Support:** Detects stress/overwhelm and renders an interactive **4-4-6-2 Breathing Guide**.

---

## Project Architecture (`app/src/main/java/com/shamia/assistant/`)

| Layer | File | Responsibility |
| --- | --- | --- |
| Entry | `MainActivity.kt` | Edge-to-edge setup, `RECORD_AUDIO` runtime permission launcher, live brightness sync |
| Models | `model/ShamiaModels.kt` | Enums, data models, `OpenSourceModelSpec`, `ModelExecutionMeta`, `HudMessage`, `PendingAction` |
| Catalog | `engine/ModelCatalog.kt` | 14 open-weight models, 4 voice models, and 3 polyphonic ambient tracks |
| Router | `engine/OpenSourceAiRepository.kt` | Strict per-model Ollama / Server / Open-Cloud routing, Ensemble, hardware profiler |
| Search | `engine/WebSearchClient.kt` | Live Google News RSS + DuckDuckGo Instant Answer + Wikipedia search grounding |
| Intents | `engine/IntentEngine.kt` | Local device intent matching, phone validation, app allowlist, alarm parser |
| Offline | `engine/OfflineCompanion.kt` | Built-in offline companion (grammar rules, STAR interview prep, breathing, 4nm/6nm guide) |
| Prompts | `engine/ShamiaPrompts.kt` | Dynamic system instruction builder with active model identity directive & tip parser |
| Audio | `engine/VoiceAssistantEngine.kt` | Android `SpeechRecognizer`, `TextToSpeech`, polyphonic `AudioTrack` chord synth, alarm chime |
| Hardware | `engine/DeviceExecutor.kt` | Safe Android hardware/settings intents & Camera2 flashlight execution |
| Telemetry | `engine/DeviceTelemetryMonitor.kt` | Live `BatteryManager` and `ConnectivityManager` state flow |
| Vault | `engine/RoutineSyllabusStore.kt` | Private on-device `SharedPreferences` persistence for routine, syllabus, tasks, notes & permissions |
| State | `ui/ShamiaUiState.kt` & `ui/ShamiaViewModel.kt` | MVVM `StateFlow` orchestration, `startNewChat()`, isolated per-model chat turns |
| Theme | `ui/theme/Theme.kt` | Obsidian Cockpit Material Design 3 dark color scheme, typography, and shapes |
| HUD UI | `ui/ShamiaHudScreen.kt` & `ui/components/*` | `QuantumCore` Canvas orb, `TopCockpitBar`, model dropdown, `HudMessageCard`, `MediaBar`, `BreathingGuide` |
| Tabs | `ui/tabs/*` | `ModelsHubTab.kt`, `PlannerTab.kt`, `PhoneAndVaultTab.kt` |

---

## Optional Local Termux Ollama Setup on Android

To run quantized open-weight models completely offline on your 4nm/6nm Android phone:

```bash
pkg update && pkg install ollama
OLLAMA_ORIGINS="*" ollama serve &
ollama pull qwen3:4b
ollama pull deepseek-r1:1.5b
```

Open the **Models** tab inside S.H.A.M.I.A., verify `http://127.0.0.1:11434`, and tap **Save & Test Ollama**.
