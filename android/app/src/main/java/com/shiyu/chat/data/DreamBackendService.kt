package com.shiyu.chat.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.shiyu.chat.R
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the prebuilt Local Dream SD1.5 NPU backend from this app's native
 * library directory. The model remains app-private and the HTTP server listens
 * only on the device loopback interface.
 */
class DreamBackendService : Service() {
    @Volatile
    private var process: Process? = null

    @Volatile
    private var stopping = false

    private var desiredModelDir: String? = null
    private var desiredWidth = DEFAULT_SIZE
    private var desiredHeight = DEFAULT_SIZE
    private var servingModelDir: String? = null
    private var servingWidth = 0
    private var servingHeight = 0
    private var runtimeDir: File? = null
    private var idleStopJob: Job? = null

    private val backendDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dream-backend")
    }.asCoroutineDispatcher()
    private val serviceScope = CoroutineScope(SupervisorJob() + backendDispatcher)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("正在准备本地生图引擎"))
        val modelDir = intent?.getStringExtra(EXTRA_MODEL_DIR)
        if (modelDir.isNullOrBlank()) {
            Log.e(TAG, "missing model directory")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        desiredModelDir = File(modelDir).canonicalPath
        desiredWidth = intent?.getIntExtra(EXTRA_WIDTH, DEFAULT_SIZE)
            ?.coerceAtLeast(8) ?: DEFAULT_SIZE
        desiredHeight = intent?.getIntExtra(EXTRA_HEIGHT, DEFAULT_SIZE)
            ?.coerceAtLeast(8) ?: DEFAULT_SIZE
        backendErrorFile().delete()
        idleStopJob?.cancel()
        serviceScope.launch {
            runCatching {
                prepareRuntimeAssets()
                startBackend(
                    desiredModelDir!!,
                    desiredWidth,
                    desiredHeight,
                )
                idleStopJob = launch {
                    delay(IDLE_STOP_MS)
                    updateNotification("本地生图引擎空闲，即将释放内存")
                    stopBackend()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }.onFailure { error ->
                Log.e(TAG, "Dream backend failed", error)
                backendErrorFile().writeText(error.message ?: "本地生图引擎启动失败")
                updateNotification("本地生图引擎启动失败")
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        idleStopJob?.cancel()
        stopBackend()
        backendDispatcher.close()
        super.onDestroy()
    }

    private fun stopBackend() {
        stopping = true
        process?.let { child ->
            runCatching { child.destroy() }
            runCatching {
                if (!child.waitFor(3, TimeUnit.SECONDS)) child.destroyForcibly()
            }
        }
        process = null
        servingModelDir = null
        servingWidth = 0
        servingHeight = 0
    }

    private fun prepareRuntimeAssets() {
        val target = File(filesDir, RUNTIME_DIR)
        target.mkdirs()
        assets.list(QNN_ASSET_DIR).orEmpty().forEach { name ->
            copyAssetIfNeeded("$QNN_ASSET_DIR/$name", File(target, name), executable = true)
        }
        copyAssetIfNeeded(
            "safety_checker.mnn",
            File(filesDir, "safety_checker.mnn"),
            executable = false,
        )
        runtimeDir = target
    }

    private fun copyAssetIfNeeded(assetPath: String, target: File, executable: Boolean) {
        val expectedSize = assets.open(assetPath).use { it.available().toLong() }
        if (!target.isFile || target.length() != expectedSize) {
            target.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                target.outputStream().buffered().use(input::copyTo)
            }
        }
        target.setReadable(true, true)
        if (executable) target.setExecutable(true, true)
    }

    private fun startBackend(
        modelDirectory: String,
        width: Int,
        height: Int,
    ) {
        val modelDir = File(modelDirectory).canonicalFile
        require(modelDir.isDirectory) { "本地模型目录不存在" }
        val runtime = runtimeDir ?: error("QNN 运行目录未准备完成")
        val nativeDir = File(applicationInfo.nativeLibraryDir)
        val executable = File(nativeDir, EXECUTABLE_NAME)
        require(executable.isFile) { "本地生图可执行引擎缺失" }
        require(executable.canExecute()) { "本地生图可执行引擎不可运行" }

        if (
            process?.isAlive == true &&
            servingModelDir == modelDir.path &&
            servingWidth == width &&
            servingHeight == height
        ) {
            updateNotification("本地生图引擎已就绪")
            return
        }

        stopping = true
        process?.let { old ->
            old.destroy()
            if (!old.waitFor(3, TimeUnit.SECONDS)) old.destroyForcibly()
        }
        process = null
        stopping = false

        val command = mutableListOf(
            executable.absolutePath,
            "--type",
            "sd15npu",
            "--model_dir",
            modelDir.absolutePath,
            "--port",
            PORT.toString(),
            "--lib_dir",
            runtime.absolutePath,
            "--safety_checker",
            File(filesDir, "safety_checker.mnn").absolutePath,
            "--no_img2img",
        )
        if (width != DEFAULT_SIZE || height != DEFAULT_SIZE) {
            val patch = File(modelDir, "${width}x$height.patch")
                .takeIf(File::isFile)
                ?: File(modelDir, "$width.patch").takeIf(File::isFile)
                ?: error("模型缺少 ${width}x$height 分辨率补丁")
            command += listOf("--patch", patch.absolutePath)
        }
        if (File(modelDir, "V_PRED").isFile) command += "--use_v_pred"

        val libraryPath = listOf(
            runtime.absolutePath,
            "/system/lib64",
            "/vendor/lib64",
            "/vendor/lib64/egl",
        ).joinToString(":")
        val environment = mapOf(
            "LD_LIBRARY_PATH" to libraryPath,
            "DSP_LIBRARY_PATH" to runtime.absolutePath,
        )
        val child = ProcessBuilder(command)
            .directory(nativeDir)
            .redirectErrorStream(true)
            .apply { environment().putAll(environment) }
            .start()
        process = child
        servingModelDir = modelDir.path
        servingWidth = width
        servingHeight = height
        Log.i(TAG, "Started: ${command.joinToString(" ")}")

        Thread {
            runCatching {
                child.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> Log.i("$TAG-native", line) }
                }
                child.waitFor()
            }.onFailure { error ->
                Log.e(TAG, "Backend monitor failed", error)
            }
            if (!stopping && process === child) {
                servingModelDir = null
                servingWidth = 0
                servingHeight = 0
                updateNotification("本地生图引擎已停止")
            }
        }.apply {
            name = "dream-backend-monitor"
            isDaemon = true
            start()
        }
        updateNotification("MeinaMix V12 · NPU 推理中")
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "本地生图引擎",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "拾语使用手机 NPU 生成角色头像时显示"
            },
        )
    }

    private fun createNotification(text: String): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("拾语 · 本地生图")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            manager.notify(NOTIFICATION_ID, createNotification(text))
        }
    }

    private fun backendErrorFile() = File(filesDir, BACKEND_ERROR_FILE)

    companion object {
        private const val TAG = "DreamBackendService"
        private const val EXECUTABLE_NAME = "libstable_diffusion_core.so"
        private const val RUNTIME_DIR = "dream-runtime"
        private const val QNN_ASSET_DIR = "qnnlibs"
        private const val CHANNEL_ID = "dream_backend"
        private const val NOTIFICATION_ID = 2
        private const val EXTRA_MODEL_DIR = "model_dir"
        private const val EXTRA_WIDTH = "width"
        private const val EXTRA_HEIGHT = "height"
        private const val DEFAULT_SIZE = 512
        private const val IDLE_STOP_MS = 10 * 60 * 1000L
        const val BACKEND_ERROR_FILE = "dream-backend-error.txt"
        const val PORT = 18081

        fun start(
            context: Context,
            modelDir: File,
            width: Int = DEFAULT_SIZE,
            height: Int = DEFAULT_SIZE,
        ) {
            val intent = Intent(context, DreamBackendService::class.java).apply {
                putExtra(EXTRA_MODEL_DIR, modelDir.absolutePath)
                putExtra(EXTRA_WIDTH, width)
                putExtra(EXTRA_HEIGHT, height)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DreamBackendService::class.java))
        }
    }
}
