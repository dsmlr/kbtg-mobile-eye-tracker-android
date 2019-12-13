package com.ria.demo.utilities

import android.app.Service
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.makeToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun Service.makeToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}