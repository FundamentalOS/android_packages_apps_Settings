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

package com.android.settings.homepage;

import android.content.Context;
import android.content.res.Configuration;

/**
 * FundamentalOS: shared colour maths for the M3-Expressive homepage icons.
 *
 * <p>Instead of a single flat secondary-container tint (all icons one colour) or the
 * full stock rainbow (every icon a wildly different hue), each entry's stock hue is
 * remapped into a narrow band of neighbouring hues around the Monet primary: the
 * accent hue is the centre, and each entry is offset by its own (shortest, signed)
 * hue delta compressed by {@link #HUE_SPREAD}. The grid then reads as an analogous
 * family of the wallpaper accent while every entry keeps a distinguishable hue.
 */
public final class HomepageIconColors {

    /** 0 = every icon collapses onto the accent, 1 = the full stock rainbow. */
    private static final double HUE_SPREAD = 0.18;
    /** Floor so containers keep some life even under a low-chroma wallpaper. */
    private static final double MIN_CHROMA = 40.0;
    /** A seed below this chroma is treated as achromatic and sits on the accent. */
    private static final double ACHROMATIC_CHROMA = 5.0;

    private HomepageIconColors() {}

    /**
     * @return {containerColor, onContainerColor} harmonised around the Monet primary.
     */
    public static int[] harmonizedContainer(Context context, int designColor) {
        final int monetPrimary = context.getColor(com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorPrimary);
        final com.google.android.material.color.utilities.Hct primary =
                com.google.android.material.color.utilities.Hct.fromInt(monetPrimary);
        final com.google.android.material.color.utilities.Hct design =
                com.google.android.material.color.utilities.Hct.fromInt(designColor);
        final double hue;
        if (design.getChroma() < ACHROMATIC_CHROMA) {
            hue = primary.getHue();
        } else {
            final double delta =
                    ((design.getHue() - primary.getHue() + 540.0) % 360.0) - 180.0;
            hue = ((primary.getHue() + delta * HUE_SPREAD) % 360.0 + 360.0) % 360.0;
        }
        final boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final double chroma = Math.max(primary.getChroma(), MIN_CHROMA);
        final int container = com.google.android.material.color.utilities.Hct
                .from(hue, chroma, dark ? 30 : 90).toInt();
        final int onContainer = com.google.android.material.color.utilities.Hct
                .from(hue, chroma, dark ? 90 : 30).toInt();
        return new int[]{container, onContainer};
    }
}
