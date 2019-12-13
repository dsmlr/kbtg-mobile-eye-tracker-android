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
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import java.io.File
import java.io.IOException
import java.util.*

/**
 * REF: https://github.com/dotWee/android-application-background-video-service/
 */
class CameraRecordService : Service(), SurfaceHolder.Callback {
    private val tag = "CameraRecordService"
    private var mediaRecorder: MediaRecorder? = null
    private var windowManager: WindowManager? = null
    private var surfaceView: SurfaceView? = null
    private var camera: Camera? = null

    override fun onCreate() {
        windowManager =
            this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
        mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        mediaRecorder!!.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH))

        val path = openFileForStorage()!!.absolutePath

        Log.d(tag, path.toString())

        mediaRecorder!!.setOutputFile(path)

        try {
            mediaRecorder!!.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        mediaRecorder!!.start()
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
                    ) + ".mp4"
                )
            }
        }

        return null
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
    }

    override fun surfaceChanged(
        surfaceHolder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {}

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}