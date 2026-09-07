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

import androidx.annotation.NonNull;

/**
 * Controller for the build-fingerprint override switch. Separate class so DashboardFragment (which
 * keys controllers by class) keeps it active alongside the other switches. Behaviour is inherited
 * from {@link IntegritySpoofSecureSwitchPreferenceController} (0/1 in Settings.Secure).
 */
public class IntegritySpoofFingerprintSwitchPreferenceController
        extends IntegritySpoofSecureSwitchPreferenceController {

    public IntegritySpoofFingerprintSwitchPreferenceController(@NonNull Context context,
            @NonNull String preferenceKey) {
        super(context, preferenceKey);
    }
}
