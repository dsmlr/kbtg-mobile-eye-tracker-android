package com.ria.demo.utilities

import android.util.Log
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.ria.demo.MainActivity.Companion.circles
import com.ria.demo.models.Circle
import com.ria.demo.utilities.Constants.Companion.SERVER_URL
import org.json.JSONObject
import java.io.File

class Uploader {
    companion object {
        const val TAG = "Uploader"

        fun uploadFaceVideo(recordType: String, videoList: ArrayList<File>) {
            if (recordType == "prediction") {
                uploadVideo(videoList, "/predict")
            } else if (recordType == "calibration") {
                uploadCalibrateVideo(videoList)
            }
        }

        fun uploadScreenVideo(videoList: ArrayList<File>) {
            uploadVideo(videoList, "/save-screen-video")
        }

        private fun uploadVideo(videoList: ArrayList<File>, path: String) {
            AndroidNetworking.upload("$SERVER_URL$path")
                .addMultipartFileList("video[]", videoList)
                .setPriority(Priority.HIGH)
                .build()
                .setUploadProgressListener { bytesUploaded, totalBytes ->
                    Log.d(
                        TAG,
                        String.format("BytesUploaded: %s / %s", bytesUploaded, totalBytes)
                    )
                }
                .getAsJSONObject(object : JSONObjectRequestListener {
                    override fun onResponse(response: JSONObject?) {
                        Log.d(TAG, response.toString())
                    }

                    override fun onError(anError: ANError?) {
                        Log.d(TAG, anError.toString())
                    }
                })
        }

        private fun uploadCalibrateVideo(videoList: ArrayList<File>) {
            val xPositionsStr = circles.map(Circle::x).joinToString(",")
            val yPositionsStr = circles.map(Circle::y).joinToString(",")

            AndroidNetworking.upload("$SERVER_URL/calibrate")
                .addMultipartFileList("video[]", videoList)
                .addMultipartParameter("xPositions", xPositionsStr)
                .addMultipartParameter("yPositions", yPositionsStr)
                .setPriority(Priority.HIGH)
                .build()
                .setUploadProgressListener { bytesUploaded, totalBytes ->
                    Log.d(
                        TAG,
                        String.format("BytesUploaded: %s / %s", bytesUploaded, totalBytes)
                    )
                }
                .getAsJSONObject(object : JSONObjectRequestListener {
                    override fun onResponse(response: JSONObject?) {
                        Log.d(TAG, response.toString())
                    }

                    override fun onError(anError: ANError?) {
                        Log.d(TAG, anError.toString())
                    }
                })
        }
    }
}