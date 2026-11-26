package com.tienda.ecommerce.sitio;

import com.google.cloud.storage.Storage;
import com.tienda.ecommerce.config.WebMvcConfig;
import com.tienda.ecommerce.storage.GcsStorageService;
import com.tienda.ecommerce.storage.LocalStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadControllerTest {

    @TempDir
    static Path tempUploadDir;

    @DynamicPropertySource
    static void configurarUploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> tempUploadDir.toString());
        registry.add("app.storage.type", () -> "local");
    }

    @Autowired
    private MockMvc mockMvc;

    private static byte[] crearImagenValida(String format) throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, Color.BLUE.getRGB());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, format, baos));
        return baos.toByteArray();
    }

    private static byte[] leerWebpReal() throws Exception {
        return Files.readAllBytes(Path.of("src/main/resources/static/assets/img/promos/portatil.webp"));
    }

    private static byte[] construirWebp(byte[] chunkId, byte[] payload, boolean incluirVp8x) {
        byte[] vp8x = incluirVp8x ? new byte[]{'V', 'P', '8', 'X', 10, 0, 0, 0, 0, 0, 0, 0, 9, 0, 0, 9, 0, 0} : new byte[0];
        int paddedPayload = payload.length + (payload.length & 1);
        int riffSize = 4 + vp8x.length + 8 + paddedPayload;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{'R', 'I', 'F', 'F'});
        out.write(riffSize & 0xFF);
        out.write((riffSize >> 8) & 0xFF);
        out.write((riffSize >> 16) & 0xFF);
        out.write((riffSize >> 24) & 0xFF);
        out.writeBytes(new byte[]{'W', 'E', 'B', 'P'});
        out.writeBytes(vp8x);
        out.writeBytes(chunkId);
        out.write(payload.length & 0xFF);
        out.write((payload.length >> 8) & 0xFF);
        out.write((payload.length >> 16) & 0xFF);
        out.write((payload.length >> 24) & 0xFF);
        out.writeBytes(payload);
        if ((payload.length & 1) == 1) {
            out.write(0);
        }
        return out.toByteArray();
    }

    @Test
    @DisplayName("POST /api/sitio/uploads y POST /uploads/* sin autenticación son rechazados (403)")
    void subir_sinAuth_retorna403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("archivo", "foto.png", "image/png", crearImagenValida("png"));
        mockMvc.perform(multipart("/api/sitio/uploads").file(file))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/uploads/cualquiera.png"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "CLIENTE")
    @DisplayName("POST /api/sitio/uploads con rol CLIENTE es rechazado (403)")
    void subir_clienteSinPermiso_retorna403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("archivo", "foto.png", "image/png", crearImagenValida("png"));
        mockMvc.perform(multipart("/api/sitio/uploads").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PRODUCTOS_GESTIONAR")
    @DisplayName("POST /api/sitio/uploads acepta PNG, JPEG y WEBP reales y guarda en modo local")
    void subir_imagenesValidasEnModoLocal_retornaUrlLocal() throws Exception {
        MockMultipartFile png = new MockMultipartFile("archivo", "equipo.png", "image/png", crearImagenValida("png"));
        MvcResult result = mockMvc.perform(multipart("/api/sitio/uploads").file(png))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.url").value(matchesPattern("^/uploads/[0-9a-f-]+\\.png$")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String nombreArchivo = body.replaceAll(".*\"/uploads/([^\"]+)\".*", "$1");
        Path guardado = tempUploadDir.resolve(nombreArchivo);
        assertTrue(Files.exists(guardado));
        assertNotNull(ImageIO.read(guardado.toFile()));

        MockMultipartFile jpg = new MockMultipartFile("archivo", "equipo.jpg", "image/jpeg", crearImagenValida("jpg"));
        mockMvc.perform(multipart("/api/sitio/uploads").file(jpg))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.url").value(matchesPattern("^/uploads/[0-9a-f-]+\\.jpg$")));

        MockMultipartFile webp = new MockMultipartFile("archivo", "equipo.webp", "image/webp", leerWebpReal());
        mockMvc.perform(multipart("/api/sitio/uploads").file(webp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.url").value(matchesPattern("^/uploads/[0-9a-f-]+\\.webp$")));
    }

    @Test
    @WithMockUser(authorities = "APARIENCIA_GESTIONAR")
    @DisplayName("POST /api/sitio/uploads rechaza HTML camuflado, MIME falsificado, SVG, bombas PNG, WebP políglota y archivos vacíos/>5MB")
    void subir_validacionesSeguridad_retorna400() throws Exception {
        long archivosAntes = Files.list(tempUploadDir).count();

        MockMultipartFile htmlComoPng = new MockMultipartFile(
                "archivo", "fake.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/sitio/uploads").file(htmlComoPng))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        MockMultipartFile jpegComoPng = new MockMultipartFile(
                "archivo", "disfraz.png", "image/png", crearImagenValida("jpg")
        );
        mockMvc.perform(multipart("/api/sitio/uploads").file(jpegComoPng))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        // Bomba de dimensiones PNG: 65535 x 65535 en cabecera IHDR
        byte[] pngBomba = crearImagenValida("png");
        pngBomba[16] = 0; pngBomba[17] = 0; pngBomba[18] = (byte) 0xFF; pngBomba[19] = (byte) 0xFF;
        pngBomba[20] = 0; pngBomba[21] = 0; pngBomba[22] = (byte) 0xFF; pngBomba[23] = (byte) 0xFF;
        MockMultipartFile bomba = new MockMultipartFile("archivo", "bomba.png", "image/png", pngBomba);
        mockMvc.perform(multipart("/api/sitio/uploads").file(bomba))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        // WebP con carga políglota añadida al final
        byte[] webpBase = leerWebpReal();
        byte[] webpPoliglota = new byte[webpBase.length + 16];
        System.arraycopy(webpBase, 0, webpPoliglota, 0, webpBase.length);
        System.arraycopy("<script>".getBytes(StandardCharsets.US_ASCII), 0, webpPoliglota, webpBase.length, 8);
        mockMvc.perform(multipart("/api/sitio/uploads").file(
                        new MockMultipartFile("archivo", "poli.webp", "image/webp", webpPoliglota)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        // WebP con chunk JUNK embebido de longitud válida
        byte[] junkPayload = "<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII);
        byte[] webpConJunk = construirWebp(new byte[]{'J', 'U', 'N', 'K'}, junkPayload, true);
        mockMvc.perform(multipart("/api/sitio/uploads").file(
                        new MockMultipartFile("archivo", "junk.webp", "image/webp", webpConJunk)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        // WebP solo con cabecera VP8X sin flujo de píxeles VP8/VP8L
        byte[] vp8xPayload = new byte[10];
        vp8xPayload[4] = 9; // width - 1 = 9 -> 10px
        vp8xPayload[7] = 9; // height - 1 = 9 -> 10px
        byte[] webpSoloVp8x = construirWebp(new byte[]{'V', 'P', '8', 'X'}, vp8xPayload, false);
        mockMvc.perform(multipart("/api/sitio/uploads").file(
                        new MockMultipartFile("archivo", "solovp8x.webp", "image/webp", webpSoloVp8x)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        // WebP con chunk ICCP arbitrario (rechazado por MED-01)
        byte[] iccpWebp = construirWebp(new byte[]{'I', 'C', 'C', 'P'}, new byte[32], true);
        mockMvc.perform(multipart("/api/sitio/uploads").file(
                        new MockMultipartFile("archivo", "iccp.webp", "image/webp", iccpWebp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        // WebP con VP8X 10x10 ocultando frame VP8 de 4097x4097 (rechazado por MED-01)
        byte[] vp8Bomba = new byte[10];
        vp8Bomba[3] = (byte) 0x9D;
        vp8Bomba[4] = 0x01;
        vp8Bomba[5] = 0x2A;
        vp8Bomba[6] = 0x01;
        vp8Bomba[7] = 0x10; // 4097 px width
        vp8Bomba[8] = 0x01;
        vp8Bomba[9] = 0x10; // 4097 px height
        byte[] webpMaskedBomb = construirWebp(new byte[]{'V', 'P', '8', ' '}, vp8Bomba, true);
        mockMvc.perform(multipart("/api/sitio/uploads").file(
                        new MockMultipartFile("archivo", "masked-bomb.webp", "image/webp", webpMaskedBomb)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        MockMultipartFile svg = new MockMultipartFile("archivo", "icono.svg", "image/svg+xml", "<svg/>".getBytes());
        mockMvc.perform(multipart("/api/sitio/uploads").file(svg))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        MockMultipartFile vacio = new MockMultipartFile("archivo", "vacio.png", "image/png", new byte[0]);
        mockMvc.perform(multipart("/api/sitio/uploads").file(vacio))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        byte[] demasiadoGrande = new byte[(5 * 1024 * 1024) + 1];
        MockMultipartFile pesado = new MockMultipartFile("archivo", "pesado.jpg", "image/jpeg", demasiadoGrande);
        mockMvc.perform(multipart("/api/sitio/uploads").file(pesado))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        assertEquals(archivosAntes, Files.list(tempUploadDir).count(), "Ningún archivo inválido debe persistirse");
    }

    @Test
    @DisplayName("POST /api/sitio/uploads con GcsStorageService devuelve URL pública de Google Cloud Storage")
    void subir_enModoGcs_retornaUrlPublicaCdn() throws Exception {
        Storage storageMock = mock(Storage.class);
        GcsStorageService gcsService = new GcsStorageService(
                storageMock,
                "electronix-uploads-demo",
                "https://storage.googleapis.com"
        );
        MockMvc gcsMvc = MockMvcBuilders.standaloneSetup(new UploadController(gcsService)).build();

        MockMultipartFile file = new MockMultipartFile("archivo", "banner.webp", "image/webp", leerWebpReal());

        gcsMvc.perform(multipart("/api/sitio/uploads").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.url").value(
                        matchesPattern("^https://storage\\.googleapis\\.com/electronix-uploads-demo/[0-9a-f-]+\\.webp$")
                ));

        verify(storageMock).createFrom(any(), any(InputStream.class), any());
    }

    @Test
    @DisplayName("WebMvcConfig registra ubicación terminada en '/' aunque uploadDir no exista aún y luego sirve el archivo")
    void webMvcConfig_directorioInexistenteAlInicio_sirveArchivoLuego(@TempDir Path baseDir) throws Exception {
        Path dirInexistente = baseDir.resolve("aun-no-creado");
        assertFalse(Files.exists(dirInexistente));

        WebMvcConfig config = new WebMvcConfig();
        ReflectionTestUtils.setField(config, "uploadDir", dirInexistente.toString());
        var appCtx = new org.springframework.context.support.StaticApplicationContext();
        appCtx.refresh();
        var servletCtx = new org.springframework.mock.web.MockServletContext();
        var registry = new org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry(appCtx, servletCtx);
        config.addResourceHandlers(registry);

        Object mapping = ReflectionTestUtils.invokeMethod(registry, "getHandlerMapping");
        assertNotNull(mapping);
        @SuppressWarnings("unchecked")
        var urlMap = (java.util.Map<String, Object>) ReflectionTestUtils.invokeMethod(mapping, "getUrlMap");
        var handler = (org.springframework.web.servlet.resource.ResourceHttpRequestHandler) urlMap.get("/uploads/**");
        assertNotNull(handler);
        assertTrue(handler.getLocations().get(0).getURI().toString().endsWith("/"),
                "La ubicación registrada debe terminar en '/' incluso si el directorio no existe al arrancar");

        LocalStorageService local = new LocalStorageService(dirInexistente.toString());
        byte[] png = crearImagenValida("png");
        local.guardar("prueba.png", new ByteArrayInputStream(png), "image/png");
        assertTrue(handler.getLocations().get(0).createRelative("prueba.png").exists());

        Files.copy(new ByteArrayInputStream(png), tempUploadDir.resolve("mvc-check.png"));
        mockMvc.perform(get("/uploads/mvc-check.png"))
                .andExpect(status().isOk());
    }
}
