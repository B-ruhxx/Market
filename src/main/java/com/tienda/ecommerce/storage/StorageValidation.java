package com.tienda.ecommerce.storage;

import com.tienda.ecommerce.common.BadRequestException;

import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validaciones compartidas de seguridad para adaptadores de almacenamiento.
 */
final class StorageValidation {

    private static final Pattern SAFE_FILENAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    private static final Pattern GCS_BUCKET_NAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,61}[a-z0-9]$");
    private static final Map<String, Set<String>> MIME_TO_EXTENSIONS = Map.of(
            "image/png", Set.of(".png"),
            "image/jpeg", Set.of(".jpg", ".jpeg"),
            "image/webp", Set.of(".webp")
    );

    private StorageValidation() {
    }

    static String validarArchivo(String nombreArchivo, String contentType, InputStream contenido) {
        if (contenido == null) {
            throw new BadRequestException("Contenido vacío");
        }
        if (nombreArchivo == null || nombreArchivo.isBlank()
                || nombreArchivo.contains("..")
                || !SAFE_FILENAME.matcher(nombreArchivo).matches()) {
            throw new BadRequestException("Nombre de archivo inválido");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new BadRequestException("Tipo de contenido inválido");
        }
        String mime = contentType.toLowerCase(Locale.ROOT).trim();
        Set<String> extensionesPermitidas = MIME_TO_EXTENSIONS.get(mime);
        if (extensionesPermitidas == null) {
            throw new BadRequestException("Tipo de archivo no permitido");
        }
        String lowerName = nombreArchivo.toLowerCase(Locale.ROOT);
        boolean extensionValida = extensionesPermitidas.stream().anyMatch(lowerName::endsWith);
        if (!extensionValida) {
            throw new BadRequestException("La extensión del archivo no coincide con su tipo MIME");
        }
        return mime;
    }

    static String validarBucketName(String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("GCP_STORAGE_BUCKET debe configurarse cuando app.storage.type=gcs");
        }
        String clean = bucketName.trim();
        if (clean.contains("..") || !GCS_BUCKET_NAME.matcher(clean).matches()) {
            throw new IllegalStateException("Nombre de bucket GCS inválido: " + clean);
        }
        return clean;
    }

    static String validarPublicBaseUrl(String publicBaseUrl) {
        String raw = (publicBaseUrl == null || publicBaseUrl.isBlank())
                ? "https://storage.googleapis.com"
                : publicBaseUrl.trim();
        while (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        final URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("GCP_STORAGE_PUBLIC_URL inválida: " + raw, e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        boolean esLocalHttp = "http".equals(scheme) && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
        if (!"https".equals(scheme) && !esLocalHttp) {
            throw new IllegalStateException("GCP_STORAGE_PUBLIC_URL debe usar esquema HTTPS: " + raw);
        }
        if (host == null || host.isBlank() || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException("GCP_STORAGE_PUBLIC_URL contiene componentes no permitidos: " + raw);
        }
        return raw;
    }
}
