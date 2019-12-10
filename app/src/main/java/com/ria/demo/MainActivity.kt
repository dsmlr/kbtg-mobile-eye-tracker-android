package com.ria.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ria.demo.models.Circle
import com.ria.demo.utilities.Constants
import com.ria.demo.utilities.makeToast
import io.fotoapparat.Fotoapparat
import io.fotoapparat.log.fileLogger
import io.fotoapparat.log.logcat
import io.fotoapparat.log.loggers
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.selector.front
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"
    private var fotoapparat: Fotoapparat? = null
    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (hasNoPermissions()) {
            requestPermission()
        }

        createFotoapparat()
        addButtonEventListener()
    }

    private fun addButtonEventListener() {
        activity_main_btn_start_calibration.setOnClickListener {
            startCalibration()
        }
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
        showCanvasView()

        // Draw circle one by one
        circles.forEachIndexed { i, circle ->
            Handler().postDelayed({
                Log.d(tag, String.format("Circle No.%s, X: %s, Y: %s", i, circle.x, circle.y))
                canvas_view.drawCircle(circle)
            }, (Constants.CIRCLE_LIFETIME_IN_MILLIS * i).toLong())
        }

        // Use countdown timer to stop drawing
        val recordDuration: Long = (circles.size * Constants.CIRCLE_LIFETIME_IN_MILLIS).toLong()
        object : CountDownTimer(recordDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                restoreMainScreen()
                canvas_view.clearView()
                makeToast("Calibrating is complete")
            }
        }.start()
    }

    private fun showCountdownTimerScreen() {
        camera_view.visibility = View.GONE
        canvas_view.visibility = View.GONE
        activity_main_btn_start_calibration.visibility = View.GONE
        activity_main_countdown_timer.visibility = View.VISIBLE
    }

    private fun showCanvasView() {
        camera_view.visibility = View.GONE
        canvas_view.visibility = View.VISIBLE
        activity_main_btn_start_calibration.visibility = View.GONE
        activity_main_countdown_timer.visibility = View.GONE
    }

    private fun restoreMainScreen() {
        camera_view.visibility = View.VISIBLE
        canvas_view.visibility = View.GONE
        activity_main_btn_start_calibration.visibility = View.VISIBLE
        activity_main_countdown_timer.visibility = View.GONE
    }

    private fun createFotoapparat() {
        fotoapparat = Fotoapparat(
            context = this,
            view = camera_view,
            scaleType = ScaleType.CenterCrop,
            lensPosition = front(),
            logger = loggers(
                logcat(),
                fileLogger(this)
            ),
            cameraErrorCallback = { error -> Log.d(tag, error.message) }
        )
    }

    private fun hasNoPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission() {
        ActivityCompat.requestPermissions(this, permissions, 0)
    }

    override fun onStart() {
        super.onStart()
        fotoapparat?.start()
    }

    override fun onStop() {
        super.onStop()
        fotoapparat?.stop()
    }
}
