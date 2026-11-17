package org.dynmap.dependencies;

import org.eclipse.jetty.http.HttpURI;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * URI cases from Jetty 553b59eff83461a7e488e592bcc3bb958a76f13f,
 * adapted to Jetty 9's constructors and accessors under Apache-2.0.
 * Copyright (c) 1995-2025 Mort Bay Consulting Pty Ltd and others.
 */
public class JettyUriParserTest {
    @Test
    public void rejectsInvalidSchemesFromUpstreamRegressionCases() {
        for (String value : new String[] {
                "://host/path", "\t://host/path", "  ://host/path",
                "unknown^://host/path", "http^://host/path"}) {
            assertThrows(value, IllegalArgumentException.class, () -> new HttpURI(value));
        }
    }

    @Test
    public void parsesIpv4MappedIpv6AsOneAuthority() {
        for (String host : new String[] {
                "[0:0:0:0:0:ffff:127.0.0.1]", "[::ffff:127.0.0.1]", "[::ffff:255.255.0.0]"}) {
            for (String suffix : new String[] {
                    "", "/", ":8123", "?q=1", "#part", ":8123?q=1", ":8123#part",
                    ":8123/path?q=1#part"}) {
                HttpURI uri = new HttpURI("http://" + host + suffix);
                assertEquals(host, uri.getHost());
                boolean hasPort = suffix.startsWith(":");
                assertEquals(host + (hasPort ? ":8123" : ""), uri.getAuthority());
                assertEquals(hasPort ? 8123 : -1, uri.getPort());
                String path = suffix.contains("/path") ? "/path" : suffix.equals("/") ? "/" : null;
                assertEquals(path, uri.getPath());
                assertEquals(suffix.contains("?") ? "q=1" : null, uri.getQuery());
                assertEquals(suffix.contains("#") ? "part" : null, uri.getFragment());
                assertFalse(uri.hasViolations());
            }
        }
    }

    @Test
    public void rejectsMalformedIpv6AuthorityBoundaries() {
        for (String value : new String[] {
                "http://[normal.com@]vulndetector.com/",
                "http://normal.com[user@vulndetector].com/",
                "http://normal.com[@]vulndetector.com/"}) {
            assertThrows(value, IllegalArgumentException.class, () -> new HttpURI(value));
        }
    }

    @Test
    public void rejectsAnInvalidNumericIpv6Literal() {
        assertThrows(IllegalArgumentException.class, () -> new HttpURI("http://[ffff]/"));
    }

    @Test
    public void authorityStopsBeforeFragmentAndQuery() {
        // cccfd20b removes this old rejection expectation when fixing empty URI paths.
        HttpURI userFragment = new HttpURI("https://user#@host.com/");
        assertEquals("user", userFragment.getHost());
        assertEquals("@host.com/", userFragment.getFragment());
        assertNull(userFragment.getUser());
        HttpURI fragment = new HttpURI("http://good.com#@evil.com");
        assertEquals("good.com", fragment.getAuthority());
        assertNull(fragment.getPath());
        assertNull(fragment.getQuery());
        assertEquals("@evil.com", fragment.getFragment());
        HttpURI query = new HttpURI("http://good.com?@evil.com");
        assertEquals("good.com", query.getAuthority());
        assertNull(query.getPath());
        assertEquals("@evil.com", query.getQuery());
        assertNull(query.getFragment());
    }

    @Test
    public void emptyAbsolutePathsRetainJetty9NullContract() {
        for (String value : new String[] {"http://localhost", "http://localhost:80", "http://[::1]", "http://[::1]:80"}) {
            HttpURI uri = new HttpURI(value);
            assertNull(value, uri.getPath());
            assertNull(value, uri.getPathQuery());
        }
        HttpURI queryOnly = new HttpURI("http://localhost:80?q=1");
        assertNull(queryOnly.getPath());
        assertEquals("?q=1", queryOnly.getPathQuery());
        assertEquals("q=1", queryOnly.getQuery());
    }

    @Test
    public void relativeQueryAndFragmentRetainOriginalEmptyPath() {
        HttpURI query = new HttpURI("?q=1");
        assertEquals("", query.getPath());
        assertEquals("", query.getDecodedPath());
        assertEquals("?q=1", query.getPathQuery());
        HttpURI fragment = new HttpURI("#part");
        assertEquals("", fragment.getPath());
        assertEquals("", fragment.getDecodedPath());
        assertEquals("", fragment.getPathQuery());
        assertEquals("part", fragment.getFragment());
    }

    @Test
    public void validSchemeAndSlashRetainExistingApiBehavior() {
        HttpURI slash = new HttpURI("/");
        assertEquals("/", slash.getPath());
        assertFalse(slash.hasViolations());
        HttpURI absolute = new HttpURI("HTTP://localhost:8123/path%20name?q=1#part");
        assertEquals("HTTP", absolute.getScheme());
        assertEquals("localhost:8123", absolute.getAuthority());
        assertEquals("/path name", absolute.getDecodedPath());
        assertFalse(absolute.hasViolations());
    }

    @Test
    public void connectAndUnicodeRetainJetty9Compatibility() {
        HttpURI connect = new HttpURI();
        connect.parseRequestTarget("CONNECT", "localhost:8123");
        assertEquals("localhost:8123", connect.getAuthority());
        assertNull(connect.getPath());
        for (String host : new String[] {"[::1]", "[::ffff:127.0.0.1]"}) {
            connect.parseRequestTarget("CONNECT", host + ":8123");
            assertEquals(host + ":8123", connect.getAuthority());
            assertEquals(8123, connect.getPort());
            assertNull(connect.getPath());
            assertNull(connect.getQuery());
            assertNull(connect.getFragment());
        }
        for (String value : new String[] {"/地図", "地図/タイル", "http://localhost/地図"}) {
            HttpURI uri = new HttpURI(value);
            assertTrue(uri.getDecodedPath().contains("地図"));
            assertFalse(uri.hasViolations());
        }
    }

}
