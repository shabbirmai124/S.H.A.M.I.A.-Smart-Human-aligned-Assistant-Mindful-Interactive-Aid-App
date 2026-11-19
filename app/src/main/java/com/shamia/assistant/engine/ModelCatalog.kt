package com.shamia.assistant.engine

import com.shamia.assistant.model.AmbientTrack
import com.shamia.assistant.model.OfflineVoiceModelSpec
import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.VoiceModelKind

object ModelCatalog {

    val CATALOG: List<OpenSourceModelSpec> = listOf(
        OpenSourceModelSpec(
            id = "qwen25-0.5b-webgpu",
            name = "Qwen 2.5 0.5B Instruct",
            provider = "Alibaba Apache 2.0 · LiteRT Direct",
            params = "0.5B Q8",
            sizeLabel = "0.5 GB Download",
            minRamGb = 2,
            hfRepo = "litert-community/Qwen2.5-0.5B-Instruct",
            ollamaTag = "qwen2.5:0.5b",
            cloudModelId = "qwen-coder",
            hfInferenceModelId = "Qwen/Qwen2.5-0.5B-Instruct",
            styleProfile = "Fast, clear, concise local answers optimized for any Android phone.",
            summary = "Direct 1-tap ungated .task download (~529 MB) for 100% offline mobile inference."
        ),
        OpenSourceModelSpec(
            id = "qwen25-1.5b-edge",
            name = "Qwen 2.5 1.5B Instruct",
            provider = "Alibaba Apache 2.0 · LiteRT Direct",
            params = "1.5B Q8",
            sizeLabel = "1.5 GB Download",
            minRamGb = 3,
            hfRepo = "litert-community/Qwen2.5-1.5B-Instruct",
            ollamaTag = "qwen2.5:1.5b",
            cloudModelId = "qwen-coder",
            hfInferenceModelId = "Qwen/Qwen2.5-1.5B-Instruct",
            styleProfile = "Detailed, constructive explanations, coding help, and English tutoring.",
            summary = "Direct 1-tap ungated .task download (~1.5 GB) for strong offline reasoning on 3GB+ phones."
        ),
        OpenSourceModelSpec(
            id = "smollm2-360m-webgpu",
            name = "SmolLM 135M (Ultra-Fast)",
            provider = "Hugging Face Apache 2.0 · LiteRT Direct",
            params = "135M Q8",
            sizeLabel = "0.2 GB Download",
            minRamGb = 2,
            hfRepo = "litert-community/SmolLM-135M-Instruct",
            ollamaTag = "smollm2:360m",
            cloudModelId = "openai-fast",
            hfInferenceModelId = "HuggingFaceTB/SmolLM2-360M-Instruct",
            styleProfile = "Ultra-lightweight instant voice & chat responses on any phone.",
            summary = "Fastest ungated .task download (~175 MB) for instant offline local execution."
        ),
        OpenSourceModelSpec(
            id = "deepseek-r1-1.5b",
            name = "DeepSeek-R1 Distill 1.5B",
            provider = "DeepSeek MIT · LiteRT Direct",
            params = "1.5B Q8",
            sizeLabel = "1.6 GB Download",
            minRamGb = 4,
            hfRepo = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            ollamaTag = "deepseek-r1:1.5b",
            cloudModelId = "qwen-coder",
            hfInferenceModelId = "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B",
            styleProfile = "Step-by-step analytical reasoning, math, and technical problem solving.",
            summary = "Direct 1-tap ungated .task download (~1.6 GB) for deep offline reasoning."
        ),
        OpenSourceModelSpec(
            id = "phi35-mini-free",
            name = "Phi-4 Mini Instruct",
            provider = "Microsoft MIT · LiteRT Direct",
            params = "3.8B Q4",
            sizeLabel = "2.4 GB Download",
            minRamGb = 6,
            hfRepo = "litert-community/Phi-4-mini-instruct",
            ollamaTag = "phi4-mini",
            cloudModelId = "openai-fast",
            hfInferenceModelId = "microsoft/Phi-4-mini-instruct",
            styleProfile = "Clear textbook-precision explanations, STEM reasoning, and coding.",
            summary = "Direct 1-tap ungated Microsoft .task download for 6GB+ smartphones."
        ),
        OpenSourceModelSpec(
            id = "gemma-3-free",
            name = "Gemma 3 1B · Open Weights",
            provider = "Google Open Source · LiteRT",
            params = "1B Q4",
            sizeLabel = "0.6 GB Download",
            minRamGb = 2,
            hfRepo = "litert-community/Gemma3-1B-IT",
            ollamaTag = "gemma3:1b",
            cloudModelId = "gemini",
            hfInferenceModelId = "google/gemma-3-1b-it",
            styleProfile = "Warm, clear, articulate personal assistant answers.",
            summary = "Google's compact 1B open-weight model (supports .task import or HF token / Ollama)."
        ),
        OpenSourceModelSpec(
            id = "llama-32-free",
            name = "Llama 3.2 1B Instruct",
            provider = "Meta Open Source · LiteRT",
            params = "1.2B Q8",
            sizeLabel = "1.2 GB Download",
            minRamGb = 3,
            hfRepo = "litert-community/Llama-3.2-1B-Instruct",
            ollamaTag = "llama3.2:1b",
            cloudModelId = "llama",
            hfInferenceModelId = "meta-llama/Llama-3.2-1B-Instruct",
            styleProfile = "Natural, friendly, conversational human voice companion style.",
            summary = "Meta mobile-first 1B model (supports .task import, HF token, or local Ollama)."
        ),
        OpenSourceModelSpec(
            id = "llama-32-3b-free",
            name = "Llama 3.2 3B Instruct",
            provider = "Meta Open Source · LiteRT",
            params = "3.2B Q4",
            sizeLabel = "2.2 GB Download",
            minRamGb = 4,
            hfRepo = "litert-community/Llama-3.2-3B-Instruct",
            ollamaTag = "llama3.2:3b",
            cloudModelId = "llama",
            hfInferenceModelId = "meta-llama/Llama-3.2-3B-Instruct",
            styleProfile = "Thoughtful, empathetic, well-structured human reasoning and writing.",
            summary = "Balanced 3B Meta open model for coding, writing, and deep Q&A on 4GB+ phones."
        ),
        OpenSourceModelSpec(
            id = "qwen3-4b-edge",
            name = "Qwen 3 4B (Phone Efficiency King)",
            provider = "Alibaba Apache 2.0",
            params = "4B",
            sizeLabel = "2.5 GB Download",
            minRamGb = 4,
            hfRepo = "litert-community/Qwen2.5-3B-Instruct",
            ollamaTag = "qwen3:4b",
            cloudModelId = "qwen-coder",
            hfInferenceModelId = "Qwen/Qwen3-4B",
            styleProfile = "Crisp, constructive, practical assistant answers with clear takeaways.",
            summary = "High-efficiency 3B–4B open-weight model for 4nm & 6nm smartphones."
        ),
        OpenSourceModelSpec(
            id = "smollm2-1.7b-edge",
            name = "SmolLM2 1.7B Instruct",
            provider = "Hugging Face Apache 2.0",
            params = "1.7B",
            sizeLabel = "1.0 GB Download",
            minRamGb = 3,
            hfRepo = "litert-community/Qwen2.5-1.5B-Instruct",
            ollamaTag = "smollm2:1.7b",
            cloudModelId = "openai-fast",
            hfInferenceModelId = "HuggingFaceTB/SmolLM2-1.7B-Instruct",
            styleProfile = "Compact, brisk, direct human-toned responses.",
            summary = "Low-battery compact open model optimized for mobile ARM CPUs."
        ),
        OpenSourceModelSpec(
            id = "gemma-4-free",
            name = "Gemma 4 · Real-Time Search",
            provider = "Google",
            params = "4B",
            sizeLabel = "2.6 GB Download",
            minRamGb = 4,
            hfRepo = "litert-community/Gemma3-1B-IT",
            ollamaTag = "gemma3:4b",
            cloudModelId = "gemini",
            hfInferenceModelId = "google/gemma-3-4b-it",
            styleProfile = "Constructive, research-grounded, articulate executive summaries.",
            summary = "Synthesizes clean answers from local weights and optional web search."
        ),
        OpenSourceModelSpec(
            id = "qwen3-8b-chinese",
            name = "Qwen 3 8B (Open Weight)",
            provider = "Alibaba Apache 2.0",
            params = "8B",
            sizeLabel = "4.8 GB Download",
            minRamGb = 8,
            hfRepo = "Qwen/Qwen3-8B",
            ollamaTag = "qwen3:8b",
            cloudModelId = "qwen-coder",
            hfInferenceModelId = "Qwen/Qwen3-8B",
            styleProfile = "Analytical, structured, step-by-step technical and study reasoning.",
            summary = "Flagship 8B open-weight model for 8GB+ 4nm smartphones (Ollama / .task import)."
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
            summary = "Open-weight 20B Mixture-of-Experts model (12GB+ RAM / LAN Ollama)."
        ),
        OpenSourceModelSpec(
            id = "offline-core",
            name = "S.H.A.M.I.A. Offline Core",
            provider = "Built-In 0 MB Local Engine",
            params = "0 MB Instant",
            sizeLabel = "0 MB Built-In",
            minRamGb = 1,
            hfRepo = "litert-community/Qwen2.5-0.5B-Instruct",
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
