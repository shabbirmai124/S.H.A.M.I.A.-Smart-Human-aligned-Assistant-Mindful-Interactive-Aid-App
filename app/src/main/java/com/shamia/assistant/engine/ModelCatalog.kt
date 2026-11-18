package com.shamia.assistant.engine

import com.shamia.assistant.model.AmbientTrack
import com.shamia.assistant.model.OfflineVoiceModelSpec
import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.VoiceModelKind

object ModelCatalog {

    val CATALOG: List<OpenSourceModelSpec> = listOf(
        OpenSourceModelSpec(
            id = "gemma-4-free",
            name = "Gemma 4 · Real-Time Search",
            provider = "Google · Live Google Search",
            params = "4B – 27B Cloud",
            sizeLabel = "Cloud + Google Search",
            minRamGb = 4,
            chipsetNote = "4nm / 6nm Ready",
            runtimeLabel = "Cloud + Google Search / Ollama",
            hfRepo = "onnx-community/gemma-3-1b-it-ONNX",
            ollamaTag = "gemma3:4b",
            cloudModelId = "gemini-search",
            hfInferenceModelId = "google/gemma-3-4b-it",
            styleProfile = "Fast, concise, research-grounded executive answers with direct source synthesis.",
            summary = "Gemma 4 assistant tier paired directly with real-time Google Search grounding and live device actions."
        ),
        OpenSourceModelSpec(
            id = "qwen3-8b-chinese",
            name = "Qwen 3 8B (Chinese Open Weight)",
            provider = "Alibaba Apache 2.0",
            params = "8B",
            sizeLabel = "Cloud / ~4.8 GB Q4",
            minRamGb = 8,
            chipsetNote = "4nm Flagship Phone Ready",
            hfRepo = "onnx-community/Qwen3-4B-ONNX",
            ollamaTag = "qwen3:8b",
            cloudModelId = "qwen-coder",
            hfInferenceModelId = "Qwen/Qwen3-8B",
            styleProfile = "Analytical, structured, step-by-step technical & academic reasoning style.",
            summary = "Flagship under-10B Chinese open-weight reasoning model delivering top accuracy for study, science, and chat."
        ),
        OpenSourceModelSpec(
            id = "qwen3-4b-edge",
            name = "Qwen 3 4B (Phone Efficiency King)",
            provider = "Alibaba Apache 2.0",
            params = "4B",
            sizeLabel = "Cloud / ~2.4 GB Q4",
            minRamGb = 4,
            chipsetNote = "4nm / 6nm Phone Ready",
            hfRepo = "onnx-community/Qwen3-4B-ONNX",
            ollamaTag = "qwen3:4b",
            cloudModelId = "qwen",
            hfInferenceModelId = "Qwen/Qwen3-4B",
            styleProfile = "Crisp, practical mobile-efficiency explanations focused on clarity and brevity.",
            summary = "Efficient 4B Chinese open-weight model rivaling 8B–14B models while fitting smoothly on 4nm/6nm phones."
        ),
        OpenSourceModelSpec(
            id = "minicpm-o-26",
            name = "MiniCPM-o 2.6 8B (Chinese Voice AI)",
            provider = "OpenBMB Tsinghua Open Source",
            params = "8B",
            sizeLabel = "Cloud / ~4.9 GB Q4",
            minRamGb = 8,
            chipsetNote = "4nm Flagship Voice & AI",
            ollamaTag = "minicpm-o2.6",
            cloudModelId = "mistral",
            hfInferenceModelId = "openbmb/MiniCPM-o-2_6",
            styleProfile = "Conversational, speech-friendly cadence designed for spoken voice readout.",
            summary = "Tsinghua OpenBMB under-10B open model optimized for accurate spoken voice assistant conversation."
        ),
        OpenSourceModelSpec(
            id = "glm-4-9b",
            name = "GLM-4 9B Chat (Zhipu Chinese Open)",
            provider = "Zhipu AI Apache 2.0",
            params = "9B",
            sizeLabel = "Cloud / ~5.3 GB Q4",
            minRamGb = 8,
            chipsetNote = "4nm Flagship Ready",
            ollamaTag = "glm4:9b",
            cloudModelId = "glm-4-9b",
            hfInferenceModelId = "THUDM/glm-4-9b-chat",
            styleProfile = "Precision bilingual & syllabus problem-solving with structured numbered breakdowns.",
            summary = "Popular Zhipu AI 9B Chinese open-weight model built for high-precision reasoning, coding, and syllabus study."
        ),
        OpenSourceModelSpec(
            id = "gpt-oss-20b-free",
            name = "GPT-OSS 20B (Open Weights)",
            provider = "OpenAI Apache 2.0",
            params = "20B MoE",
            sizeLabel = "Cloud / ~13 GB Ollama",
            minRamGb = 16,
            chipsetNote = "Cloud / 16GB+ Local",
            ollamaTag = "gpt-oss:20b",
            cloudModelId = "openai-large",
            hfInferenceModelId = "openai/gpt-oss-20b",
            styleProfile = "Deep MoE reasoning with nuanced trade-offs and thorough executive summaries.",
            summary = "20-billion parameter Apache 2.0 open-weight reasoning model for deep answers online or locally via Ollama."
        ),
        OpenSourceModelSpec(
            id = "gemma-3-free",
            name = "Gemma 3 · Open Weights",
            provider = "Google Open Source",
            params = "1B – 4B",
            sizeLabel = "Cloud / ~1.4 GB Q4",
            minRamGb = 2,
            chipsetNote = "6nm+ / 4nm Ready",
            hfRepo = "onnx-community/gemma-3-1b-it-ONNX",
            ollamaTag = "gemma3:1b",
            cloudModelId = "gemini",
            hfInferenceModelId = "google/gemma-3-4b-it",
            styleProfile = "Direct, light-footed open-weight helper with clear bullet highlights.",
            summary = "Runs online with live Google Search, or locally via Termux Ollama / on-device cache."
        ),
        OpenSourceModelSpec(
            id = "llama-32-free",
            name = "Llama 3.2 1B Instruct",
            provider = "Meta Open Source",
            params = "1.2B",
            sizeLabel = "Cloud / ~780 MB Q4",
            minRamGb = 2,
            chipsetNote = "6nm+ / 4nm Ready",
            hfRepo = "onnx-community/Llama-3.2-1B-Instruct-q4f16",
            ollamaTag = "llama3.2:1b",
            cloudModelId = "llama",
            hfInferenceModelId = "meta-llama/Llama-3.2-1B-Instruct",
            styleProfile = "Natural, upbeat Meta Llama conversational tone optimized for edge speed.",
            summary = "Meta mobile-optimized 1B open model. Instant chat online, or run locally via Termux Ollama."
        ),
        OpenSourceModelSpec(
            id = "qwen25-1.5b-edge",
            name = "Qwen 2.5 1.5B Instruct",
            provider = "Alibaba Apache 2.0",
            params = "1.5B",
            sizeLabel = "Cloud / ~980 MB Q4",
            minRamGb = 3,
            chipsetNote = "6nm+ / 4nm Ready",
            hfRepo = "onnx-community/Qwen2.5-1.5B-Instruct",
            ollamaTag = "qwen2.5:1.5b",
            cloudModelId = "qwen",
            hfInferenceModelId = "Qwen/Qwen2.5-1.5B-Instruct",
            styleProfile = "Focused English fluency & structured Q&A style.",
            summary = "Balanced 1.5B open-source model strong at English fluency coaching and structured replies."
        ),
        OpenSourceModelSpec(
            id = "phi35-mini-free",
            name = "Phi-3.5 Mini Instruct",
            provider = "Microsoft MIT Open Source",
            params = "3.8B Q4",
            sizeLabel = "Cloud / ~2.1 GB Q4",
            minRamGb = 6,
            chipsetNote = "4nm Flagship Ready",
            hfRepo = "onnx-community/Phi-3.5-mini-instruct-onnx-web",
            ollamaTag = "phi3.5",
            cloudModelId = "phi",
            hfInferenceModelId = "microsoft/Phi-3.5-mini-instruct",
            styleProfile = "Dense textbook-precision logic, math, and concise CS definitions.",
            summary = "High-accuracy 4nm flagship smartphone & desktop model with MIT open-source licensing."
        ),
        OpenSourceModelSpec(
            id = "smollm2-1.7b-edge",
            name = "SmolLM2 1.7B Instruct",
            provider = "Hugging Face Apache 2.0",
            params = "1.7B",
            sizeLabel = "Cloud / ~1.0 GB",
            minRamGb = 3,
            chipsetNote = "6nm+ Ready",
            hfRepo = "HuggingFaceTB/SmolLM2-1.7B-Instruct",
            ollamaTag = "smollm2:1.7b",
            cloudModelId = "smollm2-1.7b",
            hfInferenceModelId = "HuggingFaceTB/SmolLM2-1.7B-Instruct",
            styleProfile = "Compact, brisk, zero-fluff mobile responses.",
            summary = "Compact ~2B open model engineered for low-power mobile CPUs and local Ollama servers."
        ),
        OpenSourceModelSpec(
            id = "qwen25-0.5b-webgpu",
            name = "Qwen 2.5 0.5B (Offline Cache)",
            provider = "On-Device / Termux Q4",
            params = "0.5B",
            sizeLabel = "~390 MB Cache",
            minRamGb = 2,
            chipsetNote = "Any 6nm+ Phone",
            runtimeLabel = "Local Ollama / Edge Cloud",
            hfRepo = "onnx-community/Qwen2.5-0.5B-Instruct",
            ollamaTag = "qwen2.5:0.5b",
            cloudModelId = "qwen2.5-0.5b",
            hfInferenceModelId = "Qwen/Qwen2.5-0.5B-Instruct",
            styleProfile = "Ultra-short, punchy answers tailored to low-RAM edge constraints.",
            summary = "Ultra-light 0.5B quantized model designed for low-RAM Android handsets."
        ),
        OpenSourceModelSpec(
            id = "smollm2-360m-webgpu",
            name = "SmolLM2 360M (Ultra-Light)",
            provider = "On-Device / Termux Q4",
            params = "0.36B",
            sizeLabel = "~260 MB Cache",
            minRamGb = 2,
            chipsetNote = "Any Phone",
            runtimeLabel = "Local Ollama / Edge Cloud",
            hfRepo = "HuggingFaceTB/SmolLM2-360M-Instruct",
            ollamaTag = "smollm2:360m",
            cloudModelId = "smollm2-360m",
            hfInferenceModelId = "HuggingFaceTB/SmolLM2-360M-Instruct",
            styleProfile = "Minimalist 1–2 line ultra-compact answers.",
            summary = "Ultra-compact open model built to run smoothly offline even on entry-level 6nm/8nm phones."
        ),
        OpenSourceModelSpec(
            id = "offline-core",
            name = "DeepSeek 2B · S.H.A.M.I.A. Offline Core",
            provider = "DeepSeek-R1 2B Engine / Built-In",
            params = "2B Distill / 0 MB Instant",
            sizeLabel = "0 MB Instant · Optional ~1.0 GB Q4",
            minRamGb = 1,
            chipsetNote = "Any Device (6nm+ / 4nm Ready)",
            runtimeLabel = "Instant 0 MB Built-In",
            hfRepo = "onnx-community/DeepSeek-R1-Distill-Qwen-1.5B-ONNX",
            ollamaTag = "deepseek-r1:1.5b",
            cloudModelId = "deepseek-reasoning",
            hfInferenceModelId = "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B",
            styleProfile = "DeepSeek step-by-step distilled reasoning & built-in offline companion.",
            summary = "DeepSeek 2B-class offline edition: instant 0 MB engine for phone controls, alarms, tasks, English tips & breathing, plus DeepSeek Termux Q4.",
            isBuiltInOffline = true
        )
    )

    val VOICE_MODELS: List<OfflineVoiceModelSpec> = listOf(
        OfflineVoiceModelSpec(
            id = "whisper-tiny-en",
            name = "Whisper Tiny.en (Offline Mic)",
            kind = VoiceModelKind.STT,
            provider = "OpenAI MIT · On-Device Offline",
            sizeLabel = "~45 MB Cache",
            minRamGb = 1,
            hfRepo = "onnx-community/whisper-tiny.en",
            summary = "Fast offline English voice transcription on Android."
        ),
        OfflineVoiceModelSpec(
            id = "whisper-base-en",
            name = "Whisper Base.en (HD Mic)",
            kind = VoiceModelKind.STT,
            provider = "OpenAI MIT · On-Device Offline",
            sizeLabel = "~85 MB Cache",
            minRamGb = 2,
            hfRepo = "onnx-community/whisper-base.en",
            summary = "Higher-accuracy offline English speech recognition for diverse accents."
        ),
        OfflineVoiceModelSpec(
            id = "whisper-small-en",
            name = "Whisper Small.en (Pro Accuracy)",
            kind = VoiceModelKind.STT,
            provider = "OpenAI MIT · On-Device Offline",
            sizeLabel = "~240 MB Cache",
            minRamGb = 4,
            hfRepo = "onnx-community/whisper-small.en",
            summary = "Flagship phone offline voice recognition accuracy for lectures, terms, and conversation."
        ),
        OfflineVoiceModelSpec(
            id = "kokoro-82m-tts",
            name = "Kokoro 82M (Offline Voice)",
            kind = VoiceModelKind.TTS,
            provider = "Apache 2.0 · On-Device Offline",
            sizeLabel = "~85 MB Cache",
            minRamGb = 2,
            hfRepo = "onnx-community/Kokoro-82M-v1.0-ONNX",
            summary = "Natural human-sounding neural voice output cached and synthesized offline."
        )
    )

    val AMBIENT_TRACKS: List<AmbientTrack> = listOf(
        AmbientTrack(
            id = "track-obsidian-focus",
            title = "Obsidian Atelier Resonance",
            artist = "Shabbir Sound Lab",
            mood = "Focus",
            bpm = 64,
            baseFreqHz = 146.83,
            chordFreqs = listOf(146.83, 220.0, 261.63, 329.63)
        ),
        AmbientTrack(
            id = "track-midnight-drive",
            title = "Midnight Telemetry Pulse",
            artist = "Shabbir Sound Lab",
            mood = "Energize",
            bpm = 78,
            baseFreqHz = 130.81,
            chordFreqs = listOf(130.81, 196.0, 246.94, 293.66)
        ),
        AmbientTrack(
            id = "track-calm-horizon",
            title = "Calm Horizon Breathing",
            artist = "Shabbir Sound Lab",
            mood = "Calm",
            bpm = 52,
            baseFreqHz = 110.0,
            chordFreqs = listOf(110.0, 164.81, 220.0, 246.94)
        )
    )
}
