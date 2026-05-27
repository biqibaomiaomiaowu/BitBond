package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class StatusMapperTest {
    private static final Path ASSET_PATH = Path.of(
            "src",
            "main",
            "assets",
            "status_map_cn.json");

    @Test
    public void knownPackageMapsAbstractStatusOnly() throws IOException {
        StatusMapper mapper = StatusMapper.fromJson(readAsset());

        assertEquals("social", mapper.mapPackageName("com.tencent.mm"));
        assertTrue(StatusMapper.allowedStatusCodes().contains(mapper.mapPackageName("com.tencent.mm")));
    }

    @Test
    public void commonShortVideoAndStreamingPackagesMapToAbstractStatuses() throws IOException {
        StatusMapper mapper = StatusMapper.fromJson(readAsset());

        assertEquals("short_video", mapper.mapPackageName("com.ss.android.ugc.aweme"));
        assertEquals("short_video", mapper.mapPackageName("com.smile.gifmaker"));
        assertEquals("short_video", mapper.mapPackageName("com.zhiliaoapp.musically"));
        assertEquals("watching_show", mapper.mapPackageName("com.qiyi.video"));
        assertEquals("watching_show", mapper.mapPackageName("com.mgtv.tv"));
        assertEquals("watching_show", mapper.mapPackageName("tv.danmaku.bilibilihd"));
    }

    @Test
    public void unknownPackageFallsBackOnline() throws IOException {
        StatusMapper mapper = StatusMapper.fromJson(readAsset());

        assertEquals("online", mapper.mapPackageName("example.unknown.package"));
        assertEquals("online", mapper.mapPackageName(null));
        assertEquals("online", mapper.mapPackageName(" "));
    }

    @Test
    public void rawJsonDoesNotContainChineseDisplayNames() throws IOException {
        String rawJson = readAsset();

        assertFalse(rawJson.contains("微信"));
        assertFalse(rawJson.contains("QQ"));
        assertFalse(rawJson.contains("抖音"));
    }

    @Test
    public void assetContainsOnlyPackageAndStatusCodeFields() throws Exception {
        JSONArray entries = new JSONArray(readAsset());

        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);

            assertEquals(Set.of("package", "statusCode"), objectKeys(entry));
            assertFalse(entry.getString("package").isBlank());
            assertTrue(StatusMapper.allowedStatusCodes().contains(entry.getString("statusCode")));
        }
    }

    @Test
    public void invalidStatusCodeRejected() {
        String rawJson = """
                [
                  {"package": "com.example.invalid", "statusCode": "shopping"}
                ]
                """;

        assertThrows(IllegalArgumentException.class, () -> StatusMapper.fromJson(rawJson));
    }

    private static String readAsset() throws IOException {
        return Files.readString(ASSET_PATH, StandardCharsets.UTF_8);
    }

    private static Set<String> objectKeys(JSONObject object) {
        Set<String> keys = new HashSet<>();
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        return keys;
    }
}
