package com.android.settings.deviceinfo.aboutphone

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.android.settings.R
import com.android.settings.Utils

typealias onDeviceChanged = ((deviceName: String) -> Unit)?

class DeviceCardView : LinearLayout {

    private lateinit var deviceSummary: TextView
    private var listener: onDeviceChanged = null

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(attrs) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(attrs) }

    private fun init(attrs: AttributeSet?) {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        setBackgroundResource(R.drawable.bg_about_card_top_right)

        // Add padding to the card itself so text isn't stuck to edges
        val vPadding = (resources.displayMetrics.density * 12).toInt()
        setPadding(0, vPadding, 0, vPadding)

        val a = context.obtainStyledAttributes(attrs, R.styleable.DeviceCardView, 0, 0)
        val titleAttrText = a.getString(R.styleable.DeviceCardView_extra_title)
        a.recycle()

        val cardTitle = TextView(context)
        cardTitle.text = titleAttrText ?: context.getString(R.string.about_device_name_title)
        cardTitle.setTextAppearance(R.style.TextAppearance_HomepageCardTitle)
        cardTitle.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER)

        deviceSummary = TextView(context)
        updateDeviceNameDisplay()
        deviceSummary.textSize = 16f
        deviceSummary.setTextColor(Utils.getColorAttrDefaultColor(context, android.R.attr.textColorPrimary))
        deviceSummary.gravity = Gravity.CENTER
        deviceSummary.setPadding(
            (resources.displayMetrics.density * 4).toInt(), 0,
            (resources.displayMetrics.density * 4).toInt(), 0
        )

        addView(cardTitle)
        addView(deviceSummary)

        setOnClickListener { showRenameDialog() }
    }

    private fun updateDeviceNameDisplay() {
        var name = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        if (name == null) name = Build.MODEL
        deviceSummary.text = name
    }

    private fun showRenameDialog() {
        val alert = AlertDialog.Builder(context, R.style.Theme_AlertDialog)
        val dialogView = View.inflate(context, R.layout.device_name_dialog, null)
        val mEditText = dialogView.findViewById<EditText>(R.id.device_edit_text)

        alert.setTitle(context.getString(R.string.my_device_info_device_name_preference_title))
        alert.setView(dialogView)

        mEditText?.setText(deviceSummary.text)

        alert.setPositiveButton(android.R.string.ok) { dialog, _ ->
            val newName = mEditText?.text.toString()
            listener?.invoke(newName)
            dialog.dismiss()
        }
        alert.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
        alert.show()
    }

    fun setListener(l: onDeviceChanged) { listener = l }

    fun setDeviceName(name: String, validator: Boolean) {
        if (validator) {
            deviceSummary.text = name
            listener?.invoke(name)
        }
    }
    fun setDeviceName(name: String) { deviceSummary.text = name }
}