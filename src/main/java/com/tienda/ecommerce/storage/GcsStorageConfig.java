package com.tienda.ecommerce.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Configuración del cliente de Google Cloud Storage cuando el modo cloud está activo.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "gcs")
public class GcsStorageConfig {

    private static final List<String> STORAGE_SCOPES =
            List.of("https://www.googleapis.com/auth/devstorage.read_write");

    /**
     * Construye el cliente {@link Storage} usando una ruta JSON explícita si se define
     * {@code app.storage.gcs.credentials-path}, o Application Default Credentials (ADC) en caso contrario.
     */
    @Bean
    @ConditionalOnMissingBean(Storage.class)
    public Storage gcsStorage(
            @Value("${app.storage.gcs.credentials-path:}") String credentialsPath
    ) throws IOException {
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            Path ruta = Path.of(credentialsPath.trim()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(ruta) || !Files.isReadable(ruta)) {
                throw new IllegalStateException(
                        "GCP_CREDENTIALS_PATH no apunta a un archivo regular legible: " + ruta
                );
            }
            try (InputStream in = Files.newInputStream(ruta)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                        .createScoped(STORAGE_SCOPES);
                return StorageOptions.newBuilder()
                        .setCredentials(credentials)
                        .build()
                        .getService();
            }
        }
        return StorageOptions.getDefaultInstance().getService();
    }
}
