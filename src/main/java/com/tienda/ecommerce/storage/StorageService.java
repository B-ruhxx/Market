package com.tienda.ecommerce.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Contrato de almacenamiento de archivos multimedia (modo local o Google Cloud Storage).
 */
public interface StorageService {

    /**
     * Guarda un archivo en el almacenamiento configurado y devuelve su URL pública.
     *
     * @param nombreArchivo nombre final del archivo (por ejemplo, UUID + extensión)
     * @param contenido flujo binario del archivo
     * @param contentType tipo MIME validado (por ejemplo, "image/png")
     * @return URL pública relativa ("/uploads/...") o absoluta ("https://storage.googleapis.com/...")
     * @throws IOException si ocurre un error de entrada/salida al persistir el archivo
     */
    String guardar(String nombreArchivo, InputStream contenido, String contentType) throws IOException;
}
