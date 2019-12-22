package com.ria.demo

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.ria.demo.models.Circle
import com.ria.demo.services.CameraRecordService
import com.ria.demo.services.ScreenRecordService
import com.ria.demo.utilities.Constants
import com.ria.demo.utilities.Constants.Companion.SERVER_URL
import com.ria.demo.utilities.makeToast
import io.fotoapparat.Fotoapparat
import io.fotoapparat.log.fileLogger
import io.fotoapparat.log.logcat
import io.fotoapparat.log.loggers
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.selector.front
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.ceil


class MainActivity : AppCompatActivity(), View.OnClickListener {
    private val tag = "MainActivity"
    private val permissionCode = 1
    private val apiVersion = android.os.Build.VERSION.SDK_INT
    private var fotoapparat: Fotoapparat? = null
    private var mProjectionManager: MediaProjectionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestForPermission()
        setContentView(R.layout.activity_main)

        mProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        AndroidNetworking.initialize(applicationContext);
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
        fotoapparat?.start()
    }

    override fun onStop() {
        super.onStop()
        fotoapparat?.stop()
    }

    override fun onClick(view: View?) {
        val cameraRecordService = Intent(this, CameraRecordService::class.java)

        when (view?.id) {
            R.id.activity_main_btn_start_recording -> {
                startActivityForResult(
                    mProjectionManager!!.createScreenCaptureIntent(),
                    permissionCode
                )
                startService(cameraRecordService)

                makeToast("Start recording")

                notifyRecordingState()
            }
            R.id.activity_main_btn_stop_recording -> {
                stopBackgroundScreenRecordService()
                stopService(cameraRecordService)

                makeToast("Stop recording")

                notifyRecordingState()
            }
            R.id.activity_main_btn_start_calibration -> {
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

        object : CountDownTimer(Constants.COUNTDOWN_DURATION_IN_MILLIS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val second = ceil(((millisUntilFinished + 1000) / 1000).toDouble()).toInt()
                activity_main_countdown_timer.text = second.toString()
            }

            override fun onFinish() {
                activity_main_countdown_timer.text = ""
                drawCircles(generateCircles())
            }
        }.start()
    }

    private fun generateCircles(): ArrayList<Circle> {
        val circles = ArrayList<Circle>()

        circles.addAll(createFixedCircles())

        return circles
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

    private fun drawCircles(circles: ArrayList<Circle>) {
        // Array value for testing the image capturing
        val imageArray = arrayListOf<File>()

        showCanvasView()

        Log.d(tag, String.format("Start Calibration"))

        // Draw circle one by one
        circles.forEachIndexed { i, circle ->
            Handler().postDelayed({
                Log.d(tag, String.format("Circle No.%s, X: %s, Y: %s", i, circle.x, circle.y))

                activity_main_canvas_view.drawCircle(circle)

                // Take picture here
                addImageFromFrontCamera(imageArray)
            }, (Constants.CIRCLE_LIFETIME_IN_MILLIS * i).toLong())
        }

        // Countdown timer to hide canvas view
        val recordDuration: Long = (circles.size * Constants.CIRCLE_LIFETIME_IN_MILLIS).toLong()
        createFirstTimer(recordDuration)

        // Countdown timer to wait an async task done and show main screen
        createSecondTimer(recordDuration, imageArray)
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

    private fun createSecondTimer(
        recordDuration: Long,
        imageArray: ArrayList<File>
    ) {
        object : CountDownTimer(recordDuration + 2000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                uploadImagesToCalibrate(imageArray)
                makeToast("Calibrating is complete")
                restoreMainScreen()

                // Test logging value in image array
                Log.d(tag, String.format("Finish Calibration"))
                Log.d(tag, String.format("Image array size: %s", imageArray.size.toString()))
                imageArray.clear()
            }
        }.start()
    }

    private fun addImageFromFrontCamera(imageArray: ArrayList<File>) {
        val outputDir = applicationContext.cacheDir
        val outputFile = File.createTempFile("prefix", "extension", outputDir)

        Log.d(tag, "Captured Image")

        Handler().postDelayed({
            fotoapparat!!.takePicture().saveToFile(outputFile).whenAvailable {
                imageArray.add(outputFile)
            }
        }, Constants.CIRCLE_LIFETIME_IN_MILLIS.toLong())
    }

    private fun createImageFile(bitmap: Bitmap): File {
        val filename = DateFormat.format("yyyy-MM-dd_kk-mm-ss", Date().time).toString()
        val file = File(applicationContext.cacheDir, filename)

        file.createNewFile()

        val bos = ByteArrayOutputStream()

        bitmap.compress(CompressFormat.PNG, 0 /*ignored for PNG*/, bos)

        val bitmapData: ByteArray = bos.toByteArray()
        val fos = FileOutputStream(file)

        fos.write(bitmapData)
        fos.flush()
        fos.close()

        return file
    }

    private fun showCountdownTimerScreen() {
        activity_main_camera_view.visibility = View.GONE
        activity_main_canvas_view.visibility = View.GONE
        activity_main_btn_start_calibration.visibility = View.GONE
        activity_main_btn_start_recording.visibility = View.GONE
        activity_main_btn_stop_recording.visibility = View.GONE
        activity_main_countdown_timer.visibility = View.VISIBLE
    }

    private fun showCanvasView() {
        activity_main_camera_view.visibility = View.GONE
        activity_main_canvas_view.visibility = View.VISIBLE
        activity_main_btn_start_calibration.visibility = View.GONE
        activity_main_btn_start_recording.visibility = View.GONE
        activity_main_btn_stop_recording.visibility = View.GONE
        activity_main_countdown_timer.visibility = View.GONE
    }

    private fun restoreMainScreen() {
        activity_main_camera_view.visibility = View.VISIBLE
        activity_main_btn_start_calibration.visibility = View.VISIBLE
        activity_main_btn_start_recording.visibility = View.VISIBLE
        activity_main_btn_stop_recording.visibility = View.GONE
        activity_main_countdown_timer.visibility = View.GONE
    }

    private fun createFotoapparat() {
        fotoapparat = Fotoapparat(
            context = this,
            view = activity_main_camera_view,
            scaleType = ScaleType.CenterCrop,
            lensPosition = front(),
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
            activity_main_camera_view.visibility = View.GONE
            activity_main_is_record.visibility = View.VISIBLE
            activity_main_btn_start_recording.visibility = View.GONE
            activity_main_btn_stop_recording.visibility = View.VISIBLE
            activity_main_btn_start_calibration.isEnabled = false
        } else {
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

    private fun uploadImagesToCalibrate(
        imgList: ArrayList<File>
    ) {
        AndroidNetworking.upload("$SERVER_URL/calibrate")
            .addMultipartFileList("image[]", imgList)
            .addMultipartParameter("key", "value")
            .setPriority(Priority.HIGH)
            .build()
            .setUploadProgressListener { bytesUploaded, totalBytes ->
                Log.d(
                    tag,
                    String.format("BytesUploaded: %s / %s", bytesUploaded, totalBytes)
                )
            }
            .getAsJSONObject(object : JSONObjectRequestListener {
                override fun onResponse(response: JSONObject?) {
                    Log.d(tag, response.toString())
                }

                override fun onError(anError: ANError?) {
                    Log.d(tag, anError.toString())
                }
            })
    }
}
