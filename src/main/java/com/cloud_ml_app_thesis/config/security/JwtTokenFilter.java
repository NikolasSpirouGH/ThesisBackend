package com.cloud_ml_app_thesis.config.security;


import com.cloud_ml_app_thesis.repository.JwtTokenRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.service.security.AccountDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AccountDetailsService accountDetailsService;


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        final String jwt;
        final String username;

        String path = request.getRequestURI();

        if (path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Optional: remove this log entirely
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        username = jwtTokenProvider.extractUsername(jwt);

        log.debug("📌 Extracted JWT: {}", jwt);
        log.debug("👤 Extracted username: {}", username);

        // ⛔ Reject revoked/expired tokens before checking auth context
        var storedToken = tokenRepository.findByToken(jwt);
        boolean isTokenValid = storedToken.isPresent()
                && !storedToken.get().isExpired()
                && !storedToken.get().isRevoked();

        log.debug("🧾 Token in DB present: {}, valid: {}", storedToken.isPresent(), isTokenValid);

        if (!isTokenValid || !jwtTokenProvider.validateToken(jwt)) {
            log.warn("❌ Token is invalid or revoked for user: {}", username);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token is invalid or revoked.");
            return;
        }

        // ✅ If no auth yet, authenticate
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var userDetails = accountDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

            authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            log.info("✅ Authenticated user: {}", username);
        } else {
            log.debug("ℹ️ SecurityContext already has authentication set");
        }

        filterChain.doFilter(request, response);
    }
}