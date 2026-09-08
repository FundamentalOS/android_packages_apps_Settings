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
import android.content.pm.PackageManager;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bridges the {@link Settings.Secure} config this UI stores into the file the ROM-native keystore2
 * attestation forge actually reads.
 *
 * <p>The forge ({@code system/security/keystore2 attest_spoof}) targets by <em>UID</em> and treats
 * an empty/absent targets file as "disabled". This resolves the user's selected package names to
 * UIDs and rewrites {@code /data/misc/fundamental/targets.txt} on every change, or clears it when
 * the master switch is off. Keybox import writes {@code keybox.xml} in the same directory directly.
 *
 * <p>Both this app and keystore2 run under {@code system}; the sepolicy component grants
 * {@code system_app} create/write and {@code keystore} read on the {@code fundamental_data_file}
 * type. A config change is picked up by the forge on its next targets reload (keystore2 restart or
 * reboot); a live keystore2 that has already cached a non-empty target set keeps it until restarted.
 */
public final class IntegritySpoofBridge {

    private static final String TAG = "IntegritySpoofBridge";
    private static final File TARGETS_FILE =
            new File(IntegritySpoofKeys.KEYBOX_DIR, "targets.txt");

    private IntegritySpoofBridge() {}

    /** Re-derive {@code targets.txt} from the current Settings.Secure state. */
    public static void syncTargets(Context context) {
        if (context == null) {
            return;
        }
        final boolean enabled = Settings.Secure.getInt(context.getContentResolver(),
                IntegritySpoofKeys.SECURE_ENABLED,
                IntegritySpoofKeys.defaultEnabled(context) ? 1 : 0) == 1;
        final Set<Integer> uids = new LinkedHashSet<>();
        final StringBuilder pkgCsv = new StringBuilder();
        if (enabled) {
            final PackageManager pm = context.getPackageManager();
            for (String pkg : IntegritySpoofKeys.effectiveTargetPackages(context)) {
                final String p = pkg.trim();
                if (p.isEmpty()) {
                    continue;
                }
                if (pkgCsv.length() > 0) {
                    pkgCsv.append(',');
                }
                pkgCsv.append(p);
                try {
                    uids.add(pm.getPackageUid(p, 0));
                } catch (Exception e) {
                    Log.w(TAG, "cannot resolve uid for " + p, e);
                }
            }
        }
        // Attestation half: the keystore2 forge target-uid file.
        final StringBuilder sb = new StringBuilder();
        for (int uid : uids) {
            sb.append(uid).append('\n');
        }
        writeAtomic(sb.toString());
        // Boot-state half: the appcompat sysprop-override package list (ProcessList binds green
        // verified-boot / locked-bootloader props for these packages). Cleared when disabled so
        // BOTH halves of the spoof follow the master switch, not just the attestation half.
        try {
            DeviceConfig.setProperty(IntegritySpoofKeys.DEVICE_CONFIG_NAMESPACE_APP_COMPAT,
                    IntegritySpoofKeys.DEVICE_CONFIG_SYSPROP_OVERRIDE_PKGS,
                    enabled ? pkgCsv.toString() : "", false);
        } catch (Exception e) {
            Log.w(TAG, "update appcompat override pkg list failed", e);
        }
    }

    private static void writeAtomic(String contents) {
        try {
            final File dir = new File(IntegritySpoofKeys.KEYBOX_DIR);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                // init creates + labels this dir; if it is missing the feature is simply inert.
                Log.w(TAG, "config dir missing: " + dir);
                return;
            }
            final byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
            final File tmp = new File(dir, "targets.txt.tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(bytes);
                fos.flush();
                fos.getFD().sync();
            }
            if (!tmp.renameTo(TARGETS_FILE)) {
                try (FileOutputStream fos = new FileOutputStream(TARGETS_FILE)) {
                    fos.write(bytes);
                    fos.flush();
                    fos.getFD().sync();
                }
                tmp.delete();
            }
            // keystore2 (uid keystore, not system) reads this via the file's other-read bit.
            TARGETS_FILE.setReadable(true, false);
        } catch (Exception e) {
            Log.w(TAG, "write targets.txt failed", e);
        }
    }
}
