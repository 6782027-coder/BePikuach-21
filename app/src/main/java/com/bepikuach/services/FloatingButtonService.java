package com.bepikuach.services;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bepikuach.activities.HomeActivity;
import com.bepikuach.activities.RecentAppsActivity;
import com.bepikuach.utils.PrefManager;

public class FloatingButtonService extends Service {

    private WindowManager windowManager;
    private LinearLayout floatPanel;
    private WindowManager.LayoutParams params;
    private PrefManager prefs;
    private Handler handler;
    private Runnable checker;
    private boolean isVisible = false;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PrefManager(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        removePanel();

        int btnSizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
        int padPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());

        // פאנל אנכי עם 2 כפתורים
        floatPanel = new LinearLayout(this);
        floatPanel.setOrientation(LinearLayout.VERTICAL);
        floatPanel.setBackgroundColor(Color.TRANSPARENT);

        // כפתור מחליף אפליקציות
        TextView btnRecent = new TextView(this);
        btnRecent.setText("⧉");
        btnRecent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        btnRecent.setTextColor(Color.WHITE);
        btnRecent.setBackgroundColor(0xDD1A237E);
        btnRecent.setGravity(Gravity.CENTER);
        btnRecent.setPadding(padPx, padPx, padPx, padPx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSizePx, btnSizePx);
        lp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        btnRecent.setLayoutParams(lp);
        btnRecent.setOnClickListener(v -> {
            Intent i = new Intent(this, RecentAppsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });

        // כפתור מסך הבית
        TextView btnHome = new TextView(this);
        btnHome.setText("⌂");
        btnHome.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        btnHome.setTextColor(Color.WHITE);
        btnHome.setBackgroundColor(0xDD1A237E);
        btnHome.setGravity(Gravity.CENTER);
        btnHome.setPadding(padPx, padPx, padPx, padPx);
        btnHome.setLayoutParams(new LinearLayout.LayoutParams(btnSizePx, btnSizePx));
        btnHome.setOnClickListener(v -> {
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });

        floatPanel.addView(btnRecent);
        floatPanel.addView(btnHome);

        int gravityVal = gravityFromPref(prefs.getFloatBtnGravity());
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = gravityVal;
        params.x = padPx;
        params.y = padPx;

        try {
            windowManager.addView(floatPanel, params);
            floatPanel.setVisibility(android.view.View.GONE);
        } catch (Exception ignored) {}

        checker = new Runnable() {
            @Override public void run() {
                checkForeground();
                handler.postDelayed(this, 500);
            }
        };
        handler.post(checker);

        return START_STICKY;
    }

    private void checkForeground() {
        String pkg = getForegroundPackage();
        if (pkg == null || floatPanel == null) return;

        boolean shouldShow = !pkg.equals(getPackageName())
                && !pkg.equals("android")
                && !pkg.startsWith("com.android.systemui");

        if (shouldShow && !isVisible) {
            floatPanel.setVisibility(android.view.View.VISIBLE);
            isVisible = true;
        } else if (!shouldShow && isVisible) {
            floatPanel.setVisibility(android.view.View.GONE);
            isVisible = false;
        }
    }

    private String getForegroundPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 2000, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String last = null;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND)
                    last = e.getPackageName();
            }
            return last;
        } catch (Exception e) { return null; }
    }

    private int gravityFromPref(int g) {
        switch (g) {
            case 1: return Gravity.TOP    | Gravity.START;
            case 2: return Gravity.TOP    | Gravity.END;
            case 3: return Gravity.BOTTOM | Gravity.START;
            case 4: return Gravity.BOTTOM | Gravity.END;
            default: return Gravity.TOP   | Gravity.END;
        }
    }

    private void removePanel() {
        if (checker != null) { handler.removeCallbacks(checker); checker = null; }
        if (floatPanel != null) {
            try { windowManager.removeView(floatPanel); } catch (Exception ignored) {}
            floatPanel = null;
        }
        isVisible = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removePanel();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
