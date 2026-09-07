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
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/**
 * A free-text value stored in Settings.Secure under the preference's own key, edited through an
 * {@link EditTextPreference}. The preference is non-persistent; this controller loads the current
 * value into the dialog and writes changes straight to Settings.Secure. Only keys within this
 * feature's namespace are honored.
 */
public class IntegritySpoofSecureTextPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_PREFIX = "fundamental_integrity_";

    public IntegritySpoofSecureTextPreferenceController(@NonNull Context context,
            @NonNull String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return getPreferenceKey().startsWith(KEY_PREFIX) ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void displayPreference(@NonNull PreferenceScreen screen) {
        super.displayPreference(screen);
        final Preference pref = screen.findPreference(getPreferenceKey());
        if (pref instanceof EditTextPreference) {
            ((EditTextPreference) pref).setText(getValue());
        }
    }

    @Override
    public void updateState(@NonNull Preference preference) {
        if (preference instanceof EditTextPreference) {
            ((EditTextPreference) preference).setText(getValue());
        }
        refreshSummary(preference);
    }

    @Override
    public CharSequence getSummary() {
        final String value = getValue();
        return TextUtils.isEmpty(value)
                ? mContext.getString(R.string.fundamental_integrity_value_unset)
                : value;
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        if (!getPreferenceKey().startsWith(KEY_PREFIX)) {
            return false;
        }
        final String str = newValue == null ? "" : newValue.toString().trim();
        Settings.Secure.putString(mContext.getContentResolver(), getPreferenceKey(), str);
        refreshSummary(preference);
        return true;
    }

    private String getValue() {
        final String v =
                Settings.Secure.getString(mContext.getContentResolver(), getPreferenceKey());
        return v == null ? "" : v;
    }
}
