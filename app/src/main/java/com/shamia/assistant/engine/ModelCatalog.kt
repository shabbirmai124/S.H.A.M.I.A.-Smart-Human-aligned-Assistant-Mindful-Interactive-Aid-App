package com.shamia.assistant.engine

import com.shamia.assistant.model.AmbientTrack
import com.shamia.assistant.model.OfflineVoiceModelSpec
import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.VoiceModelKind

object ModelCatalog {

    val CATALOG: List<OpenSourceModelSpec> = listOf(
        OpenSourceModelSpec(
            id = "gemma-3-free",
            name = "Gemma 3 · Open Weights",
            provider = "Google Open Source",
            params = "1B – 4B",
            sizeLabel = "1.4 GB Download",
            minRamGb = 2,
            hfRepo = "onnx-community/gemma-3-1b-it-ONNX",
            ollamaTag = "gemma3:1b",
            cloudModelId = "gemini",
            hfInferenceModelId = "google/gemma-3-4b-it",
            styleProfile = "Warm, clear, articulate human-aligned personal assistant answers.",
            summary = "Fast everyday conversation, reasoning, English coaching & study planning."
        ),
        OpenSourceModelSpec(
            id = "llama-32-free",
            name = "Llama 3.2 1B Instruct",
            provider = "Meta Open Source",
            params = "1.2B",
            sizeLabel = "0.8 GB Download",
            minRamGb = 2,
            hfRepo = "onnx-community/Llama-3.2-1B-Instruct-q4f16",
            ollamaTag = "llama3.2:1b",
            cloudModelId = "llama",
            hfInferenceModelId = "meta-llama/Llama-3.2-1B-Instruct",
            styleProfile = "Natural, friendly, conversational human voice companion style.",
            summary = "Meta mobile-first open-weight model for fast offline chat & device control."
        ),
        OpenSourceModelSpec(
            id = "llama-32-3b-free",
            name = "Llama 3.2 3B Instruct",
            provider = "Meta Open Source",
            params = "3.2B",
            sizeLabel = "1.9 GB Download",
            minRamGb = 4,
            hfRepo = "hugging-quants/Llama-3.2-3B-Instruct-Q4_K_M-GGUF",
            ollamaTag = "llama3.2:3b",
            cloudModelId = "llama",
            hfInferenceModelId = "meta-llama/Llama-3.2-3B-Instruct",
            styleProfile = "Thoughtful, empathetic, well-structured human reasoning and writing.",
            summary = "Balanced 3B Meta open model for coding, writing, and deep Q&A on 4GB+ phones."
        ),
        OpenSourceModelSpec(
            id = "qwen25-1.5b-edge",
            name = "Qwen 2.5 1.5B Instruct",
            provider = "Alibaba Apache 2.0",
            params = "1.5B",
            sizeLabel = "1.0 GB Download",
            minRamGb = 3,
            hfRepo = "onnx-community/Qwen2.5-1.5B-Instruct",
            ollamaTag = "qwen2.5:1.5b",
            cloudModelId = "qwen-coder",
            hfInferenceModelId = "Qwen/Qwen2.5-1.5B-Instruct",
            styleProfile = "Focused, constructive, human-friendly explanations and coding help.",
            summary = "High-accuracy 1.5B model for English tutoring, coding, and structured answers."
        ),
        OpenSourceModelSpec(
            id = "qwen3-4b-edge",
            name = "Qwen 3 4B (Phone Efficiency King)",
            provider = "Alibaba Apache 2.0",
            params = "4B",
            sizeLabel = "2.4 GB Download",
            minRamGb = 4,
            hfRepo = "onnx-community/Qwen3-4B-ONNX",
            ollamaTag = "qwen3:4b",
            cloudModelId = "qwen-coder",
            hfInferenceModelId = "Qwen/Qwen3-4B",
            styleProfile = "Crisp, constructive, practical assistant answers with clear takeaways.",
            summary = "Top efficiency 4B model for 4nm & 6nm smartphones."
        ),
        OpenSourceModelSpec(
            id = "smollm2-1.7b-edge",
            name = "SmolLM2 1.7B Instruct",
            provider = "Hugging Face Apache 2.0",
            params = "1.7B",
            sizeLabel = "1.0 GB Download",
            minRamGb = 3,
            hfRepo = "HuggingFaceTB/SmolLM2-1.7B-Instruct",
            ollamaTag = "smollm2:1.7b",
            cloudModelId = "openai-fast",
            hfInferenceModelId = "HuggingFaceTB/SmolLM2-1.7B-Instruct",
            styleProfile = "Compact, brisk, direct human-toned responses.",
            summary = "Low-battery compact open model optimized for mobile ARM CPUs."
        ),
        OpenSourceModelSpec(
            id = "qwen25-0.5b-webgpu",
            name = "Qwen 2.5 0.5B (Offline Cache)",
            provider = "Alibaba · Edge Q4",
            params = "0.5B",
            sizeLabel = "0.4 GB Download",
            minRamGb = 2,
            hfRepo = "onnx-community/Qwen2.5-0.5B-Instruct",
            ollamaTag = "qwen2.5:0.5b",
            cloudModelId = "qwen-coder",
            hfInferenceModelId = "Qwen/Qwen2.5-0.5B-Instruct",
            styleProfile = "Fast, concise, low-RAM conversational answers.",
            summary = "Ultra-light 0.4 GB download for instant local phone execution."
        ),
        OpenSourceModelSpec(
            id = "smollm2-360m-webgpu",
            name = "SmolLM2 360M (Ultra-Light)",
            provider = "Hugging Face · Edge Q4",
            params = "0.36B",
            sizeLabel = "0.3 GB Download",
            minRamGb = 2,
            hfRepo = "HuggingFaceTB/SmolLM2-360M-Instruct",
            ollamaTag = "smollm2:360m",
            cloudModelId = "openai-fast",
            hfInferenceModelId = "HuggingFaceTB/SmolLM2-360M-Instruct",
            styleProfile = "Quick, friendly 2–3 sentence answers for ultra-fast voice interaction.",
            summary = "Smallest 0.3 GB open-weight model for any Android device."
        ),
        OpenSourceModelSpec(
            id = "gemma-4-free",
            name = "Gemma 4 · Real-Time Search",
            provider = "Google",
            params = "4B",
            sizeLabel = "2.6 GB Download",
            minRamGb = 4,
            hfRepo = "onnx-community/gemma-3-1b-it-ONNX",
            ollamaTag = "gemma3:4b",
            cloudModelId = "gemini",
            hfInferenceModelId = "google/gemma-3-4b-it",
            styleProfile = "Constructive, research-grounded, articulate executive summaries.",
            summary = "Synthesizes clean answers from local knowledge and optional web search."
        ),
        OpenSourceModelSpec(
            id = "phi35-mini-free",
            name = "Phi-3.5 Mini Instruct",
            provider = "Microsoft MIT",
            params = "3.8B Q4",
            sizeLabel = "2.1 GB Download",
            minRamGb = 6,
            hfRepo = "onnx-community/Phi-3.5-mini-instruct-onnx-web",
            ollamaTag = "phi3.5",
            cloudModelId = "openai-fast",
            hfInferenceModelId = "microsoft/Phi-3.5-mini-instruct",
            styleProfile = "Clear textbook-precision explanations and step-by-step logic.",
            summary = "Microsoft MIT-licensed reasoning model for university study & coding."
        ),
        OpenSourceModelSpec(
            id = "qwen3-8b-chinese",
            name = "Qwen 3 8B (Open Weight)",
            provider = "Alibaba Apache 2.0",
            params = "8B",
            sizeLabel = "4.8 GB Download",
            minRamGb = 8,
            hfRepo = "onnx-community/Qwen3-4B-ONNX",
            ollamaTag = "qwen3:8b",
            cloudModelId = "qwen-coder",
            hfInferenceModelId = "Qwen/Qwen3-8B",
            styleProfile = "Analytical, structured, step-by-step technical and study reasoning.",
            summary = "Flagship 8B open-weight model for 8GB+ 4nm smartphones."
        ),
        OpenSourceModelSpec(
            id = "minicpm-o-26",
            name = "MiniCPM-o 2.6 8B (Voice AI)",
            provider = "OpenBMB",
            params = "8B",
            sizeLabel = "4.9 GB Download",
            minRamGb = 8,
            hfRepo = "openbmb/MiniCPM-o-2_6",
            ollamaTag = "minicpm-o2.6",
            cloudModelId = "mistral",
            hfInferenceModelId = "openbmb/MiniCPM-o-2_6",
            styleProfile = "Natural spoken-cadence conversational replies for voice chat.",
            summary = "Voice-first multimodal open model for natural spoken dialogue."
        ),
        OpenSourceModelSpec(
            id = "glm-4-9b",
            name = "GLM-4 9B Chat (Open Weight)",
            provider = "Zhipu AI Apache 2.0",
            params = "9B",
            sizeLabel = "5.3 GB Download",
            minRamGb = 8,
            hfRepo = "THUDM/glm-4-9b-chat",
            ollamaTag = "glm4:9b",
            cloudModelId = "openai-large",
            hfInferenceModelId = "THUDM/glm-4-9b-chat",
            styleProfile = "Precision structured reasoning and university syllabus breakdowns.",
            summary = "9B open-weight model for deep academic & technical analysis."
        ),
        OpenSourceModelSpec(
            id = "gpt-oss-20b-free",
            name = "GPT-OSS 20B (Open Weights)",
            provider = "OpenAI Apache 2.0",
            params = "20B MoE",
            sizeLabel = "12.5 GB Download",
            minRamGb = 12,
            hfRepo = "openai/gpt-oss-20b",
            ollamaTag = "gpt-oss:20b",
            cloudModelId = "openai-large",
            hfInferenceModelId = "openai/gpt-oss-20b",
            styleProfile = "Warm, nuanced, high-depth human reasoning and problem solving.",
            summary = "Open-weight 20B Mixture-of-Experts model."
        ),
        OpenSourceModelSpec(
            id = "offline-core",
            name = "S.H.A.M.I.A. Offline Core",
            provider = "Built-In 0 MB Local Engine",
            params = "0 MB Instant",
            sizeLabel = "0 MB Built-In",
            minRamGb = 1,
            hfRepo = "onnx-community/Qwen2.5-0.5B-Instruct",
            ollamaTag = "qwen2.5:0.5b",
            cloudModelId = "openai-fast",
            hfInferenceModelId = "Qwen/Qwen2.5-0.5B-Instruct",
            styleProfile = "Instant 0 MB on-device voice companion, English coach, and phone controller.",
            summary = "Zero-download built-in local engine for instant offline assistance on any phone.",
            isBuiltInOffline = true
        )
    )

    val VOICE_MODELS: List<OfflineVoiceModelSpec> = listOf(
        OfflineVoiceModelSpec(
            id = "whisper-tiny-en",
            name = "Whisper Tiny.en (Offline Mic)",
            kind = VoiceModelKind.STT,
            provider = "OpenAI MIT · Offline STT",
            sizeLabel = "0.05 GB (45 MB) Download",
            minRamGb = 1,
            hfRepo = "onnx-community/whisper-tiny.en",
            summary = "Fast offline English voice transcription."
        ),
        OfflineVoiceModelSpec(
            id = "whisper-base-en",
            name = "Whisper Base.en (HD Mic)",
            kind = VoiceModelKind.STT,
            provider = "OpenAI MIT · Offline STT",
            sizeLabel = "0.09 GB (85 MB) Download",
            minRamGb = 2,
            hfRepo = "onnx-community/whisper-base.en",
            summary = "Clear offline voice transcription for diverse accents."
        ),
        OfflineVoiceModelSpec(
            id = "whisper-small-en",
            name = "Whisper Small.en (Pro Accuracy)",
            kind = VoiceModelKind.STT,
            provider = "OpenAI MIT · Offline STT",
            sizeLabel = "0.24 GB (240 MB) Download",
            minRamGb = 4,
            hfRepo = "onnx-community/whisper-small.en",
            summary = "High-accuracy offline voice recognition."
        ),
        OfflineVoiceModelSpec(
            id = "kokoro-82m-tts",
            name = "Kokoro 82M (Offline Voice)",
            kind = VoiceModelKind.TTS,
            provider = "Apache 2.0 · Offline TTS",
            sizeLabel = "0.09 GB (85 MB) Download",
            minRamGb = 2,
            hfRepo = "onnx-community/Kokoro-82M-v1.0-ONNX",
            summary = "Natural clear neural voice output (Female & Male)."
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
