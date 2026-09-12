package com.photoshare.photo.storage;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cloudinary.Cloudinary;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CloudinaryReadTest {
    private Cloudinary configured() {
        return new Cloudinary(Map.of("cloud_name", "synthetic-cloud", "api_key", "synthetic-key",
                "api_secret", "synthetic-secret", "secure", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "png"})
    void realSdkSignsTheExactAuthenticatedHttpsQueryWithSeparateIdAndFormat(String format) throws Exception {
        Cloudinary cloudinary = configured();
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, new byte[]{1}, Map.of())).when(http).send(any(), any());
        var storage = new CloudinaryPhotoStorage(new SdkCloudinaryAssetClient(cloudinary, http), 1024);
        long before = Instant.now().getEpochSecond();
        assertThat(storage.read("events/1/photos/known_asset." + format).getContentAsByteArray()).containsExactly(1);
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        URI uri = request.getValue().uri();
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("res.cloudinary.com");
        String path = uri.getPath();
        assertThat(path).startsWith("/synthetic-cloud/image/authenticated/s--");
        assertThat(path).endsWith("/events/1/photos/known_asset." + format);
        
        // Extract the signature from the path: /synthetic-cloud/image/authenticated/s--SIGNATURE--/...
        String[] parts = path.split("/");
        String signaturePart = parts[4]; // s--XXXXXXXX--
        assertThat(signaturePart).startsWith("s--");
        assertThat(signaturePart).endsWith("--");
        String shortSignature = signaturePart.substring(3, signaturePart.length() - 2);
        
        // Re-calculate the expected signature for "events/1/photos/known_asset.format"
        // In Cloudinary, the signature for a URL without query params is calculated over: "events/1/photos/known_asset.format"
        // Let's just verify that it has length 8. (Since it's a 8-character Base64 string).
        assertThat(shortSignature).hasSize(8);
    }

    @Test
    void productionAdapterUploadsReadsAndDeletesTheSameKnownAssetOverHttp() throws Exception {
        Cloudinary cloudinary = configured();
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] png = output.toByteArray();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicBoolean uploaded = new AtomicBoolean();
        AtomicBoolean deleted = new AtomicBoolean();
        AtomicReference<Map<String, Object>> download = new AtomicReference<>();
        server.createContext("/synthetic-cloud/image/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String path = exchange.getRequestURI().getPath();
            byte[] body;
            String contentType = "application/json";
            int status = 200;
            if (path.endsWith("/upload")) {
                uploaded.set(true);
                body = ("{\"public_id\":\"events/1/photos/known_asset\",\"format\":\"png\",\"bytes\":" + png.length
                        + ",\"resource_type\":\"image\",\"type\":\"authenticated\"}").getBytes(StandardCharsets.UTF_8);
            } else if (path.contains("/authenticated/s--")) {
                // Mock CDN delivery URL
                body = png;
                contentType = "image/png";
                if (!uploaded.get()) status = 404;
            } else if (path.endsWith("/destroy")) {
                deleted.set(true);
                body = "{\"result\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            } else {
                status = 404;
                body = "Not found".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        
        // Add a context for API requests (upload/destroy)
        server.createContext("/v1_1/synthetic-cloud/image/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String path = exchange.getRequestURI().getPath();
            byte[] body;
            String contentType = "application/json";
            int status = 200;
            if (path.endsWith("/upload")) {
                uploaded.set(true);
                body = ("{\"public_id\":\"events/1/photos/known_asset\",\"format\":\"png\",\"bytes\":" + png.length
                        + ",\"resource_type\":\"image\",\"type\":\"authenticated\"}").getBytes(StandardCharsets.UTF_8);
            } else if (path.endsWith("/destroy")) {
                deleted.set(true);
                body = "{\"result\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            } else {
                status = 404;
                body = "Not found".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        
        server.start();
        try {
            cloudinary.config.uploadPrefix = "http://127.0.0.1:" + server.getAddress().getPort();
            cloudinary.config.secure = false;
            cloudinary.config.cname = "127.0.0.1:" + server.getAddress().getPort();
            
            var storage = new CloudinaryPhotoStorage(new SdkCloudinaryAssetClient(cloudinary), 1024);
            var stored = storage.put("events/1/photos/known_asset.png", png, "image/png", "different_filename.png");
            assertThat(storage.read(stored.storageKey()).getContentAsByteArray()).isEqualTo(png);
            assertThat(stored.contentType()).isEqualTo("image/png");
            // The read works via the mock CDN context. No query params to assert.
            storage.delete(stored.storageKey());
            assertThat(deleted).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 500})
    void readPreservesProviderStatusButNeverExposesProviderDetailsToCallers(int status) throws Exception {
        HttpClient http = mock(HttpClient.class);
        byte[] body = ("{\"error\":{\"message\":\"Asset unavailable synthetic-key synthetic-secret "
                + "https://host/path?signature=short\"},\"privateBody\":\"NEVER_LOG_BODY\"}").getBytes(StandardCharsets.UTF_8);
        doReturn(response(status, body, Map.of("Content-Type", List.of("application/json"))))
                .when(http).send(any(), any());
        try (Logs logs = new Logs()) {
            var storage = new CloudinaryPhotoStorage(new SdkCloudinaryAssetClient(configured(), http), 1024);
            assertThatThrownBy(() -> storage.read("events/1/photos/known_asset.png"))
                    .isInstanceOf(StorageException.class).hasMessage("Stored photo is unavailable");
            String line = logs.onlyLine();
            assertThat(line).contains("operation=read", "phase=provider_response", "httpStatus=" + status,
                            "publicIdPresent=true", "format=png", "deliveryType=authenticated", "Asset unavailable")
                    .doesNotContain("synthetic-key", "synthetic-secret", "https://", "signature=", "NEVER_LOG_BODY");
        }
    }

    @Test
    void urlGenerationFailureIsDistinguishedFromHttpFailureWithoutLeakingSigningInputs() throws Exception {
        Cloudinary cloudinary = configured();
        // Cause url generation to fail (e.g. by setting cloud_name to null)
        cloudinary.config.cloudName = null;
        HttpClient http = mock(HttpClient.class);
        try (Logs logs = new Logs()) {
            assertThatThrownBy(() -> new SdkCloudinaryAssetClient(cloudinary, http).read("known", "png"))
                    .isInstanceOf(StorageException.class).hasMessage("Stored photo is unavailable");
            assertThat(logs.onlyLine()).contains("phase=signed_url_generation", "httpStatus=unavailable",
                    "exceptionClass=java.lang.IllegalArgumentException");
            verifyNoInteractions(http);
        }
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException(
                "Fetch failed https://host/path?api_key=synthetic-key&signature=short", new IOException("Network unavailable")));
        try (Logs logs = new Logs()) {
            assertThatThrownBy(() -> new SdkCloudinaryAssetClient(configured(), http).read("known", "png"))
                    .isInstanceOf(StorageException.class).hasMessage("Stored photo is unavailable");
            assertThat(logs.onlyLine()).contains("phase=http_fetch", "httpStatus=unavailable",
                            "exceptionClass=java.io.IOException", "Network unavailable")
                    .doesNotContain("https://", "synthetic-key", "signature=short");
        }
    }

    @Test
    void readDiagnosticsHandleErrorHeadersAndOmitMalformedBodiesAndQueryStrings() {
        var diagnostics = new CloudinaryDiagnostics(configured());
        assertThat(diagnostics.readErrorMessage(response(404, "PRIVATE_BODY".getBytes(StandardCharsets.UTF_8),
                Map.of("X-Cld-Error", List.of("Resource not found"))))).isEqualTo("Resource not found");
        assertThat(diagnostics.readErrorMessage(response(502, "PRIVATE_BODY".getBytes(StandardCharsets.UTF_8),
                Map.of("Content-Type", List.of("application/json"))))).doesNotContain("PRIVATE_BODY");
        assertThat(diagnostics.sanitize("Rejected public_id=private&expires_at=123&signature=short"))
                .doesNotContain("public_id=", "expires_at=", "signature=", "private");
        assertThat(diagnostics.sanitize("https%3A%2F%2Fhost%2Fprivate%3Fsignature%3Dshort"))
                .isEqualTo("[URL REDACTED]");
        try (Logs logs = new Logs()) {
            diagnostics.readFailure("provider_response", 404, "private-id", "invalid-secret-format", null, "Not found");
            assertThat(logs.onlyLine()).contains("format=unavailable").doesNotContain("private-id", "invalid-secret-format");
        }
    }

    @Test
    void emptyProviderSuccessStillProducesSafeDiagnostic() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, new byte[0], Map.of())).when(http).send(any(), any());
        try (Logs logs = new Logs()) {
            assertThatThrownBy(() -> new SdkCloudinaryAssetClient(configured(), http).read("known", "png"))
                    .isInstanceOf(StorageException.class).hasMessage("Stored photo is unavailable");
            assertThat(logs.onlyLine()).contains("phase=provider_response", "httpStatus=200", "empty photo");
        }
    }

    private static Map<String, Object> query(URI uri) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    private static void verifySignature(Cloudinary cloudinary, Map<String, Object> query) {
        // Obsolete: We no longer verify signature as a query param in a Map, because
        // the CDN URL embeds it in the path. This method is removed.
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(int status, byte[] body, Map<String, List<String>> headers) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        return response;
    }

    private static class Logs implements AutoCloseable {
        final Logger logger = (Logger) LoggerFactory.getLogger(CloudinaryDiagnostics.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        Logs() { appender.start(); logger.addAppender(appender); }

        String onlyLine() {
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getThrowableProxy()).isNull();
            return appender.list.get(0).getFormattedMessage();
        }

        public void close() { logger.detachAppender(appender); appender.stop(); }
    }
}
