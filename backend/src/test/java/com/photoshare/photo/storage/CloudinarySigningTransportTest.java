package com.photoshare.photo.storage;

import com.cloudinary.Cloudinary;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinarySigningTransportTest {
    @Test
    void eachUploadOptionPreservesSignatureAtTheActualHttp5TransportBoundary() throws Exception {
        try (Capture capture = new Capture()) {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("type", "authenticated");
            List<Map.Entry<String, Object>> additions = List.of(
                    Map.entry("resource_type", "image"),
                    Map.entry("public_id", "events/42/photos/signing_test"),
                    Map.entry("format", "png"),
                    Map.entry("overwrite", false),
                    Map.entry("unique_filename", false),
                    Map.entry("filename_override", "Wedding & reception café.png"),
                    Map.entry("return_error", true));
            Set<String> signedKeys = new java.util.HashSet<>(Set.of("type", "timestamp"));
            for (int step = -1; step < additions.size(); step++) {
                if (step >= 0) {
                    var addition = additions.get(step);
                    options.put(addition.getKey(), addition.getValue());
                    if (!Set.of("resource_type", "return_error").contains(addition.getKey())) signedKeys.add(addition.getKey());
                }
                Map<String, Object> before = new LinkedHashMap<>(options);
                capture.cloudinary.uploader().upload(new byte[]{1, 2, 3}, options);
                capture.verifySignedFields(signedKeys);
                assertThat(options).isEqualTo(before);
            }
        }
    }

    @Test
    void productionAdapterAndDestroySignExactlyTheTransmittedParameters() throws Exception {
        try (Capture capture = new Capture()) {
            var client = new SdkCloudinaryAssetClient(capture.cloudinary);
            client.upload(new byte[]{1, 2, 3}, "events/42/photos/signing_test", "png", "wedding.png");
            capture.verifySignedFields(Set.of("type", "timestamp", "public_id", "format", "overwrite",
                    "unique_filename", "filename_override"));
            client.delete("events/42/photos/signing_test");
            capture.verifySignedFields(Set.of("type", "timestamp", "public_id", "invalidate"));
        }
    }

    private static final class Capture implements AutoCloseable {
        private final HttpServer server;
        private final Cloudinary cloudinary;
        private volatile Map<String, List<String>> fields;

        Capture() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            cloudinary = new Cloudinary(Map.of("cloud_name", "synthetic-cloud", "api_key", "synthetic-key",
                    "api_secret", "synthetic-secret", "upload_prefix", "http://127.0.0.1:" + server.getAddress().getPort()));
            server.createContext("/v1_1/synthetic-cloud/image/", exchange -> {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                String boundary = contentType.substring(contentType.indexOf("boundary=") + 9).split(";")[0].replace("\"", "").trim();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, List<String>> parsed = new LinkedHashMap<>();
                for (String part : body.split(Pattern.quote("--" + boundary))) {
                    int headerEnd = part.indexOf("\r\n\r\n");
                    if (headerEnd < 0) continue;
                    var name = Pattern.compile("name=\"([^\"]+)\"").matcher(part.substring(0, headerEnd));
                    if (!name.find()) continue;
                    String value = part.substring(headerEnd + 4);
                    if (value.endsWith("\r\n")) value = value.substring(0, value.length() - 2);
                    parsed.computeIfAbsent(name.group(1), ignored -> new ArrayList<>()).add(value);
                }
                fields = parsed;
                String response = exchange.getRequestURI().getPath().endsWith("destroy") ? "{\"result\":\"ok\"}"
                        : "{\"public_id\":\"events/42/photos/signing_test\",\"format\":\"png\",\"bytes\":3,\"resource_type\":\"image\",\"type\":\"authenticated\"}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
        }

        void verifySignedFields(Set<String> expectedKeys) {
            assertThat(fields).isNotNull();
            assertThat(fields.values()).allMatch(values -> values.size() == 1);
            assertThat(fields).doesNotContainKeys("return_error", "resource_type", "cloud_name", "api_secret",
                    "folder", "asset_folder", "eager", "transformation", "tags", "context", "use_filename");
            Map<String, Object> transmitted = new LinkedHashMap<>();
            fields.forEach((name, values) -> {
                if (!Set.of("file", "api_key", "signature").contains(name)) transmitted.put(name, values.get(0));
            });
            assertThat(transmitted.keySet()).isEqualTo(expectedKeys);
            assertThat(transmitted.get("type")).isEqualTo("authenticated");
            assertThat(fields.get("api_key").get(0).equals(cloudinary.config.apiKey)).isTrue();
            String expectedSignature = cloudinary.apiSignRequest(transmitted, cloudinary.config.apiSecret, cloudinary.config.signatureVersion);
            // Do not print either signature, even if the comparison fails.
            assertThat(expectedSignature.equals(fields.get("signature").get(0)))
                    .as("SDK signature must match exact fields received over HTTP").isTrue();
        }

        @Override
        public void close() { server.stop(0); }
    }
}
