package com.ria.demo.utilities

import android.content.Context
import android.os.Handler
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.ria.demo.MainActivity.Companion.circles
import com.ria.demo.models.Circle
import com.ria.demo.utilities.Constants.Companion.APP_NAME
import com.ria.demo.utilities.Constants.Companion.NOTIFICATION_ID
import com.ria.demo.utilities.Constants.Companion.SERVER_URL
import com.ria.demo.utilities.Constants.Companion.TYPE_CALIBRATION
import com.ria.demo.utilities.Constants.Companion.TYPE_PREDICTION
import org.json.JSONObject
import java.io.File

class Uploader(context: Context) {
    companion object {
        private const val TAG = "Uploader"
        private const val SAVE_SCREEN_VIDEO_PATH = "save-screen-video"
        private const val CHECK_STATUS_PATH = "check-status"
        private const val PREDICT_PATH = "predict"
        private const val CALIBRATE_PATH = "calibrate"
        private const val VIDEO_KEY = "video[]"
        private const val STATUS_KEY = "status"
        private const val X_POSITIONS_KEY = "xPositions"
        private const val Y_POSITIONS_KEY = "yPositions"
    }

    val notificationHelper = NotificationHelper(context)

    fun uploadFaceVideo(recordType: String, videoList: ArrayList<File>) {
        if (recordType == TYPE_PREDICTION) {
            uploadFaceVideoForPrediction(videoList)
        } else if (recordType == TYPE_CALIBRATION) {
            uploadFaceVideoForCalibration(videoList)
        }
    }

    fun uploadScreenVideo(videoList: ArrayList<File>) {
        AndroidNetworking.upload("$SERVER_URL/$SAVE_SCREEN_VIDEO_PATH")
            .addMultipartFileList(VIDEO_KEY, videoList)
            .setPriority(Priority.HIGH)
            .build()
            .getAsJSONObject(object : JSONObjectRequestListener {
                override fun onResponse(response: JSONObject?) {
                }

                override fun onError(anError: ANError?) {
                }
            })
    }

    private fun uploadFaceVideoForPrediction(videoList: ArrayList<File>) {
        AndroidNetworking.get("$SERVER_URL/$CHECK_STATUS_PATH")
            .setPriority(Priority.HIGH)
            .build()
            .getAsJSONObject(object : JSONObjectRequestListener {
                override fun onResponse(response: JSONObject?) {
                    val calibrationStatus = response!![STATUS_KEY] == "true"

                    if (calibrationStatus) {
                        AndroidNetworking.upload("$SERVER_URL/$PREDICT_PATH")
                            .addMultipartFileList(VIDEO_KEY, videoList)
                            .setPriority(Priority.HIGH)
                            .build()
                            .getAsJSONObject(object : JSONObjectRequestListener {
                                override fun onResponse(response: JSONObject?) {
                                    val builder = notificationHelper.createNotificationBuilder(
                                        APP_NAME,
                                        "Prediction finished."
                                    )

                                    notificationHelper.makeNotification(builder, NOTIFICATION_ID)
                                }

                                override fun onError(anError: ANError?) {
                                    val builder = notificationHelper.createNotificationBuilder(
                                        APP_NAME,
                                        "Prediction failed."
                                    )

                                    notificationHelper.makeNotification(builder, NOTIFICATION_ID)
                                }
                            })
                    } else {
                        Handler().postDelayed({ uploadFaceVideoForPrediction(videoList) }, 20000)
                    }
                }

                override fun onError(anError: ANError?) {
                }
            })
    }

    private fun uploadFaceVideoForCalibration(videoList: ArrayList<File>) {
        val xPositionsStr = circles.map(Circle::x).joinToString(",")
        val yPositionsStr = circles.map(Circle::y).joinToString(",")

        AndroidNetworking.upload("$SERVER_URL/$CALIBRATE_PATH")
            .addMultipartFileList(VIDEO_KEY, videoList)
            .addMultipartParameter(X_POSITIONS_KEY, xPositionsStr)
            .addMultipartParameter(Y_POSITIONS_KEY, yPositionsStr)
            .setPriority(Priority.HIGH)
            .build()
            .getAsJSONObject(object : JSONObjectRequestListener {
                override fun onResponse(response: JSONObject?) {
                }

                override fun onError(anError: ANError?) {
                }
            })
    }
}