package com.tienda.ecommerce.sitio;

import com.tienda.ecommerce.common.ApiResponse;
import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.storage.StorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

// Upload de imágenes de apariencia, productos y estudio.
// Valida contenido real, delega en StorageService (local o GCS) y devuelve la URL pública.
@RestController
@RequestMapping("/api/sitio")
public class UploadController {

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PRODUCTOS_GESTIONAR','APARIENCIA_GESTIONAR')")
    public ResponseEntity<ApiResponse<Map<String, String>>> subir(
            @RequestParam("archivo") MultipartFile archivo
    ) throws IOException {
        final ImageUploadValidator.ValidatedImage imagen;
        try {
            imagen = ImageUploadValidator.validarYSanitizar(archivo);
        } catch (BadRequestException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
        String nombre = UUID.randomUUID() + imagen.extension();
        try (var in = new ByteArrayInputStream(imagen.bytes())) {
            String url = storageService.guardar(nombre, in, imagen.contentType());
            return ResponseEntity.ok(ApiResponse.exito("Archivo subido", Map.of("url", url)));
        }
    }
}
