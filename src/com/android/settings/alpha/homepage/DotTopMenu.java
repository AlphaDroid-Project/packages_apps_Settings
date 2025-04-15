package com.android.settings.alpha.homepage;

import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import static android.content.ContentValues.TAG;

import com.android.settings.R;

public class DotTopMenu extends Preference {

    private long lastTouchTime = 0;
    private long currentTouchTime = 0;

    public DotTopMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(context.getResources().
                getIdentifier("layout/dot_top_menu", null, context.getPackageName()));

    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        final boolean selectable = false;
        final Context context = getContext();

        holder.itemView.setFocusable(selectable);
        holder.itemView.setClickable(selectable);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        LinearLayout mAbout = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/about", null, context.getPackageName()));

        String mDeviceName = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.DEVICE_NAME);
        if (mDeviceName == null) {
            mDeviceName = Build.MODEL;
        }

        TextView deviceName = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/device_name", null, context.getPackageName()));
        if (deviceName != null) {
            deviceName.setText(mDeviceName);
        }

        mAbout.setClickable(true);
        mAbout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$MyDeviceInfoActivity"));
                context.startActivity(intent);
            }
        });

        LinearLayout mAlpha = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/alpha_settings", null, context.getPackageName()));
        mAlpha.setClickable(true);
        mAlpha.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$AlphaSettingsActivity"));
                context.startActivity(intent);
            }
        });
    }
}
