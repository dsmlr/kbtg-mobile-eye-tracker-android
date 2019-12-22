package com.ria.demo.utilities

import android.util.Log
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.ria.demo.utilities.Constants.Companion.SERVER_URL
import org.json.JSONObject
import java.io.File

class Uploader {
    companion object {
        const val TAG = "Uploader"

        fun uploadImagesToCalibrate(imgList: ArrayList<File>) {
            AndroidNetworking.upload("$SERVER_URL/calibrate")
                .addMultipartFileList("image[]", imgList)
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

        fun uploadVideosToPredict(videoList: ArrayList<File>) {
            AndroidNetworking.upload("$SERVER_URL/predict")
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