package com.android.settings.deviceinfo.aboutphone

import android.animation.ValueAnimator
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.storage.StorageManager
import android.os.storage.VolumeInfo
import android.provider.Settings
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.android.settings.Utils
import com.android.settings.R
import com.android.settingslib.widget.theme.R as SettingsLibRes
import java.io.IOException
import kotlin.math.sin
import kotlin.random.Random

class StorageCardView(context: Context, attrs: AttributeSet?) : AboutBaseCard(context, attrs) {
    private var freeBytes: Long = 0
    private var usedBytes: Long = 0
    private var totalBytes: Long = 0
    private var mUsedPercent = 0
    private lateinit var waveView: WaveView
    private var progressAnimator: ValueAnimator? = null

    init {
        clipChildren = true
        clipToPadding = true

        setBackgroundResource(R.drawable.bg_about_card_bottom_right)

        layout = RelativeLayout(context)
        layout.layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        layout.clipChildren = true
        layout.clipToPadding = true

        val cardTitle = TextView(context)
        cardTitle.text = context.getString(R.string.storage_card_title)
        cardTitle.setTextAppearance(R.style.TextAppearance_HomepageCardTitle)
        val titleParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        cardTitle.layoutParams = titleParams
        val topPadding = (resources.displayMetrics.density * 8).toInt()
        cardTitle.setPadding(0, topPadding, 0, 0)
        cardTitle.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER)
        layout.addView(cardTitle)

        // 1. Wave View (Fills card edge-to-edge)
        waveView = WaveView(context)
        val waveParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        waveView.layoutParams = waveParams
        waveView.alpha = 0.5f

        val textContainer = LinearLayout(context)
        textContainer.orientation = LinearLayout.VERTICAL
        textContainer.gravity = Gravity.CENTER
        val containerParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        textContainer.layoutParams = containerParams

        val sidePadding = (resources.displayMetrics.density * 8).toInt()
        textContainer.setPadding(sidePadding, 0, sidePadding, 0)

        val storageInfoTotal = TextView(context)
        storageInfoTotal.textSize = 12f
        storageInfoTotal.setTextColor(Utils.getColorAttrDefaultColor(context, android.R.attr.textColorSecondary))
        storageInfoTotal.gravity = Gravity.CENTER
        storageInfoTotal.text = resources.getString(R.string.storage_card_info)

        val storageInfoUsed = TextView(context)
        storageInfoUsed.textSize = 18f
        storageInfoUsed.setTextColor(Utils.getColorAttrDefaultColor(context, android.R.attr.textColorPrimary))
        storageInfoUsed.gravity = Gravity.CENTER

        textContainer.addView(storageInfoTotal)
        textContainer.addView(storageInfoUsed)

        layout.addView(waveView)
        layout.addView(textContainer)

        manageStorageInfo(storageInfoUsed)

        addView(layout)
        radius = defaultRadius.toFloat()

        layout.setOnClickListener {
            context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        }
    }

    private fun manageStorageInfo(storageInfoUsed: TextView) {
        val storageManager: StorageManager?  = context.getSystemService(StorageManager::class.java)
        if (storageManager != null) {
            val volumes = storageManager.volumes
            for (vol in volumes) {
                val path = vol.getPath()
                if (vol.isMountedReadable) {
                    if (vol.getType() == VolumeInfo.TYPE_PRIVATE) {
                        val stats = context.getSystemService(StorageStatsManager::class.java)
                        try {
                            totalBytes = stats!! .getTotalBytes(vol.getFsUuid())
                            freeBytes = stats.getFreeBytes(vol.getFsUuid())
                            usedBytes = totalBytes - freeBytes
                        } catch (e: IOException) {}
                    }
                    val used = Formatter.formatFileSize(context, usedBytes, Formatter.FLAG_SHORTER)
                    val total = Formatter.formatFileSize(context, totalBytes, Formatter.FLAG_SHORTER)
                    storageInfoUsed.text = "$used / $total"

                    if (totalBytes > 0) {
                        mUsedPercent = (usedBytes * 100 / totalBytes).toInt()
                    }
                    waveView.setLowStorage(freeBytes < storageManager.getStorageLowBytes(path))
                }
            }
        } else {
            storageInfoUsed.text = "33 GB / 256 GB"
            mUsedPercent = 15
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed({ startFillAnimation(mUsedPercent) }, 300)
    }

    private fun startFillAnimation(targetProgress: Int) {
        if (targetProgress <= 0) return
        progressAnimator?.cancel()

        progressAnimator = ValueAnimator.ofFloat(0f, targetProgress.toFloat()).apply {
            duration = 1400
            interpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1f)

            addUpdateListener {
                // Feed the wave a float so the fill height advances sub-pixel per frame.
                // The previous Int truncation produced a visible 1.3 px stair-step every
                // ~3 frames on 120 Hz panels — the dominant source of the perceived stutter.
                val value = (it.animatedValue as Float).coerceIn(0f, 100f)
                waveView.setProgress(value)
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressAnimator?.cancel()
    }

    private inner class WaveView(context: Context?) : View(context) {
        private var mAboveWaveColor = Utils.getColorAttrDefaultColor(context, android.R.attr.colorAccent)
        private var mProgress = 0f
        private val mWaveHeight = 10f
        // Phase advance per millisecond. The previous implementation advanced by mWaveHz
        // (0.02 rad) every postDelayed(16), which drifts off vsync on 90/120 Hz panels and
        // produced the visible stutter. Driving the phase from frameTimeNanos keeps the wave
        // locked to the display refresh independent of frame rate.
        private val mWaveSpeedRadPerMs = 0.02f / 16f
        private val mAboveWavePath = Path()
        private val mBlowWavePath = Path()
        private val clipPath = Path()
        private val clipRect = RectF()
        private val cornerRadii = FloatArray(8)
        private var clipDirty = true
        val aboveWavePaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
        val blowWavePaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false; alpha = 100 }
        private var mAboveOffset = 0.0f
        private var mBlowOffset = 0f
        private var mLastFrameTimeNanos = 0L
        private val mChoreographer = Choreographer.getInstance()
        private var mFrameCallbackPosted = false
        private val mFrameCallback = Choreographer.FrameCallback { frameTimeNanos ->
            mFrameCallbackPosted = false
            if (!isShown || alpha == 0f || width == 0 || height == 0) {
                // Stop scheduling while invisible. We'll resume on the next attach/visibility.
                mLastFrameTimeNanos = 0L
                return@FrameCallback
            }
            if (mLastFrameTimeNanos != 0L) {
                val deltaMs = (frameTimeNanos - mLastFrameTimeNanos) / 1_000_000f
                val basePhase = deltaMs * mWaveSpeedRadPerMs
                mAboveOffset += basePhase
                mBlowOffset += basePhase + (deltaMs * (0.01f / 16f))
            }
            mLastFrameTimeNanos = frameTimeNanos
            invalidate()
            scheduleNextFrame()
        }

        private var largeRadius = 0f
        private var smallRadius = 0f

        init {
            initializePainters()
            largeRadius = resources.getDimensionPixelSize(SettingsLibRes.dimen.settingslib_preference_corner_radius).toFloat()
            smallRadius = (resources.displayMetrics.density * 4)
        }

        fun setLowStorage(low: Boolean) {
            mAboveWaveColor = if (low) Utils.getColorAttrDefaultColor(context, android.R.attr.colorError)
                              else Utils.getColorAttrDefaultColor(context, android.R.attr.colorAccent)
            initializePainters()
        }

        fun initializePainters() {
            aboveWavePaint.color = mAboveWaveColor
            blowWavePaint.color = mAboveWaveColor
            blowWavePaint.alpha = 100
        }

        fun setProgress(p: Float) {
            mProgress = p.coerceIn(0f, 100f)
            invalidate()
        }

        fun setProgress(p: Int) {
            setProgress(p.toFloat())
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            // Rebuild the rounded-corner clip only when the bounds actually change.
            // Top-Left (small), Top-Right (large), Bottom-Right (small), Bottom-Left (small)
            cornerRadii[0] = smallRadius; cornerRadii[1] = smallRadius // TL
            cornerRadii[2] = largeRadius; cornerRadii[3] = largeRadius // TR
            cornerRadii[4] = smallRadius; cornerRadii[5] = smallRadius // BR
            cornerRadii[6] = smallRadius; cornerRadii[7] = smallRadius // BL
            clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
            clipPath.reset()
            clipPath.addRoundRect(clipRect, cornerRadii, Path.Direction.CW)
            clipDirty = false
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            if (clipDirty) {
                clipRect.set(0f, 0f, w, h)
                clipPath.reset()
                clipPath.addRoundRect(clipRect, cornerRadii, Path.Direction.CW)
                clipDirty = false
            }
            canvas.clipPath(clipPath)

            val waveTop = h * (1f - mProgress / 100f)
            mAboveWavePath.rewind(); mAboveWavePath.moveTo(0f, h)
            mBlowWavePath.rewind(); mBlowWavePath.moveTo(0f, h)

            // Step 12 px instead of 10 px. The card is short and clipped, so the lower
            // resolution is invisible and saves ~17% of the per-frame lineTo calls.
            val invW = if (w > 0f) (2.0 * Math.PI / w) else 0.0
            var x = 0f
            while (x <= w) {
                val xd = x.toDouble()
                val y1 = waveTop + mWaveHeight * sin(xd * invW + mAboveOffset).toFloat()
                val y2 = waveTop + mWaveHeight * sin(xd * invW + mBlowOffset).toFloat()
                mAboveWavePath.lineTo(x, y1)
                mBlowWavePath.lineTo(x, y2)
                x += 12f
            }
            mAboveWavePath.lineTo(w, h); mAboveWavePath.close()
            mBlowWavePath.lineTo(w, h); mBlowWavePath.close()

            canvas.drawPath(mBlowWavePath, blowWavePaint)
            canvas.drawPath(mAboveWavePath, aboveWavePaint)
        }

        private fun scheduleNextFrame() {
            if (!mFrameCallbackPosted) {
                mFrameCallbackPosted = true
                mChoreographer.postFrameCallback(mFrameCallback)
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            mLastFrameTimeNanos = 0L
            scheduleNextFrame()
        }

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            super.onVisibilityChanged(changedView, visibility)
            if (visibility == VISIBLE) {
                mLastFrameTimeNanos = 0L
                scheduleNextFrame()
            } else {
                mChoreographer.removeFrameCallback(mFrameCallback)
                mFrameCallbackPosted = false
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            mChoreographer.removeFrameCallback(mFrameCallback)
            mFrameCallbackPosted = false
            mLastFrameTimeNanos = 0L
        }
    }
}