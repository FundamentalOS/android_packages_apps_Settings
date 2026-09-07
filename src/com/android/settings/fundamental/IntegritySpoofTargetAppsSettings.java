/*
 * Copyright (C) 2026 The FundamentalOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.fundamental;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class IntegritySpoofTargetAppsSettings extends DashboardFragment {

    private static final String TAG = "IntegrityTargetApps";

    private final Set<String> mSelected = new LinkedHashSet<>();

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.fundamental_integrity_target_apps;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        mSelected.clear();
        mSelected.addAll(parseCsv(Settings.Secure.getString(
                getContext().getContentResolver(), IntegritySpoofKeys.SECURE_TARGET_PACKAGES)));
        populateApps();
    }

    private void populateApps() {
        final Context context = getContext();
        final PreferenceScreen screen = getPreferenceScreen();
        if (context == null || screen == null) {
            return;
        }
        final PackageManager pm = context.getPackageManager();

        final Set<String> candidates = new TreeSet<>();
        final Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(
                Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
            if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                candidates.add(ri.activityInfo.packageName);
            }
        }
        candidates.add(IntegritySpoofKeys.PKG_GMS);
        candidates.add(IntegritySpoofKeys.PKG_VENDING);
        candidates.addAll(mSelected);

        final List<AppRow> rows = new ArrayList<>();
        for (String pkg : candidates) {
            try {
                final ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                rows.add(new AppRow(pkg, pm.getApplicationLabel(ai).toString()));
            } catch (PackageManager.NameNotFoundException e) {
                rows.add(new AppRow(pkg, pkg));
            }
        }
        Collections.sort(rows, Comparator.comparing(r -> r.label.toLowerCase()));

        for (AppRow row : rows) {
            final SwitchPreferenceCompat pref = new SwitchPreferenceCompat(screen.getContext());
            pref.setKey("app:" + row.pkg);
            pref.setTitle(row.label);
            pref.setSummary(row.pkg);
            pref.setPersistent(false);
            pref.setChecked(mSelected.contains(row.pkg));
            try {
                pref.setIcon(pm.getApplicationIcon(row.pkg));
            } catch (PackageManager.NameNotFoundException ignored) {
                // no icon available
            }
            pref.setOnPreferenceChangeListener((p, newValue) -> {
                if (Boolean.TRUE.equals(newValue)) {
                    mSelected.add(row.pkg);
                } else {
                    mSelected.remove(row.pkg);
                }
                persist();
                return true;
            });
            screen.addPreference(pref);
        }
    }

    private void persist() {
        final String csv = TextUtils.join(",", mSelected);
        Settings.Secure.putString(getContext().getContentResolver(),
                IntegritySpoofKeys.SECURE_TARGET_PACKAGES, csv);
        try {
            DeviceConfig.setProperty(IntegritySpoofKeys.DEVICE_CONFIG_NAMESPACE_APP_COMPAT,
                    IntegritySpoofKeys.DEVICE_CONFIG_SYSPROP_OVERRIDE_PKGS, csv, false);
        } catch (Exception e) {
            Log.w(TAG, "Unable to update DeviceConfig package list", e);
        }
        // Mirror the selection into the keystore2 forge's target-uid file.
        IntegritySpoofBridge.syncTargets(getContext());
    }

    static List<String> parseCsv(String csv) {
        final List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(csv)) {
            return out;
        }
        for (String p : csv.split(",")) {
            final String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static final class AppRow {
        final String pkg;
        final String label;

        AppRow(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }
}
