package org.dynmap.storage.aws_s3;

import java.io.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import io.github.linktosriram.s3lite.api.exception.ErrorResponse;
import io.github.linktosriram.s3lite.api.exception.NoSuchKeyException;
import io.github.linktosriram.s3lite.api.exception.S3Exception;
import io.github.linktosriram.s3lite.http.spi.SdkHttpClient;
import io.github.linktosriram.s3lite.http.spi.request.ImmutableRequest;
import io.github.linktosriram.s3lite.http.spi.response.ImmutableResponse;
import io.github.linktosriram.s3lite.http.urlconnection.URLConnectionSdkHttpClient;

/** Keep SDK error handling independent of the old JAXB runtime and JVM-wide flags. */
final class SafeS3HttpClient implements SdkHttpClient {
    private final SdkHttpClient delegate;
    SafeS3HttpClient(SdkHttpClient delegate) { this.delegate = delegate; }

    static SdkHttpClient create() {
        return new SafeS3HttpClient(URLConnectionSdkHttpClient.withCustomizer(connection -> {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
        }));
    }

    @Override public ImmutableResponse apply(ImmutableRequest request) {
        ImmutableResponse response = delegate.apply(request);
        if (response.getStatus().is2xxSuccessful()) return response;
        int status = response.getStatus().getStatusCode();
        String code = status >= 500 ? "ServiceUnavailable" : status == 429 ? "SlowDown"
                : status == 408 ? "RequestTimeout" : "HTTP" + status;
        try (InputStream stream = response.getResponseBody().orElse(null)) {
            if (stream != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while (bytes.size() <= 65536 && (length = stream.read(buffer)) != -1) bytes.write(buffer, 0, length);
                if (bytes.size() <= 65536) {
                    String parsed = errorCode(bytes.toByteArray());
                    if (parsed != null) code = parsed;
                }
            }
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
        ErrorResponse error = new ErrorResponse();
        error.setCode(code);
        // Do not log arbitrary server response bodies, keys or potentially sensitive messages.
        error.setMessage("S3 HTTP " + status + " (" + code + ")");
        if ("NoSuchKey".equals(code) && status == 404) throw new NoSuchKeyException(error);
        throw new S3Exception(error);
    }

    private static String errorCode(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                public void warning(SAXParseException ex) throws SAXException { throw ex; }
                public void error(SAXParseException ex) throws SAXException { throw ex; }
                public void fatalError(SAXParseException ex) throws SAXException { throw ex; }
            });
            NodeList codes = builder.parse(new ByteArrayInputStream(xml)).getElementsByTagNameNS("*", "Code");
            if (codes.getLength() == 1) {
                String value = codes.item(0).getTextContent();
                if (value.matches("[A-Za-z0-9]{1,64}")) return value;
            }
        } catch (Exception invalid) { /* Fail closed to HTTP status, never expand external entities. */ }
        return null;
    }

    @Override public void close() throws IOException { delegate.close(); }
}
