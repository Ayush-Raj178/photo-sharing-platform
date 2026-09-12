package com.photoshare.security;

import com.photoshare.common.ApiException;
import com.photoshare.config.PhotoShareProperties;
import com.photoshare.user.UserAccount;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class JwtService {
    private final JwtEncoder staffEncoder;
    private final JwtEncoder galleryEncoder;
    private final JwtDecoder galleryDecoder;
    private final PhotoShareProperties properties;

    public JwtService(
            @Qualifier("staffJwtEncoder") JwtEncoder staffEncoder,
            @Qualifier("galleryJwtEncoder") JwtEncoder galleryEncoder,
            @Qualifier("galleryJwtDecoder") JwtDecoder galleryDecoder,
            PhotoShareProperties properties
    ) {
        this.staffEncoder = staffEncoder;
        this.galleryEncoder = galleryEncoder;
        this.galleryDecoder = galleryDecoder;
        this.properties = properties;
    }

    public Token issueStaff(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.jwt().staffTtlSeconds());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())
                .audience(List.of("photoshare-staff"))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("type", "staff")
                .claim("role", user.getRole().name())
                .build();
        return new Token(encode(staffEncoder, claims), properties.jwt().staffTtlSeconds());
    }

    public Token issueGallery(long galleryId, int pinVersion) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(Long.toString(galleryId))
                .audience(List.of("photoshare-gallery"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.jwt().galleryTtlSeconds()))
                .claim("type", "gallery")
                .claim("pinVersion", pinVersion)
                .build();
        return new Token(encode(galleryEncoder, claims), properties.jwt().galleryTtlSeconds());
    }

    public GalleryGrant decodeGallery(String token) {
        try {
            Jwt jwt = galleryDecoder.decode(token);
            Number pinVersion = jwt.getClaim("pinVersion");
            return new GalleryGrant(Long.parseLong(jwt.getSubject()), pinVersion.intValue());
        } catch (RuntimeException ex) {
            throw ApiException.unauthorized("INVALID_GALLERY_ACCESS", "Gallery access is invalid or expired");
        }
    }

    private String encode(JwtEncoder encoder, JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public record Token(String value, long expiresIn) {}
    public record GalleryGrant(long galleryId, int pinVersion) {}
}
