package com.android.settings.alpha.homepage;

import android.animation.ObjectAnimator;
import android.app.settings.SettingsEnums;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BlendMode;
import android.os.BatteryManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.SettingsApplication;
import com.android.settings.homepage.SettingsHomepageActivity;
import com.android.settings.accounts.AvatarViewMixin;
import com.android.settings.overlay.FeatureFactory;

import com.android.settingslib.widget.LayoutPreference;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class NadTopMenu extends Preference {

    private long lastTouchTime = 0;
    private long currentTouchTime = 0;

    private SettingsHomepageActivity mHomePageActivity;
    private AvatarViewMixin mAvatarViewMixin;
    private BroadcastReceiver mReceiver;

    public NadTopMenu(Context context, AttributeSet attrs) {
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

        holder.itemView.setFocusable(selectable);
        holder.itemView.setClickable(selectable);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        // get homepage activity
        mHomePageActivity = ((SettingsApplication) context.getApplicationContext()).getHomeActivity();

        // avatar
        ImageView avatarImageView = (ImageView) holder.itemView.findViewById(
                context.getResources().getIdentifier("id/account_avatar", null, context.getPackageName()));
        if (mHomePageActivity != null) {
            if (AvatarViewMixin.isAvatarSupported(mHomePageActivity)) {
                avatarImageView.setVisibility(View.VISIBLE);
                mAvatarViewMixin = new AvatarViewMixin(mHomePageActivity, avatarImageView);
                mHomePageActivity.getLifecycle().addObserver(mAvatarViewMixin);
            } else {
                avatarImageView.setVisibility(View.GONE);
            }
        }

        // Alpha Settings
        LinearLayout alphaSettingsLayout = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/alpha_settings", null, context.getPackageName()));
        alphaSettingsLayout.setClickable(true);
        alphaSettingsLayout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$AlphaSettingsActivity"));
                context.startActivity(intent);
            }
        });

        // search
        View toolbar =  holder.itemView.findViewById(context.getResources().
                getIdentifier("id/search_action_bar", null, context.getPackageName()));
        if (mHomePageActivity != null) {
            FeatureFactory.getFeatureFactory().getSearchFeatureProvider()
                    .initSearchToolbar(mHomePageActivity /* activity */, toolbar,
                            SettingsEnums.SETTINGS_HOMEPAGE);
        }

        // wifi
        LinearLayout wifiLayout = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/wifi", null, context.getPackageName()));
        wifiLayout.setClickable(true);
        wifiLayout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$NetworkDashboardActivity"));
                context.startActivity(intent);
            }
        });

        // battery
        LinearLayout batteryLayout = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/battery", null, context.getPackageName()));

        TextView batterySummaryTextView = holder.itemView.findViewById(context.getResources().
                getIdentifier("id/battery_summary", null, context.getPackageName()));

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Get the battery scale
                int batteryLevel;
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
                boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;

                // get the battery level
                int rawLevel =
                        intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);

                // scale it
                float scaledLevel = rawLevel / (float) scale;

                // convert to percentage
                batteryLevel = (int) ((scaledLevel) * 100);

                ImageView batteryImageView = (ImageView) holder.itemView.findViewById(
                        context.getResources().getIdentifier("id/battery_icon", null, context.getPackageName()));

                // get battery info and set summary
                String summary = "";
                if (isCharging) {
                    batteryImageView.setImageResource(R.drawable.ic_top_menu_battery_charging);
                    batteryImageView.setRotation(0.0f);
                    if (usbCharge) {
                        summary = "USB \u00B7 ";
                    } else if (acCharge) {
                        summary = "AC \u00B7 ";
                    }
                } else {
                    batteryImageView.setImageResource(R.drawable.ic_top_menu_battery);
                    batteryImageView.setRotation(90.0f);
                }
                summary += batteryLevel + "%";
                batterySummaryTextView.setText(summary);
            }
        };
        context.registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        batteryLayout.setClickable(true);
        batteryLayout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$PowerUsageSummaryActivity"));
                context.startActivity(intent);
            }
        });
    }

    @Override
    public void onDetached() {
        super.onDetached();
        if (mReceiver != null) {
            getContext().unregisterReceiver(mReceiver);
        }
        if (mHomePageActivity != null && mAvatarViewMixin != null) {
            mHomePageActivity.getLifecycle().removeObserver(mAvatarViewMixin);
        }
    }
}
