package com.shamia.assistant.engine

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

class VoiceAssistantEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ambientJob: Job? = null
    @Volatile
    private var ambientVolume: Float = 0.25f

    fun initTts(onSpeakingChanged: (Boolean) -> Unit) {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale.UK)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.setPitch(0.94f)
                tts?.setSpeechRate(1.03f)
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = mainHandler.post { onSpeakingChanged(true) }.let {}
                    override fun onDone(utteranceId: String?) = mainHandler.post { onSpeakingChanged(false) }.let {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = mainHandler.post { onSpeakingChanged(false) }.let {}
                })
            }
        }
    }

    fun speak(text: String) {
        if (!ttsReady) return
        val clean = text
            .replace(Regex("\\[(?:Active )?Model:[^\\]]+\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[ENG_TIP:[^\\]]+\\]"), "")
            .replace(Regex("\\[COACH_TIP\\][\\s\\S]*?\\[/COACH_TIP\\]"), "")
            .replace(Regex("[*_`#]"), "")
            .trim()
        if (clean.isEmpty()) return
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "shamia_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("Android SpeechRecognizer is unavailable on this device.")
                return@post
            }
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}

                    override fun onPartialResults(partialResults: Bundle?) {
                        val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = list?.firstOrNull().orEmpty()
                        if (partial.isNotBlank()) onPartial(partial)
                    }

                    override fun onResults(results: Bundle?) {
                        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = list?.firstOrNull().orEmpty().trim()
                        if (best.isNotEmpty()) onFinal(best) else onError("No speech detected.")
                    }

                    override fun onError(error: Int) {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Grant Microphone permission in Android Settings."
                            SpeechRecognizer.ERROR_NO_MATCH -> "Did not catch that clearly—please try again."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
                            else -> "Voice recognition unavailable right now."
                        }
                        onError(msg)
                    }
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    fun stopListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
        }
    }

    fun setAmbientVolume(volume: Float) {
        ambientVolume = volume.coerceIn(0.05f, 0.55f)
    }

    fun startAmbientChord(chordFreqs: List<Double>, bpm: Int = 64) {
        stopAmbientTone()
        val freqs = if (chordFreqs.isNotEmpty()) chordFreqs else listOf(146.83, 220.0, 261.63, 329.63)
        val beatMs = (60_000L / bpm.coerceIn(45, 110))
        ambientJob = audioScope.launch {
            val sampleRate = 22050
            val chunkSamples = sampleRate / 2
            val buffer = ShortArray(chunkSamples)
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(chunkSamples * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            runCatching { track.play() }
            var sampleOffset = 0L
            var step = 0
            while (isActive) {
                val activeArp = freqs[step % freqs.size]
                val root = freqs.first()
                val fifth = freqs.getOrElse(1) { root * 1.5 }
                val vol = ambientVolume * 2200.0
                for (i in 0 until chunkSamples) {
                    val t = (sampleOffset + i).toDouble() / sampleRate
                    val pad = 0.45 * sin(2.0 * PI * root * t) +
                        0.30 * sin(2.0 * PI * fifth * t) +
                        0.25 * sin(2.0 * PI * activeArp * t)
                    val shimmer = 0.85 + 0.15 * sin(2.0 * PI * 0.25 * t)
                    buffer[i] = (pad * shimmer * vol).toInt().coerceIn(-32767, 32767).toShort()
                }
                sampleOffset += chunkSamples
                step += 1
                runCatching { track.write(buffer, 0, buffer.size) }
                if (!isActive) break
                delay((beatMs / 4L).coerceAtLeast(25L))
            }
            runCatching {
                track.stop()
                track.release()
            }
        }
    }

    fun stopAmbientTone() {
        ambientJob?.cancel()
        ambientJob = null
    }

    fun playAlarmChime() {
        audioScope.launch {
            val sampleRate = 22050
            val notes = listOf(523.25, 659.25, 783.99)
            for (freq in notes) {
                val count = (sampleRate * 0.28).toInt()
                val buf = ShortArray(count)
                for (i in 0 until count) {
                    val env = 1.0 - (i.toDouble() / count)
                    buf[i] = (sin(2.0 * PI * freq * i / sampleRate) * 7500 * env).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                runCatching {
                    track.write(buf, 0, buf.size)
                    track.play()
                    delay(320)
                    track.stop()
                    track.release()
                }
            }
        }
    }

    fun release() {
        stopAmbientTone()
        audioScope.cancel()
        mainHandler.post { speechRecognizer?.destroy() }
        tts?.stop()
        tts?.shutdown()
    }
}
