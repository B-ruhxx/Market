package com.tienda.ecommerce.config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validación compartida de orígenes permitidos para CORS y WebSocket/STOMP.
 * Rechaza comodines globales ({@code *}, {@code https://*}, {@code *evil.com}, etc.)
 * y sufijos públicos amplios para evitar exposición entre orígenes arbitrarios.
 */
public final class CorsOriginValidator {

    private static final Set<String> PUBLIC_MULTI_LABEL_SUFFIXES = Set.of(
            "co.uk", "org.uk", "ac.uk", "gov.uk",
            "com.pe", "org.pe", "edu.pe", "gob.pe",
            "com.br", "com.au", "com.ar", "com.mx", "co.jp"
    );

    private CorsOriginValidator() {
    }

    public static List<String> parseAndValidate(String[] rawOrigins) {
        if (rawOrigins == null) {
            return List.of();
        }
        List<String> origins = Arrays.stream(rawOrigins)
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        for (String origin : origins) {
            validarPatronSeguro(origin);
        }
        return origins;
    }

    private static void validarPatronSeguro(String origin) {
        String lower = origin.toLowerCase(Locale.ROOT);
        if ("*".equals(lower) || "null".equals(lower)) {
            throw new IllegalStateException("Origen CORS inseguro no permitido: '" + origin + "'");
        }
        int schemeIdx = lower.indexOf("://");
        if (schemeIdx <= 0) {
            throw new IllegalStateException("Origen CORS debe incluir esquema explícito (http:// o https://): '" + origin + "'");
        }
        String scheme = lower.substring(0, schemeIdx);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalStateException("Esquema CORS no permitido: '" + origin + "'");
        }
        String remainder = lower.substring(schemeIdx + 3);
        if (remainder.contains("@") || remainder.contains("?") || remainder.contains("#")) {
            throw new IllegalStateException("Origen CORS contiene componentes no permitidos: '" + origin + "'");
        }
        int slashIdx = remainder.indexOf('/');
        String authority = slashIdx >= 0 ? remainder.substring(0, slashIdx) : remainder;
        String host;
        if (authority.startsWith("[")) {
            int closeIdx = authority.indexOf(']');
            if (closeIdx < 0) {
                throw new IllegalStateException("Origen CORS IPv6 malformado: '" + origin + "'");
            }
            host = authority.substring(0, closeIdx + 1);
        } else {
            int colonIdx = authority.indexOf(':');
            if (colonIdx >= 0 && authority.indexOf(':', colonIdx + 1) >= 0) {
                throw new IllegalStateException("Origen CORS malformado: '" + origin + "'");
            }
            host = colonIdx >= 0 ? authority.substring(0, colonIdx) : authority;
        }

        if (host.isBlank() || "*".equals(host) || "*.*".equals(host)) {
            throw new IllegalStateException("Patrón de host CORS demasiado amplio: '" + origin + "'");
        }
        if (host.contains("*")) {
            if (!host.startsWith("*.")) {
                throw new IllegalStateException("Patrón wildcard CORS inválido (solo se admite prefijo '*.'): '" + origin + "'");
            }
            String domain = host.substring(2);
            if (domain.isBlank() || domain.contains("*") || domain.startsWith(".") || domain.endsWith(".")
                    || !domain.contains(".") || PUBLIC_MULTI_LABEL_SUFFIXES.contains(domain)) {
                throw new IllegalStateException("Patrón wildcard CORS demasiado amplio: '" + origin + "'");
            }
        }
        try {
            URI.create(origin.replace("*.", "sub."));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Origen CORS malformado: '" + origin + "'", ex);
        }
    }
}
