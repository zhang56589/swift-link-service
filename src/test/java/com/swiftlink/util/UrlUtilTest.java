package com.swiftlink.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlUtilTest {

    @Test
    void shouldValidateHttpUrl() {
        assertTrue(UrlUtil.isHttpUrl("https://example.com/path?a=1&b=2"));
        assertTrue(UrlUtil.isHttpUrl("http://example.com"));
        assertFalse(UrlUtil.isHttpUrl("javascript:alert(1)"));
        assertFalse(UrlUtil.isHttpUrl("ftp://example.com/file"));
        assertFalse(UrlUtil.isHttpUrl("not-a-url"));
        assertFalse(UrlUtil.isHttpUrl("https://user:pass@example.com"));
    }

    @Test
    void shouldExtractHost() {
        assertEquals("example.com", UrlUtil.extractHost("https://Example.com/path"));
        assertEquals(null, UrlUtil.extractHost("not-a-url"));
    }

    @Test
    void shouldDetectInternalHost() {
        assertTrue(UrlUtil.isInternalHost("127.0.0.1"));
        assertTrue(UrlUtil.isInternalHost("10.1.2.3"));
        assertTrue(UrlUtil.isInternalHost("192.168.1.1"));
        assertTrue(UrlUtil.isInternalHost("172.16.0.1"));
        assertTrue(UrlUtil.isInternalHost("localhost"));
        assertFalse(UrlUtil.isInternalHost("example.com"));
    }
}
