package org.dynmap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClientSanitizerTest {
    @Test
    public void keepsSafeFormattingAndStripsActiveContent() {
        String html = Client.sanitizeHTML(
                "<b>Player</b><span style='color:#ff0000'>red</span>"
                + "<a href='https://example.com/'>link</a>"
                + "<a href='javascript:alert(1)'>blocked</a>"
                + "<img src='https://example.com/icon.png' onerror='alert(1)'>"
                + "<script>alert(1)</script>");

        assertTrue(html.contains("<b>Player</b>"));
        assertTrue(html.contains("color:"));
        assertTrue(html.contains("https://example.com/"));
        assertFalse(html.contains("javascript:"));
        assertFalse(html.contains("onerror"));
        assertFalse(html.contains("alert(1)"));
    }
}
