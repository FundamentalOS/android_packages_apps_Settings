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

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/**
 * Summary for the entry that opens the app-selection sub-screen: shows how many packages are
 * currently listed in {@link IntegritySpoofKeys#SECURE_TARGET_PACKAGES}.
 */
public class IntegritySpoofTargetAppsPreferenceController extends BasePreferenceController {

    public IntegritySpoofTargetAppsPreferenceController(@NonNull Context context,
            @NonNull String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        final String csv = Settings.Secure.getString(mContext.getContentResolver(),
                IntegritySpoofKeys.SECURE_TARGET_PACKAGES);
        final int count = countPackages(csv);
        if (count == 0) {
            return mContext.getString(R.string.fundamental_integrity_target_apps_none);
        }
        return mContext.getString(R.string.fundamental_integrity_target_apps_count, count);
    }

    static int countPackages(String csv) {
        if (TextUtils.isEmpty(csv)) {
            return 0;
        }
        int count = 0;
        for (String p : csv.split(",")) {
            if (!TextUtils.isEmpty(p.trim())) {
                count++;
            }
        }
        return count;
    }
}
