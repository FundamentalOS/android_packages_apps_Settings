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
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.OnActivityResultListener;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * FundamentalOS integrity configuration screen. Lets the user enable the feature, choose which
 * apps it applies to, import a keybox file, and set a build-fingerprint override. All persistence
 * is via {@link Settings.Secure} (and DeviceConfig for the package list); the enforcing service
 * runs in system_server and reads those values.
 */
@SearchIndexable
public class IntegritySpoofSettings extends DashboardFragment implements OnActivityResultListener {

    private static final String TAG = "IntegritySpoofSettings";

    private static final String KEY_IMPORT_KEYBOX = "fundamental_integrity_import_keybox";
    private static final int REQUEST_IMPORT_KEYBOX = 1001;
    private static final int MAX_KEYBOX_BYTES = 512 * 1024;

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
        return R.xml.fundamental_integrity_spoof_settings;
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference) {
        if (KEY_IMPORT_KEYBOX.equals(preference.getKey())) {
            launchKeyboxPicker();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void launchKeyboxPicker() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"text/xml", "application/xml",
                "text/plain", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQUEST_IMPORT_KEYBOX);
        } catch (Exception e) {
            Log.w(TAG, "No document picker available", e);
            Toast.makeText(getContext(), R.string.fundamental_integrity_keybox_import_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_IMPORT_KEYBOX) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (resultCode != android.app.Activity.RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        handleKeyboxPicked(data.getData());
    }

    private void handleKeyboxPicked(@NonNull Uri uri) {
        final byte[] bytes = readAll(uri);
        if (bytes == null) {
            toast(getString(R.string.fundamental_integrity_keybox_import_failed));
            return;
        }
        final IntegritySpoofKeyboxValidator.Result result =
                IntegritySpoofKeyboxValidator.validate(bytes);
        if (!result.valid) {
            toast(getString(R.string.fundamental_integrity_keybox_invalid, result.toSummary()));
            return;
        }
        if (!installKeybox(bytes)) {
            toast(getString(R.string.fundamental_integrity_keybox_import_failed));
            return;
        }
        // Cache a short status line so the summary renders without re-reading the protected file.
        Settings.Secure.putString(getContentResolver(), IntegritySpoofKeys.SECURE_KEYBOX_STATUS,
                result.toSummary());
        toast(getString(R.string.fundamental_integrity_keybox_imported, result.toSummary()));
        // Refresh the visible summary of the import preference.
        updatePreferenceStates();
    }

    private byte[] readAll(@NonNull Uri uri) {
        final ContentResolver cr = getContentResolver();
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_KEYBOX_BYTES) {
                    Log.w(TAG, "Keybox file too large");
                    return null;
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (Exception e) {
            Log.w(TAG, "Unable to read picked file", e);
            return null;
        }
    }

    private boolean installKeybox(@NonNull byte[] bytes) {
        try {
            final File dir = new File(IntegritySpoofKeys.KEYBOX_DIR);
            if (!dir.isDirectory()) {
                // init should have created this; attempt anyway for resilience.
                dir.mkdirs();
            }
            final File tmp = new File(dir, "keybox.xml.tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(bytes);
                fos.flush();
                fos.getFD().sync();
            }
            final File dst = new File(IntegritySpoofKeys.KEYBOX_PATH);
            if (!tmp.renameTo(dst)) {
                // renameTo can fail across some tmpfs edges; fall back to a direct write.
                try (FileOutputStream fos = new FileOutputStream(dst)) {
                    fos.write(bytes);
                    fos.flush();
                    fos.getFD().sync();
                }
                tmp.delete();
            }
            // keystore2 (uid keystore, not system) reads this via the file's other-read bit.
            dst.setReadable(true, false);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Unable to write keybox to " + IntegritySpoofKeys.KEYBOX_PATH, e);
            return false;
        }
    }

    private void toast(String msg) {
        if (getContext() != null && !TextUtils.isEmpty(msg)) {
            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
        }
    }

    /** For Search. */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.fundamental_integrity_spoof_settings);
}
