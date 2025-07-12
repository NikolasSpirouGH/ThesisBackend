package com.cloud_ml_app_thesis.config.security;


import com.cloud_ml_app_thesis.entity.User;
import io.jsonwebtoken.Jwts;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.cloud_ml_app_thesis.repository.UserRepository;



@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    // Remove jwtSecret since we use RSA keys instead of a shared secret.
    @Value("${jwt.expirationMs}")
    private int jwtExpirationMs;

    // Inject RSA key configuration and user repository via constructor.
    private final RsaKeyProperties rsaKeyProperties;
    private final UserRepository userRepository;

    // This key will hold the RSA private key for signing tokens.
    private Key key;

    @PostConstruct
    public void init() {
        // Use the RSA private key from your configuration for signing.
        key = rsaKeyProperties.getPrivateKey();
    }

    public String generateToken(Authentication authentication) {
        AccountDetails userPrincipal = (AccountDetails) authentication.getPrincipal();
        // Extract roles from the user and map them to a list of strings.
        List<String> roles = userPrincipal.getUser().getRoles().stream()
                .map(role -> role.getName().getAuthority())
                .collect(Collectors.toList());
        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(rsaKeyProperties.getPublicKey()).build().parseSignedClaims(token);            return true;
        } catch (JwtException | IllegalArgumentException e) {
            throw new RuntimeException("Expired or invalid JWT token");
        }
    }


    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(rsaKeyProperties.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
    public Authentication getAuthentication(String token) {
        String username = extractUsername(token);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + username));
        AccountDetails userDetails = new AccountDetails(user);
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }
}
