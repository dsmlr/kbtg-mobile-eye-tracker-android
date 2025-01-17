package com.ria.demo.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Environment
import android.os.IBinder
import android.text.format.DateFormat
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.ria.demo.utilities.Constants.Companion.RECORD_TYPE
import com.ria.demo.utilities.Uploader
import java.io.File
import java.io.IOException
import java.util.*

/**
 * REF: https://github.com/dotWee/android-application-background-video-service/
 */
class CameraRecordService : Service(), SurfaceHolder.Callback {
    companion object {
        private const val TAG = "CameraRecordService"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var windowManager: WindowManager? = null
    private var surfaceView: SurfaceView? = null
    private var camera: Camera? = null
    private lateinit var uploader: Uploader
    private lateinit var file: File
    private lateinit var recordType: String

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        recordType = intent!!.getStringExtra(RECORD_TYPE)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        uploader = Uploader(this)
        windowManager = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        surfaceView = SurfaceView(this)

        val layoutParams = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.LEFT or Gravity.TOP

        windowManager!!.addView(surfaceView, layoutParams)
        surfaceView!!.holder.addCallback(this)
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        mediaRecorder = MediaRecorder()

        for (i in 0 until Camera.getNumberOfCameras()) {
            val cameraInfo = Camera.CameraInfo()

            Camera.getCameraInfo(i, cameraInfo)

            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                camera = Camera.open(1)
            }
        }

        camera!!.unlock()

        mediaRecorder!!.setPreviewDisplay(surfaceHolder.surface)
        mediaRecorder!!.setCamera(camera)
        mediaRecorder!!.setOrientationHint(270)
        mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        mediaRecorder!!.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P))

        file = openFileForStorage()!!

        mediaRecorder!!.setOutputFile(file.absolutePath)

        try {
            mediaRecorder!!.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        mediaRecorder!!.start()
    }

    override fun onDestroy() {
        if (mediaRecorder != null) {
            mediaRecorder!!.stop()
            mediaRecorder!!.reset()
            mediaRecorder!!.release()
        }

        if (camera != null) {
            camera!!.lock()
            camera!!.release()
        }

        if (windowManager != null) {
            windowManager!!.removeView(surfaceView)
        }

        uploader.uploadFaceVideo(recordType, arrayListOf(file))

        stopSelf()
    }

    override fun surfaceChanged(
        surfaceHolder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun openFileForStorage(): File? {
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val currentDate = DateFormat.format("yyyy-MM-dd_kk-mm-ss", Date().time)
            val directory =
                File(Environment.getExternalStoragePublicDirectory(File.separator), "DEMO")

            return when {
                !directory.exists() && !directory.mkdirs() -> null
                else -> File(directory.path + File.separator + currentDate + "face.mp4")
            }
        }

        return null
    }
}