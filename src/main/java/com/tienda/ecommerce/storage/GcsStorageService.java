package com.tienda.ecommerce.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Adaptador cloud de {@link StorageService}: sube objetos directamente a la raíz del bucket
 * de Google Cloud Storage sin sobrescribir objetos existentes y retorna su URL pública HTTPS.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "gcs")
public class GcsStorageService implements StorageService {

    private static final String DEFAULT_CACHE_CONTROL = "public, max-age=604800";

    private final Storage storage;
    private final String bucketName;
    private final String publicBaseUrl;
    private final boolean includeBucketInPublicUrl;

    public GcsStorageService(Storage storage, String bucketName, String publicBaseUrl) {
        this(storage, bucketName, publicBaseUrl, true);
    }

    @Autowired
    public GcsStorageService(
            Storage storage,
            @Value("${app.storage.gcs.bucket:}") String bucketName,
            @Value("${app.storage.gcs.public-base-url:https://storage.googleapis.com}") String publicBaseUrl,
            @Value("${app.storage.gcs.include-bucket-in-public-url:true}") boolean includeBucketInPublicUrl
    ) {
        this.storage = storage;
        this.bucketName = StorageValidation.validarBucketName(bucketName);
        this.publicBaseUrl = StorageValidation.validarPublicBaseUrl(publicBaseUrl);
        this.includeBucketInPublicUrl = includeBucketInPublicUrl;
    }

    @Override
    public String guardar(String nombreArchivo, InputStream contenido, String contentType) throws IOException {
        String mime = StorageValidation.validarArchivo(nombreArchivo, contentType, contenido);

        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, nombreArchivo))
                .setContentType(mime)
                .setContentDisposition("inline")
                .setCacheControl(DEFAULT_CACHE_CONTROL)
                .build();
        storage.createFrom(blobInfo, contenido, Storage.BlobWriteOption.doesNotExist());

        if (includeBucketInPublicUrl) {
            return publicBaseUrl + "/" + bucketName + "/" + nombreArchivo;
        }
        return publicBaseUrl + "/" + nombreArchivo;
    }
}
