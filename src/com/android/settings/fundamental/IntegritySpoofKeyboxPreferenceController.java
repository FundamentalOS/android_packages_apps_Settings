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

import java.io.File;

/** Summary for the file-import entry. The click is handled by the hosting fragment. */
public class IntegritySpoofKeyboxPreferenceController extends BasePreferenceController {

    public IntegritySpoofKeyboxPreferenceController(@NonNull Context context,
            @NonNull String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        final String cached = Settings.Secure.getString(mContext.getContentResolver(),
                IntegritySpoofKeys.SECURE_KEYBOX_STATUS);
        if (!TextUtils.isEmpty(cached)) {
            return mContext.getString(R.string.fundamental_integrity_keybox_installed, cached);
        }
        if (new File(IntegritySpoofKeys.KEYBOX_PATH).exists()) {
            return mContext.getString(R.string.fundamental_integrity_keybox_present);
        }
        return mContext.getString(R.string.fundamental_integrity_keybox_absent);
    }
}
