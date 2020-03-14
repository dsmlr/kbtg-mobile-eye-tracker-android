package com.ria.demo

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.androidnetworking.AndroidNetworking
import com.ria.demo.models.Circle
import com.ria.demo.services.CameraRecordService
import com.ria.demo.services.ScreenRecordService
import com.ria.demo.utilities.Constants
import com.ria.demo.utilities.Constants.Companion.RECORD_TYPE
import com.ria.demo.utilities.makeLongToast
import com.ria.demo.utilities.makeToast
import io.fotoapparat.Fotoapparat
import io.fotoapparat.configuration.CameraConfiguration
import io.fotoapparat.log.fileLogger
import io.fotoapparat.log.logcat
import io.fotoapparat.log.loggers
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.selector.*
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(), View.OnClickListener {
    companion object {
        lateinit var notificationManager: NotificationManager
        var circles = ArrayList<Circle>()
    }

    private val tag = "MainActivity"
    private val permissionCode = 1
    private val apiVersion = android.os.Build.VERSION.SDK_INT
    private var fotoapparat: Fotoapparat? = null
    private var mProjectionManager: MediaProjectionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        9
        requestForPermission()
        setContentView(R.layout.activity_main)

        mProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val okHttpClient = OkHttpClient().newBuilder()
            .connectTimeout(600, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)
            .build()

        AndroidNetworking.initialize(applicationContext, okHttpClient)
        createFotoapparat()
        addButtonEventListener()
        notifyRecordingState()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != permissionCode) {
            Log.e(tag, "Unknown request code: $requestCode")

            return
        }

        if (resultCode == RESULT_OK) {
            Log.d(tag, "onActivityResult: Request code = $requestCode")
            Log.d(tag, "onActivityResult: Result code = $resultCode")

            startBackgroundScreenRecordService(resultCode, data)
        } else {
            makeToast("Screen Cast Permission Denied")

            return
        }
    }

    override fun onStart() {
        super.onStart()

        if (!isServiceRunning()) {
            restartFotoapparat()
        }
    }

    override fun onStop() {
        super.onStop()
        fotoapparat?.stop()
    }

    override fun onResume() {
        super.onResume()

        if (!isServiceRunning()) {
            restartFotoapparat()
        }
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.activity_main_btn_start_recording -> {
                val cameraRecordService = Intent(this, CameraRecordService::class.java)
                cameraRecordService.putExtra(RECORD_TYPE, "prediction")

                startActivityForResult(
                    mProjectionManager!!.createScreenCaptureIntent(),
                    permissionCode
                )
                startService(cameraRecordService)

                makeToast("Start recording")

                notifyRecordingState()
            }
            R.id.activity_main_btn_stop_recording -> {
                val cameraRecordService = Intent(this, CameraRecordService::class.java)

                stopBackgroundScreenRecordService()
                stopService(cameraRecordService)

                makeToast("Stop recording")

                restartFotoapparat()
                notifyRecordingState()
            }
            R.id.activity_main_btn_start_calibration -> {
                val cameraRecordService = Intent(this, CameraRecordService::class.java)
                cameraRecordService.putExtra(RECORD_TYPE, "calibration")
                Log.d(tag, "Before start service timestamp: ${System.currentTimeMillis()}")
                startService(cameraRecordService)
                Log.d(tag, "After start service timestamp: ${System.currentTimeMillis()}")
                startCalibration()
            }
        }
    }

    private fun addButtonEventListener() {
        activity_main_btn_start_recording.setOnClickListener(this)
        activity_main_btn_stop_recording.setOnClickListener(this)
        activity_main_btn_start_calibration.setOnClickListener(this)
    }

    private fun startCalibration() {
        showCountdownTimerScreen()
        activity_main_countdown_timer.text = "Ready"

        object : CountDownTimer(Constants.COUNTDOWN_DURATION_IN_MILLIS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                activity_main_countdown_timer.text = ""
                generateCircles()
                drawCircles()
            }
        }.start()
    }

    private fun generateCircles() {
        circles.clear()
        circles.addAll(createFixedCircles())
    }

    private fun createFixedCircles(): ArrayList<Circle> {
        val fixedCircles = ArrayList<Circle>()
        val xAxisValues = arrayListOf(
            Constants.CIRCLE_RADIUS,
            Constants.SCREEN_WIDTH / 2,
            Constants.SCREEN_WIDTH - Constants.CIRCLE_RADIUS
        )
        val yAxisValues = arrayListOf(
            Constants.CIRCLE_RADIUS,
            Constants.SCREEN_HEIGHT / 2,
            Constants.SCREEN_HEIGHT - Constants.CIRCLE_RADIUS
        )

        yAxisValues.forEach { y ->
            xAxisValues.forEach { x ->
                fixedCircles.add(Circle(x, y, Constants.CIRCLE_RADIUS))
            }
        }
        fixedCircles.shuffle()

        return fixedCircles
    }

    private fun drawCircles() {
        showCanvasView()

        Log.d(tag, String.format("Start Calibration"))

        // Draw circle one by one
        circles.forEachIndexed { i, circle ->
            Handler().postDelayed({
                Log.d(
                    tag,
                    String.format(
                        "Circle No.%s, Timestamp: %s, X: %s, Y: %s",
                        i + 1,
                        System.currentTimeMillis(),
                        circle.x,
                        circle.y
                    )
                )

                activity_main_canvas_view.drawCircle(circle)
            }, (Constants.CIRCLE_LIFETIME_IN_MILLIS * i).toLong())
        }

        // Countdown timer to hide canvas view
        val recordDuration: Long = (circles.size * Constants.CIRCLE_LIFETIME_IN_MILLIS).toLong()
        createFirstTimer(recordDuration)

        // Countdown timer to wait an async task done and show main screen
        createSecondTimer(recordDuration)
    }

    private fun createFirstTimer(recordDuration: Long) {
        object : CountDownTimer(recordDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                activity_main_canvas_view.visibility = View.GONE
                activity_main_canvas_view.clearView()
            }
        }.start()
    }

    private fun createSecondTimer(recordDuration: Long) {
        val cameraRecordService = Intent(this, CameraRecordService::class.java)

        object : CountDownTimer(recordDuration + 2000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                stopService(cameraRecordService)
                restartFotoapparat()
                restoreMainScreen()

                makeLongToast("Calibration process is complete.")
                Log.d(tag, String.format("Finish Calibration"))
            }
        }.start()
    }

    private fun showCountdownTimerScreen() {
        activity_main_rectangle_frame.visibility = View.GONE
        activity_main_camera_view.visibility = View.GONE
        activity_main_canvas_view.visibility = View.GONE
        activity_main_btn_start_calibration.visibility = View.GONE
        activity_main_btn_start_recording.visibility = View.GONE
        activity_main_btn_stop_recording.visibility = View.GONE
        activity_main_countdown_timer.visibility = View.VISIBLE
    }

    private fun showCanvasView() {
        activity_main_rectangle_frame.visibility = View.GONE
        activity_main_camera_view.visibility = View.GONE
        activity_main_canvas_view.visibility = View.VISIBLE
        activity_main_btn_start_calibration.visibility = View.GONE
        activity_main_btn_start_recording.visibility = View.GONE
        activity_main_btn_stop_recording.visibility = View.GONE
        activity_main_countdown_timer.visibility = View.GONE
    }

    private fun restoreMainScreen() {
        activity_main_rectangle_frame.visibility = View.VISIBLE
        activity_main_camera_view.visibility = View.VISIBLE
        activity_main_btn_start_calibration.visibility = View.VISIBLE
        activity_main_btn_start_recording.visibility = View.VISIBLE
        activity_main_btn_stop_recording.visibility = View.GONE
        activity_main_countdown_timer.visibility = View.GONE
    }

    private fun createFotoapparat() {
        val cameraConfiguration = CameraConfiguration(
            pictureResolution = firstAvailable(
                { Resolution(1280, 720) },
                highestResolution()
            ),
            previewResolution = highestResolution(),
            previewFpsRange = highestFps(),
            focusMode = firstAvailable(
                continuousFocusPicture(),
                autoFocus(),
                fixed()
            ),
            flashMode = off(),
            antiBandingMode = firstAvailable(
                auto(),
                hz50(),
                hz60(),
                none()
            ),
            jpegQuality = manualJpegQuality(90),
            sensorSensitivity = lowestSensorSensitivity(),
            frameProcessor = { frame -> }
        )

        fotoapparat = Fotoapparat(
            context = this,
            view = activity_main_camera_view,
            scaleType = ScaleType.CenterCrop,
            lensPosition = front(),
            cameraConfiguration = cameraConfiguration,
            logger = loggers(
                logcat(),
                fileLogger(this)
            ),
            cameraErrorCallback = { error -> Log.d(tag, error.message) }
        )
    }

    private fun requestForPermission() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.CAMERA,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val granted = PackageManager.PERMISSION_GRANTED

        if (apiVersion >= android.os.Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.INTERNET
                ) != granted || ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != granted
                || ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.SYSTEM_ALERT_WINDOW
                ) != granted
                || ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != granted
                || ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != granted
                || ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != granted
            ) {
                ActivityCompat.requestPermissions(this, permissions, 2)
            }
        }
    }

    private fun notifyRecordingState() {
        if (isServiceRunning()) {
            fotoapparat?.stop()

            activity_main_rectangle_frame.visibility = View.GONE
            activity_main_camera_view.visibility = View.GONE
            activity_main_is_record.visibility = View.VISIBLE
            activity_main_btn_start_recording.visibility = View.GONE
            activity_main_btn_stop_recording.visibility = View.VISIBLE
            activity_main_btn_start_calibration.isEnabled = false
        } else {
            fotoapparat?.start()

            activity_main_rectangle_frame.visibility = View.VISIBLE
            activity_main_camera_view.visibility = View.VISIBLE
            activity_main_is_record.visibility = View.GONE
            activity_main_btn_start_recording.visibility = View.VISIBLE
            activity_main_btn_stop_recording.visibility = View.GONE
            activity_main_btn_start_calibration.isEnabled = true
        }
    }

    private fun startBackgroundScreenRecordService(resultCode: Int, data: Intent?) {
        val intent = ScreenRecordService.newIntent(this, resultCode, data)

        startService(intent)
    }

    private fun stopBackgroundScreenRecordService() {
        val intent = Intent(this, ScreenRecordService::class.java)

        stopService(intent)
    }

    private fun isServiceRunning(): Boolean {
        val manager =
            getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            val serviceName = service.service.className

            if (serviceName == CameraRecordService::class.java.name
                || serviceName == ScreenRecordService::class.java.name
            ) {
                return true
            }
        }

        return false
    }

    private fun restartFotoapparat() {
        fotoapparat?.stop()
        fotoapparat?.start()
    }
}
