package com.tienda.ecommerce.sitio;

import com.tienda.ecommerce.auth.JwtService;
import com.tienda.ecommerce.usuario.Rol;
import com.tienda.ecommerce.usuario.TipoDocumento;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UploadMultipartServletIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> tempDir.resolve("deferred-uploads").toString());
        registry.add("app.storage.type", () -> "local");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    private String obtenerTokenAdmin() {
        String email = "upload-admin@tienda.com";
        Usuario admin = usuarioRepository.findByEmail(email).orElseGet(() -> {
            Usuario u = new Usuario(
                    null, "Admin", "Uploads", null,
                    TipoDocumento.DNI, "91234567",
                    email, "hash-no-usado", Rol.ADMIN, true
            );
            return usuarioRepository.save(u);
        });
        return jwtService.generarToken(admin);
    }

    private static HttpEntity<MultiValueMap<String, Object>> crearMultipart(String token, String field, byte[] bytes, String filename, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        if (bytes != null) {
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(contentType);
            body.add(field, new HttpEntity<>(resource, partHeaders));
        } else {
            body.add(field, "valor");
        }
        return new HttpEntity<>(body, headers);
    }

    @Test
    @DisplayName("Servlet real: archivo > 5MB retorna 413 JSON (nunca 500) y parte faltante retorna 400 JSON")
    void multipartLimitesYErrores_retornanCodigosJsonEsperados() throws Exception {
        String token = obtenerTokenAdmin();

        // 1. Archivo > 5MB dispara MaxUploadSizeExceededException en Tomcat -> 413 Payload Too Large JSON
        byte[] pesado = new byte[(5 * 1024 * 1024) + (256 * 1024)];
        ResponseEntity<Map> res413 = restTemplate.postForEntity(
                "/api/sitio/uploads",
                crearMultipart(token, "archivo", pesado, "grande.png", MediaType.IMAGE_PNG),
                Map.class
        );
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, res413.getStatusCode());
        assertNotNull(res413.getBody());
        assertEquals(false, res413.getBody().get("exito"));

        // 2. Multipart sin parte 'archivo' -> 400 Bad Request JSON (nunca 500)
        ResponseEntity<Map> res400 = restTemplate.postForEntity(
                "/api/sitio/uploads",
                crearMultipart(token, "otroCampo", null, null, MediaType.TEXT_PLAIN),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res400.getStatusCode());
        assertNotNull(res400.getBody());
        assertEquals(false, res400.getBody().get("exito"));

        // 3. PNG real vía HTTP completo -> 200 OK y accesible por GET /uploads/...
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, Color.RED.getRGB());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, "png", baos));

        ResponseEntity<Map> res200 = restTemplate.postForEntity(
                "/api/sitio/uploads",
                crearMultipart(token, "archivo", baos.toByteArray(), "valido.png", MediaType.IMAGE_PNG),
                Map.class
        );
        assertEquals(HttpStatus.OK, res200.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> datos = (Map<String, Object>) res200.getBody().get("datos");
        String url = String.valueOf(datos.get("url"));
        assertTrue(url.startsWith("/uploads/"));

        ResponseEntity<byte[]> getRes = restTemplate.getForEntity(url, byte[].class);
        assertEquals(HttpStatus.OK, getRes.getStatusCode());
    }
}
