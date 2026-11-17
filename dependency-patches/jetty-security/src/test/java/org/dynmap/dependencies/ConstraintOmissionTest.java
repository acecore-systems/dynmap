package org.dynmap.dependencies;

import java.util.Arrays;
import java.util.HashSet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Password;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConstraintOmissionTest {
    @Test
    public void omittedMethodsMatchWholeTokens() throws Exception {
        ConstraintMapping mapping = mapping(new String[] {"PROPPATCH"}, true);
        assertEquals(200, status("PROPPATCH", null, mapping));
        assertEquals(403, status("PATCH", null, mapping));
        assertEquals(403, status("GET", null, mapping));
    }

    @Test
    public void multipleOmissionsKeepSubstringAndSuffixMethodsProtected() throws Exception {
        ConstraintMapping mapping = mapping(new String[] {"GET", "POST"}, true);
        assertEquals(200, status("GET", null, mapping));
        assertEquals(200, status("POST", null, mapping));
        assertEquals(403, status("ET", null, mapping));
        assertEquals(403, status("OST", null, mapping));
        assertEquals(403, status("omission", null, mapping));
    }

    @Test
    public void duplicateOmissionsPreserveForbiddenInBothOrders() throws Exception {
        ConstraintMapping forbidden = mapping(new String[] {"GET"}, true);
        ConstraintMapping permit = mapping(new String[] {"GET"}, false);
        assertEquals(403, status("POST", null, forbidden, permit));
        assertEquals(403, status("POST", null, permit, forbidden));
        assertEquals(200, status("GET", null, forbidden, permit));
    }

    @Test
    public void duplicateOmissionsCombineBothRoleSetsInBothOrders() throws Exception {
        ConstraintMapping alpha = mapping(new String[] {"GET"}, true, "alpha");
        ConstraintMapping beta = mapping(new String[] {"GET"}, true, "beta");
        assertEquals(200, status("POST", "alpha", alpha, beta));
        assertEquals(200, status("POST", "beta", alpha, beta));
        assertEquals(200, status("POST", "alpha", beta, alpha));
        assertEquals(200, status("POST", "beta", beta, alpha));
        assertEquals(401, status("POST", null, alpha, beta));
    }

    @Test
    public void distinctOmissionSetsRetainExistingCombination() throws Exception {
        ConstraintMapping alpha = mapping(new String[] {"GET"}, true, "alpha");
        ConstraintMapping beta = mapping(new String[] {"POST"}, true, "beta");
        assertEquals(403, status("GET", "alpha", alpha, beta));
        assertEquals(200, status("GET", "beta", alpha, beta));
        assertEquals(200, status("POST", "alpha", alpha, beta));
        assertEquals(403, status("POST", "beta", alpha, beta));
    }

    private static ConstraintMapping mapping(String[] omissions, boolean authenticated, String... roles) {
        Constraint constraint = new Constraint();
        constraint.setAuthenticate(authenticated);
        constraint.setRoles(roles);
        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setMethodOmissions(omissions);
        mapping.setConstraint(constraint);
        return mapping;
    }

    private static int status(String method, String user, ConstraintMapping... mappings) throws Exception {
        Server server = new Server();
        LocalConnector connector = new LocalConnector(server);
        server.setConnectors(new Connector[] {connector});
        UserStore users = new UserStore();
        users.addUser("alpha", new Password("password"), new String[] {"alpha"});
        users.addUser("beta", new Password("password"), new String[] {"beta"});
        HashLoginService login = new HashLoginService("unit");
        login.setUserStore(users);
        server.addBean(login);
        ConstraintSecurityHandler security = new ConstraintSecurityHandler();
        security.setAuthenticator(new BasicAuthenticator());
        security.setRealmName("unit");
        security.setConstraintMappings(Arrays.asList(mappings), new HashSet<>(Arrays.asList("alpha", "beta")));
        security.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
                response.setStatus(200);
                baseRequest.setHandled(true);
            }
        });
        server.setHandler(security);
        try {
            server.start();
            String authorization = user == null ? "" : "Authorization: Basic " +
                    java.util.Base64.getEncoder().encodeToString((user + ":password").getBytes("UTF-8")) + "\r\n";
            String response = connector.getResponse(method + " /unit HTTP/1.1\r\nHost: localhost\r\n" +
                    authorization + "Connection: close\r\n\r\n");
            return Integer.parseInt(response.split(" ", 3)[1]);
        } finally {
            server.stop();
        }
    }
}
