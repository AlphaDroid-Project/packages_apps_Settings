package com.android.settings.deviceinfo.aboutphone

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemProperties
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.android.settings.R
import com.android.settings.Utils

class RomCardView(context: Context, attrs: AttributeSet?) : AboutBaseCard(context, attrs) {

    private lateinit var versionText: TextView
    private var versionAnimationRun = false

    // Check if dark theme
    private val isDarkTheme: Boolean
        get() {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return nightMode == Configuration.UI_MODE_NIGHT_YES
        }

    init {
        setBackgroundResource(R.drawable.bg_about_card_left)

        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        layout = RelativeLayout(context)
        layout.layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )

        // Container for AlphaLogoView and version text
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER_HORIZONTAL
        container.layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )

        // AlphaLogoView
        val alphaLogoView = AlphaLogoView(context)
        val alphaLogoParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0
        )
        alphaLogoParams.weight = 1f
        alphaLogoView.layoutParams = alphaLogoParams

        // Version text
        val alphaVersion = SystemProperties.get("ro.alpha.build.version", "4.2")
        versionText = TextView(context)
        versionText.text = alphaVersion
        versionText.textSize = 16f
        versionText.setTextColor(Color.WHITE)
        versionText.gravity = Gravity.CENTER
        versionText.visibility = View.INVISIBLE
        versionText.alpha = 0f

        // Rounded Background
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.RECTANGLE
        shape.cornerRadius = (resources.displayMetrics.density * 12)

        var badgeColor = Utils.getColorAttrDefaultColor(context, android.R.attr.colorAccent)

        // Darken the color if in dark theme
        if (isDarkTheme) {
            // Darken by blending with black (e.g., 20% darken)
            val factor = 0.8f
            val r = (Color.red(badgeColor) * factor).toInt()
            val g = (Color.green(badgeColor) * factor).toInt()
            val b = (Color.blue(badgeColor) * factor).toInt()
            badgeColor = Color.rgb(r, g, b)
        }

        shape.setColor(badgeColor)
        versionText.background = shape

        val paddingH = (resources.displayMetrics.density * 12).toInt()
        val paddingV = (resources.displayMetrics.density * 4).toInt()
        versionText.setPadding(paddingH, paddingV, paddingH, paddingV)

        val versionParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        versionParams.topMargin = (resources.displayMetrics.density * -12).toInt()
        versionParams.bottomMargin = (resources.displayMetrics.density * 12).toInt()
        versionText.layoutParams = versionParams

        alphaLogoView.setOnAnimationEndListener {
            showVersionText()
        }

        container.addView(alphaLogoView)
        container.addView(versionText)

        layout.addView(container)
        addView(layout)
        radius = defaultRadius.toFloat()

        layout.setOnClickListener {
            context.startActivity(Intent("android.settings.FIRMWARE_VERSION_SETTINGS"))
        }
    }

    private fun showVersionText() {
        if (versionAnimationRun) return
        versionAnimationRun = true

        versionText.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(versionText, "alpha", 0f, 1f).apply {
            duration = 500
            start()
        }
    }
}