package com.ria.demo.utilities

import android.util.Log
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.ria.demo.models.Circle
import com.ria.demo.utilities.Constants.Companion.SERVER_URL
import org.json.JSONObject
import java.io.File
import kotlin.collections.ArrayList

class Uploader {
    companion object {
        const val TAG = "Uploader"

        fun uploadImagesToCalibrate(imageArray: ArrayList<File>, circles: ArrayList<Circle>) {
            val xPositionsStr = circles.map(Circle::x).joinToString(",")
            val yPositionsStr = circles.map(Circle::y).joinToString(",")

            AndroidNetworking.upload("$SERVER_URL/calibrate")
                .addMultipartFileList("image[]", imageArray)
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

        fun uploadFaceVideo(videoList: ArrayList<File>) {
            uploadVideo(videoList, "/predict")
        }

        fun uploadScreenVideo(videoList: ArrayList<File>) {
            uploadVideo(videoList, "/save-screen-video")
        }

        private fun uploadVideo(videoList: ArrayList<File>, path: String) {
            AndroidNetworking.upload("$SERVER_URL$path")
                .addMultipartFileList("video[]", videoList)
                .addMultipartParameter("key", "value")
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