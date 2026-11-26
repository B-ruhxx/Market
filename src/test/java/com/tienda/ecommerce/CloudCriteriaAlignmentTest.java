package com.tienda.ecommerce;

import com.tienda.ecommerce.auth.AuthServiceImpl;
import com.tienda.ecommerce.auth.JwtService;
import com.tienda.ecommerce.auth.LoginAttemptService;
import com.tienda.ecommerce.auth.dto.LoginRequest;
import com.tienda.ecommerce.carrito.CarritoService;
import com.tienda.ecommerce.chat.ChatAuthChannelInterceptor;
import com.tienda.ecommerce.chat.ConversacionChatRepository;
import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.common.CredencialesInvalidasException;
import com.tienda.ecommerce.config.CacheConfig;
import com.tienda.ecommerce.config.CorsOriginValidator;
import com.tienda.ecommerce.config.JwtProperties;
import com.tienda.ecommerce.configuracion.AccesoService;
import com.tienda.ecommerce.common.TooManyRequestsException;
import com.tienda.ecommerce.crm.CrmServiceImpl;
import com.tienda.ecommerce.pedido.Pedido;
import com.tienda.ecommerce.usuario.Rol;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudCriteriaAlignmentTest {

    @Test
    void authService_usuarioInactivoConClaveIncorrecta_registraFalloYNoRevelaEstado() {
        UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
        CarritoService carritoService = mock(CarritoService.class);
        AccesoService accesoService = mock(AccesoService.class);
        LoginAttemptService loginAttemptService = spy(new LoginAttemptService());
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        JwtService jwtService = new JwtService(jwtProps);

        Usuario inactivo = new Usuario(10L, "Cliente Inactivo", "inactivo@electronix.pe",
                encoder.encode("ClaveCorrecta#1"), Rol.CLIENTE, false);
        when(usuarioRepo.findByEmail("inactivo@electronix.pe")).thenReturn(Optional.of(inactivo));

        AuthServiceImpl authService = new AuthServiceImpl(
                usuarioRepo, carritoService, encoder, jwtService, accesoService, loginAttemptService, null);

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest("inactivo@electronix.pe", "ClaveIncorrecta")));

        assertTrue(ex.getMessage().contains("Credenciales incorrectas"));
        verify(loginAttemptService, times(1)).registrarFallo("inactivo@electronix.pe");
    }

    @Test
    void authService_usuarioInactivoConClaveCorrecta_mantieneMensajeInactiva() {
        UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
        CarritoService carritoService = mock(CarritoService.class);
        AccesoService accesoService = mock(AccesoService.class);
        LoginAttemptService loginAttemptService = new LoginAttemptService();
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        JwtService jwtService = new JwtService(jwtProps);

        Usuario inactivo = new Usuario(11L, "Cliente Inactivo", "inactivo2@electronix.pe",
                encoder.encode("ClaveCorrecta#1"), Rol.CLIENTE, false);
        when(usuarioRepo.findByEmail("inactivo2@electronix.pe")).thenReturn(Optional.of(inactivo));

        AuthServiceImpl authService = new AuthServiceImpl(
                usuarioRepo, carritoService, encoder, jwtService, accesoService, loginAttemptService, null);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.login(new LoginRequest("inactivo2@electronix.pe", "ClaveCorrecta#1")));

        assertTrue(ex.getMessage().contains("inactiva"));
    }

    @Test
    void chatInterceptor_revocaFrameSinHeaderDespuesDeCambioDePassword() {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        JwtService jwtService = new JwtService(jwtProps);

        Usuario usuario = new Usuario(5L, "Cliente Chat", "chat@electronix.pe", "hash", Rol.CLIENTE, true);
        String token = jwtService.generarToken(usuario);

        UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
        AccesoService accesoService = mock(AccesoService.class);
        ConversacionChatRepository conversacionRepo = mock(ConversacionChatRepository.class);
        when(usuarioRepo.findByEmail("chat@electronix.pe")).thenReturn(Optional.of(usuario));
        when(accesoService.rolActivo(usuario)).thenReturn(true);

        ChatAuthChannelInterceptor interceptor =
                new ChatAuthChannelInterceptor(jwtService, usuarioRepo, accesoService, conversacionRepo);
        MessageChannel channel = mock(MessageChannel.class);
        Map<String, Object> sessionAttributes = new HashMap<>();

        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setLeaveMutable(true);
        connectAccessor.setSessionAttributes(sessionAttributes);
        connectAccessor.setNativeHeader("Authorization", "Bearer " + token);
        Message<byte[]> connectMsg = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        interceptor.preSend(connectMsg, channel);
        assertNotNull(sessionAttributes.get("jwtIatMs"));

        // Cambio de contraseña posterior a CONNECT invalida frames siguientes aun sin reenviar Authorization
        usuario.setPasswordModificadoEn(LocalDateTime.now().plusSeconds(5));

        StompHeaderAccessor sendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
        sendAccessor.setLeaveMutable(true);
        sendAccessor.setSessionAttributes(sessionAttributes);
        sendAccessor.setUser(connectAccessor.getUser());
        sendAccessor.setDestination("/app/chat/enviar");
        Message<byte[]> sendMsg = MessageBuilder.createMessage("{}".getBytes(), sendAccessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(sendMsg, channel));
        assertTrue(ex.getMessage().contains("Sesión revocada"));
    }

    @Test
    void infra_monitoringTlsYDemoC4AlineadosConRubrica() throws Exception {
        String monitoringTf = Files.readString(Path.of("terraform/monitoring.tf"));
        assertTrue(monitoringTf.contains("google_monitoring_uptime_check_config"));
        assertTrue(monitoringTf.contains("google_monitoring_alert_policy"));
        assertTrue(monitoringTf.contains("/actuator/health"));
        assertFalse(monitoringTf.contains("cloud-platform"));

        String tlsCompose = Files.readString(Path.of("docker-compose.tls.yml"));
        assertTrue(tlsCompose.contains("caddy:"));
        assertTrue(tlsCompose.contains("443:443"));
        assertTrue(tlsCompose.contains("PUBLIC_DOMAIN"));

        String caddyfile = Files.readString(Path.of("nginx/Caddyfile"));
        assertTrue(caddyfile.contains("Strict-Transport-Security"));
        assertTrue(caddyfile.contains("reverse_proxy ecommerce-nginx:80"));

        Path demoScript = Path.of("scripts/demo-c4-pruebas.sh");
        assertTrue(Files.exists(demoScript));
        assertTrue(Files.isExecutable(demoScript));
        String demoContent = Files.readString(demoScript);
        assertTrue(demoContent.contains("P1."));
        assertTrue(demoContent.contains("P2."));
        assertTrue(demoContent.contains("P3."));
        assertTrue(demoContent.contains("P4."));
    }

    @Test
    void cacheConfig_afterCommitConRedisCaido_degradaSinLanzarExcepcionYRegistraMetrica() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CacheConfig cacheConfig = new CacheConfig(registry);
        RedisConnectionFactory failingFactory = mock(RedisConnectionFactory.class);
        when(failingFactory.getConnection()).thenThrow(new RedisConnectionFailureException("Redis down"));

        CacheManager manager = cacheConfig.cacheManager(failingFactory);
        Cache cache = manager.getCache(CacheConfig.CACHE_PRODUCTOS);
        assertNotNull(cache);

        AbstractPlatformTransactionManager txManager = new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };

        assertDoesNotThrow(() -> new TransactionTemplate(txManager).executeWithoutResult(status -> {
            assertNull(cache.get(1L));
            cache.evict(1L);
            cache.clear();
        }));

        assertTrue(registry.counter("electronix.cache.evict.errors").count() >= 2.0,
                "afterCommit con Redis caído debe registrar métrica y no abortar el commit");
    }

    @Test
    void chatInterceptor_rechazaSendATopicYTokenAjenoYExpirado() {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        JwtService jwtService = new JwtService(jwtProps);

        Usuario userA = new Usuario(5L, "Cliente A", "a@electronix.pe", "hash", Rol.CLIENTE, true);
        Usuario userB = new Usuario(6L, "Cliente B", "b@electronix.pe", "hash", Rol.CLIENTE, true);
        String tokenA = jwtService.generarToken(userA);
        String tokenB = jwtService.generarToken(userB);

        UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
        AccesoService accesoService = mock(AccesoService.class);
        ConversacionChatRepository conversacionRepo = mock(ConversacionChatRepository.class);
        when(usuarioRepo.findByEmail("a@electronix.pe")).thenReturn(Optional.of(userA));
        when(accesoService.rolActivo(userA)).thenReturn(true);

        ChatAuthChannelInterceptor interceptor =
                new ChatAuthChannelInterceptor(jwtService, usuarioRepo, accesoService, conversacionRepo);
        MessageChannel channel = mock(MessageChannel.class);
        Map<String, Object> sessionAttributes = new HashMap<>();

        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setLeaveMutable(true);
        connectAccessor.setSessionAttributes(sessionAttributes);
        connectAccessor.setNativeHeader("Authorization", "Bearer " + tokenA);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders()), channel);

        // 1. SEND directo a /topic/chat/... debe ser bloqueado (HIGH-03)
        StompHeaderAccessor sendTopic = StompHeaderAccessor.create(StompCommand.SEND);
        sendTopic.setLeaveMutable(true);
        sendTopic.setSessionAttributes(sessionAttributes);
        sendTopic.setUser(connectAccessor.getUser());
        sendTopic.setDestination("/topic/chat/1");
        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.createMessage("{}".getBytes(), sendTopic.getMessageHeaders()), channel));

        // 2. SEND en sesión de A usando token fresco de B en header debe ser rechazado (HIGH-04)
        userA.setPasswordModificadoEn(LocalDateTime.now().plusSeconds(5));
        StompHeaderAccessor sendForeignJwt = StompHeaderAccessor.create(StompCommand.SEND);
        sendForeignJwt.setLeaveMutable(true);
        sendForeignJwt.setSessionAttributes(sessionAttributes);
        sendForeignJwt.setUser(connectAccessor.getUser());
        sendForeignJwt.setDestination("/app/chat.enviar");
        sendForeignJwt.setNativeHeader("Authorization", "Bearer " + tokenB);
        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.createMessage("{}".getBytes(), sendForeignJwt.getMessageHeaders()), channel));

        // 3. Sesión expirada después de CONNECT debe ser rechazada en frames siguientes (HIGH-04)
        userA.setPasswordModificadoEn(null);
        sessionAttributes.put("jwtExpMs", System.currentTimeMillis() - 5000);
        StompHeaderAccessor sendExpired = StompHeaderAccessor.create(StompCommand.SEND);
        sendExpired.setLeaveMutable(true);
        sendExpired.setSessionAttributes(sessionAttributes);
        sendExpired.setUser(connectAccessor.getUser());
        sendExpired.setDestination("/app/chat.enviar");
        AccessDeniedException expEx = assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.createMessage("{}".getBytes(), sendExpired.getMessageHeaders()), channel));
        assertTrue(expEx.getMessage().contains("expirada"));
    }

    @Test
    void crmService_puntosUsanRequiredParaEvitarDeadlockConCheckout() throws Exception {
        Transactional acreditarTx = CrmServiceImpl.class
                .getMethod("acreditarPuntosPorCompra", Pedido.class)
                .getAnnotation(Transactional.class);
        Transactional revertirTx = CrmServiceImpl.class
                .getMethod("revertirPuntos", Long.class)
                .getAnnotation(Transactional.class);

        assertNotNull(acreditarTx);
        assertNotNull(revertirTx);
        assertEquals(Propagation.REQUIRED, acreditarTx.propagation());
        assertEquals(Propagation.REQUIRED, revertirTx.propagation());
    }

    @Test
    void loginAttemptService_preservaBloqueosActivosSiMapaLocalSeLlena() {
        LoginAttemptService limiter = new LoginAttemptService();
        for (int i = 0; i < 5; i++) {
            limiter.registrarFallo("objetivo-bloqueado@electronix.pe");
        }
        assertThrows(TooManyRequestsException.class, () -> limiter.verificarBloqueo("objetivo-bloqueado@electronix.pe"));

        for (int i = 0; i < 10_050; i++) {
            limiter.registrarFallo("ruido-" + i + "@electronix.pe");
        }
        assertThrows(TooManyRequestsException.class, () -> limiter.verificarBloqueo("objetivo-bloqueado@electronix.pe"),
                "Inundar con claves falsas no debe desalojar bloqueos activos");
    }

    @Test
    void authService_usuarioDesconocidoEjecutaPasswordEncoderParaMitigarTiming() {
        UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
        when(usuarioRepo.findByEmail("noexiste@electronix.pe")).thenReturn(Optional.empty());
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches(anyString(), anyString())).thenReturn(false);

        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        AuthServiceImpl authService = new AuthServiceImpl(
                usuarioRepo, mock(CarritoService.class), encoder, new JwtService(jwtProps),
                mock(AccesoService.class), new LoginAttemptService(), null);

        assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest("noexiste@electronix.pe", "Cualquiera123")));
        verify(encoder, times(1)).matches(eq("Cualquiera123"), anyString());
    }

    @Test
    void corsValidator_rechazaComodinesParcialesUserinfoYSufijosPublicos() {
        assertThrows(IllegalStateException.class, () -> CorsOriginValidator.parseAndValidate(new String[]{"https://*evil.com"}));
        assertThrows(IllegalStateException.class, () -> CorsOriginValidator.parseAndValidate(new String[]{"https://a.*"}));
        assertThrows(IllegalStateException.class, () -> CorsOriginValidator.parseAndValidate(new String[]{"https://evil.*.com"}));
        assertThrows(IllegalStateException.class, () -> CorsOriginValidator.parseAndValidate(new String[]{"https://*.co.uk"}));
        assertThrows(IllegalStateException.class, () -> CorsOriginValidator.parseAndValidate(new String[]{"https://*.com.pe"}));
        assertThrows(IllegalStateException.class, () -> CorsOriginValidator.parseAndValidate(new String[]{"https://user:pass@example.com"}));

        List<String> validos = CorsOriginValidator.parseAndValidate(new String[]{"https://*.trycloudflare.com", "https://tienda.electronix.pe"});
        assertEquals(2, validos.size());
    }

    @Test
    void jwtService_fallaAlArrancarSiSecretoVacioOCorto() {
        JwtProperties vacio = new JwtProperties();
        assertThrows(IllegalStateException.class, () -> new JwtService(vacio).validarConfiguracion());

        JwtProperties cortoBase64 = new JwtProperties();
        cortoBase64.setSecret("c2hvcnQ="); // "short" -> 5 bytes < 32
        assertThrows(IllegalStateException.class, () -> new JwtService(cortoBase64).validarConfiguracion());
    }
}
