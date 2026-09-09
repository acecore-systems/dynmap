package org.dynmap.storage.aws_s3;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;
import javax.net.ssl.HttpsURLConnection;
import org.junit.Test;
import io.github.linktosriram.s3lite.api.exception.*;
import io.github.linktosriram.s3lite.http.spi.*;
import io.github.linktosriram.s3lite.http.spi.response.ImmutableResponse;
import io.github.linktosriram.s3lite.http.urlconnection.URLConnectionSdkHttpClient;

public class SafeS3HttpClientTest {
    private S3Exception failure(int status, String body) {
        SdkHttpClient delegate = mock(SdkHttpClient.class);
        ImmutableResponse response = mock(ImmutableResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.fromStatusCode(status));
        when(response.getResponseBody()).thenReturn(Optional.of(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
        when(delegate.apply(null)).thenReturn(response);
        try { new SafeS3HttpClient(delegate).apply(null); throw new AssertionError(); }
        catch (S3Exception error) { return error; }
    }
    @Test public void errorXmlDoesNotRequireJaxbAnd404IsTyped() {
        assertTrue(failure(404,"<Error><Code>NoSuchKey</Code></Error>") instanceof NoSuchKeyException);
        assertEquals("InternalError",failure(500,"<Error><Code>InternalError</Code></Error>").getCode());
        assertFalse(failure(500,"<Error><Code>NoSuchKey</Code></Error>") instanceof NoSuchKeyException);
    }
    @Test public void malformedOrOversizedOrEntityResponseFailsClosed() {
        assertEquals("ServiceUnavailable",failure(503,"<html>unavailable").getCode());
        assertEquals("HTTP404",failure(404,"<!DOCTYPE Error [<!ENTITY ex SYSTEM 'file:///nonexistent'>]><Error><Code>&ex;</Code></Error>").getCode());
        assertEquals("HTTP404",failure(404,new String(new char[70000]).replace('\0','x')).getCode());
        assertEquals("SlowDown",failure(429,"").getCode());
    }
    @Test public void bodyIsNotIncludedInExceptionMessage() {
        S3Exception error = failure(403,"<Error><Code>AccessDenied</Code><Message>private-data</Message></Error>");
        assertFalse(error.getMessage().contains("private-data"));
    }
    @Test public void successResponseIsUntouched() {
        SdkHttpClient delegate = mock(SdkHttpClient.class);
        ImmutableResponse response = mock(ImmutableResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.OK); when(delegate.apply(null)).thenReturn(response);
        assertSame(response,new SafeS3HttpClient(delegate).apply(null));
        verify(response,never()).getResponseBody();
    }
    @SuppressWarnings("unchecked")
    @Test public void factorySetsOnlyPerConnectionTimeouts() throws Exception {
        SdkHttpClient client = SafeS3HttpClient.create();
        Field delegate = SafeS3HttpClient.class.getDeclaredField("delegate"); delegate.setAccessible(true);
        Field customizer = URLConnectionSdkHttpClient.class.getDeclaredField("customizer"); customizer.setAccessible(true);
        HttpsURLConnection connection = mock(HttpsURLConnection.class);
        ((Consumer<HttpsURLConnection>)customizer.get(delegate.get(client))).accept(connection);
        verify(connection).setConnectTimeout(5000); verify(connection).setReadTimeout(10000);
        verifyNoMoreInteractions(connection);
    }
}
