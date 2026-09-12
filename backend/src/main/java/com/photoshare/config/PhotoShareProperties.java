package com.photoshare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@ConfigurationProperties("photoshare")
@Validated
public record PhotoShareProperties(
        String frontendOrigin,
        String corsAllowedOrigins,
        Jwt jwt,
        Storage storage,
        RateLimit rateLimit
) {
    public record Jwt(
            String issuer,
            String staffSecretBase64,
            String gallerySecretBase64,
            long staffTtlSeconds,
            long galleryTtlSeconds
    ) {}

    public record Storage(
            String driver,
            Path localRoot,
            Cloudinary cloudinary,
            long maxFileSizeBytes,
            long maxRequestSizeBytes,
            int maxFilesPerUpload,
            long maxImagePixels
    ) {
        public record Cloudinary(
                String cloudName,
                String apiKey,
                String apiSecret
        ) {}
    }

    public record RateLimit(
            long windowSeconds,
            int loginAttempts,
            int loginIpAttempts,
            int pinAttempts,
            int pinIpAttempts,
            int registrationAttempts,
            int memberProvisionAttempts
    ) {}
}
