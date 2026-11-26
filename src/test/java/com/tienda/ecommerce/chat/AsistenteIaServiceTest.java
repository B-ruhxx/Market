package com.tienda.ecommerce.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.ecommerce.auth.AuthServiceImpl;
import com.tienda.ecommerce.auth.JwtService;
import com.tienda.ecommerce.auth.LoginAttemptService;
import com.tienda.ecommerce.auth.dto.LoginRequest;
import com.tienda.ecommerce.auth.dto.RegistroRequest;
import com.tienda.ecommerce.carrito.CarritoService;
import com.tienda.ecommerce.chat.dto.ConversacionResponse;
import com.tienda.ecommerce.chat.dto.MensajeChatRequest;
import com.tienda.ecommerce.chat.dto.MensajeChatResponse;
import com.tienda.ecommerce.chat.ia.AsistenteHerramientasService;
import com.tienda.ecommerce.chat.ia.AsistenteIaService;
import com.tienda.ecommerce.chat.ia.GeminiClient;
import com.tienda.ecommerce.chat.ia.GeminiProperties;
import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.common.StockInsuficienteException;
import com.tienda.ecommerce.config.JwtProperties;
import com.tienda.ecommerce.configuracion.AccesoService;
import com.tienda.ecommerce.configuracion.RolConfigRepository;
import com.tienda.ecommerce.crm.CrmService;
import com.tienda.ecommerce.crm.CuponUsoRepository;
import com.tienda.ecommerce.crm.PromocionCupon;
import com.tienda.ecommerce.crm.PromocionCuponRepository;
import com.tienda.ecommerce.crm.TipoDescuento;
import com.tienda.ecommerce.crm.dto.BilleteraResponse;
import com.tienda.ecommerce.pedido.PedidoRepository;
import com.tienda.ecommerce.producto.Categoria;
import com.tienda.ecommerce.producto.CategoriaRepository;
import com.tienda.ecommerce.producto.Producto;
import com.tienda.ecommerce.producto.ProductoRepository;
import com.tienda.ecommerce.usuario.Rol;
import com.tienda.ecommerce.usuario.TipoDocumento;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import com.tienda.ecommerce.usuario.UsuarioServiceImpl;
import com.tienda.ecommerce.usuario.dto.CrearUsuarioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import(ChatServiceImpl.class)
class AsistenteIaServiceTest {

    @Autowired private ConversacionChatRepository conversacionRepo;
    @Autowired private ChatMensajeRepository mensajeRepo;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private ProductoRepository productoRepo;
    @Autowired private CategoriaRepository categoriaRepo;
    @Autowired private PedidoRepository pedidoRepo;
    @Autowired private PromocionCuponRepository cuponRepo;
    @Autowired private RolConfigRepository rolConfigRepo;
    @Autowired private ChatService chatService;

    private Usuario cliente;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        cliente = usuarioRepo.findByEmail("ana.chat@test.com").orElseGet(() ->
                usuarioRepo.save(new Usuario(null, "Ana", "Cliente", null,
                        TipoDocumento.DNI, "11223344", "ana.chat@test.com", "hash", Rol.CLIENTE, true)));
        admin = usuarioRepo.findByEmail("luis.admin@test.com").orElseGet(() ->
                usuarioRepo.save(new Usuario(null, "Luis", "Soporte", null,
                        TipoDocumento.DNI, "99887766", "luis.admin@test.com", "hash", Rol.ADMIN, true)));
    }

    @Test
    @DisplayName("Nueva conversación inicia en modo BOT, admin toma control y bloquea salas cerradas")
    void flujoHibrido_modoInicialBot_yTomaControlAdmin() {
        ConversacionResponse conv = chatService.obtenerOCrearConversacionCliente(cliente.getId());
        assertEquals(ConversacionChat.MODO_BOT, conv.getModoAtencion());

        chatService.procesarYGuardarMensaje(new MensajeChatRequest(conv.getId(), cliente.getId(), "Hola"), cliente.getId(), false);
        assertEquals(ConversacionChat.MODO_BOT, chatService.obtenerModoAtencion(conv.getId()));

        // Cliente no puede forzar HUMANO_ACTIVO directamente
        assertThrows(AccessDeniedException.class, () ->
                chatService.cambiarModoAtencion(conv.getId(), ConversacionChat.MODO_HUMANO_ACTIVO, cliente.getId(), false));

        // Admin responde -> cambia automáticamente a HUMANO_ACTIVO
        chatService.procesarYGuardarMensaje(new MensajeChatRequest(conv.getId(), admin.getId(), "Hola Ana, te atiendo"), admin.getId(), true);
        assertEquals(ConversacionChat.MODO_HUMANO_ACTIVO, chatService.obtenerModoAtencion(conv.getId()));

        // Cliente NO puede revertir HUMANO_ACTIVO -> BOT
        assertThrows(AccessDeniedException.class, () ->
                chatService.cambiarModoAtencion(conv.getId(), ConversacionChat.MODO_BOT, cliente.getId(), false));

        // Admin devuelve la conversación al bot
        ConversacionResponse devuelta = chatService.cambiarModoAtencion(conv.getId(), ConversacionChat.MODO_BOT, admin.getId(), true);
        assertEquals(ConversacionChat.MODO_BOT, devuelta.getModoAtencion());
        assertNull(devuelta.getAdminId());

        // Sala cerrada rechaza nuevos mensajes y cambios de modo
        ConversacionChat entity = conversacionRepo.findById(conv.getId()).orElseThrow();
        entity.setEstado("CERRADA");
        conversacionRepo.saveAndFlush(entity);

        assertThrows(BadRequestException.class, () ->
                chatService.procesarYGuardarMensaje(new MensajeChatRequest(conv.getId(), cliente.getId(), "Mensaje tarde"), cliente.getId(), false));
        assertThrows(BadRequestException.class, () ->
                chatService.cambiarModoAtencion(conv.getId(), ConversacionChat.MODO_ESPERANDO_HUMANO, cliente.getId(), false));
    }

    @Test
    @DisplayName("AsistenteIaService ejecuta function calling buscar_productos y guarda respuesta del bot inactiva")
    void asistenteIa_ejecutaFunctionCallingYResponde() {
        Categoria cat = categoriaRepo.save(new Categoria(null, "Laptops", "Portátiles"));
        Producto laptop = productoRepo.save(new Producto(null, "Laptop Pro 14", "16GB RAM 512GB SSD",
                new BigDecimal("3500.00"), 6, null, cat, true));

        ConversacionResponse conv = chatService.obtenerOCrearConversacionCliente(cliente.getId());
        chatService.procesarYGuardarMensaje(new MensajeChatRequest(conv.getId(), cliente.getId(), "Busco una laptop"), cliente.getId(), false);

        GeminiProperties props = new GeminiProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");

        GeminiClient mockGemini = mock(GeminiClient.class);
        CarritoService mockCarrito = mock(CarritoService.class);
        CrmService mockCrm = mock(CrmService.class);
        SimpMessagingTemplate mockMessaging = mock(SimpMessagingTemplate.class);

        AsistenteHerramientasService herramientas = new AsistenteHerramientasService(
                productoRepo, categoriaRepo, pedidoRepo, mockCarrito, mockCrm, cuponRepo, chatService
        );
        ReflectionTestUtils.setField(herramientas, "messagingTemplate", mockMessaging);

        Map<String, Object> respFunctionCall = Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "role", "model",
                                "parts", List.of(Map.of(
                                        "functionCall", Map.of(
                                                "name", "buscar_productos",
                                                "args", Map.of("termino", "Laptop")
                                        )
                                ))
                        )
                ))
        );
        Map<String, Object> respTextoFinal = Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "role", "model",
                                "parts", List.of(Map.of(
                                        "text", "Te recomiendo **Laptop Pro 14** a S/ 3500.00 (/producto.html?id=" + laptop.getId() + ")."
                                ))
                        )
                ))
        );

        when(mockGemini.generarContenido(anyList(), anyList(), anyString()))
                .thenReturn(respFunctionCall)
                .thenReturn(respTextoFinal);

        AsistenteIaService iaService = new AsistenteIaService(props, mockGemini, herramientas, chatService, mockMessaging);
        iaService.procesarMensajeClienteAsync(conv.getId(), cliente.getId());

        List<MensajeChatResponse> historial = chatService.obtenerHistorial(conv.getId(), cliente.getId(), false);
        assertEquals(2, historial.size());
        MensajeChatResponse msgBot = historial.get(1);
        assertEquals("ASISTENTE_IA", msgBot.getRemitenteRol().name());
        assertTrue(msgBot.getContenido().contains("Laptop Pro 14"));

        // La cuenta virtual del bot debe permanecer deshabilitada y con documento reservado
        Usuario botUser = usuarioRepo.findByEmail(ChatServiceImpl.BOT_EMAIL).orElseThrow();
        assertFalse(botUser.isActivo());
        assertEquals(ChatServiceImpl.BOT_DOCUMENTO, botUser.getNumeroDocumento());

        verify(mockMessaging).convertAndSend(eq("/topic/chat/" + conv.getId()),
                argThat((Object arg) -> arg instanceof MensajeChatResponse r
                        && msgBot.getId().equals(r.getId())
                        && msgBot.getContenido().equals(r.getContenido())));
        verify(mockMessaging, atLeastOnce()).convertAndSend(eq("/topic/chat/" + conv.getId() + "/estado"), anyMap());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Toma de control humano en vuelo impide sobrescritura de modo o mensajes tardíos de IA")
    void asistenteIa_tomaControlHumanoEnVuelo_noSobrescribeNiResponde() throws Exception {
        ConversacionResponse conv = chatService.obtenerOCrearConversacionCliente(cliente.getId());
        chatService.procesarYGuardarMensaje(new MensajeChatRequest(conv.getId(), cliente.getId(), "Necesito ayuda"), cliente.getId(), false);

        GeminiProperties props = new GeminiProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");

        GeminiClient mockGemini = mock(GeminiClient.class);
        SimpMessagingTemplate mockMessaging = mock(SimpMessagingTemplate.class);
        AsistenteHerramientasService herramientas = new AsistenteHerramientasService(
                productoRepo, categoriaRepo, pedidoRepo, mock(CarritoService.class), mock(CrmService.class), cuponRepo, chatService
        );
        ReflectionTestUtils.setField(herramientas, "messagingTemplate", mockMessaging);

        CountDownLatch geminiEntered = new CountDownLatch(1);
        CountDownLatch releaseGemini = new CountDownLatch(1);

        when(mockGemini.generarContenido(anyList(), anyList(), anyString())).thenAnswer(inv -> {
            geminiEntered.countDown();
            assertTrue(releaseGemini.await(5, TimeUnit.SECONDS));
            return Map.of("candidates", List.of(Map.of("content", Map.of(
                    "role", "model",
                    "parts", List.of(Map.of(
                            "functionCall", Map.of(
                                    "name", "solicitar_asesor_humano",
                                    "args", Map.of("motivo", "tardío")
                            )
                    ))
            ))));
        });

        AsistenteIaService iaService = new AsistenteIaService(props, mockGemini, herramientas, chatService, mockMessaging);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> iaService.procesarMensajeClienteAsync(conv.getId(), cliente.getId()));
            assertTrue(geminiEntered.await(5, TimeUnit.SECONDS));

            // Admin toma control mientras Gemini aún está procesando
            chatService.procesarYGuardarMensaje(
                    new MensajeChatRequest(conv.getId(), admin.getId(), "Ya estoy aquí para ayudarte"),
                    admin.getId(), true
            );
            assertEquals(ConversacionChat.MODO_HUMANO_ACTIVO, chatService.obtenerModoAtencion(conv.getId()));

            releaseGemini.countDown();
            future.get(5, TimeUnit.SECONDS);

            // El modo sigue HUMANO_ACTIVO y no se guardó ni difundió mensaje del bot
            assertEquals(ConversacionChat.MODO_HUMANO_ACTIVO, chatService.obtenerModoAtencion(conv.getId()));
            List<MensajeChatResponse> historial = chatService.obtenerHistorial(conv.getId(), admin.getId(), true);
            assertEquals(2, historial.size());
            assertTrue(historial.stream().noneMatch(m -> "ASISTENTE_IA".equals(m.getRemitenteRol().name())));
            verify(mockMessaging, never()).convertAndSend(eq("/topic/admin/chat-alerts"), anyMap());
        } finally {
            executor.shutdownNow();
            // Limpiar filas comprometidas fuera de transacción para aislar otros tests
            conversacionRepo.deleteById(conv.getId());
            usuarioRepo.deleteById(cliente.getId());
            usuarioRepo.deleteById(admin.getId());
        }
    }

    @Test
    @DisplayName("Herramientas IA verifican permisos, manejan stock sin rollback externo y filtran cupones")
    void herramientas_validanPermisosErroresYCupones() {
        AccesoService mockAcceso = mock(AccesoService.class);
        CarritoService mockCarrito = mock(CarritoService.class);
        CrmService mockCrm = mock(CrmService.class);
        CuponUsoRepository mockCuponUso = mock(CuponUsoRepository.class);

        when(mockAcceso.tiene(cliente, "COMPRAR")).thenReturn(false);
        when(mockAcceso.tiene(cliente, "CRM_VER_PUNTOS")).thenReturn(true);

        AsistenteHerramientasService herramientas = new AsistenteHerramientasService(
                productoRepo, categoriaRepo, pedidoRepo, mockCarrito, mockCrm, cuponRepo, chatService
        );
        ReflectionTestUtils.setField(herramientas, "usuarioRepository", usuarioRepo);
        ReflectionTestUtils.setField(herramientas, "accesoService", mockAcceso);
        ReflectionTestUtils.setField(herramientas, "cuponUsoRepository", mockCuponUso);

        // Sin permiso COMPRAR no invoca CarritoService
        Map<String, Object> sinPermiso = herramientas.ejecutarTool("agregar_al_carrito", Map.of("producto_id", 1, "cantidad", 1), 1L, cliente.getId());
        assertEquals(false, sinPermiso.get("exito"));
        verifyNoInteractions(mockCarrito);

        // Con permiso COMPRAR pero stock insuficiente devuelve fallo limpio
        when(mockAcceso.tiene(cliente, "COMPRAR")).thenReturn(true);
        when(mockCarrito.agregarItem(eq(cliente.getId()), any())).thenThrow(new StockInsuficienteException("Stock insuficiente"));
        Map<String, Object> sinStock = herramientas.ejecutarTool("agregar_al_carrito", Map.of("producto_id", 1, "cantidad", 2), 1L, cliente.getId());
        assertEquals(false, sinStock.get("exito"));
        assertTrue(String.valueOf(sinStock.get("mensaje")).contains("Stock insuficiente"));

        // Filtrado de cupones: excluye ENVIO_GRATIS y agotados
        PromocionCupon publico = new PromocionCupon();
        publico.setCodigo("TECH10");
        publico.setTipoDescuento(TipoDescuento.PORCENTAJE);
        publico.setValor(new BigDecimal("10"));
        publico.setLimiteUsosTotal(100);
        publico.setFechaInicio(LocalDateTime.now().minusDays(1));
        publico.setFechaFin(LocalDateTime.now().plusDays(10));
        cuponRepo.save(publico);

        PromocionCupon envioGratis = new PromocionCupon();
        envioGratis.setCodigo("FREESHIP");
        envioGratis.setTipoDescuento(TipoDescuento.ENVIO_GRATIS);
        envioGratis.setValor(new BigDecimal("1"));
        envioGratis.setLimiteUsosTotal(100);
        envioGratis.setFechaInicio(LocalDateTime.now().minusDays(1));
        envioGratis.setFechaFin(LocalDateTime.now().plusDays(10));
        cuponRepo.save(envioGratis);

        BilleteraResponse bill = new BilleteraResponse();
        bill.setSaldoPuntos(150);
        when(mockCrm.obtenerBilleteraUsuario(cliente.getId())).thenReturn(bill);

        Map<String, Object> resCupones = herramientas.ejecutarTool("consultar_mis_puntos_y_cupones", Map.of(), 1L, cliente.getId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = (List<Map<String, Object>>) resCupones.get("cuponesVigentes");
        assertEquals(1, lista.size());
        assertEquals("TECH10", lista.get(0).get("codigo"));
    }

    @Test
    @DisplayName("Seguridad cuenta bot: bloquea registro 00000000/SYSBOT001, login y asignación de rol ASISTENTE_IA")
    void seguridadBot_bloqueaRegistroLoginYEdicion() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AccesoService mockAcceso = mock(AccesoService.class);
        when(mockAcceso.rolActivo(any())).thenReturn(true);

        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        JwtService jwtService = new JwtService(jwtProps);

        AuthServiceImpl authService = new AuthServiceImpl(
                usuarioRepo, mock(CarritoService.class), encoder, jwtService, mockAcceso, new LoginAttemptService(), null
        );
        UsuarioServiceImpl usuarioService = new UsuarioServiceImpl(usuarioRepo, encoder, mockAcceso);

        // Self-registration con DNI 00000000 o correo bot rechazado
        RegistroRequest regReservado = new RegistroRequest("Test", "Uno", null, TipoDocumento.DNI, "00000000", "u@test.com", "Pass12345!");
        assertThrows(BadRequestException.class, () -> authService.registro(regReservado));

        RegistroRequest regBotEmail = new RegistroRequest("Test", "Uno", null, TipoDocumento.DNI, "12345678", ChatServiceImpl.BOT_EMAIL, "Pass12345!");
        assertThrows(BadRequestException.class, () -> authService.registro(regBotEmail));

        // Forzar creación del bot
        ConversacionResponse conv = chatService.obtenerOCrearConversacionCliente(cliente.getId());
        chatService.guardarMensajeBot(conv.getId(), "Hola");
        Usuario bot = usuarioRepo.findByEmail(ChatServiceImpl.BOT_EMAIL).orElseThrow();
        bot.setPasswordHash(encoder.encode("Clave1234!"));
        usuarioRepo.saveAndFlush(bot);

        // Login del bot bloqueado
        assertThrows(BadRequestException.class, () ->
                authService.login(new LoginRequest(ChatServiceImpl.BOT_EMAIL, "Clave1234!")));

        // UsuarioServiceImpl bloquea crear/cambiar rol/eliminar/editar bot
        CrearUsuarioRequest crearBotRol = new CrearUsuarioRequest();
        crearBotRol.setNombres("Usuario");
        crearBotRol.setApellidoPaterno("Prueba");
        crearBotRol.setTipoDocumento(TipoDocumento.DNI);
        crearBotRol.setNumeroDocumento("22334455");
        crearBotRol.setEmail("x@test.com");
        crearBotRol.setPassword("Clave1234!");
        crearBotRol.setRol(Rol.valueOf("ASISTENTE_IA"));
        assertThrows(BadRequestException.class, () -> usuarioService.crear(crearBotRol));
        assertThrows(BadRequestException.class, () -> usuarioService.cambiarRol(cliente.getId(), Rol.valueOf("ASISTENTE_IA")));
        assertThrows(BadRequestException.class, () -> usuarioService.eliminar(bot.getId()));
    }

    @Test
    @DisplayName("ChatAuthChannelInterceptor exige JWT STOMP en CONNECT, protege alertas admin y propaga eventos Redis")
    void interceptorYPubSub_seguridadYFanout() throws Exception {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        JwtService jwtService = new JwtService(jwtProps);

        AccesoService acceso = mock(AccesoService.class);
        when(acceso.rolActivo(cliente)).thenReturn(true);
        when(acceso.rolActivo(admin)).thenReturn(true);
        when(acceso.tiene(cliente, "CHAT_ATENDER")).thenReturn(false);
        when(acceso.tiene(admin, "CHAT_ATENDER")).thenReturn(true);

        ConversacionResponse conv = chatService.obtenerOCrearConversacionCliente(cliente.getId());
        ChatAuthChannelInterceptor interceptor = new ChatAuthChannelInterceptor(jwtService, usuarioRepo, acceso, conversacionRepo);
        MessageChannel channel = mock(MessageChannel.class);

        // CONNECT con Principal preexistente pero sin header Authorization STOMP debe fallar (HIGH-05)
        StompHeaderAccessor connectSinJwt = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectSinJwt.setLeaveMutable(true);
        connectSinJwt.setUser(() -> cliente.getEmail());
        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.createMessage(new byte[0], connectSinJwt.getMessageHeaders()), channel));

        // Cliente puede suscribirse a /topic/chat/{id}/estado y /user/queue/errors
        String tokenCliente = jwtService.generarToken(cliente);
        StompHeaderAccessor subEstado = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subEstado.setLeaveMutable(true);
        subEstado.setNativeHeader("Authorization", "Bearer " + tokenCliente);
        subEstado.setUser(() -> cliente.getEmail());
        subEstado.setDestination("/topic/chat/" + conv.getId() + "/estado");
        assertNotNull(interceptor.preSend(MessageBuilder.createMessage(new byte[0], subEstado.getMessageHeaders()), channel));

        StompHeaderAccessor subErrors = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subErrors.setLeaveMutable(true);
        subErrors.setNativeHeader("Authorization", "Bearer " + tokenCliente);
        subErrors.setUser(() -> cliente.getEmail());
        subErrors.setDestination("/user/queue/errors");
        assertNotNull(interceptor.preSend(MessageBuilder.createMessage(new byte[0], subErrors.getMessageHeaders()), channel));

        // Cliente NO puede suscribirse a /topic/admin/chat-alerts
        StompHeaderAccessor subAlertCliente = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAlertCliente.setLeaveMutable(true);
        subAlertCliente.setNativeHeader("Authorization", "Bearer " + tokenCliente);
        subAlertCliente.setUser(() -> cliente.getEmail());
        subAlertCliente.setDestination("/topic/admin/chat-alerts");
        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.createMessage(new byte[0], subAlertCliente.getMessageHeaders()), channel));

        // Admin SI puede suscribirse a /topic/admin/chat-alerts
        String tokenAdmin = jwtService.generarToken(admin);
        StompHeaderAccessor subAlertAdmin = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAlertAdmin.setLeaveMutable(true);
        subAlertAdmin.setNativeHeader("Authorization", "Bearer " + tokenAdmin);
        subAlertAdmin.setUser(() -> admin.getEmail());
        subAlertAdmin.setDestination("/topic/admin/chat-alerts");
        assertNotNull(interceptor.preSend(MessageBuilder.createMessage(new byte[0], subAlertAdmin.getMessageHeaders()), channel));

        // ChatPubSubBridge propaga eventos /estado y /admin/chat-alerts
        SimpMessagingTemplate simp = mock(SimpMessagingTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ChatPubSubBridge bridge = new ChatPubSubBridge(simp, mapper, redis);

        Map<String, Object> evento = Map.of("tipo", "ESCALADO_HUMANO", "conversacionId", conv.getId());
        bridge.publicarEvento("/topic/admin/chat-alerts", evento);
        verify(redis).convertAndSend(eq(ChatPubSubBridge.EVENT_CHANNEL), anyString());

        String payload = mapper.writeValueAsString(new ChatPubSubBridge.ChatEstadoBroadcastEvent("/topic/admin/chat-alerts", evento));
        bridge.onMessage(
                new DefaultMessage(ChatPubSubBridge.EVENT_CHANNEL.getBytes(StandardCharsets.UTF_8), payload.getBytes(StandardCharsets.UTF_8)),
                null
        );
        verify(simp).convertAndSend(eq("/topic/admin/chat-alerts"), anyMap());
    }

    @Test
    @DisplayName("HIGH-01 y MEDIUM-03: No envía turno terminado en model, filtra thought=true y confirma escalado IA")
    void asistenteIa_noDuplicaTurnoNiFiltraThoughtsYConfirmaEscalado() {
        ConversacionResponse conv = chatService.obtenerOCrearConversacionCliente(cliente.getId());
        chatService.procesarYGuardarMensaje(new MensajeChatRequest(conv.getId(), cliente.getId(), "Hola"), cliente.getId(), false);
        chatService.procesarYGuardarMensaje(new MensajeChatRequest(conv.getId(), cliente.getId(), "Ayuda con laptop"), cliente.getId(), false);

        GeminiProperties props = new GeminiProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");

        GeminiClient mockGemini = mock(GeminiClient.class);
        SimpMessagingTemplate mockMessaging = mock(SimpMessagingTemplate.class);
        AsistenteHerramientasService herramientas = new AsistenteHerramientasService(
                productoRepo, categoriaRepo, pedidoRepo, mock(CarritoService.class), mock(CrmService.class), cuponRepo, chatService
        );
        ReflectionTestUtils.setField(herramientas, "messagingTemplate", mockMessaging);

        Map<String, Object> respConThought = Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "role", "model",
                                "parts", List.of(
                                        Map.of("thought", true, "text", "Razonamiento privado que no debe verse"),
                                        Map.of("text", "Hola, con gusto te ayudo con laptops.")
                                )
                        )
                ))
        );
        when(mockGemini.generarContenido(anyList(), anyList(), anyString())).thenReturn(respConThought);

        AsistenteIaService iaService = new AsistenteIaService(props, mockGemini, herramientas, chatService, mockMessaging);
        iaService.procesarMensajeClienteAsync(conv.getId(), cliente.getId());
        // Segunda tarea para el segundo mensaje rápido: el último turno ya es ASISTENTE_IA, no debe invocar a Gemini de nuevo
        iaService.procesarMensajeClienteAsync(conv.getId(), cliente.getId());

        verify(mockGemini, times(1)).generarContenido(anyList(), anyList(), anyString());

        List<MensajeChatResponse> historial = chatService.obtenerHistorial(conv.getId(), cliente.getId(), false);
        assertEquals(3, historial.size());
        String textoBot = historial.get(2).getContenido();
        assertFalse(textoBot.contains("Razonamiento privado"));
        assertTrue(textoBot.contains("Hola, con gusto te ayudo"));

        // Cliente pide asesor -> IA invoca solicitar_asesor_humano y guarda confirmación
        chatService.procesarYGuardarMensaje(new MensajeChatRequest(conv.getId(), cliente.getId(), "Quiero hablar con un asesor"), cliente.getId(), false);
        Map<String, Object> respEscalar = Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "role", "model",
                                "parts", List.of(Map.of(
                                        "functionCall", Map.of(
                                                "name", "solicitar_asesor_humano",
                                                "args", Map.of("motivo", "Cliente solicita humano")
                                        )
                                ))
                        )
                ))
        );
        when(mockGemini.generarContenido(anyList(), anyList(), anyString())).thenReturn(respEscalar);
        iaService.procesarMensajeClienteAsync(conv.getId(), cliente.getId());

        assertEquals(ConversacionChat.MODO_ESPERANDO_HUMANO, chatService.obtenerModoAtencion(conv.getId()));
        List<MensajeChatResponse> trasEscalar = chatService.obtenerHistorial(conv.getId(), cliente.getId(), false);
        assertEquals(5, trasEscalar.size());
        assertEquals("ASISTENTE_IA", trasEscalar.get(4).getRemitenteRol().name());
        assertTrue(trasEscalar.get(4).getContenido().contains("asesor"));
    }

    @Test
    @DisplayName("HIGH-02: Takeover humano en vuelo impide ejecutar agregar_al_carrito")
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void asistenteIa_tomaControlHumanoEnVuelo_impideAgregarAlCarrito() throws Exception {
        ConversacionResponse conv = chatService.obtenerOCrearConversacionCliente(cliente.getId());
        chatService.procesarYGuardarMensaje(new MensajeChatRequest(conv.getId(), cliente.getId(), "Agrega laptop"), cliente.getId(), false);

        GeminiProperties props = new GeminiProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");

        GeminiClient mockGemini = mock(GeminiClient.class);
        CarritoService mockCarrito = mock(CarritoService.class);
        SimpMessagingTemplate mockMessaging = mock(SimpMessagingTemplate.class);
        AsistenteHerramientasService herramientas = new AsistenteHerramientasService(
                productoRepo, categoriaRepo, pedidoRepo, mockCarrito, mock(CrmService.class), cuponRepo, chatService
        );

        CountDownLatch geminiEntered = new CountDownLatch(1);
        CountDownLatch releaseGemini = new CountDownLatch(1);
        when(mockGemini.generarContenido(anyList(), anyList(), anyString())).thenAnswer(inv -> {
            geminiEntered.countDown();
            assertTrue(releaseGemini.await(5, TimeUnit.SECONDS));
            return Map.of("candidates", List.of(Map.of("content", Map.of(
                    "role", "model",
                    "parts", List.of(Map.of(
                            "functionCall", Map.of(
                                    "name", "agregar_al_carrito",
                                    "args", Map.of("producto_id", 1, "cantidad", 1)
                            )
                    ))
            ))));
        });

        AsistenteIaService iaService = new AsistenteIaService(props, mockGemini, herramientas, chatService, mockMessaging);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> iaService.procesarMensajeClienteAsync(conv.getId(), cliente.getId()));
            assertTrue(geminiEntered.await(5, TimeUnit.SECONDS));

            chatService.procesarYGuardarMensaje(new MensajeChatRequest(conv.getId(), admin.getId(), "Te atiendo yo"), admin.getId(), true);
            releaseGemini.countDown();
            future.get(5, TimeUnit.SECONDS);

            verifyNoInteractions(mockCarrito);
        } finally {
            executor.shutdownNow();
            conversacionRepo.deleteById(conv.getId());
            usuarioRepo.deleteById(cliente.getId());
            usuarioRepo.deleteById(admin.getId());
        }
    }

    @Test
    @DisplayName("HIGH-03: Revocar CHAT_ATENDER cierra sesión de asesor suscrito solo a /topic/chat/{id}")
    void interceptorYRegistry_revocaAsesorEnSalaIndividual() throws Exception {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        JwtService jwtService = new JwtService(jwtProps);

        AccesoService acceso = mock(AccesoService.class);
        when(acceso.rolActivo(admin)).thenReturn(true);
        when(acceso.tiene(admin, "CHAT_ATENDER")).thenReturn(true);

        ConversacionResponse conv = chatService.obtenerOCrearConversacionCliente(cliente.getId());
        ChatSessionRegistry registry = new ChatSessionRegistry(usuarioRepo, acceso);
        ChatAuthChannelInterceptor interceptor = new ChatAuthChannelInterceptor(jwtService, usuarioRepo, acceso, conversacionRepo);
        ReflectionTestUtils.setField(interceptor, "sessionRegistry", registry);

        var ws = mock(org.springframework.web.socket.WebSocketSession.class);
        when(ws.getId()).thenReturn("ws-admin-1");
        when(ws.isOpen()).thenReturn(true);
        registry.registrarSocket(ws);

        String tokenAdmin = jwtService.generarToken(admin);
        StompHeaderAccessor connectAcc = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAcc.setLeaveMutable(true);
        connectAcc.setSessionId("ws-admin-1");
        connectAcc.setSessionAttributes(new java.util.HashMap<>());
        connectAcc.setNativeHeader("Authorization", "Bearer " + tokenAdmin);
        MessageChannel channel = mock(MessageChannel.class);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], connectAcc.getMessageHeaders()), channel);

        // Admin se suscribe únicamente a /topic/chat/{id} (sin /topic/admin/chat-alerts)
        StompHeaderAccessor subAcc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAcc.setLeaveMutable(true);
        subAcc.setSessionId("ws-admin-1");
        subAcc.setSessionAttributes(connectAcc.getSessionAttributes());
        subAcc.setUser(() -> admin.getEmail());
        subAcc.setNativeHeader("Authorization", "Bearer " + tokenAdmin);
        subAcc.setDestination("/topic/chat/" + conv.getId());
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], subAcc.getMessageHeaders()), channel);

        // Permiso CHAT_ATENDER aún activo -> no cierra
        registry.purgarSesionesRevocadasOExpiradas();
        verify(ws, never()).close(any());

        // Revocar CHAT_ATENDER -> debe cerrar el socket en purga y bloquear siguientes frames
        when(acceso.tiene(admin, "CHAT_ATENDER")).thenReturn(false);
        registry.purgarSesionesRevocadasOExpiradas();
        verify(ws, times(1)).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);

        StompHeaderAccessor sendAcc = StompHeaderAccessor.create(StompCommand.SEND);
        sendAcc.setLeaveMutable(true);
        sendAcc.setSessionId("ws-admin-1");
        sendAcc.setSessionAttributes(connectAcc.getSessionAttributes());
        sendAcc.setUser(() -> admin.getEmail());
        sendAcc.setNativeHeader("Authorization", "Bearer " + tokenAdmin);
        sendAcc.setDestination("/app/chat.enviar");
        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.createMessage(new byte[0], sendAcc.getMessageHeaders()), channel));
    }
}
