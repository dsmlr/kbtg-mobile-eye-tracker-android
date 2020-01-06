package com.ria.demo.services

import android.app.*
import android.app.Activity.RESULT_OK
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.ria.demo.R
import com.ria.demo.utilities.Uploader
import java.io.File
import java.util.*

/**
 * REF: https://github.com/pkrieter/android-background-screen-recorder
 */
class ScreenRecordService : Service() {
    private val tag = "ScreenRecordService"
    private val notificationId = 23

    private var mServiceHandler: ServiceHandler? = null
    private var mMediaProjection: MediaProjection? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mMediaRecorder: MediaRecorder? = null
    private var resultCode = 0
    private var data: Intent? = null
    private var mScreenStateReceiver: BroadcastReceiver? = null
    private lateinit var file: File

    companion object BackgroundScreenRecordService {
        private const val EXTRA_RESULT_CODE = "resultcode"
        private const val EXTRA_DATA = "data"

        fun newIntent(context: Context?, resultCode: Int, data: Intent?): Intent? {
            val intent = Intent(context, ScreenRecordService::class.java)

            intent.putExtra(EXTRA_RESULT_CODE, resultCode)
            intent.putExtra(EXTRA_DATA, data)

            return intent
        }
    }

    inner class MyBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    startRecording(resultCode, data)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    stopRecording()
                }
                Intent.ACTION_CONFIGURATION_CHANGED -> {
                    stopRecording()
                    startRecording(resultCode, data)
                }
            }
        }
    }

    private inner class ServiceHandler : Handler {
        constructor(looper: Looper?) : super(looper)

        override fun handleMessage(msg: Message?) {
            if (resultCode == RESULT_OK) {
                startRecording(resultCode, data)
            } else {
            }
        }
    }

    override fun onCreate() {
        Log.d(tag, "onCreate")

        val notificationIntent = Intent(this, ScreenRecordService::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "channel_id"
            val channel = NotificationChannel(
                channelId,
                "channel_name",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )
            Notification.Builder(this, channelId)
                .setContentTitle("DataRecorder")
                .setContentText("Your screen is being recorded and saved to your phone.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setTicker("Tickertext")
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("DataRecorder")
                .setContentText("Your screen is being recorded and saved to your phone.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setTicker("Tickertext")
                .build()
        }

        startForeground(notificationId, notification)
        mScreenStateReceiver = MyBroadcastReceiver()

        val screenStateFilter = IntentFilter()

        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON)
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF)
        screenStateFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED)

        registerReceiver(mScreenStateReceiver, screenStateFilter)

        val thread = HandlerThread("ServiceStartArguments", THREAD_PRIORITY_BACKGROUND)

        thread.start()

        val mServiceLooper = thread.looper

        mServiceHandler = ServiceHandler(mServiceLooper)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        data = intent.getParcelableExtra(EXTRA_DATA)

        if (resultCode == 0 || data == null) {
            throw IllegalStateException("Result code or data missing.")
        }

        val msg: Message = mServiceHandler!!.obtainMessage()

        msg.arg1 = startId
        mServiceHandler!!.sendMessage(msg)

        return START_REDELIVER_INTENT
    }

    private fun startRecording(resultCode: Int, data: Intent?) {
        Log.d(tag, "Screen Record Activate")

        val mProjectionManager =
            applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mMediaRecorder = MediaRecorder()
        val metrics = DisplayMetrics()
        val wm =
            applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(metrics)
        val mScreenDensity = metrics.densityDpi
        val displayWidth = metrics.widthPixels
        val displayHeight = metrics.heightPixels

        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder!!.setVideoEncodingBitRate(8 * 1000 * 1000)
        mMediaRecorder!!.setVideoFrameRate(15)
        mMediaRecorder!!.setVideoSize(displayWidth, displayHeight)

        file = openFileForStorage()!!
        mMediaRecorder!!.setOutputFile(file.absolutePath)

        try {
            mMediaRecorder!!.prepare()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data)
        val surface: Surface = mMediaRecorder!!.surface
        mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(
            "MainActivity",
            displayWidth,
            displayHeight,
            mScreenDensity,
            VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            surface,
            null,
            null
        )
        mMediaRecorder!!.start()

        Log.d(tag, "Started recording")
    }

    private fun openFileForStorage(): File? {
        val directory: File
        val storageState = Environment.getExternalStorageState()

        if (storageState == Environment.MEDIA_MOUNTED) {
            directory = File(Environment.getExternalStoragePublicDirectory(File.separator), "DEMO")
            return when {
                !directory.exists() && !directory.mkdirs() -> null
                else -> File(
                    directory.path + File.separator + DateFormat.format(
                        "yyyy-MM-dd_kk-mm-ss",
                        Date().time
                    ) + "_screen.mp4"
                )
            }
        }

        return null
    }

    private fun stopRecording() {
        mMediaRecorder!!.stop()
        mMediaProjection!!.stop()
        mMediaRecorder!!.release()
        mVirtualDisplay!!.release()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        stopRecording()
        Uploader.uploadScreenVideo(arrayListOf(file))
        unregisterReceiver(mScreenStateReceiver)
        stopSelf()
    }
}