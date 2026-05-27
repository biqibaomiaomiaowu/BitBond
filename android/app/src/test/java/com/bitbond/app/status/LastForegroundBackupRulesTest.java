package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

public class LastForegroundBackupRulesTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String[] SENSITIVE_FOREGROUND_PREFS_FILES = {
            "bitbond_last_foreground.xml",
            "bitbond_background_refresh.xml"
    };

    @Test
    public void manifestWiresBackupRulesThatExcludeLastForegroundPreferences() throws Exception {
        Path mainSource = findMainSourceDirectory();
        Document manifest = parseXml(mainSource.resolve("AndroidManifest.xml"));
        Element application = (Element) manifest.getElementsByTagName("application").item(0);

        assertEquals("@xml/backup_rules", application.getAttributeNS(ANDROID_NS, "fullBackupContent"));
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(ANDROID_NS, "dataExtractionRules"));

        assertBackupRulesExcludeSensitiveForegroundPrefs(mainSource.resolve("res/xml/backup_rules.xml"));
        assertDataExtractionRulesExcludeSensitiveForegroundPrefs(
                mainSource.resolve("res/xml/data_extraction_rules.xml"),
                "cloud-backup");
        assertDataExtractionRulesExcludeSensitiveForegroundPrefs(
                mainSource.resolve("res/xml/data_extraction_rules.xml"),
                "device-transfer");
    }

    private static void assertBackupRulesExcludeSensitiveForegroundPrefs(Path rulesPath) throws Exception {
        assertTrue(Files.exists(rulesPath));
        Document rules = parseXml(rulesPath);

        for (String prefsFile : SENSITIVE_FOREGROUND_PREFS_FILES) {
            assertTrue(containsExclude(rules.getDocumentElement(), prefsFile));
        }
    }

    private static void assertDataExtractionRulesExcludeSensitiveForegroundPrefs(
            Path rulesPath,
            String sectionName) throws Exception {
        assertTrue(Files.exists(rulesPath));
        Document rules = parseXml(rulesPath);
        NodeList sections = rules.getElementsByTagName(sectionName);

        assertEquals(1, sections.getLength());
        for (String prefsFile : SENSITIVE_FOREGROUND_PREFS_FILES) {
            assertTrue(containsExclude((Element) sections.item(0), prefsFile));
        }
    }

    private static boolean containsExclude(Element parent, String path) {
        NodeList excludes = parent.getElementsByTagName("exclude");
        for (int i = 0; i < excludes.getLength(); i++) {
            Element exclude = (Element) excludes.item(i);
            if ("sharedpref".equals(exclude.getAttribute("domain"))
                    && path.equals(exclude.getAttribute("path"))) {
                return true;
            }
        }
        return false;
    }

    private static Document parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static Path findMainSourceDirectory() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("app/src/main");
            if (Files.exists(candidate.resolve("AndroidManifest.xml"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate app/src/main");
    }
}
