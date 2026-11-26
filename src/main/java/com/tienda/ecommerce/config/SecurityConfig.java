package com.tienda.ecommerce.config;

import com.tienda.ecommerce.auth.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final MetricsScrapeAuthFilter metricsScrapeAuthFilter;

    @Value("${app.cors.allowed-origins:http://localhost:8080,http://localhost:3000}")
    private String[] allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, MetricsScrapeAuthFilter metricsScrapeAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.metricsScrapeAuthFilter = metricsScrapeAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Recursos estáticos del frontend
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/producto.html",
                                "/carrito.html",
                                "/checkout.html",
                                "/mis-pedidos.html",
                                "/login.html",
                                "/registro.html",
                                "/admin/**",
                                "/assets/**",
                                "/static/**",
                                "/favicon.ico",
                                "/*.css",
                                "/*.js",
                                "/*.html"
                        ).permitAll()

                        // Endpoints públicos de autenticación
                        .requestMatchers("/api/auth/**").permitAll()

                        // Endpoints de salud para orquestadores y balanceadores (Liveness / Readiness)
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // Endpoints de métricas, info y Prometheus restringidos a roles administrativos o token dedicado de scraping
                        .requestMatchers("/actuator/prometheus")
                        .hasAnyAuthority("CONFIGURACION_GESTIONAR", MetricsScrapeAuthFilter.AUTHORITY_METRICS_SCRAPE)
                        .requestMatchers(
                                "/actuator",
                                "/actuator/info",
                                "/actuator/metrics",
                                "/actuator/metrics/**"
                        ).hasAuthority("CONFIGURACION_GESTIONAR")

                        // Cualquier otro endpoint de actuator denegado por defecto
                        .requestMatchers("/actuator/**").denyAll()

                        // Endpoints públicos de catálogo
                        .requestMatchers(HttpMethod.GET, "/api/categorias/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pagos/metodos").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/monedas").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sitio/publico").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/productos/admin").hasAnyAuthority("PRODUCTOS_VER", "PRODUCTOS_GESTIONAR", "INVENTARIO_GESTIONAR")
                        .requestMatchers(HttpMethod.GET, "/api/productos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/crm/niveles").permitAll()

                        // Handshake de WebSocket
                        .requestMatchers("/ws-chat/**").permitAll()

                        // Endpoints administrativos
                        .requestMatchers("/api/admin/crm/**").hasAuthority("CRM_GESTIONAR")
                        .requestMatchers("/api/admin/**").hasAuthority("CONFIGURACION_GESTIONAR")
                        .requestMatchers("/api/reportes/**").hasAuthority("REPORTES_VER")

                        // Cualquier otra petición requiere autenticación
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(metricsScrapeAuthFilter, JwtAuthFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<MetricsScrapeAuthFilter> metricsScrapeFilterRegistration(MetricsScrapeAuthFilter filter) {
        FilterRegistrationBean<MetricsScrapeAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = CorsOriginValidator.parseAndValidate(allowedOrigins);
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // La API autentica vía Authorization: Bearer (JWT) y STOMP CONNECT; no usa cookies ni credenciales CORS.
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
