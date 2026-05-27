package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.usage.UsageEvents;

import java.util.List;

import org.junit.Test;

public class ForegroundAppReaderTest {
    private static final String BITBOND_PACKAGE = "com.bitbond.app";

    @Test
    public void selectMostRecentForegroundPackageSkipsOwnPackageCandidates() {
        List<ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot> events = List.of(
                snapshot("com.tencent.mm", 100L),
                snapshot(BITBOND_PACKAGE, 200L));

        assertEquals(
                "com.tencent.mm",
                ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                        events,
                        BITBOND_PACKAGE));
    }

    @Test
    public void selectMostRecentForegroundPackageSkipsInvalidPackageCandidates() {
        List<ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot> events = List.of(
                snapshot("com.tencent.mm", 100L),
                snapshot(null, 200L),
                snapshot("   ", 300L),
                snapshot(BITBOND_PACKAGE, 400L));

        assertEquals(
                "com.tencent.mm",
                ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                        events,
                        BITBOND_PACKAGE));
    }

    @Test
    public void selectMostRecentForegroundPackageSkipsSystemHomeInputMethodAndSecurityCandidates() {
        List<ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot> events = List.of(
                snapshot("com.tencent.mm", 100L),
                snapshot("android", 200L),
                snapshot("com.android.systemui", 300L),
                snapshot("com.android.settings", 400L),
                snapshot("com.android.launcher3", 500L),
                snapshot("com.google.android.apps.nexuslauncher", 600L),
                snapshot("com.google.android.inputmethod.latin", 700L),
                snapshot("com.samsung.android.honeyboard", 800L),
                snapshot("com.miui.securitycenter", 900L),
                snapshot("com.huawei.systemmanager", 1000L));

        assertEquals(
                "com.tencent.mm",
                ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                        events,
                        BITBOND_PACKAGE));
    }

    @Test
    public void selectMostRecentForegroundPackageFallsBackToPreviousRealAppWhenNewestIsMiuiHome() {
        List<ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot> events = List.of(
                snapshot("com.tencent.mm", 100L),
                snapshot("com.miui.home", 200L));

        assertEquals(
                "com.tencent.mm",
                ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                        events,
                        BITBOND_PACKAGE));
    }

    @Test
    public void selectMostRecentForegroundPackageKeepsNormalExternalPackages() {
        List<ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot> events = List.of(
                snapshot("com.tencent.mm", 100L),
                snapshot("com.ss.android.ugc.aweme", 200L),
                snapshot("com.xingin.xhs", 300L));

        assertEquals(
                "com.xingin.xhs",
                ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                        events,
                        BITBOND_PACKAGE));
    }

    @Test
    public void selectMostRecentForegroundPackageKeepsExternalPackagesWithSystemWordsInName() {
        List<ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot> events = List.of(
                snapshot("com.tencent.mm", 100L),
                snapshot("com.example.inputmethod.tutorial", 200L),
                snapshot("com.example.securitycenter.news", 300L),
                snapshot("com.example.safecenter.client", 400L));

        assertEquals(
                "com.example.safecenter.client",
                ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                        events,
                        BITBOND_PACKAGE));
    }

    @Test
    public void selectMostRecentForegroundPackageKeepsGoogleQuickSearchBox() {
        List<ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot> events = List.of(
                snapshot("com.tencent.mm", 100L),
                snapshot("com.google.android.googlequicksearchbox", 200L));

        assertEquals(
                "com.google.android.googlequicksearchbox",
                ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                        events,
                        BITBOND_PACKAGE));
    }

    @Test
    public void selectMostRecentForegroundPackageKeepsExternalPackagesThatOnlyShareBlockedPrefixText() {
        List<ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot> events = List.of(
                snapshot("com.tencent.mm", 100L),
                snapshot("com.google.android.inputmethodtrainer", 200L),
                snapshot("com.miui.securitycenternews", 300L),
                snapshot("com.coloros.safecenterapp", 400L));

        assertEquals(
                "com.coloros.safecenterapp",
                ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                        events,
                        BITBOND_PACKAGE));
    }

    @Test
    public void selectMostRecentForegroundPackageUsesLaterExternalCandidateForTimestampTie() {
        List<ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot> events = List.of(
                snapshot("com.tencent.mm", 100L),
                snapshot("com.tencent.mobileqq", 100L));

        assertEquals(
                "com.tencent.mobileqq",
                ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                        events,
                        BITBOND_PACKAGE));
    }

    @Test
    public void selectMostRecentForegroundPackageUsesLaterCandidateWhenAndroidForegroundEventConstantsAlias() {
        assertEquals(UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND);

        List<ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot> events = List.of(
                snapshot("com.tencent.mm", 100L, UsageEvents.Event.ACTIVITY_RESUMED),
                snapshot("com.tencent.mobileqq", 100L, UsageEvents.Event.MOVE_TO_FOREGROUND));

        assertEquals(
                "com.tencent.mobileqq",
                ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                        events,
                        BITBOND_PACKAGE));
    }

    @Test
    public void selectMostRecentForegroundPackageReturnsNullWithoutExternalCandidates() {
        assertNull(ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                List.of(snapshot(BITBOND_PACKAGE, 100L)),
                BITBOND_PACKAGE));
        assertNull(ForegroundAppReader.UsageEventsForegroundAppReader.selectMostRecentForegroundPackage(
                List.of(),
                BITBOND_PACKAGE));
    }

    private static ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot snapshot(
            String packageName,
            long timestamp) {
        return new ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot(packageName, timestamp);
    }

    private static ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot snapshot(
            String packageName,
            long timestamp,
            int eventType) {
        return new ForegroundAppReader.UsageEventsForegroundAppReader.ForegroundEventSnapshot(
                packageName,
                timestamp,
                eventType);
    }
}
