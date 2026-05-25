package com.bitbond.app.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.BuildConfig;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class SupabaseConfigTest {
    @Test
    public void configValuesNormalizeHostAndConfiguredFlag() {
        SupabaseConfig.Values configured = SupabaseConfig.fromValues(" https://api.example.test ", " anon-key ");

        assertTrue(configured.isConfigured());
        assertEquals("https://api.example.test", configured.url());
        assertEquals("anon-key", configured.anonKey());
        assertEquals("api.example.test", configured.host());
    }

    @Test
    public void configValuesRejectBlankUrlOrKey() {
        assertFalse(SupabaseConfig.fromValues("", "key").isConfigured());
        assertFalse(SupabaseConfig.fromValues("https://api.example.test", "").isConfigured());
        assertFalse(SupabaseConfig.fromValues(null, "key").isConfigured());
        assertFalse(SupabaseConfig.fromValues("https://api.example.test", null).isConfigured());
    }

    @Test
    public void configValuesRejectMalformedOrNonHttpsUrl() {
        assertFalse(SupabaseConfig.fromValues("not-a-url", "key").isConfigured());
        assertEquals("", SupabaseConfig.fromValues("not-a-url", "key").host());
        assertFalse(SupabaseConfig.fromValues("https:///path", "key").isConfigured());
        assertEquals("", SupabaseConfig.fromValues("https:///path", "key").host());
        assertFalse(SupabaseConfig.fromValues("http://api.example.test", "key").isConfigured());
    }

    @Test
    public void generatedBuildConfigExposesOnlyClientSafeSupabaseFields() {
        Set<String> fieldNames = new HashSet<>();
        for (Field field : BuildConfig.class.getDeclaredFields()) {
            fieldNames.add(field.getName());
        }

        assertTrue(fieldNames.contains("SUPABASE_URL"));
        assertTrue(fieldNames.contains("SUPABASE_ANON_KEY"));
        assertFalse(fieldNames.contains("SUPABASE_SERVICE_ROLE_KEY"));
        assertFalse(fieldNames.contains("DATABASE_URL"));
    }
}
