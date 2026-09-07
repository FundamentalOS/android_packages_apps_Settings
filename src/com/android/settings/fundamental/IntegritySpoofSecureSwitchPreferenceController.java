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

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.TogglePreferenceController;

/**
 * A 0/1 switch stored in Settings.Secure under the preference's own key. Used (via thin per-switch
 * subclasses, since DashboardFragment keys its controllers by class) by the switches on the
 * FundamentalOS integrity screen; each preference declares the Settings.Secure name as its
 * android:key. The change listener is wired in updateState()/displayPreference() so a plain
 * SwitchPreferenceCompat toggle routes through setChecked() into Settings.Secure — TogglePreference-
 * Controller itself only special-cases the main switch. (The switches must use the bare
 * {@code SwitchPreferenceCompat} tag, not the fully-qualified name, or PreferenceXmlParserUtils
 * skips the controller.)
 */
public class IntegritySpoofSecureSwitchPreferenceController extends TogglePreferenceController {

    private static final String KEY_PREFIX = "fundamental_integrity_";

    public IntegritySpoofSecureSwitchPreferenceController(@NonNull Context context,
            @NonNull String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public void displayPreference(@NonNull PreferenceScreen screen) {
        super.displayPreference(screen);
        wireListener(screen.findPreference(getPreferenceKey()));
    }

    @Override
    public void updateState(@NonNull Preference preference) {
        super.updateState(preference);
        wireListener(preference);
    }

    private void wireListener(Preference preference) {
        if (preference != null) {
            preference.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public int getAvailabilityStatus() {
        return getPreferenceKey().startsWith(KEY_PREFIX) ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public boolean isChecked() {
        return Settings.Secure.getInt(mContext.getContentResolver(), getPreferenceKey(), 0) == 1;
    }

    @Override
    public boolean setChecked(boolean value) {
        if (!getPreferenceKey().startsWith(KEY_PREFIX)) {
            return false;
        }
        final boolean ok = Settings.Secure.putInt(mContext.getContentResolver(), getPreferenceKey(),
                value ? 1 : 0);
        if (ok && IntegritySpoofKeys.SECURE_ENABLED.equals(getPreferenceKey())) {
            // The master switch's real effect is to (re)derive or clear the forge's target-uid file.
            IntegritySpoofBridge.syncTargets(mContext);
        }
        return ok;
    }

    @Override
    public boolean isSliceable() {
        return false;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return NO_RES;
    }
}
