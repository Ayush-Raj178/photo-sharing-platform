package com.photoshare.security;

import com.photoshare.config.PhotoShareProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean("staffJwtEncoder")
    JwtEncoder staffJwtEncoder(PhotoShareProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secret(properties.jwt().staffSecretBase64()))
                .algorithm(MacAlgorithm.HS256).build();
    }

    @Bean("galleryJwtEncoder")
    JwtEncoder galleryJwtEncoder(PhotoShareProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secret(properties.jwt().gallerySecretBase64()))
                .algorithm(MacAlgorithm.HS256).build();
    }

    @Bean("staffJwtDecoder")
    JwtDecoder staffJwtDecoder(PhotoShareProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secret(properties.jwt().staffSecretBase64()))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(validator(properties.jwt().issuer(), "photoshare-staff", "staff"));
        return decoder;
    }

    @Bean("galleryJwtDecoder")
    JwtDecoder galleryJwtDecoder(PhotoShareProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secret(properties.jwt().gallerySecretBase64()))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(validator(properties.jwt().issuer(), "photoshare-gallery", "gallery"));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("staffJwtDecoder") JwtDecoder staffJwtDecoder,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/public/**", "/api/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(request -> request.getRequestURI().startsWith("/api/v1/public/")
                                ? null : defaultResolver.resolve(request))
                        .jwt(jwt -> jwt.decoder(staffJwtDecoder)
                                .jwtAuthenticationConverter(new StaffJwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, 401, "AUTHENTICATION_REQUIRED",
                                        "Authentication is required")))
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, 403, "FORBIDDEN", "Operation is not permitted")))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(PhotoShareProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(properties.corsAllowedOrigins().split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Location", "Retry-After"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static OAuth2TokenValidator<Jwt> validator(String issuer, String audience, String type) {
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean validAudience = jwt.getAudience().contains(audience);
            boolean validType = type.equals(jwt.getClaimAsString("type"));
            return validAudience && validType
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error(
                            "invalid_token", "Token audience or type is invalid", null));
        };
        return new DelegatingOAuth2TokenValidator<>(defaults, claims);
    }

    private static SecretKey secret(String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            if (decoded.length < 32) {
                throw new IllegalStateException("JWT secrets must decode to at least 32 bytes");
            }
            return new SecretKeySpec(decoded, "HmacSHA256");
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalStateException("JWT secrets must be valid base64 values", ex);
        }
    }

    private static void writeSecurityError(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        String requestId = UUID.randomUUID().toString();
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"requestId\":\"" + requestId + "\"}}");
    }
}
