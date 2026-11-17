package org.dynmap.dependencies;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.http.HttpVersion;
import org.junit.Test;

import static org.junit.Assert.*;

public class JettyChunkParserTest {
    private static final String REQUEST =
            "POST /unit HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n";

    @Test
    public void validChunksAndExtensionsSurviveEveryBufferBoundary() {
        String[] bodies = {
            "1\r\nx\r\n0\r\n\r\n",
            "1;a=token\r\nx\r\n0\r\n\r\n",
            "1 ; a = \"quoted; \\\" value\" ; b=c\r\nx\r\n0\r\n\r\n",
            "1;a=\"\"\r\nx\r\n0\r\n\r\n",
            "1;a\r\nx\r\n0;end=done\r\nX-Trailer: yes\r\n\r\n",
            "1\r\nx\r\n2\r\nyz\r\n0\r\n\r\n"
        };
        for (String body : bodies) {
            String message = REQUEST + body;
            for (int split = 0; split <= message.length(); split++) {
                RecordingHandler handler = parse(message, split);
                assertNull("split " + split + " " + body, handler.failure);
                assertTrue("split " + split + " " + body, handler.complete);
                assertTrue(handler.body.length() > 0);
            }
        }
    }

    @Test
    public void invalidChunkSyntaxIsRejectedAtEveryBufferBoundary() {
        String[] bodies = {
            "1;a=\"unclosed\r\nx\r\n0\r\n\r\n",
            "1;a=\"escape\\\r\nx\r\n0\r\n\r\n",
            "1;a=token\nx\r\n0\r\n\r\n",
            "1\nx\r\n0\r\n\r\n",
            "1\r\nx\n0\r\n\r\n",
            "1\r\nx0\r\n\r\n",
            "1\r\nx\r\n0\r\nX-Trailer: yes\n\r\n",
            "1;a=\"ok\"invalid\r\nx\r\n0\r\n\r\n"
        };
        for (String body : bodies) {
            String message = REQUEST + body;
            for (int split = 0; split <= message.length(); split++) {
                RecordingHandler handler = parse(message, split);
                assertTrue("split " + split + " " + body, handler.failure != null || handler.earlyEof);
                if (handler.failure != null) {
                    assertEquals(400, handler.failure.getCode());
                }
                assertFalse(handler.complete);
            }
        }
    }

    @Test
    public void singleByteReadOnlyBuffersRetainChunkState() {
        RecordingHandler handler = new RecordingHandler();
        HttpParser parser = new HttpParser((HttpParser.RequestHandler)handler);
        byte[] bytes = (REQUEST + "1;a=\"v\\\"q\"\r\nx\r\n0\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        for (byte value : bytes) {
            feed(parser, ByteBuffer.wrap(new byte[] {value}).asReadOnlyBuffer());
        }
        assertNull(handler.failure);
        assertTrue(handler.complete);
        assertEquals("x", handler.body.toString());
    }

    @Test
    public void rejectsEveryAdvisoryChunkTerminatorInAllComplianceModes() {
        String[] bodies = {
            "1;a=token\nx\r\n0\r\n\r\n",
            "1\r\nx\n0\r\n\r\n",
            "1\r\nx\r\n0\r\nX-Trailer: yes\n\r\n"
        };
        for (HttpCompliance compliance : HttpCompliance.values()) {
            for (String body : bodies) {
                String message = REQUEST + body;
                for (int split = 0; split <= message.length(); split++) {
                    RecordingHandler handler = parse(message, split, compliance);
                    assertTrue(compliance + " split " + split, handler.failure != null || handler.earlyEof);
                    assertFalse(compliance + " split " + split, handler.complete);
                }
            }
        }
    }

    @Test
    public void parserResetPreservesOrdinaryRequestHandling() {
        RecordingHandler handler = new RecordingHandler();
        HttpParser parser = new HttpParser((HttpParser.RequestHandler)handler);
        feed(parser, buffer(REQUEST + "1;a=\"yes\"\r\nx\r\n0\r\n\r\n"));
        assertTrue(handler.complete);
        parser.reset();
        handler.complete = false;
        feed(parser, buffer("GET /map HTTP/1.1\r\nHost: localhost\r\n\r\n"));
        assertNull(handler.failure);
        assertTrue(handler.complete);
        assertEquals("GET", handler.method);
        assertEquals("/map", handler.uri);
    }

    @Test
    public void fixedLengthAndResponseParsingRemainCompatible() {
        RecordingHandler request = parse(
                "POST /form HTTP/1.1\r\nHost: localhost\r\nContent-Length: 3\r\n\r\nabc", 25);
        assertTrue(request.complete);
        assertEquals("abc", request.body.toString());

        RecordingHandler response = new RecordingHandler();
        HttpParser parser = new HttpParser((HttpParser.ResponseHandler)response);
        feed(parser, buffer("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok"));
        assertNull(response.failure);
        assertEquals(200, response.status);
        assertEquals("ok", response.body.toString());
        assertTrue(response.complete);
    }

    private static RecordingHandler parse(String message, int split) {
        return parse(message, split, null);
    }

    private static RecordingHandler parse(String message, int split, HttpCompliance compliance) {
        RecordingHandler handler = new RecordingHandler();
        HttpParser parser = compliance == null ? new HttpParser((HttpParser.RequestHandler)handler)
                : new HttpParser((HttpParser.RequestHandler)handler, compliance);
        feed(parser, buffer(message.substring(0, split)));
        if (handler.failure == null) {
            feed(parser, buffer(message.substring(split)));
        }
        return handler;
    }

    private static ByteBuffer buffer(String value) {
        return ByteBuffer.wrap(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void feed(HttpParser parser, ByteBuffer bytes) {
        while (bytes.hasRemaining() && !parser.isComplete() && !parser.isClosed()) {
            int before = bytes.position();
            parser.parseNext(bytes);
            if (before == bytes.position()) {
                break;
            }
        }
    }

    private static final class RecordingHandler implements HttpParser.RequestHandler, HttpParser.ResponseHandler {
        private final StringBuilder body = new StringBuilder();
        private BadMessageException failure;
        private boolean complete;
        private boolean earlyEof;
        private String method;
        private String uri;
        private int status;

        @Override
        public boolean startRequest(String method, String uri, HttpVersion version) {
            this.method = method;
            this.uri = uri;
            return false;
        }

        @Override
        public boolean startResponse(HttpVersion version, int status, String reason) {
            this.status = status;
            return false;
        }

        @Override
        public boolean content(ByteBuffer item) {
            while (item.hasRemaining()) {
                body.append((char)(item.get() & 255));
            }
            return false;
        }

        @Override public boolean headerComplete() { return false; }
        @Override public boolean contentComplete() { return false; }
        @Override public boolean messageComplete() { complete = true; return false; }
        @Override public void parsedHeader(HttpField field) { }
        @Override public void earlyEOF() { earlyEof = true; }
        @Override public void badMessage(BadMessageException failure) { this.failure = failure; }
        @Override public int getHeaderCacheSize() { return 0; }
    }
}
