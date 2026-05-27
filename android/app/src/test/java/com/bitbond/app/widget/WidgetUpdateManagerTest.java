package com.bitbond.app.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.status.StatusModels.PartnerProfile;
import com.bitbond.app.status.StatusModels.PartnerStatus;

import java.time.Instant;

import org.json.JSONObject;
import org.junit.Test;

public class WidgetUpdateManagerTest {
    @Test
    public void snapshotFromPartnerStatusKeepsOnlyPublicAbstractStatus() throws Exception {
        PartnerStatus status = new PartnerStatus(
                true,
                new PartnerProfile("小禾", "avatar_cat"),
                "watching_show",
                Instant.parse("2026-05-27T08:00:00Z"),
                Instant.parse("2026-05-27T08:15:00Z"),
                false,
                "{\"paired\":true,\"packageName\":\"com.qiyi.video\",\"token\":\"secret\"}");

        WidgetStatusSnapshot snapshot = WidgetStatusSnapshot.fromPartnerStatus(status);

        assertTrue(snapshot.paired());
        assertEquals("watching_show", snapshot.statusCode());
        assertEquals("2026-05-27T08:00:00Z", snapshot.updatedAt());
        assertFalse(snapshot.toJson().toString().contains("packageName"));
        assertFalse(snapshot.toJson().toString().contains("secret"));
    }

    @Test
    public void snapshotRoundTripsThroughPublicJson() throws Exception {
        WidgetStatusSnapshot snapshot = new WidgetStatusSnapshot(
                true,
                "music",
                "2026-05-27T08:00:00Z",
                true);

        WidgetStatusSnapshot restored = WidgetStatusSnapshot.fromJson(snapshot.toJson().toString());

        assertTrue(restored.paired());
        assertEquals("music", restored.statusCode());
        assertEquals("2026-05-27T08:00:00Z", restored.updatedAt());
        assertTrue(restored.sharing());
    }

    @Test
    public void renderStateUsesCachedStatusOrFallback() throws Exception {
        WidgetRenderState cached = WidgetUpdateManager.renderState(new WidgetStatusSnapshot(
                true,
                "short_video",
                "2026-05-27T08:00:00Z",
                true));
        WidgetRenderState fallback = WidgetUpdateManager.renderState(null);

        assertEquals("short_video", cached.statusCode());
        assertEquals("short_video", cached.statusText());
        assertEquals("offline", fallback.statusCode());
        assertFalse(fallback.paired());
        assertFalse(fallback.sharing());
    }

    @Test
    public void recognizesOpenAndRefreshActions() {
        assertEquals(
                WidgetUpdateManager.WidgetAction.OPEN_APP,
                WidgetUpdateManager.actionFor(WidgetUpdateManager.ACTION_OPEN_APP));
        assertEquals(
                WidgetUpdateManager.WidgetAction.REFRESH,
                WidgetUpdateManager.actionFor(WidgetUpdateManager.ACTION_REFRESH));
        assertEquals(
                WidgetUpdateManager.WidgetAction.UPDATE,
                WidgetUpdateManager.actionFor("android.appwidget.action.APPWIDGET_UPDATE"));
    }
}
