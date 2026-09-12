package com.photoshare.photo.storage;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class CloudinaryDiagnosticsTest {
    private Cloudinary testCloudinary() {
        return new Cloudinary(Map.of("cloud_name", "synthetic-cloud", "api_key", "synthetic-key",
                "api_secret", "synthetic-secret", "secure", true));
    }

    @Test
    void sanitizationRemovesCredentialsSigningInputsUrlsBodiesAndLogInjection() {
        CloudinaryDiagnostics diagnostics = new CloudinaryDiagnostics(testCloudinary());
        assertThat(diagnostics.sanitize("Invalid Signature abc123. String to sign public_id=private"))
                .isEqualTo("Invalid Signature [REDACTED]");
        assertThat(diagnostics.sanitize("synthetic-key synthetic-secret https://host/path?signature=abc"))
                .doesNotContain("synthetic-key", "synthetic-secret", "https://", "abc");
        assertThat(diagnostics.sanitize("Authorization: Bearer short-token"))
                .isEqualTo("Authorization [REDACTED]");
        assertThat(diagnostics.sanitize("Server returned unexpected status code - 502 - arbitrary response body"))
                .isEqualTo("Server returned unexpected status code - 502 [body omitted]");
        assertThat(diagnostics.sanitize("Invalid JSON response from server raw body"))
                .isEqualTo("Invalid JSON response from server [body omitted]");
        assertThat(diagnostics.sanitize("Failed {\"response\":\"private\"}"))
                .isEqualTo("Failed [body omitted]");
        assertThat(diagnostics.sanitize("Failure\r\nforged log\u2028line")).doesNotContain("\r", "\n", "\u2028");
        assertThat(diagnostics.sanitize(Map.of("body", "private"))).isEqualTo("unavailable");
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrappedRuntimeExceptionLogsSanitizedOuterAndRootMessages() throws Exception {
        Cloudinary mocked = spy(testCloudinary());
        Uploader uploader = mock(Uploader.class);
        doReturn(uploader).when(mocked).uploader();
        when(uploader.upload(any(byte[].class), any(Map.class))).thenThrow(
                new RuntimeException("Upload rejected synthetic-secret", new IllegalArgumentException("Invalid api_key synthetic-key")));
        withLogs(logs -> {
            assertThatThrownBy(() -> new SdkCloudinaryAssetClient(mocked)
                    .upload(new byte[]{1}, "events/1/photos/test", "png", "test.png"))
                    .isInstanceOfSatisfying(StorageException.class, error -> assertThat(error.cleanupAllowed()).isFalse());
            String line = logs.list.get(0).getFormattedMessage();
            assertThat(line).contains("exceptionClass=java.lang.RuntimeException", "rootType=java.lang.IllegalArgumentException",
                    "phase=sdk_call_outcome_unknown", "Upload rejected", "Invalid api_key [REDACTED]")
                    .doesNotContain("synthetic-secret", "synthetic-key");
            assertThat(logs.list.get(0).getThrowableProxy()).isNull();
        });
    }

    @Test
    void actualHttp5SdkPreservesHttpErrorAndNeverLogsTheResponseBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> multipart = new AtomicReference<>();
        server.createContext("/v1_1/synthetic-cloud/image/upload", exchange -> {
            multipart.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"error\":{\"message\":\"Invalid Signature secret-signature String to sign private-input\","
                    + "\"code\":\"AUTH_FAILED\"},\"privateBody\":\"NEVER_LOG_BODY\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Cloudinary cloudinary = testCloudinary();
            cloudinary.config.uploadPrefix = "http://127.0.0.1:" + server.getAddress().getPort();
            withLogs(logs -> {
                assertThatThrownBy(() -> new SdkCloudinaryAssetClient(cloudinary)
                        .upload(new byte[]{1, 2, 3}, "events/1/photos/test", "png", "test.png"))
                        .isInstanceOfSatisfying(StorageException.class, error -> assertThat(error.cleanupAllowed()).isFalse())
                        .hasMessage("Photo bytes could not be stored");
                assertThat(multipart.get()).contains("name=\"file\"", "name=\"public_id\"", "events/1/photos/test",
                        "name=\"type\"", "authenticated", "name=\"format\"", "png");
                String line = logs.list.get(0).getFormattedMessage();
                assertThat(line).contains("phase=provider_response", "httpStatus=401", "providerCode=AUTH_FAILED",
                        "message=\"Invalid Signature [REDACTED]\"")
                        .doesNotContain("secret-signature", "private-input", "NEVER_LOG_BODY", "synthetic-key", "synthetic-secret");
                assertThat(logs.list.get(0).getThrowableProxy()).isNull();
            });
        } finally {
            server.stop(0);
        }
    }

    private void withLogs(java.util.function.Consumer<ListAppender<ILoggingEvent>> assertion) {
        Logger logger = (Logger) LoggerFactory.getLogger(CloudinaryDiagnostics.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            assertion.accept(logs);
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }
}
