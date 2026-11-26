package com.tienda.ecommerce.storage;

import com.tienda.ecommerce.common.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Adaptador local de {@link StorageService}: escribe de forma atómica en disco y devuelve /uploads/{archivo}.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path directorio;

    public LocalStorageService(@Value("${app.upload-dir:uploads}") String dir) throws IOException {
        Path base = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(base);
        this.directorio = base.toRealPath();
    }

    @Override
    public String guardar(String nombreArchivo, InputStream contenido, String contentType) throws IOException {
        StorageValidation.validarArchivo(nombreArchivo, contentType, contenido);

        Path destino = directorio.resolve(nombreArchivo).normalize();
        if (!destino.startsWith(directorio) || !directorio.equals(destino.getParent())) {
            throw new BadRequestException("Nombre de archivo inválido");
        }
        if (Files.isSymbolicLink(destino)) {
            throw new BadRequestException("Destino inválido");
        }

        if (Files.exists(destino)) {
            throw new BadRequestException("Ya existe un archivo con ese nombre");
        }

        Path temporal = Files.createTempFile(directorio, ".upload-", ".tmp");
        try {
            Files.copy(contenido, temporal, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporal, destino);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
        return "/uploads/" + nombreArchivo;
    }
}
