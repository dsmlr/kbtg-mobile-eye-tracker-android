package com.ria.demo.utilities

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.makeToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun AppCompatActivity.makeLongToast(text: String?) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
