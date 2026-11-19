package com.shamia.assistant.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shamia.assistant.MainActivity
import com.shamia.assistant.model.OpenSourceModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class BackgroundModelState(
    val downloadProgress: Map<String, Pair<Int, String>> = emptyMap(),
    val partialPercents: Map<String, Int> = emptyMap(),
    val downloadErrors: Map<String, String> = emptyMap(),
    val newlyInstalledModelIds: Set<String> = emptySet(),
    val loadedInRamModelId: String? = null,
    val loadingInRamModelId: String? = null,
    val loadedBackendLabel: String = "",
    val activeTaskLabel: String = "",
    val activeTaskProgressPct: Int = -1
) {
    val hasActiveWork: Boolean
        get() = downloadProgress.isNotEmpty() || loadingInRamModelId != null
}

/**
 * Process-scoped manager that survives Activity backgrounding, screen-off, and app switching.
 * Coordinates with [ModelBackgroundService] (dataSync foreground service + partial wake lock)
 * so 8x Turbo model downloads and local RAM preloading continue uninterrupted when the user
 * leaves the app screen.
 */
object ModelBackgroundHub {

    private const val TAG = "ShamiaModelBgHub"

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private val preloadLock = Any()
    @Volatile
    private var activePreloadJob: Job? = null
    @Volatile
    private var activePreloadModelId: String? = null

    private val _state = MutableStateFlow(BackgroundModelState())
    val state: StateFlow<BackgroundModelState> = _state.asStateFlow()

    @Volatile
    private var lastNotificationPushMs: Long = 0L

    init {
        // Keep RAM residency state strictly synchronized with OnDeviceLlmEngine (single source of truth)
        appScope.launch {
            OnDeviceLlmEngine.runtimeState.collect { rt ->
                _state.update { cur ->
                    cur.copy(
                        loadedInRamModelId = rt.loadedModelId,
                        loadingInRamModelId = rt.loadingModelId,
                        loadedBackendLabel = rt.backendLabel
                    )
                }
            }
        }
    }

    fun syncInitialPartials(context: Context, modelIds: List<String>) {
        val partials = ModelDownloadManager.scanAllPartialDownloadPercents(context, modelIds)
        val rt = OnDeviceLlmEngine.runtimeState.value
        _state.update { cur ->
            cur.copy(
                partialPercents = cur.partialPercents + partials,
                loadedInRamModelId = rt.loadedModelId,
                loadingInRamModelId = rt.loadingModelId,
                loadedBackendLabel = rt.backendLabel
            )
        }
    }

    fun isDownloading(modelId: String): Boolean = activeDownloadJobs.containsKey(modelId)

    fun cancelStalePreload(exceptModelId: String? = null) {
        synchronized(preloadLock) {
            if (activePreloadModelId != null && activePreloadModelId != exceptModelId) {
                activePreloadJob?.cancel()
                activePreloadJob = null
                activePreloadModelId = null
            }
        }
    }

    fun enqueueDownload(
        context: Context,
        spec: OpenSourceModelSpec,
        ollamaEndpoint: String,
        silent: Boolean = false,
        onFinished: ((ok: Boolean, message: String) -> Unit)? = null
    ) {
        val appCtx = context.applicationContext
        if (activeDownloadJobs.containsKey(spec.id)) return

        _state.update { cur ->
            cur.copy(
                downloadProgress = cur.downloadProgress + (spec.id to (1 to "Starting background Turbo download...")),
                downloadErrors = cur.downloadErrors - spec.id,
                activeTaskLabel = "Downloading ${spec.name}",
                activeTaskProgressPct = 1
            )
        }
        startOrRefreshService(appCtx, "Downloading ${spec.name}", 1, force = true)

        val job = appScope.launch {
            try {
                val (ok, detailMessage) = ModelDownloadManager.installModelOffline(
                    context = appCtx,
                    spec = spec,
                    ollamaEndpoint = ollamaEndpoint
                ) { pct, statusText ->
                    _state.update { cur ->
                        cur.copy(
                            downloadProgress = cur.downloadProgress + (spec.id to (pct to statusText)),
                            activeTaskLabel = "${spec.name} · $statusText",
                            activeTaskProgressPct = pct
                        )
                    }
                    startOrRefreshService(appCtx, "${spec.name} · $statusText", pct, force = false)
                }

                val store = RoutineSyllabusStore(appCtx)
                if (ok) {
                    val savedInstalled = store.loadInstalledModelIds() + spec.id
                    store.saveInstalledModelIds(savedInstalled)
                    if (!silent) {
                        store.saveSelectedModelId(spec.id)
                    }
                }

                val partialPct = if (!ok) ModelDownloadManager.getPartialDownloadPercent(appCtx, spec.id) else null
                _state.update { cur ->
                    val nextInstalled = if (ok) cur.newlyInstalledModelIds + spec.id else cur.newlyInstalledModelIds
                    val nextPartials = if (ok) {
                        cur.partialPercents - spec.id
                    } else if (partialPct != null) {
                        cur.partialPercents + (spec.id to partialPct)
                    } else {
                        cur.partialPercents - spec.id
                    }
                    val nextErrors = if (ok) {
                        cur.downloadErrors - spec.id
                    } else {
                        cur.downloadErrors + (spec.id to detailMessage)
                    }
                    cur.copy(
                        downloadProgress = cur.downloadProgress - spec.id,
                        partialPercents = nextPartials,
                        downloadErrors = nextErrors,
                        newlyInstalledModelIds = nextInstalled
                    )
                }
                onFinished?.invoke(ok, detailMessage)

                if (ok) {
                    // Immediately load weights into RAM inside the same active background service!
                    runPreloadInternal(appCtx, spec.id, spec.name)
                }
            } catch (_: CancellationException) {
                val partialPct = ModelDownloadManager.getPartialDownloadPercent(appCtx, spec.id)
                _state.update { cur ->
                    val nextPartials = if (partialPct != null) cur.partialPercents + (spec.id to partialPct) else cur.partialPercents
                    cur.copy(
                        downloadProgress = cur.downloadProgress - spec.id,
                        partialPercents = nextPartials
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Background download error for ${spec.id}", t)
                val errMsg = t.message ?: "Download interrupted"
                val partialPct = ModelDownloadManager.getPartialDownloadPercent(appCtx, spec.id)
                _state.update { cur ->
                    val nextPartials = if (partialPct != null) cur.partialPercents + (spec.id to partialPct) else cur.partialPercents
                    cur.copy(
                        downloadProgress = cur.downloadProgress - spec.id,
                        partialPercents = nextPartials,
                        downloadErrors = cur.downloadErrors + (spec.id to errMsg)
                    )
                }
                onFinished?.invoke(false, errMsg)
            } finally {
                activeDownloadJobs.remove(spec.id)
                stopServiceIfIdle(appCtx)
            }
        }
        activeDownloadJobs[spec.id] = job
    }

    fun enqueuePreload(context: Context, modelId: String, modelName: String) {
        val appCtx = context.applicationContext
        if (modelId == "offline-core") {
            cancelStalePreload(null)
            return
        }
        if (!OnDeviceLlmEngine.hasRealLocalWeights(appCtx, modelId)) return
        if (OnDeviceLlmEngine.isModelHotInRam(modelId)) {
            return
        }

        synchronized(preloadLock) {
            if (activePreloadModelId == modelId && activePreloadJob?.isActive == true) {
                return
            }
            // Cancel any stale preload for an older model so only the user's chosen model loads into RAM
            activePreloadJob?.cancel()
            activePreloadModelId = modelId
            activePreloadJob = appScope.launch {
                try {
                    runPreloadInternal(appCtx, modelId, modelName)
                } finally {
                    synchronized(preloadLock) {
                        if (activePreloadModelId == modelId) {
                            activePreloadJob = null
                            activePreloadModelId = null
                        }
                    }
                    stopServiceIfIdle(appCtx)
                }
            }
        }
    }

    private suspend fun runPreloadInternal(appCtx: Context, modelId: String, modelName: String) {
        _state.update {
            it.copy(
                activeTaskLabel = "Loading $modelName into RAM...",
                activeTaskProgressPct = -1
            )
        }
        startOrRefreshService(appCtx, "Loading $modelName into RAM...", -1, force = true)
        OnDeviceLlmEngine.preloadModel(appCtx, modelId)
    }

    fun pauseDownload(context: Context, modelId: String) {
        val appCtx = context.applicationContext
        activeDownloadJobs.remove(modelId)?.cancel()
        val partialPct = ModelDownloadManager.getPartialDownloadPercent(appCtx, modelId)
        _state.update { cur ->
            val nextPartials = if (partialPct != null) cur.partialPercents + (modelId to partialPct) else cur.partialPercents
            cur.copy(
                downloadProgress = cur.downloadProgress - modelId,
                partialPercents = nextPartials
            )
        }
        stopServiceIfIdle(appCtx)
    }

    fun pauseAllDownloads(context: Context) {
        val appCtx = context.applicationContext
        val ids = activeDownloadJobs.keys.toList()
        ids.forEach { id -> pauseDownload(appCtx, id) }
    }

    fun clearPartialDownload(context: Context, modelId: String) {
        val appCtx = context.applicationContext
        activeDownloadJobs.remove(modelId)?.cancel()
        ModelDownloadManager.clearPartialDownload(appCtx, modelId)
        _state.update { cur ->
            cur.copy(
                downloadProgress = cur.downloadProgress - modelId,
                partialPercents = cur.partialPercents - modelId,
                downloadErrors = cur.downloadErrors - modelId
            )
        }
        stopServiceIfIdle(appCtx)
    }

    fun markRemoved(modelId: String) {
        activeDownloadJobs.remove(modelId)?.cancel()
        synchronized(preloadLock) {
            if (activePreloadModelId == modelId) {
                activePreloadJob?.cancel()
                activePreloadJob = null
                activePreloadModelId = null
            }
        }
        _state.update { cur ->
            cur.copy(
                downloadProgress = cur.downloadProgress - modelId,
                partialPercents = cur.partialPercents - modelId,
                downloadErrors = cur.downloadErrors - modelId,
                newlyInstalledModelIds = cur.newlyInstalledModelIds - modelId
            )
        }
    }

    private fun startOrRefreshService(context: Context, statusText: String, pct: Int, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationPushMs < 650L) return
        lastNotificationPushMs = now
        val intent = Intent(context, ModelBackgroundService::class.java).apply {
            action = ModelBackgroundService.ACTION_UPDATE_PROGRESS
            putExtra(ModelBackgroundService.EXTRA_STATUS, statusText)
            putExtra(ModelBackgroundService.EXTRA_PROGRESS, pct)
        }
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }.onFailure { err ->
            // On Android 12+ if started from strict background restrictions, work still runs safely in appScope
            Log.w(TAG, "Foreground service start deferred: ${err.message}")
        }
    }

    private fun stopServiceIfIdle(context: Context) {
        val preloadRunning = synchronized(preloadLock) { activePreloadJob?.isActive == true }
        if (!_state.value.hasActiveWork && activeDownloadJobs.isEmpty() && !preloadRunning) {
            _state.update { it.copy(activeTaskLabel = "", activeTaskProgressPct = -1) }
            runCatching {
                context.stopService(Intent(context, ModelBackgroundService::class.java))
            }
        }
    }
}

/**
 * Google Play-compliant Foreground Service (`foregroundServiceType="dataSync"`) that keeps
 * offline open-weight model downloads and RAM preloading running reliably when the user
 * switches away from the app or turns off the screen.
 */
class ModelBackgroundService : Service() {

    companion object {
        const val CHANNEL_ID = "shamia_model_bg_sync"
        const val NOTIFICATION_ID = 4108
        const val ACTION_UPDATE_PROGRESS = "com.shamia.assistant.ACTION_MODEL_BG_UPDATE"
        const val ACTION_PAUSE_ALL = "com.shamia.assistant.ACTION_MODEL_BG_PAUSE"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_PROGRESS = "extra_progress"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        acquireWakeLockSafe()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE_ALL) {
            ModelBackgroundHub.pauseAllDownloads(applicationContext)
            stopSelfSafely()
            return START_NOT_STICKY
        }

        val status = intent?.getStringExtra(EXTRA_STATUS)
            ?.takeIf { it.isNotBlank() }
            ?: ModelBackgroundHub.state.value.activeTaskLabel.ifBlank { "Preparing offline AI model..." }
        val pct = intent?.getIntExtra(EXTRA_PROGRESS, -1) ?: ModelBackgroundHub.state.value.activeTaskProgressPct

        val notification = buildNotification(status, pct)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { err ->
            Log.w("ModelBackgroundService", "startForeground skipped: ${err.message}")
        }

        if (!ModelBackgroundHub.state.value.hasActiveWork) {
            stopSelfSafely()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 14/15/16 dataSync FGS timeout compliance: save checkpoints & stop cleanly
        ModelBackgroundHub.pauseAllDownloads(applicationContext)
        stopSelfSafely()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLockSafe()
        super.onDestroy()
    }

    private fun stopSelfSafely() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        releaseWakeLockSafe()
        stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Offline Model Downloads & Loading",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps local AI model downloads and RAM loading active when screen is off"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(statusText: String, pct: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ModelBackgroundService::class.java).apply { action = ACTION_PAUSE_ALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("S.H.A.M.I.A. Offline AI")
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (pct in 0..100) {
            builder.setProgress(100, pct, false)
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
        } else {
            builder.setProgress(100, 0, true)
        }

        return builder.build()
    }

    private fun acquireWakeLockSafe() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SHAMIA::ModelBackgroundSync").apply {
                    setReferenceCounted(false)
                    acquire(30L * 60L * 1000L) // Max 30-minute safety cap; released early on completion
                }
            }
        }
    }

    private fun releaseWakeLockSafe() {
        runCatching {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
    }
}
