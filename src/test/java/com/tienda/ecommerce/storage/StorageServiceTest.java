package com.tienda.ecommerce.storage;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.tienda.ecommerce.common.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StorageServiceTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    StorageModeValidator.class,
                    LocalStorageService.class,
                    GcsStorageService.class,
                    GcsStorageConfig.class
            );

    @Test
    @DisplayName("LocalStorageService guarda de forma atómica y retorna /uploads/{archivo}")
    void localStorage_guardaArchivo(@TempDir Path tempDir) throws Exception {
        LocalStorageService service = new LocalStorageService(tempDir.toString());
        byte[] contenido = "png-demo".getBytes(StandardCharsets.UTF_8);

        String url = service.guardar("foto.png", new ByteArrayInputStream(contenido), "image/png");

        assertEquals("/uploads/foto.png", url);
        Path archivo = tempDir.resolve("foto.png");
        assertTrue(Files.exists(archivo));
        assertArrayEquals(contenido, Files.readAllBytes(archivo));

        // No debe sobrescribir archivos existentes (consistente con GCS doesNotExist)
        assertThrows(BadRequestException.class, () ->
                service.guardar("foto.png", new ByteArrayInputStream(contenido), "image/png"));
    }

    @Test
    @DisplayName("LocalStorageService no deja archivos parciales ni temporales cuando falla el stream")
    void localStorage_falloEnStream_noDejaBasura(@TempDir Path tempDir) throws Exception {
        LocalStorageService service = new LocalStorageService(tempDir.toString());
        InputStream streamRoto = new InputStream() {
            private int leidos = 0;

            @Override
            public int read() throws IOException {
                if (leidos++ < 5) {
                    return 0x41;
                }
                throw new IOException("Conexión interrumpida");
            }
        };

        assertThrows(IOException.class, () ->
                service.guardar("interrumpida.png", streamRoto, "image/png"));
        try (var list = Files.list(tempDir)) {
            assertEquals(List.of(), list.toList(), "No debe quedar ningún archivo parcial ni temporal");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "../escape.png,image/png",
            "..\\escape.png,image/png",
            "sub/foto.png,image/png",
            "a?b.png,image/png",
            "a#b.png,image/png",
            ".hidden.png,image/png",
            "evil.html,image/png",
            "foto.png,text/html",
            "foto.webp,image/png",
            "'   ',image/png"
    })
    @DisplayName("Ambos adaptadores rechazan nombres peligrosos, MIME no permitido y discrepancia extensión/MIME")
    void ambosAdaptadores_rechazanEntradasPeligrosas(String nombre, String mime, @TempDir Path tempDir) throws Exception {
        LocalStorageService local = new LocalStorageService(tempDir.toString());
        GcsStorageService gcs = new GcsStorageService(mock(Storage.class), "mi-bucket", "https://storage.googleapis.com");

        assertThrows(BadRequestException.class, () ->
                local.guardar(nombre, new ByteArrayInputStream(new byte[]{1}), mime));
        assertThrows(BadRequestException.class, () ->
                gcs.guardar(nombre, new ByteArrayInputStream(new byte[]{1}), mime));
    }

    @Test
    @DisplayName("Ambos adaptadores rechazan byte nulo en nombre de archivo con BadRequestException")
    void ambosAdaptadores_rechazanNullByte(@TempDir Path tempDir) throws Exception {
        LocalStorageService local = new LocalStorageService(tempDir.toString());
        GcsStorageService gcs = new GcsStorageService(mock(Storage.class), "mi-bucket", "https://storage.googleapis.com");

        assertThrows(BadRequestException.class, () ->
                local.guardar("a\0b.png", new ByteArrayInputStream(new byte[]{1}), "image/png"));
        assertThrows(BadRequestException.class, () ->
                gcs.guardar("a\0b.png", new ByteArrayInputStream(new byte[]{1}), "image/png"));
    }

    @Test
    @DisplayName("GcsStorageService sube objeto sin sobrescribir (doesNotExist) a la raíz del bucket y retorna URL CDN")
    void gcsStorage_subeObjetoYRetornaUrlPublica() throws Exception {
        Storage storageMock = mock(Storage.class);
        GcsStorageService service = new GcsStorageService(
                storageMock,
                "electronix-uploads-test",
                "https://storage.googleapis.com/"
        );
        byte[] bytes = "imagen-webp".getBytes(StandardCharsets.UTF_8);

        String url = service.guardar("producto-123.webp", new ByteArrayInputStream(bytes), "image/webp");

        assertEquals("https://storage.googleapis.com/electronix-uploads-test/producto-123.webp", url);

        ArgumentCaptor<BlobInfo> blobCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storageMock).createFrom(
                blobCaptor.capture(),
                any(InputStream.class),
                eq(Storage.BlobWriteOption.doesNotExist())
        );
        BlobInfo blob = blobCaptor.getValue();
        assertEquals("electronix-uploads-test", blob.getBucket());
        assertEquals("producto-123.webp", blob.getName());
        assertEquals("image/webp", blob.getContentType());
        assertEquals("public, max-age=604800", blob.getCacheControl());
    }

    @Test
    @DisplayName("GcsStorageService soporta CDN personalizado en raíz sin incluir nombre de bucket")
    void gcsStorage_soportaCdnEnRaiz() throws Exception {
        Storage storageMock = mock(Storage.class);
        GcsStorageService service = new GcsStorageService(
                storageMock,
                "electronix-uploads-test",
                "https://cdn.electronix.com/",
                false
        );

        String url = service.guardar("foto.jpg", new ByteArrayInputStream(new byte[]{1}), "image/jpeg");
        assertEquals("https://cdn.electronix.com/foto.jpg", url);
    }

    @Test
    @DisplayName("GcsStorageService valida bucketName y publicBaseUrl en construcción")
    void gcsStorage_validacionesConfig() {
        Storage storageMock = mock(Storage.class);

        assertThrows(IllegalStateException.class, () ->
                new GcsStorageService(storageMock, "   ", "https://storage.googleapis.com"));
        assertThrows(IllegalStateException.class, () ->
                new GcsStorageService(storageMock, "gs://bucket", "https://storage.googleapis.com"));
        assertThrows(IllegalStateException.class, () ->
                new GcsStorageService(storageMock, "bucket/sub", "https://storage.googleapis.com"));
        assertThrows(IllegalStateException.class, () ->
                new GcsStorageService(storageMock, "mi-bucket", "javascript:alert(1)"));
        assertThrows(IllegalStateException.class, () ->
                new GcsStorageService(storageMock, "mi-bucket", "http://insecure.example.com"));
        assertThrows(IllegalStateException.class, () ->
                new GcsStorageService(storageMock, "mi-bucket", "https://user@host.example.com"));
    }

    @Test
    @DisplayName("GcsStorageConfig rechaza rutas de credenciales inexistentes o directorios")
    void gcsConfig_rechazaRutaInvalida(@TempDir Path tempDir) {
        GcsStorageConfig config = new GcsStorageConfig();

        assertThrows(IllegalStateException.class, () ->
                config.gcsStorage(tempDir.resolve("no-existe.json").toString()));
        assertThrows(IllegalStateException.class, () ->
                config.gcsStorage(tempDir.toString()));
    }

    @Test
    @DisplayName("Híbrido: por defecto (sin APP_STORAGE_TYPE) activa LocalStorageService")
    void wiring_porDefectoActivaLocal(@TempDir Path tempDir) {
        contextRunner
                .withPropertyValues("app.upload-dir=" + tempDir)
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertNotNull(ctx.getBean(LocalStorageService.class));
                    assertEquals(0, ctx.getBeansOfType(GcsStorageService.class).size());
                });
    }

    @Test
    @DisplayName("Híbrido: con APP_STORAGE_TYPE=gcs activa GcsStorageService y desactiva LocalStorageService")
    void wiring_modoGcsActivaGcsStorage() {
        contextRunner
                .withBean(Storage.class, () -> mock(Storage.class))
                .withPropertyValues(
                        "app.storage.type=gcs",
                        "app.storage.gcs.bucket=electronix-uploads-prod"
                )
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertNotNull(ctx.getBean(GcsStorageService.class));
                    assertEquals(0, ctx.getBeansOfType(LocalStorageService.class).size());
                });
    }

    @Test
    @DisplayName("Híbrido: con APP_STORAGE_TYPE=gcs sin bucket o con valor inválido el arranque falla explícitamente")
    void wiring_modoInvalidoFalla() {
        contextRunner
                .withBean(Storage.class, () -> mock(Storage.class))
                .withPropertyValues("app.storage.type=gcs")
                .run(ctx -> assertNotNull(ctx.getStartupFailure()));

        contextRunner
                .withPropertyValues("app.storage.type=s3")
                .run(ctx -> {
                    assertNotNull(ctx.getStartupFailure());
                    assertInstanceOf(IllegalStateException.class, ctx.getStartupFailure());
                });

        contextRunner
                .withPropertyValues("app.storage.type=")
                .run(ctx -> {
                    assertNotNull(ctx.getStartupFailure());
                    assertInstanceOf(IllegalStateException.class, ctx.getStartupFailure());
                });
    }

    @Test
    @DisplayName("Perfil prod desactiva demo-seed por defecto y no incluye túneles de desarrollo en CORS")
    void perfilProd_defaultsSeguros() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=prod")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    var env = ctx.getEnvironment();
                    assertEquals("false", env.getProperty("app.demo-seed.enabled"));
                    String cors = env.getProperty("app.cors.allowed-origins", "");
                    assertFalse(cors.contains("localhost"), "prod no debe incluir localhost por defecto");
                    assertFalse(cors.contains("trycloudflare.com"), "prod no debe incluir túneles Cloudflare por defecto");
                    assertFalse(cors.contains("loca.lt"), "prod no debe incluir localtunnel por defecto");
                });
    }
}
