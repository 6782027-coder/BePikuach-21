package com.bepikuach.activities;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bepikuach.R;
import com.bepikuach.admin.DeviceAdminReceiver;
import com.bepikuach.utils.AdminAppsAdapter;
import com.bepikuach.utils.AppInfo;
import com.bepikuach.utils.LockTaskManager;
import com.bepikuach.utils.PrefManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectAppsActivity extends AppCompatActivity {

    private PrefManager prefManager;
    private LockTaskManager lockTaskManager;
    private AdminAppsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_apps);

        prefManager = new PrefManager(this);
        lockTaskManager = new LockTaskManager(this);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.select_apps));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // הצג את כל האפליקציות כולל מוסתרות
        if (lockTaskManager.isDeviceOwner) {
            lockTaskManager.showAllAppsTemporarily();
        }

        List<AppInfo> apps = getAllInstalledApps();
        adapter = new AdminAppsAdapter(apps);

        RecyclerView list = findViewById(R.id.appsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        SearchView search = findViewById(R.id.searchApps);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { return false; }
            @Override public boolean onQueryTextChange(String q) { adapter.filter(q); return true; }
        });

        Button saveBtn = findViewById(R.id.btnSave);
        saveBtn.setOnClickListener(v -> {
            Set<String> approved = new HashSet<>();
            for (AppInfo app : adapter.getAllApps()) {
                if (app.isApproved) approved.add(app.packageName);
            }
            prefManager.setApprovedApps(approved);

            // עדכן LockTask מיד
            lockTaskManager.updateApprovedPackages(approved);

            Toast.makeText(this, "נשמר בהצלחה ✓", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private List<AppInfo> getAllInstalledApps() {
        List<AppInfo> result = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Set<String> approved = prefManager.getApprovedApps();

        // קבל את כל האפליקציות כולל מוסתרות — חובה MATCH_DISABLED_COMPONENTS + MATCH_UNINSTALLED_PACKAGES
        List<ApplicationInfo> installed = pm.getInstalledApplications(
            PackageManager.MATCH_DISABLED_COMPONENTS |
            PackageManager.GET_META_DATA
        );

        // אם Device Owner — הוסף גם חבילות מוסתרות דרך DPM
        if (lockTaskManager.isDeviceOwner) {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, DeviceAdminReceiver.class);
            // DPM לא נותן רשימה ישירה — אבל showAllAppsTemporarily() כבר ביטל hidden
            // לכן נשתמש ב-MATCH_UNINSTALLED_PACKAGES שמחזיר גם אפליקציות שעדיין installed אך hidden
            installed = pm.getInstalledApplications(
                PackageManager.MATCH_DISABLED_COMPONENTS |
                PackageManager.MATCH_UNINSTALLED_PACKAGES |
                PackageManager.GET_META_DATA
            );
        }

        for (ApplicationInfo info : installed) {
            if (info.packageName.equals("com.bepikuach")) continue;

            // בדוק אם יש launcher intent — כולל אפליקציות מוסתרות/מושבתות
            Intent launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setPackage(info.packageName);

            List<?> activities = pm.queryIntentActivities(
                launchIntent,
                PackageManager.MATCH_DISABLED_COMPONENTS
            );
            if (activities.isEmpty()) continue;

            // אפליקציות מערכת בלי launcher — דלג
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && activities.isEmpty()) continue;

            String name;
            try {
                name = pm.getApplicationLabel(info).toString();
            } catch (Exception e) {
                name = info.packageName;
            }

            try {
                result.add(new AppInfo(
                    name,
                    info.packageName,
                    pm.getApplicationIcon(info.packageName),
                    approved.contains(info.packageName)
                ));
            } catch (PackageManager.NameNotFoundException ignored) {
                // נסה עם info ישירות
                try {
                    result.add(new AppInfo(
                        name,
                        info.packageName,
                        pm.getApplicationIcon(info),
                        approved.contains(info.packageName)
                    ));
                } catch (Exception ignored2) {}
            }
        }
        result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // אם יצאנו בלי לשמור — החזר הסתרה
        lockTaskManager.restoreHiddenApps(prefManager.getApprovedApps());
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
