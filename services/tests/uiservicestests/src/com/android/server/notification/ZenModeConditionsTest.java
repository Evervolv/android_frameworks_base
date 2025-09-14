/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.notification;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.IPackageManager;
import android.net.Uri;
import android.os.Build;
import android.service.notification.ConditionProviderService;
import android.service.notification.IConditionProvider;
import android.service.notification.ZenModeConfig;
import android.service.notification.ZenModeConfig.ZenRule;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.server.UiServiceTestCase;
import com.android.server.notification.ConditionProviders.ConditionRecord;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class ZenModeConditionsTest extends UiServiceTestCase {

    private static final ComponentName SCHEDULE_CPS = new ComponentName("android",
            "com.android.server.notification.ScheduleConditionProvider");
    private static final Uri SCHEDULE_CPS_CONDITION_ID = Uri.parse(
            "condition://android/schedule?days=1.2.3&start=3.0&end=5.0&exitAtAlarm=true");

    private static final String PACKAGE = "com.some.package";
    private static final ComponentName PACKAGE_CPS = new ComponentName(PACKAGE,
            PACKAGE + ".TheConditionProviderService");

    private ZenModeConditions mZenModeConditions;
    private ConditionProviders mConditionProviders;

    @Before
    public void setUp() {
        mConditionProviders = new ConditionProviders(mContext, new ManagedServices.UserProfiles(),
                mock(IPackageManager.class));
        mZenModeConditions = new ZenModeConditions(mock(ZenModeHelper.class), mConditionProviders);
        ((Set<?>) mConditionProviders.getSystemProviders()).clear(); // Hack, remove built-in CPSes

        ScheduleConditionProvider scheduleConditionProvider = new ScheduleConditionProvider();
        mConditionProviders.addSystemProvider(scheduleConditionProvider);

        ConditionProviderService packageConditionProvider = new PackageConditionProviderService();
        mConditionProviders.registerGuestService(mConditionProviders.new ManagedServiceInfo(
                (IConditionProvider) packageConditionProvider.onBind(null), PACKAGE_CPS,
                mContext.getUserId(), false, mock(ServiceConnection.class),
                Build.VERSION_CODES.TIRAMISU, 44));
    }

    @Test
    public void evaluateRule_systemRuleWithSystemConditionProvider_evaluates() {
        ZenRule systemRule = newSystemZenRule("1", SCHEDULE_CPS, SCHEDULE_CPS_CONDITION_ID);
        ZenModeConfig config = configWithRules(systemRule);

        mZenModeConditions.evaluateConfig(config, null, /* processSubscriptions= */ true);

        ConditionRecord conditionRecord = mConditionProviders.getRecord(SCHEDULE_CPS_CONDITION_ID,
                SCHEDULE_CPS);
        assertThat(conditionRecord).isNotNull();
        assertThat(conditionRecord.subscribed).isTrue();
    }

    @Test
    public void evaluateConfig_packageRuleWithSystemConditionProvider_ignored() {
        ZenRule packageRule = newPackageZenRule(PACKAGE, SCHEDULE_CPS, SCHEDULE_CPS_CONDITION_ID);
        ZenModeConfig config = configWithRules(packageRule);

        mZenModeConditions.evaluateConfig(config, null, /* processSubscriptions= */ true);

        assertThat(mConditionProviders.getRecord(SCHEDULE_CPS_CONDITION_ID, SCHEDULE_CPS))
                .isNull();
    }

    @Test
    public void evaluateConfig_packageRuleWithPackageCpsButSystemLikeConditionId_usesPackageCps() {
        ZenRule packageRule = newPackageZenRule(PACKAGE, PACKAGE_CPS,
                SCHEDULE_CPS_CONDITION_ID);
        ZenModeConfig config = configWithRules(packageRule);

        mZenModeConditions.evaluateConfig(config, /* trigger= */ PACKAGE_CPS,
                /* processSubscriptions= */ true);

        ConditionRecord packageCpsRecord = mConditionProviders.getRecord(SCHEDULE_CPS_CONDITION_ID,
                PACKAGE_CPS);
        assertThat(packageCpsRecord).isNotNull();
        assertThat(packageCpsRecord.subscribed).isTrue();

        ConditionRecord systemCpsRecord = mConditionProviders.getRecord(SCHEDULE_CPS_CONDITION_ID,
                SCHEDULE_CPS);
        assertThat(systemCpsRecord).isNull();
    }

    private static ZenModeConfig configWithRules(ZenRule... zenRules) {
        ZenModeConfig config = new ZenModeConfig();
        for (ZenRule zenRule : zenRules) {
            config.automaticRules.put(zenRule.id, zenRule);
        }
        return config;
    }

    private static ZenRule newSystemZenRule(String id, ComponentName component, Uri conditionId) {
        ZenRule systemRule = new ZenRule();
        systemRule.id = id;
        systemRule.name = "System Rule " + id;
        systemRule.pkg = ZenModeConfig.SYSTEM_AUTHORITY;
        systemRule.component = component;
        systemRule.conditionId = conditionId;
        return systemRule;
    }

    private static ZenRule newPackageZenRule(String packageName, ComponentName component,
            Uri conditionId) {
        ZenRule packageRule = new ZenRule();
        packageRule.id = "id " + packageName;
        packageRule.name = "Package Rule " + packageName;
        packageRule.pkg = packageName;
        packageRule.component = component;
        packageRule.conditionId = conditionId;
        return packageRule;
    }

    private static class PackageConditionProviderService extends ConditionProviderService {

        @Override
        public void onConnected() { }

        @Override
        public void onSubscribe(Uri conditionId) { }

        @Override
        public void onUnsubscribe(Uri conditionId) { }
    }
}
