package com.tienda.ecommerce.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Autenticación dedicada de mínimo privilegio para el scraper de Prometheus.
 * Solo autoriza {@code /actuator/prometheus} cuando {@code PROMETHEUS_SCRAPE_TOKEN} está configurado.
 */
@Component
public class MetricsScrapeAuthFilter extends OncePerRequestFilter {

    public static final String AUTHORITY_METRICS_SCRAPE = "METRICS_SCRAPE";
    private static final RequestMatcher PROMETHEUS_MATCHER = new AntPathRequestMatcher("/actuator/prometheus", "GET");

    private final byte[] expectedTokenBytes;

    public MetricsScrapeAuthFilter(
            @Value("${app.monitoring.prometheus-scrape-token:}") String scrapeToken
    ) {
        String clean = scrapeToken == null ? "" : scrapeToken.trim();
        this.expectedTokenBytes = clean.isEmpty() ? null : clean.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (expectedTokenBytes != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && PROMETHEUS_MATCHER.matches(request)) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String provided = authHeader.substring(7).trim();
                byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
                if (MessageDigest.isEqual(expectedTokenBytes, providedBytes)) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            "prometheus-scraper",
                            null,
                            List.of(new SimpleGrantedAuthority(AUTHORITY_METRICS_SCRAPE))
                    );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
