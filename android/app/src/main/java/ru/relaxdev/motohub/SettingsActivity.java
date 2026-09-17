package ru.relaxdev.motohub;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(size);
        v.setPadding(dp(18), dp(10), dp(18), dp(10));
        return v;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(18), dp(12), dp(18));
        root.setBackgroundColor(Color.rgb(8, 8, 8));

        TextView title = text("MOTOHUB", 28);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(70)));

        TextView subtitle = text("НАСТРОЙКИ ПРИЛОЖЕНИЯ", 12);
        subtitle.setTextColor(Color.LTGRAY);
        root.addView(subtitle);

        Switch notifications = new Switch(this);
        notifications.setText("Уведомления");
        notifications.setTextColor(Color.WHITE);
        notifications.setTextSize(17);
        notifications.setPadding(dp(18), dp(12), dp(18), dp(12));

        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean enabled = true;
        if (Build.VERSION.SDK_INT >= 24 && nm != null) {
            enabled = nm.areNotificationsEnabled();
        }
        notifications.setChecked(enabled);
        notifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked && Build.VERSION.SDK_INT >= 26 && nm != null) {
                nm.deleteNotificationChannel("motohub_general");
            } else if (isChecked) {
                recreateChannel();
            }
        });
        root.addView(notifications, new LinearLayout.LayoutParams(-1, dp(60)));

        TextView openSystem = text("Разрешения уведомлений Android  ›", 16);
        openSystem.setTextColor(Color.rgb(225, 6, 0));
        openSystem.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            } catch (Exception ignored) {}
        });
        root.addView(openSystem, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView theme = text("Тема приложения", 16);
        root.addView(theme, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView themeInfo = text("Основной дизайн MotoHub управляется сайтом.\n"
                + "Приложение использует фирменную красно-чёрную оболочку.", 14);
        themeInfo.setTextColor(Color.LTGRAY);
        root.addView(themeInfo, new LinearLayout.LayoutParams(-1, dp(70)));

        TextView version = text("Версия 1.1.0", 14);
        version.setTextColor(Color.LTGRAY);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, dp(55));
        vp.topMargin = dp(16);
        root.addView(version, vp);

        TextView back = text("‹  НАЗАД", 16);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(60)));

        setContentView(root);
    }

    private void recreateChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                android.app.NotificationChannel channel =
                        new android.app.NotificationChannel(
                                "motohub_general",
                                "MotoHub",
                                NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Уведомления MotoHub");
                nm.createNotificationChannel(channel);
            }
        }
    }
}
