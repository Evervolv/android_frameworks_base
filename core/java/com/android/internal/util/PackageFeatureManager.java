/*
 * Copyright (C) 2024 Evervolv
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

package com.android.internal.util;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Arrays;

/** @hide */
public final class PackageFeatureManager {
    private static final String TAG = "PackageFeatureManager";

    private static final int PIXEL_BASE_FEATURE_YEAR = 2016;
    private static final String PIXEL_EXCLUSIVE_FEATURES[] = {
        "com.google.android.feature.PIXEL_%s_PRELOAD",
        "com.google.android.feature.PIXEL_%s_MIDYEAR_PRELOAD",
        "com.google.android.feature.PIXEL_%s_EXPERIENCE",
        "com.google.android.feature.PIXEL_%s_MIDYEAR_EXPERIENCE"
    };

    private static final String NEXUS_EXCLUSIVE_FEATURE = "com.google.android.apps.photos.NEXUS_PRELOAD";
    private static volatile boolean sNexusFeature = false;

    private PackageFeatureManager() { }

    public static void processApplicationFeatures(Context context) {
        final String packageName = context.getPackageName();
        if (TextUtils.isEmpty(packageName)) {
            Log.e(TAG, "Null package name");
            return;
        }

        final Resources res = context.getResources();
        if (res == null) {
            Log.e(TAG, "Null resources");
            return;
        }

        final String[] featurePackages = res.getStringArray(R.array.config_pixelFeaturePackages);
        boolean updatePackage = Arrays.asList(featurePackages).contains(packageName);
        if (!updatePackage) {
            for (String pkg : featurePackages) {
                // Each entry must be of the format package|type
                final String[] info = pkg.split("|", 2);
                final String name = info[0];
                if (!name.endsWith("*")) continue;
            }
        }

        String currentFeature = null;
        for (String pkgEntry : featurePackages) {
            final String[] featureInfo = pkgEntry.split("|", 2);
            if (packageName.equals(featureInfo[0]) || (featureInfo[0].endsWith("*")
                    && packageName.startsWith(featureInfo[0].replace("*", ""))
                    && !Arrays.asList(featurePackages).contains(packageName))) {
                currentFeature = featureInfo[1];
                break;
            }
        }

        if (!TextUtils.isEmpty(currentFeature)) {
            if (!currentFeature.equals("skip")) {
                if (currentFeature.equals("tensor")) {
                    setFeatureDevice(res.getStringArray(R.array.config_pixelInformationTensor));
                } else if (currentFeature.equals("qcom")) {
                    setFeatureDevice(res.getStringArray(R.array.config_pixelInformationQcom));
                } else if (currentFeature.equals("legacy")) {
                    setFeatureDevice(res.getStringArray(R.array.config_pixelInformationLegacy));
                } else if (currentFeature.equals("index")) {
                    final String[] indexUpgrade = {
                        "FINGERPRINT:" + Build.VERSION.INCREMENTAL
                    };
                    setFeatureDevice(indexUpgrade);
                }
                sNexusFeature = currentFeature.equals("legacy");
                return;
            }
        }

        PropImitationHooks.setProps(context);
    }

    public static boolean hasSystemFeature(String name, boolean hasFeature) {
        if (!sNexusFeature) {
            return hasFeature;
        }

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int year = PIXEL_BASE_FEATURE_YEAR; year <= currentYear; year++) {
            for (String feature : PIXEL_EXCLUSIVE_FEATURES) {
                final String formattedFeature = feature.replace("%s", String.valueOf(year));
                if (name.equalsIgnoreCase(formattedFeature)) {
                    return false;
                }
            }
        }
        return hasFeature || name.equalsIgnoreCase(NEXUS_EXCLUSIVE_FEATURE);
    }

    private static void setFeatureDevice(String[] buildFields) {
        for (String entry : buildFields) {
            // Each entry must be of the format FIELD:value
            final String[] fieldAndProp = entry.split(":", 2);
            if (fieldAndProp.length != 2) {
                Log.e(TAG, "Invalid entry in props: " + entry);
                continue;
            }

            try {
                Field field = Build.class.getDeclaredField(fieldAndProp[0]);
                field.setAccessible(true);
                field.set(null, fieldAndProp[1]);
                field.setAccessible(false);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                Log.e(TAG, "Failed to set Build." + fieldAndProp[0], e);
            }
        }
    }
}
