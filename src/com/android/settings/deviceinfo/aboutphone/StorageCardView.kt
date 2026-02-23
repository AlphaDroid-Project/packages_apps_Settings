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
                // Clamp value to 0-100 to prevent visual glitches during overshoot
                val value = (it.animatedValue as Float).coerceIn(0f, 100f)
                waveView.setProgress(value.toInt())
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
        private var mProgress = 0
        private val mWaveHeight = 10
        private val mWaveHz = 0.02f
        private val mAboveWavePath = Path()
        private val mBlowWavePath = Path()
        private val clipPath = Path()
        val aboveWavePaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
        val blowWavePaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true; alpha = 100 }
        private var mAboveOffset = 0.0f
        private var mBlowOffset = 0f
        private var mRefreshRunnable: Runnable?  = null

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

        fun setProgress(p: Int) {
            mProgress = p.coerceIn(0, 100)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            clipPath.reset()
            // Top-Left (small), Top-Right (large), Bottom-Right (small), Bottom-Left (small)
            val radii = floatArrayOf(
                smallRadius, smallRadius, // TL
                largeRadius, largeRadius, // TR
                smallRadius, smallRadius, // BR
                smallRadius, smallRadius  // BL
            )
            clipPath.addRoundRect(RectF(0f, 0f, w, h), radii, Path.Direction.CW)
            canvas.clipPath(clipPath)

            val waveTop = h * (1f - mProgress / 100f)
            mAboveWavePath.reset(); mAboveWavePath.moveTo(0f, h)
            mBlowWavePath.reset(); mBlowWavePath.moveTo(0f, h)

            var x = 0f
            while (x <= w) {
                val y1 = waveTop + mWaveHeight * sin((x / w * 2 * Math.PI + mAboveOffset).toDouble()).toFloat()
                val y2 = waveTop + mWaveHeight * sin((x / w * 2 * Math.PI + mBlowOffset).toDouble()).toFloat()
                mAboveWavePath.lineTo(x, y1)
                mBlowWavePath.lineTo(x, y2)
                x += 10f
            }
            mAboveWavePath.lineTo(w, h); mAboveWavePath.close()
            mBlowWavePath.lineTo(w, h); mBlowWavePath.close()

            canvas.drawPath(mBlowWavePath, blowWavePaint)
            canvas.drawPath(mAboveWavePath, aboveWavePaint)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            mRefreshRunnable = object : Runnable {
                override fun run() {
                    mAboveOffset += mWaveHz
                    mBlowOffset += mWaveHz + 0.01f
                    invalidate()
                    postDelayed(this, 16)
                }
            }
            post(mRefreshRunnable)
        }
        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            removeCallbacks(mRefreshRunnable)
        }
    }
}