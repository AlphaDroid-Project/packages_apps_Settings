package com.android.settings.deviceinfo.aboutphone

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.os.SystemProperties
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.android.settings.Utils

import com.android.settings.R;
class RomCardView(context: Context, attrs: AttributeSet?) : AboutBaseCard(context, attrs) {

    init {
        layout.id = R.id.rom_card_id
        layout.gravity = Gravity.CENTER_HORIZONTAL
        val linearLayout = LinearLayout(context)
        linearLayout.orientation = LinearLayout.VERTICAL
        val rom_logo = ImageView(context)
        rom_logo.setPadding(12, 12, 12, 12)
        rom_logo.setScaleType(ScaleType.CENTER_INSIDE)
        /*
        rom_logo.imageTintList = ColorStateList.valueOf(
            Utils.getColorAttrDefaultColor(
                context,
                android.R.attr.colorAccent
            )
        )
        */
        linearLayout.id = R.id.rom_logo_id
        rom_logo.setImageResource(R.drawable.ic_rom_logo)
        val rlparams = RelativeLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        rlparams.addRule(RelativeLayout.ABOVE, R.id.rom_logo_id)
        rom_logo.layoutParams = rlparams
        minimumWidth = rom_logo.width
        val androidVersion = Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY.toString()
        val alphaVersion = SystemProperties.get("ro.alpha.build.version")
        val rom_title = TextView(context)
        rom_title.text = "AlphaDroid $alphaVersion"
        rom_title.setTextColor(
            Utils.getColorAttrDefaultColor(
                context,
                android.R.attr.textColorPrimary
            )
        )
        rom_title.setPadding(0, 12, 0, 6)
        rom_title.textSize = 18f
        rom_title.textAlignment = TEXT_ALIGNMENT_CENTER
        rom_title.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rom_version = TextView(context)
        rom_version.text = String.format(resources.getString(
                R.string.about_device_rom_title), androidVersion)
        rom_version.setTextColor(
            Utils.getColorAttrDefaultColor(
                context,
                android.R.attr.textColorSecondary
            )
        )
        rom_version.setPadding(0, 2, 0, 12)
        rom_version.textSize = 14f
        rom_version.isSingleLine = true
        rom_version.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rom_version.textAlignment = TEXT_ALIGNMENT_CENTER
        linearLayout.gravity = Gravity.CENTER_HORIZONTAL
        val lparamas = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        lparamas.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        linearLayout.layoutParams = lparamas
        linearLayout.addView(rom_title)
        linearLayout.addView(rom_version)
        linearLayout.setBackgroundColor(resources.getColor(R.color.contextual_card_background, null))
        layout.addView(rom_logo, rlparams)
        layout.addView(linearLayout, lparamas)
    }
}
