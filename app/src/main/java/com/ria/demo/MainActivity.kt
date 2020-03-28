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
import com.ria.demo.utilities.Constants.Companion.TYPE_CALIBRATION
import com.ria.demo.utilities.Constants.Companion.TYPE_PREDICTION
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
        private const val TAG = "MainActivity"

        var circles = ArrayList<Circle>()
        lateinit var notificationManager: NotificationManager
    }

    private val permissionCode = 1
    private val apiVersion = android.os.Build.VERSION.SDK_INT
    private var fotoapparat: Fotoapparat? = null
    private var mProjectionManager: MediaProjectionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val okHttpClient = OkHttpClient().newBuilder()
            .connectTimeout(600, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)
            .build()

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        requestForPermission()
        setContentView(R.layout.activity_main)
        AndroidNetworking.initialize(applicationContext, okHttpClient)
        createFotoapparat()
        addButtonEventListener()
        notifyRecordingState()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != permissionCode) {
            return
        }

        if (resultCode == RESULT_OK) {
            startBackgroundScreenRecordService(resultCode, data)
        } else {
            makeToast("Screen Cast Permission Denied")

            return
        }
    }

    override fun onStart() {
        super.onStart()

        if (!isServiceStillRunning()) {
            restartFotoapparat()
        }
    }

    override fun onStop() {
        super.onStop()

        fotoapparat?.stop()
    }

    override fun onResume() {
        super.onResume()

        if (!isServiceStillRunning()) {
            restartFotoapparat()
        }
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.activity_main_btn_start_recording -> {
                val cameraRecordService = Intent(this, CameraRecordService::class.java)

                cameraRecordService.putExtra(RECORD_TYPE, TYPE_PREDICTION)
                startActivityForResult(
                    mProjectionManager!!.createScreenCaptureIntent(),
                    permissionCode
                )
                startService(cameraRecordService)
                notifyRecordingState()

                makeToast("Start recording")
            }
            R.id.activity_main_btn_stop_recording -> {
                val cameraRecordService = Intent(this, CameraRecordService::class.java)

                stopBackgroundScreenRecordService()
                stopService(cameraRecordService)
                restartFotoapparat()
                notifyRecordingState()

                makeToast("Stop recording")
            }
            R.id.activity_main_btn_start_calibration -> {
                val cameraRecordService = Intent(this, CameraRecordService::class.java)

                cameraRecordService.putExtra(RECORD_TYPE, TYPE_CALIBRATION)
                startService(cameraRecordService)
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
        activity_main_countdown_timer.text = getString(R.string.text_ready)

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

        circles.forEachIndexed { i, circle ->
            Handler().postDelayed({
                activity_main_canvas_view.drawCircle(circle)
            }, (Constants.CIRCLE_LIFETIME_IN_MILLIS * i).toLong())
        }

        val recordDuration: Long = (circles.size * Constants.CIRCLE_LIFETIME_IN_MILLIS).toLong()

        createFirstTimer(recordDuration)
        createSecondTimer(recordDuration)
    }

    // First countdown timer for hiding canvas view after the process of showing circles is finish
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

    // Second countdown timer for stopping the camera service and restore the main screen
    // after 2 seconds of ending the process of showing circles
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
            frameProcessor = { }
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
            cameraErrorCallback = { }
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

        if (apiVersion >= android.os.Build.VERSION_CODES.M) {
            if (isPermissionNotGranted(Manifest.permission.INTERNET) ||
                isPermissionNotGranted(Manifest.permission.CAMERA) ||
                isPermissionNotGranted(Manifest.permission.SYSTEM_ALERT_WINDOW) ||
                isPermissionNotGranted(Manifest.permission.RECORD_AUDIO) ||
                isPermissionNotGranted(Manifest.permission.READ_EXTERNAL_STORAGE) ||
                isPermissionNotGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) {
                ActivityCompat.requestPermissions(this, permissions, 2)
            }
        }
    }

    private fun isPermissionNotGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) != PackageManager.PERMISSION_GRANTED
    }

    private fun notifyRecordingState() {
        if (isServiceStillRunning()) {
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

    private fun isServiceStillRunning(): Boolean {
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
