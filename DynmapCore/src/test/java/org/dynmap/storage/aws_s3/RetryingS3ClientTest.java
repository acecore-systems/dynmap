package org.dynmap.storage.aws_s3;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.*;
import org.junit.Test;
import io.github.linktosriram.s3lite.api.client.S3Client;
import io.github.linktosriram.s3lite.api.exception.S3Exception;
import io.github.linktosriram.s3lite.api.request.GetObjectRequest;

public class RetryingS3ClientTest {
    private final GetObjectRequest request = GetObjectRequest.builder().bucketName("test").key("test").build();
    private S3Exception error(String code) { S3Exception e = mock(S3Exception.class); when(e.getCode()).thenReturn(code); return e; }

    @Test public void transportFailureRetriesThenSucceeds() {
        S3Client client = mock(S3Client.class);
        when(client.getObjectAsBytes(request)).thenThrow(new UncheckedIOException(new SocketTimeoutException())).thenReturn(null);
        List<Long> sleeps = new ArrayList<>();
        RetryingS3Client.wrap(client, sleeps::add).getObjectAsBytes(request);
        verify(client, times(2)).getObjectAsBytes(request);
        assertEquals(Collections.singletonList(250L), sleeps);
    }
    @Test public void internalErrorHasThreeAttemptsNotInfiniteLoop() {
        S3Client client = mock(S3Client.class); S3Exception e = error("InternalError");
        when(client.getObjectAsBytes(request)).thenThrow(e);
        List<Long> sleeps = new ArrayList<>();
        try { RetryingS3Client.wrap(client, sleeps::add).getObjectAsBytes(request); fail(); }
        catch (S3Exception actual) { assertSame(e, actual); }
        verify(client, times(3)).getObjectAsBytes(request);
        assertEquals(Arrays.asList(250L,1000L), sleeps);
    }
    @Test public void authenticationAndNotFoundAreNotRetried() {
        for (String code : Arrays.asList("AccessDenied", "NoSuchKey", "SignatureDoesNotMatch")) {
            S3Client client = mock(S3Client.class); S3Exception e = error(code);
            when(client.getObjectAsBytes(request)).thenThrow(e);
            try { RetryingS3Client.wrap(client, ms -> fail()).getObjectAsBytes(request); fail(); }
            catch (S3Exception actual) { assertSame(e, actual); }
            verify(client).getObjectAsBytes(request);
        }
    }
    @Test public void interruptionIsPreservedAndStopsRetries() {
        S3Client client = mock(S3Client.class);
        S3Exception error = error("InternalError");
        when(client.getObjectAsBytes(request)).thenThrow(error);
        try {
            RetryingS3Client.wrap(client, ms -> { throw new InterruptedException(); }).getObjectAsBytes(request);
            fail();
        } catch (S3Exception expected) { assertTrue(Thread.currentThread().isInterrupted()); }
        finally { Thread.interrupted(); }
        verify(client).getObjectAsBytes(request);
    }
}
