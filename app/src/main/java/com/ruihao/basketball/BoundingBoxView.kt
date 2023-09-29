package com.ruihao.basketball

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

class BoundingBoxView(context: Context?, attrs: AttributeSet?) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {
    private var mSurfaceHolder: SurfaceHolder = holder
    private val mPaint: Paint
    private var mIsCreated = false

    init {
        mSurfaceHolder.addCallback(this)
        mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT)
        setZOrderOnTop(true)
        mPaint = Paint()
        mPaint.isAntiAlias = true
        mPaint.color = Color.RED
        mPaint.strokeWidth = 5f
        mPaint.style = Paint.Style.STROKE
    }

    override fun surfaceChanged(
        surfaceHolder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        mIsCreated = true
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        mIsCreated = false
    }

    fun setResults(detRets: List<VisionDetRet?>) {
        if (!mIsCreated) {
            return
        }
        val canvas = mSurfaceHolder.lockCanvas()

        //清除掉上一次的画框。
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawColor(Color.TRANSPARENT)
        for (detRet in detRets) {
            val rect =
                detRet?.let { Rect(it.left, it.top, it.right, it.bottom) }
            if (rect != null) {
                canvas.drawRect(rect, mPaint)
            }
        }
        mSurfaceHolder.unlockCanvasAndPost(canvas)
    }
}
