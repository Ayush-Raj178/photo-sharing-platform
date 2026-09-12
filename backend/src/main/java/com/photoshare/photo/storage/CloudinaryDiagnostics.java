package com.photoshare.photo.storage;

import com.cloudinary.Cloudinary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Logs selected, sanitized fields only; never pass SDK exceptions or response maps to the logger. */
final class CloudinaryDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryDiagnostics.class);
    private final Cloudinary cloudinary;

    CloudinaryDiagnostics(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    void providerFailure(String operation, Map<?, ?> error) {
        Object code = error.get("http_code");
        String status = code instanceof Number number && number.intValue() >= 100 && number.intValue() <= 599
                ? Integer.toString(number.intValue()) : "unavailable";
        log.warn("Cloudinary operation failed [operation={}, phase=provider_response, exceptionClass=none, "
                        + "rootType=none, httpStatus={}, providerCode={}, message=\"{}\"]",
                operation, status, sanitize(error.get("code")), sanitize(error.get("message")));
    }

    void exceptionFailure(String operation, String phase, Exception exception) {
        Throwable root = exception;
        for (int depth = 0; depth < 16 && root.getCause() != null && root.getCause() != root; depth++) {
            root = root.getCause();
        }
        log.warn("Cloudinary operation failed [operation={}, phase={}, exceptionClass={}, rootType={}, "
                        + "httpStatus=unavailable, providerCode=unavailable, message=\"{}\", rootMessage=\"{}\"]",
                operation, phase, exception.getClass().getName(), root.getClass().getName(),
                sanitize(exception.getMessage()), sanitize(root.getMessage()));
    }

    void readFailure(String phase, Integer status, String publicId, String format,
                     Exception exception, String providerMessage) {
        Throwable root = exception;
        for (int depth = 0; root != null && depth < 16 && root.getCause() != null
                && root.getCause() != root; depth++) root = root.getCause();
        log.warn("Cloudinary operation failed [operation=read, phase={}, httpStatus={}, publicIdPresent={}, "
                        + "format={}, deliveryType=authenticated, exceptionClass={}, rootType={}, message=\"{}\", rootMessage=\"{}\"]",
                phase, status == null ? "unavailable" : status,
                publicId != null && !publicId.isBlank(),
                "jpg".equals(format) || "png".equals(format) ? format : "unavailable",
                exception == null ? "none" : exception.getClass().getName(),
                root == null ? "none" : root.getClass().getName(),
                sanitize(exception == null ? providerMessage : exception.getMessage()),
                sanitize(root == null ? null : root.getMessage()));
    }

    String readErrorMessage(HttpResponse<byte[]> response) {
        String header = response.headers().firstValue("X-Cld-Error").orElse(null);
        if (header != null) return header;
        // Inspect only a small JSON error envelope; never log a response or parsing exception.
        byte[] body = response.body();
        if (body != null && body.length <= 16_384
                && response.headers().firstValue("Content-Type").orElse("").startsWith("application/json")) {
            try {
                var error = new org.cloudinary.json.JSONObject(new String(body, StandardCharsets.UTF_8))
                        .optJSONObject("error");
                if (error != null && error.opt("message") instanceof String message) return message;
            } catch (RuntimeException ignored) {
                // Malformed/HTML bodies are intentionally omitted.
            }
        }
        return "Cloudinary photo download rejected; provider message unavailable";
    }

    String sanitize(Object value) {
        if (!(value instanceof String) && !(value instanceof Number)) return "unavailable";
        String message = value.toString();
        // These SDK-generated messages may append a complete HTTP response body.
        if (message.contains("Invalid JSON response from server")) return "Invalid JSON response from server [body omitted]";
        if (message.contains("Server returned unexpected status code -")) {
            message = message.replaceFirst("(?s)^.*?(Server returned unexpected status code -\\s*\\d{3}).*$", "$1 [body omitted]");
        }
        if (cloudinary.config != null) {
            for (String secret : new String[]{cloudinary.config.apiKey, cloudinary.config.apiSecret}) {
                if (secret != null && !secret.isEmpty()) {
                    message = message.replace(secret, "[REDACTED]")
                            .replace(URLEncoder.encode(secret, StandardCharsets.UTF_8), "[REDACTED]");
                }
            }
        }
        message = message.replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]", " ")
                .replaceAll("(?i)(?:https?|cloudinary)://\\S+", "[URL REDACTED]")
                .replaceAll("(?i)(?:https?|cloudinary)%3a%2f%2f\\S+", "[URL REDACTED]")
                .replaceAll("\\?\\S+|(?i)\\b[a-z_][a-z0-9_]*=[^\\s]*&[^\\s]*", "[QUERY REDACTED]")
                // Omit any embedded body, signing input, credential or header suffix entirely.
                .replaceAll("(?is)(\\b(?:api[_ -]?key|api[_ -]?secret|signature|authorization|bearer|basic|token|password|credential|cookie|secret|signed[_ -]?url|string to sign)\\b).*$", "$1 [REDACTED]")
                .replaceAll("(?s)[{<].*$", "[body omitted]")
                .replaceAll("[A-Za-z0-9_+/=.-]{24,}", "[OPAQUE VALUE REDACTED]")
                .replace('"', '\'');
        return message.length() <= 300 ? message : message.substring(0, 300) + " [truncated]";
    }
}
