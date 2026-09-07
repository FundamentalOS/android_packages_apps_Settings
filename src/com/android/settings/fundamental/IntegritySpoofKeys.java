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

/**
 * Canonical names for the FundamentalOS Play Integrity attestation-forge configuration.
 *
 * <p>These string values are the SHARED CONTRACT between this Settings UI (the writer) and the
 * system_server keystore-certificate post-processor service (the reader). The service defines its
 * own copy of these constants; the string literals below MUST stay byte-for-byte identical on both
 * sides. Do not rename a value without changing it in the service too.
 */
public final class IntegritySpoofKeys {

    private IntegritySpoofKeys() {}

    // ---- Settings.Secure keys (read by the Java post-processor service) ----

    /** Master enable for the forge. {@code "1"} = on, {@code "0"}/absent = off. */
    public static final String SECURE_ENABLED = "fundamental_integrity_enabled";

    /**
     * Comma-separated list of package names the forge targets (e.g. the Play Store / GMS). The
     * service parses ATTESTATION_APPLICATION_ID out of each attested leaf and only forges when the
     * requesting package is in this list.
     */
    public static final String SECURE_TARGET_PACKAGES = "fundamental_integrity_target_packages";

    /** Enable the runtime build-fingerprint override. {@code "1"} = on, {@code "0"}/absent = off. */
    public static final String SECURE_FINGERPRINT_ENABLED =
            "fundamental_integrity_fingerprint_enabled";

    /**
     * The full build fingerprint the service spoofs when {@link #SECURE_FINGERPRINT_ENABLED} is on,
     * e.g. {@code google/caiman/caiman:16/BP4A.../...:user/release-keys}.
     */
    public static final String SECURE_FINGERPRINT = "fundamental_integrity_fingerprint";

    /**
     * UI-only: a short human-readable status line for the last-installed keybox, so the import
     * preference can render a summary without needing SELinux read access to the keybox file. The
     * service does not read this.
     */
    public static final String SECURE_KEYBOX_STATUS = "fundamental_integrity_keybox_status";

    // ---- DeviceConfig (read by ProcessList to bind per-app sysprop overrides at fork) ----

    /**
     * DeviceConfig namespace matching {@code android.provider.DeviceConfig.NAMESPACE_APP_COMPAT}
     * ("app_compat"). We use the literal so the Settings module does not depend on the @SystemApi
     * constant's visibility.
     */
    public static final String DEVICE_CONFIG_NAMESPACE_APP_COMPAT = "app_compat";

    /**
     * DeviceConfig property listing the packages that get bound sysprop overrides (green
     * verifiedbootstate / locked bootloader, and the fingerprint override). ProcessList reads this
     * exact key from the app_compat namespace.
     */
    public static final String DEVICE_CONFIG_SYSPROP_OVERRIDE_PKGS =
            "appcompat_sysprop_override_pkgs";

    // ---- Keybox file ----

    /**
     * Filesystem location of the attestation keybox XML. The Settings app writes it here after
     * validating an imported file; the system_server service reads it. Both processes run as the
     * system uid, so DAC read works once the file exists; SELinux (sepolicy component) must allow
     * system_app to create/write and system_server to read this path.
     */
    public static final String KEYBOX_PATH = "/data/misc/fundamental/keybox.xml";

    /** Parent directory of {@link #KEYBOX_PATH}. Created by init; the UI also attempts mkdirs(). */
    public static final String KEYBOX_DIR = "/data/misc/fundamental";

    // ---- Well-known package ----

    /** Google Play services / Play Store host of the integrity attestation key. */
    public static final String PKG_GMS = "com.google.android.gms";
    /** Google Play Store, holder of {@code integrity.api.key.alias}. */
    public static final String PKG_VENDING = "com.android.vending";
}
