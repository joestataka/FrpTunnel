package io.github.joestataka.frptunnel

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
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.concurrent.thread

/**
 * 前台服务：负责拉起内置的 frp-panel 二进制，并把它的输出实时写入 [TunnelState]。
 */
class TunnelService : Service() {

    private var process: Process? = null
    @Volatile
    private var readerThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel("用户手动停止")
                return START_NOT_STICKY
            }
            else -> {
                val args = intent?.getStringArrayListExtra(EXTRA_ARGS)
                    ?: intent?.getStringArrayExtra(EXTRA_ARGS)?.toList()
                    ?: emptyList()
                startTunnel(args)
            }
        }
        return START_STICKY
    }

    private fun startTunnel(args: List<String>) {
        Log.i(TAG, "startTunnel args=$args")
        if (process != null) {
            TunnelState.append("[系统] 隧道已在运行中，忽略本次启动")
            return
        }
        val binPath = applicationInfo.nativeLibraryDir + "/" + BIN_NAME
        val bin = File(binPath)
        Log.i(TAG, "binPath=$binPath exists=${bin.exists()} canExecute=${bin.canExecute()}")
        if (!bin.exists()) {
            TunnelState.append("[错误] 未找到内置二进制: $binPath")
            TunnelState.exitInfo.value = "内置二进制缺失"
            stopSelf()
            return
        }
        if (!bin.canExecute()) {
            bin.setExecutable(true, false)
        }

        startAsForeground()
        TunnelState.running.value = true
        TunnelState.exitInfo.value = null
        TunnelState.append("[系统] 工作目录: ${filesDir.absolutePath}")
        TunnelState.append("[系统] 执行: $binPath ${args.joinToString(" ")}")

        try {
            val pb = ProcessBuilder(listOf(binPath) + args)
            pb.redirectErrorStream(true)
            pb.directory(filesDir)
            pb.environment()["HOME"] = filesDir.absolutePath
            pb.environment()["TMPDIR"] = cacheDir.absolutePath
            val proc = pb.start()
            process = proc
            Log.i(TAG, "process started")

            readerThread = thread(name = "frp-panel-reader") {
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            TunnelState.append(line)
                            Log.i(TAG, "output: $line")
                            line = reader.readLine()
                        }
                    }
                } catch (e: Exception) {
                    TunnelState.append("[读取异常] ${e.message}")
                    Log.e(TAG, "reader error", e)
                }
            }

            thread(name = "frp-panel-waiter") {
                val code = try {
                    proc.waitFor()
                } catch (e: InterruptedException) {
                    -1
                }
                process = null
                TunnelState.running.value = false
                TunnelState.exitInfo.value = "进程退出，退出码 $code"
                TunnelState.append("[系统] 进程已退出，退出码 $code")
                Log.i(TAG, "process exited code=$code")
                stopForegroundCompat()
                stopSelf()
            }
        } catch (e: Exception) {
            process = null
            TunnelState.running.value = false
            TunnelState.exitInfo.value = "启动失败: ${e.message}"
            TunnelState.append("[异常] 启动失败: ${e.message}")
            Log.e(TAG, "start failed", e)
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun stopTunnel(reason: String) {
        TunnelState.append("[系统] 正在停止隧道（$reason）...")
        val proc = process
        process = null
        try {
            proc?.destroy()
            proc?.waitFor()
        } catch (_: Exception) {
        }
        TunnelState.running.value = false
        TunnelState.exitInfo.value = reason
        stopForegroundCompat()
        stopSelf()
    }

    private fun startAsForeground() {
        val notification = buildNotification("隧道运行中")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FRP 隧道")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "隧道状态",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        val proc = process
        process = null
        try {
            proc?.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "io.github.joestataka.frptunnel.action.START"
        const val ACTION_STOP = "io.github.joestataka.frptunnel.action.STOP"
        const val EXTRA_ARGS = "extra_args"
        const val BIN_NAME = "libfrppanel.so"
        private const val TAG = "FRPTunnel"
        private const val CHANNEL_ID = "frp_tunnel"
        private const val NOTIF_ID = 10086
    }
}