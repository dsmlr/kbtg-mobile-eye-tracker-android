package com.ria.demo.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Nullable
import com.ria.demo.models.Circle

class CanvasView(context: Context, @Nullable attrs: AttributeSet) : View(context, attrs) {
    companion object {
        private const val TAG = "CanvasView"
    }

    private var x = 0
    private var y = 0
    private var radius = 0
    private var redDotRadius = 0
    private val whiteCirclePaint = Paint()
    private val redDotPaint = Paint()

    override fun onDraw(canvas: Canvas) {
        if (this.x == 0 && this.y == 0 && this.radius == 0) {
            canvas.drawColor(Color.BLACK)
            return
        }

        canvas.drawCircle(
            this.x.toFloat(),
            this.y.toFloat(),
            this.radius.toFloat(),
            this.whiteCirclePaint
        )

        canvas.drawCircle(
            this.x.toFloat(),
            this.y.toFloat(),
            this.redDotRadius.toFloat(),
            this.redDotPaint
        )
    }

    fun drawCircle(circle: Circle) {
        this.x = circle.x
        this.y = circle.y
        this.radius = circle.radius
        this.redDotRadius = this.radius / 2
        this.whiteCirclePaint.style = Paint.Style.FILL
        this.whiteCirclePaint.color = Color.WHITE
        this.redDotPaint.style = Paint.Style.FILL
        this.redDotPaint.color = Color.RED

        invalidate()
    }

    fun clearView() {
        this.x = 0
        this.y = 0
        this.radius = 0
    }
}