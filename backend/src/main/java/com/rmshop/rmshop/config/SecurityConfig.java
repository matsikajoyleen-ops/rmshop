package com.rmshop.rmshop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    // Extra frontend addresses allowed to call the API, comma-separated
    // (e.g. a custom domain). Set CORS_EXTRA_ORIGINS on the host; no code change needed.
    @Value("${CORS_EXTRA_ORIGINS:}")
    private String extraOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = new ArrayList<>(List.of(
                "http://localhost:5500",
                "http://127.0.0.1:5500",
                "https://rmshop-seven.vercel.app",
                // Cloudflare Pages: the live site and its per-deploy preview addresses
                "https://rmshop.pages.dev",
                "https://*.rmshop.pages.dev"
        ));
        Arrays.stream(extraOrigins.split(","))
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .forEach(origins::add);

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
