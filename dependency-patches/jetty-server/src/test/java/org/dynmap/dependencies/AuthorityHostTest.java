package org.dynmap.dependencies;

import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.Test;

import static org.junit.Assert.*;

public class AuthorityHostTest {
    @Test
    public void mismatchedHostIsRejectedBeforeCustomizerAndHandler() throws Exception {
        Result result = request("GET http://example.test/resource HTTP/1.1\r\nHost: other.test\r\n", true, null);
        assertEquals(400, result.status);
        assertEquals(0, result.customizers);
        assertEquals(0, result.handlers);
    }

    @Test
    public void conflictingDuplicateHostsAreRejectedBeforeHandler() throws Exception {
        Result result = request("POST /ctx/auth/info HTTP/1.1\r\nHost: test\r\nHost: localhost\r\n", false, HttpCompliance.RFC7230);
        assertEquals(400, result.status);
        assertEquals(0, result.customizers);
        assertEquals(0, result.handlers);
    }

    @Test
    public void caseAndEquivalentDefaultPortsRemainValid() throws Exception {
        for (String[] item : new String[][] {
                {"http://Example.test/resource", "EXAMPLE.TEST:80"},
                {"http://example.test:80/resource", "example.test"},
                {"https://example.test/resource", "example.test:443"},
                {"wss://example.test/resource", "EXAMPLE.TEST:443"},
                {"ftp://example.test/resource", "example.test:21"},
                {"custom://example.test:8123/resource", "example.test:8123"}}) {
            Result result = request("GET " + item[0] + " HTTP/1.1\r\nHost: " + item[1] + "\r\n", false, null);
            assertEquals(item[0], 200, result.status);
            assertEquals(1, result.customizers);
            assertEquals(1, result.handlers);
        }
    }

    @Test
    public void differentHostOrEffectivePortIsRejected() throws Exception {
        for (String[] item : new String[][] {
                {"http://example.test/resource", "other.test"},
                {"http://example.test:80/resource", "example.test:81"},
                {"https://example.test/resource", "example.test:80"}}) {
            assertEquals(400, request("GET " + item[0] + " HTTP/1.1\r\nHost: " + item[1] + "\r\n", false, null).status);
        }
    }

    @Test
    public void noPathAndQueryOnlyTargetsRemainNormalRequests() throws Exception {
        for (String target : new String[] {"http://example.test:80", "http://example.test:80?q=1", "http://[::1]:80?q=1"}) {
            String host = target.contains("[::1]") ? "[::1]:80" : "example.test:80";
            Result result = request("GET " + target + " HTTP/1.1\r\nHost: " + host + "\r\n", false, null);
            assertEquals(target, 200, result.status);
            assertEquals(1, result.handlers);
        }
    }

    @Test
    public void originFormAndMissingHostKeepExistingPolicy() throws Exception {
        assertEquals(200, request("GET /resource HTTP/1.1\r\nHost: example.test\r\n", false, null).status);
        assertEquals(400, request("GET /resource HTTP/1.1\r\n", false, null).status);
        assertEquals(200, request("GET /resource HTTP/1.0\r\n", false, null).status);
        assertEquals(200, request("GET http://example.test/resource HTTP/1.0\r\n", false, null).status);
    }

    @Test
    public void connectRetainsExactAuthorityAndRejectsMismatches() throws Exception {
        assertEquals(405, request("CONNECT Example.test:443 HTTP/1.1\r\nHost: example.test:443\r\n", false, null).status);
        assertEquals(400, request("CONNECT example.test:443 HTTP/1.1\r\nHost: other.test:443\r\n", false, null).status);
        assertEquals(400, request("CONNECT example.test:443 HTTP/1.1\r\nHost: example.test:80\r\n", false, null).status);
    }

    @Test
    public void ipv6LiteralCaseAndDefaultPortRemainValid() throws Exception {
        assertEquals(200, request("GET http://[::1]:80/resource HTTP/1.1\r\nHost: [::1]\r\n", false, null).status);
        assertEquals(200, request("GET http://[2001:DB8::1]/resource HTTP/1.1\r\nHost: [2001:db8::1]:80\r\n", false, null).status);
    }

    @Test
    public void everyComplianceModeRejectsAuthorityMismatch() throws Exception {
        for (HttpCompliance compliance : HttpCompliance.values()) {
            assertEquals(compliance.toString(), 400,
                    request("GET http://example.test/resource HTTP/1.1\r\nHost: other.test\r\n", false, compliance).status);
        }
    }

    private static Result request(String headers, boolean rewriteAuthority, HttpCompliance compliance) throws Exception {
        Server server = new Server();
        AtomicInteger customizers = new AtomicInteger();
        AtomicInteger handlers = new AtomicInteger();
        HttpConfiguration configuration = new HttpConfiguration();
        configuration.addCustomizer((connector, config, request) -> {
            customizers.incrementAndGet();
            if (rewriteAuthority) {
                request.getHttpURI().setAuthority("other.test", 80);
            }
        });
        HttpConnectionFactory factory = new HttpConnectionFactory(configuration);
        if (compliance != null) {
            factory.setHttpCompliance(compliance);
        }
        LocalConnector connector = new LocalConnector(server, factory);
        server.setConnectors(new Connector[] {connector});
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
                handlers.incrementAndGet();
                // The in-memory test handler never creates a CONNECT tunnel.
                response.setStatus(request.getMethod().equals("CONNECT") ? 405 : 200);
                response.setContentLength(0);
                baseRequest.setHandled(true);
            }
        });
        try {
            server.start();
            String response = connector.getResponse(headers + "Connection: close\r\n\r\n");
            return new Result(Integer.parseInt(response.split(" ", 3)[1]), customizers.get(), handlers.get());
        } finally {
            server.stop();
        }
    }

    private static class Result {
        final int status;
        final int customizers;
        final int handlers;

        Result(int status, int customizers, int handlers) {
            this.status = status;
            this.customizers = customizers;
            this.handlers = handlers;
        }
    }
}
