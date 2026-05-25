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
import android.graphics.Rect
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

    private companion object {
        private const val LABEL = "AlphaDroid"
    }

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

    // Cached values that only change when the view is resized. Recomputing measureText and
    // the path offsets every frame for 2 s of animation was the dominant CPU cost on this view.
    private var cachedLabelWidth = 0f
    private var cachedStartOffset = 0f
    private var cachedCenterOffset = 0f
    private var cachedVOffset = 0f
    // Damage rect for the text band only. Invalidating the whole view forced the static
    // bitmap to be recorded again every frame.
    private val textDamageRect = Rect()

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

        // Cache text metrics now that text size, paint, and path are stable.
        cachedLabelWidth = textPaint.measureText(LABEL)
        cachedStartOffset = 0f
        cachedCenterOffset = (textPathLength - cachedLabelWidth) / 2f
        cachedVOffset = textPaint.textSize + (resources.displayMetrics.density * 2f)

        // Damage rect covers the upper half of the view where the text band travels.
        // The arc spans 180° starting at the ring center, so the band is bounded by the
        // ring rect we already computed plus a margin for shadow + stroke.
        val damageMargin = (resources.displayMetrics.density * 12f).toInt()
        textDamageRect.set(
            (ringRect.left.toInt() - damageMargin).coerceAtLeast(0),
            (ringRect.top.toInt() - damageMargin).coerceAtLeast(0),
            (ringRect.right.toInt() + damageMargin).coerceAtMost(w),
            (ringRect.bottom.toInt() + damageMargin).coerceAtMost(h)
        )
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
        if (textPathMeasure == null || textPathLength <= 0f) return

        val currentOffset = cachedStartOffset +
                (cachedCenterOffset - cachedStartOffset) * progress

        if (!isDarkTheme) {
            canvas.drawTextOnPath(LABEL, textPath, currentOffset, cachedVOffset, textStrokePaint)
        }

        canvas.drawTextOnPath(LABEL, textPath, currentOffset, cachedVOffset, textPaint)
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
                // Invalidate only the band where the text travels. The logo bitmap
                // sits underneath but doesn't change during the animation, so damaging
                // the whole view forced HWUI to re-record the bitmap draw every frame.
                if (!textDamageRect.isEmpty) {
                    invalidate(textDamageRect)
                } else {
                    invalidate()
                }
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