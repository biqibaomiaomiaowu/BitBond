package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
