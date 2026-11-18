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
            provider = "Google",
            params = "4B – 27B Cloud",
            sizeLabel = "Cloud + Search",
            minRamGb = 4,
            hfRepo = "onnx-community/gemma-3-1b-it-ONNX",
            ollamaTag = "gemma3:4b",
            cloudModelId = "gemini-search",
            hfInferenceModelId = "google/gemma-3-4b-it",
            styleProfile = "Fast, concise, research-grounded executive answers."
        ),
        OpenSourceModelSpec(
            id = "qwen3-8b-chinese",
            name = "Qwen 3 8B (Chinese Open Weight)",
            provider = "Alibaba Apache 2.0",
            params = "8B",
            sizeLabel = "Cloud / ~4.8 GB Q4",
            minRamGb = 8,
            hfRepo = "onnx-community/Qwen3-4B-ONNX",
            ollamaTag = "qwen3:8b",
            cloudModelId = "qwen-coder",
            hfInferenceModelId = "Qwen/Qwen3-8B",
            styleProfile = "Analytical, structured, step-by-step technical reasoning."
        ),
        OpenSourceModelSpec(
            id = "qwen3-4b-edge",
            name = "Qwen 3 4B (Phone Efficiency King)",
            provider = "Alibaba Apache 2.0",
            params = "4B",
            sizeLabel = "Cloud / ~2.4 GB Q4",
            minRamGb = 4,
            hfRepo = "onnx-community/Qwen3-4B-ONNX",
            ollamaTag = "qwen3:4b",
            cloudModelId = "qwen",
            hfInferenceModelId = "Qwen/Qwen3-4B",
            styleProfile = "Crisp, practical mobile-efficiency answers."
        ),
        OpenSourceModelSpec(
            id = "minicpm-o-26",
            name = "MiniCPM-o 2.6 8B (Chinese Voice AI)",
            provider = "OpenBMB",
            params = "8B",
            sizeLabel = "Cloud / ~4.9 GB Q4",
            minRamGb = 8,
            hfRepo = "openbmb/MiniCPM-o-2_6",
            ollamaTag = "minicpm-o2.6",
            cloudModelId = "mistral",
            hfInferenceModelId = "openbmb/MiniCPM-o-2_6",
            styleProfile = "Natural, spoken-cadence conversational replies.",
            supportsOfflineInstall = false
        ),
        OpenSourceModelSpec(
            id = "glm-4-9b",
            name = "GLM-4 9B Chat (Zhipu Chinese Open)",
            provider = "Zhipu AI Apache 2.0",
            params = "9B",
            sizeLabel = "Cloud / ~5.3 GB Q4",
            minRamGb = 8,
            hfRepo = "THUDM/glm-4-9b-chat",
            ollamaTag = "glm4:9b",
            cloudModelId = "glm-4-9b",
            hfInferenceModelId = "THUDM/glm-4-9b-chat",
            styleProfile = "Precision structured reasoning and syllabus study breakdowns.",
            supportsOfflineInstall = false
        ),
        OpenSourceModelSpec(
            id = "gpt-oss-20b-free",
            name = "GPT-OSS 20B (Open Weights)",
            provider = "OpenAI Apache 2.0",
            params = "20B MoE",
            sizeLabel = "Cloud / ~13 GB Ollama",
            minRamGb = 16,
            ollamaTag = "gpt-oss:20b",
            cloudModelId = "openai-large",
            hfInferenceModelId = "openai/gpt-oss-20b",
            styleProfile = "Deep MoE reasoning with balanced trade-offs.",
            supportsOfflineInstall = false
        ),
        OpenSourceModelSpec(
            id = "gemma-3-free",
            name = "Gemma 3 · Open Weights",
            provider = "Google Open Source",
            params = "1B – 4B",
            sizeLabel = "Cloud / ~1.4 GB Q4",
            minRamGb = 2,
            hfRepo = "onnx-community/gemma-3-1b-it-ONNX",
            ollamaTag = "gemma3:1b",
            cloudModelId = "gemini",
            hfInferenceModelId = "google/gemma-3-4b-it",
            styleProfile = "Direct, lightweight answers with clear highlights."
        ),
        OpenSourceModelSpec(
            id = "llama-32-free",
            name = "Llama 3.2 1B Instruct",
            provider = "Meta Open Source",
            params = "1.2B",
            sizeLabel = "Cloud / ~780 MB Q4",
            minRamGb = 2,
            hfRepo = "onnx-community/Llama-3.2-1B-Instruct-q4f16",
            ollamaTag = "llama3.2:1b",
            cloudModelId = "llama",
            hfInferenceModelId = "meta-llama/Llama-3.2-1B-Instruct",
            styleProfile = "Natural, upbeat edge conversational style."
        ),
        OpenSourceModelSpec(
            id = "qwen25-1.5b-edge",
            name = "Qwen 2.5 1.5B Instruct",
            provider = "Alibaba Apache 2.0",
            params = "1.5B",
            sizeLabel = "Cloud / ~980 MB Q4",
            minRamGb = 3,
            hfRepo = "onnx-community/Qwen2.5-1.5B-Instruct",
            ollamaTag = "qwen2.5:1.5b",
            cloudModelId = "qwen",
            hfInferenceModelId = "Qwen/Qwen2.5-1.5B-Instruct",
            styleProfile = "Focused English fluency and concise Q&A."
        ),
        OpenSourceModelSpec(
            id = "phi35-mini-free",
            name = "Phi-3.5 Mini Instruct",
            provider = "Microsoft MIT",
            params = "3.8B Q4",
            sizeLabel = "Cloud / ~2.1 GB Q4",
            minRamGb = 6,
            hfRepo = "onnx-community/Phi-3.5-mini-instruct-onnx-web",
            ollamaTag = "phi3.5",
            cloudModelId = "phi",
            hfInferenceModelId = "microsoft/Phi-3.5-mini-instruct",
            styleProfile = "Dense textbook-precision logic and definitions."
        ),
        OpenSourceModelSpec(
            id = "smollm2-1.7b-edge",
            name = "SmolLM2 1.7B Instruct",
            provider = "Hugging Face Apache 2.0",
            params = "1.7B",
            sizeLabel = "Cloud / ~1.0 GB",
            minRamGb = 3,
            hfRepo = "HuggingFaceTB/SmolLM2-1.7B-Instruct",
            ollamaTag = "smollm2:1.7b",
            cloudModelId = "smollm2-1.7b",
            hfInferenceModelId = "HuggingFaceTB/SmolLM2-1.7B-Instruct",
            styleProfile = "Compact, brisk, zero-fluff responses."
        ),
        OpenSourceModelSpec(
            id = "qwen25-0.5b-webgpu",
            name = "Qwen 2.5 0.5B (Offline Cache)",
            provider = "Alibaba · Edge Q4",
            params = "0.5B",
            sizeLabel = "~390 MB Cache",
            minRamGb = 2,
            hfRepo = "onnx-community/Qwen2.5-0.5B-Instruct",
            ollamaTag = "qwen2.5:0.5b",
            cloudModelId = "qwen2.5-0.5b",
            hfInferenceModelId = "Qwen/Qwen2.5-0.5B-Instruct",
            styleProfile = "Ultra-short low-RAM answers."
        ),
        OpenSourceModelSpec(
            id = "smollm2-360m-webgpu",
            name = "SmolLM2 360M (Ultra-Light)",
            provider = "Hugging Face · Edge Q4",
            params = "0.36B",
            sizeLabel = "~260 MB Cache",
            minRamGb = 2,
            hfRepo = "HuggingFaceTB/SmolLM2-360M-Instruct",
            ollamaTag = "smollm2:360m",
            cloudModelId = "smollm2-360m",
            hfInferenceModelId = "HuggingFaceTB/SmolLM2-360M-Instruct",
            styleProfile = "Minimalist 1–2 line ultra-compact answers."
        ),
        OpenSourceModelSpec(
            id = "offline-core",
            name = "DeepSeek 2B · S.H.A.M.I.A. Offline Core",
            provider = "Built-In 0 MB Engine",
            params = "2B Distill / 0 MB Instant",
            sizeLabel = "0 MB Built-In",
            minRamGb = 1,
            hfRepo = "onnx-community/DeepSeek-R1-Distill-Qwen-1.5B-ONNX",
            ollamaTag = "deepseek-r1:1.5b",
            cloudModelId = "deepseek-reasoning",
            hfInferenceModelId = "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B",
            styleProfile = "Built-in offline companion and DeepSeek distilled reasoning.",
            isBuiltInOffline = true
        )
    )

    val VOICE_MODELS: List<OfflineVoiceModelSpec> = listOf(
        OfflineVoiceModelSpec(
            id = "whisper-tiny-en",
            name = "Whisper Tiny.en (Offline Mic)",
            kind = VoiceModelKind.STT,
            provider = "OpenAI MIT · Offline STT",
            sizeLabel = "~45 MB Cache",
            minRamGb = 1,
            hfRepo = "onnx-community/whisper-tiny.en",
            summary = "Fast offline English voice transcription."
        ),
        OfflineVoiceModelSpec(
            id = "whisper-base-en",
            name = "Whisper Base.en (HD Mic)",
            kind = VoiceModelKind.STT,
            provider = "OpenAI MIT · Offline STT",
            sizeLabel = "~85 MB Cache",
            minRamGb = 2,
            hfRepo = "onnx-community/whisper-base.en",
            summary = "Clear offline voice transcription for diverse accents."
        ),
        OfflineVoiceModelSpec(
            id = "whisper-small-en",
            name = "Whisper Small.en (Pro Accuracy)",
            kind = VoiceModelKind.STT,
            provider = "OpenAI MIT · Offline STT",
            sizeLabel = "~240 MB Cache",
            minRamGb = 4,
            hfRepo = "onnx-community/whisper-small.en",
            summary = "High-accuracy offline voice recognition."
        ),
        OfflineVoiceModelSpec(
            id = "kokoro-82m-tts",
            name = "Kokoro 82M (Offline Voice)",
            kind = VoiceModelKind.TTS,
            provider = "Apache 2.0 · Offline TTS",
            sizeLabel = "~85 MB Cache",
            minRamGb = 2,
            hfRepo = "onnx-community/Kokoro-82M-v1.0-ONNX",
            summary = "Natural clear neural voice output."
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
