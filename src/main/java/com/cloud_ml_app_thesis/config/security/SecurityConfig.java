package com.cloud_ml_app_thesis.config.security;

import com.cloud_ml_app_thesis.service.security.AccountDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AccountDetailsService userDetailsService;


    /**
     * Main security filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors->{})
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/algorithms/get-algorithms",
                                "/api/algorithms/weka/*/options",
                                "/api/train/parse-dataset-columns",
                                "/api/auth/**",

                                // ✅ Swagger/OpenAPI (χωρίς /api)
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",

                                // ✅ Swagger/OpenAPI (με /api) — αυτό σου λείπει
                                "/api/swagger-ui/**",
                                "/api/swagger-ui.html",
                                "/api/v3/api-docs/**",

                                "/api/users/forgot-password",
                                "/api/users/reset-password",
                                "/rapidoc.html",
                                "/redoc.html",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);

        // Use patterns to support minikube service tunnels (random ports on 127.0.0.1)
        // Also support environment-specified origins for specific deployments
        String corsOrigins = System.getenv().getOrDefault("CORS_ALLOWED_ORIGINS", "");

        List<String> allowedOriginPatterns = new java.util.ArrayList<>();

        // Always allow localhost and 127.0.0.1 with any port (for minikube service tunnels)
        allowedOriginPatterns.add("http://localhost:*");
        allowedOriginPatterns.add("http://127.0.0.1:*");
        allowedOriginPatterns.add("https://localhost:*");
        allowedOriginPatterns.add("https://127.0.0.1:*");

        // Add custom origins from environment (e.g., http://192.168.58.2:30173)
        if (!corsOrigins.isEmpty()) {
            allowedOriginPatterns.addAll(List.of(corsOrigins.split(",")));
        }

        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public Argon2PasswordEncoder passwordEncoder() {
        // Parameters must match the password hashes in the actual database
        // m=4096 (4MB), t=3 (iterations), p=1 (parallelism)
        return new Argon2PasswordEncoder(16, 32, 1, 4096, 3);
    }

    /**
     * Provide AuthenticationManager if you need to manually authenticate
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
