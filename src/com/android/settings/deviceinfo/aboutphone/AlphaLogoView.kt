package com.android.settings.deviceinfo.aboutphone

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.android.settings.R

class AlphaLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Callback for when animation completes
    private var animationEndListener: (() -> Unit)? = null

    fun setOnAnimationEndListener(listener: () -> Unit) {
        animationEndListener = listener
    }

    // Track if animation has already run
    private var hasAnimationRun = false
    private var isAnimating = false

    // Check if dark theme
    private val isDarkTheme: Boolean
        get() {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return nightMode == Configuration.UI_MODE_NIGHT_YES
        }

    // Logo bitmap
    private var logoBitmap: Bitmap? = null
    private var scaledBitmap: Bitmap? = null
    private var lastImageSize = 0

    // Text color always white
    private val textColor = Color.WHITE

    // Paints
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 50f
        textAlign = Paint.Align.LEFT
        style = Paint.Style.FILL
        isFakeBoldText = true
    }

    // Stroke paint for light theme
    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 50f
        textAlign = Paint.Align.LEFT
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isFakeBoldText = true
    }

    // Geometry
    private var cx = 0f
    private var cy = 0f
    private var imageSize = 0f

    // Text path matching the ring in the image
    private val textPath = Path()
    private var textPathMeasure: PathMeasure? = null
    private var textPathLength = 0f

    // Animation
    private var progress = 0f
    private var animator: ValueAnimator? = null

    init {
        // Set shadow based on theme
        if (isDarkTheme) {
            textPaint.setShadowLayer(8f, 0f, 0f, Color.BLACK)
        } else {
            textPaint.setShadowLayer(4f, 1f, 1f, Color.DKGRAY)
        }
    }

    private fun loadBitmap() {
        if (logoBitmap == null) {
            logoBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg_alpha_logo)
        }
    }

    private fun createScaledBitmap(size: Int) {
        if (size <= 0) return
        if (size == lastImageSize && scaledBitmap != null && !scaledBitmap!! .isRecycled) return

        loadBitmap()
        logoBitmap?.let { original ->
            if (! original.isRecycled) {
                if (scaledBitmap != null && !scaledBitmap!!.isRecycled && lastImageSize != size) {
                    scaledBitmap?.recycle()
                }
                scaledBitmap = Bitmap.createScaledBitmap(original, size, size, true)
                lastImageSize = size
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        cx = w / 2f
        cy = h / 2f

        val minDim = w.coerceAtMost(h).toFloat()
        imageSize = minDim * 0.85f

        createScaledBitmap(imageSize.toInt())

        val ringCenterY = cy + (imageSize * 0.08f)
        val ringRadiusX = imageSize * 0.48f
        val ringRadiusY = imageSize * 0.18f

        val ringRect = RectF(
            cx - ringRadiusX,
            ringCenterY - ringRadiusY,
            cx + ringRadiusX,
            ringCenterY + ringRadiusY
        )

        textPath.reset()
        textPath.addArc(ringRect, 180f, -180f)
        textPathMeasure = PathMeasure(textPath, false)
        textPathLength = textPathMeasure?.length ?: 0f

        val textSize = resources.displayMetrics.density * 18
        textPaint.textSize = textSize
        textStrokePaint.textSize = textSize
        textStrokePaint.strokeWidth = resources.displayMetrics.density * 2.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = scaledBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            val left = cx - (bitmap.width / 2f)
            val top = cy - (bitmap.height / 2f)
            canvas.drawBitmap(bitmap, left, top, null)
        }

        drawText(canvas)
    }

    private fun drawText(canvas: Canvas) {
        val pm = textPathMeasure ?: return
        val label = "AlphaDroid"
        val textWidth = textPaint.measureText(label)

        val startOffset = 0f
        val centerOffset = (textPathLength - textWidth) / 2f

        val currentOffset = startOffset + (centerOffset - startOffset) * progress

        val vOffset = textPaint.textSize + (resources.displayMetrics.density * 2f)

        if (!isDarkTheme) {
            canvas.drawTextOnPath(label, textPath, currentOffset, vOffset, textStrokePaint)
        }

        canvas.drawTextOnPath(label, textPath, currentOffset, vOffset, textPaint)
    }

    fun startAnimation() {
        // Don't run animation again if already completed or currently running
        if (hasAnimationRun || isAnimating) return

        isAnimating = true
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { va ->
                progress = va.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Only trigger if animation actually completed (not cancelled)
                    if (progress >= 1f) {
                        hasAnimationRun = true
                        isAnimating = false
                        animationEndListener?.invoke()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    isAnimating = false
                }
            })
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        createScaledBitmap(imageSize.toInt())

        // Only start animation if it hasn't run yet
        if (! hasAnimationRun && !isAnimating) {
            postDelayed({ startAnimation() }, 400)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}