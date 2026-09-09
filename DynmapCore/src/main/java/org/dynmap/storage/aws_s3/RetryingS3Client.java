package org.dynmap.storage.aws_s3;

import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import io.github.linktosriram.s3lite.api.client.S3Client;
import io.github.linktosriram.s3lite.api.exception.S3Exception;

/** Bounded retries for the idempotent operations used by this storage backend. */
final class RetryingS3Client {
    interface Sleeper { void sleep(long millis) throws InterruptedException; }

    static S3Client wrap(S3Client delegate) {
        return wrap(delegate, Thread::sleep);
    }

    static S3Client wrap(S3Client delegate, Sleeper sleeper) {
        return (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(),
                new Class<?>[] { S3Client.class }, (proxy, method, args) -> {
            for (int attempt = 0; ; attempt++) {
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException ex) {
                    Throwable cause = ex.getCause();
                    boolean retry = cause instanceof UncheckedIOException;
                    if (cause instanceof S3Exception) {
                        String code = ((S3Exception) cause).getCode();
                        retry = "InternalError".equals(code) || "ServiceUnavailable".equals(code)
                                || "SlowDown".equals(code) || "RequestTimeout".equals(code);
                    }
                    if (!retry || attempt >= 2 || Thread.currentThread().isInterrupted()) throw cause;
                    try {
                        sleeper.sleep(attempt == 0 ? 250 : 1000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw cause;
                    }
                }
            }
        });
    }
}
