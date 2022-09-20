package com.android.settings.homepage;

import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
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

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class TopMenu extends Preference {

    private long lastTouchTime = 0;
    private long currentTouchTime = 0;

    public TopMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(context.getResources().
                getIdentifier("layout/nad_top_menu", null, context.getPackageName()));

    }


    // system prop
    public static String getSystemProperty(String key) {
        String value = null;

        try {
            value = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, key);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        final boolean selectable = false;
        final Context context = getContext();

        TextView mBatteryText = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/battery_title", null, context.getPackageName()));

        //TextView deviceName = holder.itemView.findViewById(context.getResources().
        //        getIdentifier("id/device_name", null, context.getPackageName()));

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Get the battery scale
                int mProgressStatus = 0;
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
                boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;

                // get the battery level
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);

                // Calculate the battery charged percentage
                float percentage = level / (float) scale;
                // Update the progress bar to display current battery charged percentage
                mProgressStatus = (int) ((percentage) * 100);

                // Show the battery charged percentage text
                if (isCharging) {
                    if (usbCharge) {
                        mBatteryText.setText("⚡ USB " + mProgressStatus + "%");
                    } else if (acCharge) {
                        mBatteryText.setText("⚡ AC " + mProgressStatus + "%");
                    } else {
                        mBatteryText.setText(context.getString(R.string.power_usage_summary_title) + " " + mProgressStatus + "%");
                    }
                } else {
                    mBatteryText.setText(context.getString(R.string.power_usage_summary_title) + " " + mProgressStatus + "%");

                }
            }

        }, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        holder.itemView.setFocusable(selectable);
        holder.itemView.setClickable(selectable);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        /*LinearLayout mAbout = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/about", null, context.getPackageName()));
        mAbout.setClickable(true);
        mAbout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$MyDeviceInfoActivity"));
                context.startActivity(intent);
            }
        });*/

        LinearLayout mNad = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/alpha_settings", null, context.getPackageName()));
        mNad.setClickable(true);
        mNad.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$AlphaSettingsActivity"));
                context.startActivity(intent);
            }
        });

        LinearLayout mWifi = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/wifi", null, context.getPackageName()));
        mWifi.setClickable(true);
        mWifi.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$NetworkDashboardActivity"));
                context.startActivity(intent);
            }
        });

        LinearLayout mBattery = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/battery", null, context.getPackageName()));
        mBattery.setClickable(true);
        mBattery.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$PowerUsageSummaryActivity"));
                context.startActivity(intent);
            }
        });

        //deviceName.setText(getDeviceModel());
        //setInfo("ro.product.model", deviceName);
    }
}
