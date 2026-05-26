package com.bitbond.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BitBondWebAssetsTest {
    @Test
    public void indexUrlUsesHttpsOriginInsteadOfFileAssetOrigin() {
        assertEquals("https://bitbond.local/index.html", BitBondWebAssets.INDEX_URL);
        assertEquals(BitBondWebAssets.INDEX_URL, MainActivity.initialWebUrl());
    }

    @Test
    public void mapsTrustedOriginUrlsToPackagedAssetPaths() {
        assertEquals("index.html", BitBondWebAssets.assetPathForUrl("https://bitbond.local/"));
        assertEquals("index.html", BitBondWebAssets.assetPathForUrl("https://bitbond.local/index.html"));
        assertEquals("assets/index-CX_qwI9n.js", BitBondWebAssets.assetPathForUrl("https://bitbond.local/assets/index-CX_qwI9n.js"));
        assertEquals("pixel/room/room_main.png", BitBondWebAssets.assetPathForUrl("https://bitbond.local/pixel/room/room_main.png"));
    }

    @Test
    public void rejectsExternalOriginsAndTraversalPaths() {
        assertNull(BitBondWebAssets.assetPathForUrl("file:///android_asset/index.html"));
        assertNull(BitBondWebAssets.assetPathForUrl("https://example.test/index.html"));
        assertNull(BitBondWebAssets.assetPathForUrl("https://bitbond.local/../secret.txt"));
        assertNull(BitBondWebAssets.assetPathForUrl("not a url"));
    }

    @Test
    public void returnsMimeTypesForViteAndPixelAssets() {
        assertEquals("text/html", BitBondWebAssets.mimeTypeForAssetPath("index.html"));
        assertEquals("application/javascript", BitBondWebAssets.mimeTypeForAssetPath("assets/index.js"));
        assertEquals("text/css", BitBondWebAssets.mimeTypeForAssetPath("assets/index.css"));
        assertEquals("image/png", BitBondWebAssets.mimeTypeForAssetPath("pixel/room/room_main.png"));
        assertEquals("application/octet-stream", BitBondWebAssets.mimeTypeForAssetPath("assets/data.bin"));
    }
}
