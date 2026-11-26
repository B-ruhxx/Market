package com.tienda.ecommerce.chat;

import com.tienda.ecommerce.chat.dto.ConversacionResponse;
import com.tienda.ecommerce.chat.dto.MensajeChatRequest;
import com.tienda.ecommerce.chat.dto.MensajeChatResponse;
import com.tienda.ecommerce.chat.ia.GeminiProperties;
import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.common.RecursoNoEncontradoException;
import com.tienda.ecommerce.configuracion.AccesoService;
import com.tienda.ecommerce.configuracion.RolConfig;
import com.tienda.ecommerce.configuracion.RolConfigRepository;
import com.tienda.ecommerce.usuario.Rol;
import com.tienda.ecommerce.usuario.TipoDocumento;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatServiceImpl implements ChatService {

    public static final String BOT_EMAIL = "bot@electronix.com";
    public static final String BOT_DOCUMENTO = "SYSBOT001";
    private static final int MAX_CONVERSACIONES_ADMIN = 100;
    private static final int MAX_HISTORIAL_REST = 200;

    private static final Set<String> MODOS_VALIDOS = Set.of(
            ConversacionChat.MODO_BOT,
            ConversacionChat.MODO_ESPERANDO_HUMANO,
            ConversacionChat.MODO_HUMANO_ACTIVO
    );

    private final ConversacionChatRepository conversacionRepository;
    private final ChatMensajeRepository chatMensajeRepository;
    private final UsuarioRepository usuarioRepository;

    @Autowired(required = false)
    private RolConfigRepository rolConfigRepository;

    @Autowired(required = false)
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private AccesoService accesoService;

    @Autowired(required = false)
    private ChatEventPublisher eventPublisher;

    @Autowired(required = false)
    private GeminiProperties geminiProperties;

    public ChatServiceImpl(
            ConversacionChatRepository conversacionRepository,
            ChatMensajeRepository chatMensajeRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.conversacionRepository = conversacionRepository;
        this.chatMensajeRepository = chatMensajeRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional
    public ConversacionResponse obtenerOCrearConversacionCliente(Long clienteId) {
        ConversacionChat conversacion = conversacionRepository.findActivaByClienteId(clienteId)
                .orElseGet(() -> crearConversacionSegura(clienteId));

        return mapearConversacion(conversacion);
    }

    private ConversacionChat crearConversacionSegura(Long clienteId) {
        Usuario cliente = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario cliente no encontrado con id: " + clienteId));
        try {
            ConversacionChat nueva = new ConversacionChat(null, cliente, null, "ACTIVA");
            nueva.setModoAtencion(ConversacionChat.MODO_BOT);
            return conversacionRepository.saveAndFlush(nueva);
        } catch (DataIntegrityViolationException ex) {
            return conversacionRepository.findActivaByClienteId(clienteId)
                    .orElseThrow(() -> ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversacionResponse> listarConversacionesAdmin() {
        List<ConversacionChat> lista = conversacionRepository.findAllConUsuarios(PageRequest.of(0, MAX_CONVERSACIONES_ADMIN));
        if (lista.isEmpty()) {
            return List.of();
        }

        List<Long> ids = lista.stream().map(ConversacionChat::getId).toList();
        Map<Long, String> ultimosPorConv = new HashMap<>();
        for (ChatMensaje m : chatMensajeRepository.findUltimosMensajesPorConversaciones(ids)) {
            if (m.getConversacion() != null && m.getConversacion().getId() != null) {
                ultimosPorConv.put(m.getConversacion().getId(), m.getContenido());
            }
        }

        List<ConversacionResponse> respuesta = new ArrayList<>(lista.size());
        for (ConversacionChat conv : lista) {
            ConversacionResponse dto = mapearConversacion(conv);
            dto.setUltimoMensaje(ultimosPorConv.getOrDefault(conv.getId(), "Sin mensajes"));
            respuesta.add(dto);
        }

        respuesta.sort(Comparator.comparingInt(c -> prioridadModo(c.getModoAtencion())));
        return respuesta;
    }

    private int prioridadModo(String modo) {
        if (ConversacionChat.MODO_ESPERANDO_HUMANO.equals(modo)) return 0;
        if (ConversacionChat.MODO_HUMANO_ACTIVO.equals(modo)) return 1;
        return 2;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MensajeChatResponse> obtenerHistorial(Long conversacionId, Long usuarioId, boolean esAdmin) {
        ConversacionChat conversacion = conversacionRepository.findByIdConUsuarios(conversacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Conversación no encontrada con id: " + conversacionId));

        if (!esAdmin && !conversacion.getCliente().getId().equals(usuarioId)) {
            throw new AccessDeniedException("No tienes permiso para consultar los mensajes de esta conversación");
        }

        List<ChatMensaje> recientes = chatMensajeRepository.findRecientesPorConversacion(
                conversacionId, PageRequest.of(0, MAX_HISTORIAL_REST)
        );
        List<MensajeChatResponse> resultado = new ArrayList<>(recientes.size());
        for (int i = recientes.size() - 1; i >= 0; i--) {
            resultado.add(mapearMensaje(recientes.get(i)));
        }
        return resultado;
    }

    @Override
    @Transactional
    public MensajeChatResponse procesarYGuardarMensaje(MensajeChatRequest request, Long remitenteAutenticadoId, boolean esAdmin) {
        return procesarYGuardarMensajeInternal(request, remitenteAutenticadoId, esAdmin).mensaje();
    }

    @Override
    @Transactional
    public ResultadoMensajeChat procesarYGuardarMensajeConResultado(MensajeChatRequest request, Long remitenteAutenticadoId, boolean esAdmin) {
        return procesarYGuardarMensajeInternal(request, remitenteAutenticadoId, esAdmin);
    }

    private ResultadoMensajeChat procesarYGuardarMensajeInternal(MensajeChatRequest request, Long remitenteAutenticadoId, boolean esAdmin) {
        ConversacionChat conversacion = conversacionRepository.findByIdConLock(request.getConversacionId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Conversación no encontrada con id: " + request.getConversacionId()));

        if (!"ACTIVA".equals(conversacion.getEstado())) {
            throw new BadRequestException("Esta conversación ya no está activa");
        }

        // Seguridad WS: solo el dueño o un admin guardan mensajes
        if (!esAdmin && !conversacion.getCliente().getId().equals(remitenteAutenticadoId)) {
            throw new AccessDeniedException("No tienes permiso para escribir en esta conversación");
        }

        // Seguridad WS: el remitente real es el autenticado, no el del payload
        Usuario remitente = usuarioRepository.findById(remitenteAutenticadoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Remitente no encontrado con id: " + remitenteAutenticadoId));

        // Si un admin responde, toma control humano de la sala y silencia al bot
        boolean cambioAModoHumano = false;
        if (esAdmin) {
            boolean cambio = false;
            if (conversacion.getAdmin() == null) {
                conversacion.setAdmin(remitente);
                cambio = true;
            }
            if (!ConversacionChat.MODO_HUMANO_ACTIVO.equals(conversacion.getModoAtencion())) {
                conversacion.setModoAtencion(ConversacionChat.MODO_HUMANO_ACTIVO);
                cambio = true;
                cambioAModoHumano = true;
            }
            if (cambio) {
                conversacionRepository.save(conversacion);
            }
        }

        ChatMensaje mensaje = new ChatMensaje(
                null,
                conversacion,
                remitente,
                request.getContenido().trim()
        );
        mensaje.setFechaEnvio(LocalDateTime.now());

        ChatMensaje guardado = chatMensajeRepository.save(mensaje);
        return new ResultadoMensajeChat(mapearMensaje(guardado), cambioAModoHumano);
    }

    @Override
    @Transactional
    public ConversacionResponse cambiarModoAtencion(Long conversacionId, String modoAtencion, Long usuarioId, boolean esAdmin) {
        String modoNormalizado = modoAtencion == null ? "" : modoAtencion.trim().toUpperCase(Locale.ROOT);
        if (!MODOS_VALIDOS.contains(modoNormalizado)) {
            throw new BadRequestException("Modo de atención inválido: " + modoAtencion);
        }

        ConversacionChat conversacion = conversacionRepository.findByIdConLock(conversacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Conversación no encontrada con id: " + conversacionId));

        if (!"ACTIVA".equals(conversacion.getEstado())) {
            throw new BadRequestException("No se puede cambiar el modo de una conversación cerrada o archivada");
        }

        String modoAnterior = conversacion.getModoAtencion();

        if (!esAdmin) {
            if (!conversacion.getCliente().getId().equals(usuarioId)) {
                throw new AccessDeniedException("No tienes permiso para modificar esta conversación");
            }
            if (!ConversacionChat.MODO_ESPERANDO_HUMANO.equals(modoNormalizado)) {
                throw new AccessDeniedException("Solo un asesor puede activar o reactivar ese modo de atención");
            }
            if (ConversacionChat.MODO_HUMANO_ACTIVO.equals(modoAnterior)) {
                throw new BadRequestException("Un asesor humano ya está atendiendo esta conversación");
            }
            if (ConversacionChat.MODO_ESPERANDO_HUMANO.equals(modoAnterior)) {
                return mapearConversacion(conversacion);
            }
        }

        conversacion.setModoAtencion(modoNormalizado);
        if (esAdmin && ConversacionChat.MODO_HUMANO_ACTIVO.equals(modoNormalizado)) {
            Usuario admin = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
            conversacion.setAdmin(admin);
        } else if (ConversacionChat.MODO_BOT.equals(modoNormalizado)) {
            conversacion.setAdmin(null);
        }

        ConversacionChat guardada = conversacionRepository.save(conversacion);
        ConversacionResponse response = mapearConversacion(guardada);

        if (eventPublisher != null) {
            eventPublisher.publicarEvento("/topic/chat/" + conversacionId + "/estado", Map.of(
                    "tipo", "MODO_CAMBIADO",
                    "conversacionId", conversacionId,
                    "modoAtencion", response.getModoAtencion(),
                    "adminNombre", response.getAdminNombre() != null ? response.getAdminNombre() : ""
            ));

            if (ConversacionChat.MODO_ESPERANDO_HUMANO.equals(response.getModoAtencion())) {
                eventPublisher.publicarEvento("/topic/admin/chat-alerts", Map.of(
                        "tipo", "ESCALADO_HUMANO",
                        "conversacionId", conversacionId,
                        "clienteNombre", response.getClienteNombre() != null ? response.getClienteNombre() : "Cliente",
                        "motivo", "Solicitado desde el chat"
                ));

                if (!ConversacionChat.MODO_ESPERANDO_HUMANO.equals(modoAnterior) && !esAdmin) {
                    MensajeChatResponse aviso = persistirMensajeBot(
                            guardada,
                            "Hemos notificado a nuestro equipo de soporte. Un asesor revisará tu consulta en breve."
                    );
                    eventPublisher.publicarMensaje("/topic/chat/" + conversacionId, aviso);
                }
            } else {
                eventPublisher.publicarEvento("/topic/admin/chat-alerts", Map.of(
                        "tipo", "MODO_CAMBIADO",
                        "conversacionId", conversacionId,
                        "modoAtencion", response.getModoAtencion()
                ));
            }
        }

        return response;
    }

    @Override
    @Transactional
    public Optional<ConversacionResponse> escalarAHumanoDesdeBot(Long conversacionId, String motivo) {
        ConversacionChat conversacion = conversacionRepository.findByIdConLock(conversacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Conversación no encontrada con id: " + conversacionId));
        if (!"ACTIVA".equals(conversacion.getEstado()) || !ConversacionChat.MODO_BOT.equals(conversacion.getModoAtencion())) {
            return Optional.empty();
        }
        conversacion.setModoAtencion(ConversacionChat.MODO_ESPERANDO_HUMANO);
        ConversacionChat guardada = conversacionRepository.save(conversacion);
        return Optional.of(mapearConversacion(guardada));
    }

    @Override
    @Transactional
    public MensajeChatResponse guardarMensajeBot(Long conversacionId, String contenido) {
        ConversacionChat conversacion = conversacionRepository.findByIdConLock(conversacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Conversación no encontrada con id: " + conversacionId));
        return persistirMensajeBot(conversacion, contenido);
    }

    @Override
    @Transactional
    public Optional<MensajeChatResponse> guardarMensajeBotSiModoBot(Long conversacionId, String contenido) {
        ConversacionChat conversacion = conversacionRepository.findByIdConLock(conversacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Conversación no encontrada con id: " + conversacionId));
        if (!"ACTIVA".equals(conversacion.getEstado()) || !ConversacionChat.MODO_BOT.equals(conversacion.getModoAtencion())) {
            return Optional.empty();
        }
        Optional<ChatMensaje> ultimo = chatMensajeRepository.findUltimoMensaje(conversacionId);
        if (ultimo.isPresent()) {
            Usuario remitenteUltimo = ultimo.get().getRemitente();
            if (remitenteUltimo != null && accesoService != null && accesoService.tiene(remitenteUltimo, "CHAT_ATENDER")) {
                return Optional.empty();
            }
        }
        return Optional.of(persistirMensajeBot(conversacion, contenido));
    }

    @Override
    @Transactional
    public Optional<MensajeChatResponse> guardarConfirmacionEscaladoBot(Long conversacionId, String contenido) {
        ConversacionChat conversacion = conversacionRepository.findByIdConLock(conversacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Conversación no encontrada con id: " + conversacionId));
        if (!"ACTIVA".equals(conversacion.getEstado())
                || !ConversacionChat.MODO_ESPERANDO_HUMANO.equals(conversacion.getModoAtencion())
                || conversacion.getAdmin() != null) {
            return Optional.empty();
        }
        Optional<ChatMensaje> ultimo = chatMensajeRepository.findUltimoMensaje(conversacionId);
        if (ultimo.isPresent()) {
            Usuario remitenteUltimo = ultimo.get().getRemitente();
            if (remitenteUltimo != null && accesoService != null && accesoService.tiene(remitenteUltimo, "CHAT_ATENDER")) {
                return Optional.empty();
            }
        }
        return Optional.of(persistirMensajeBot(conversacion, contenido));
    }

    private MensajeChatResponse persistirMensajeBot(ConversacionChat conversacion, String contenido) {
        Usuario bot = obtenerOCrearUsuarioBot();
        ChatMensaje mensaje = new ChatMensaje(null, conversacion, bot, contenido.trim());
        mensaje.setFechaEnvio(LocalDateTime.now());
        ChatMensaje guardado = chatMensajeRepository.save(mensaje);
        return mapearMensaje(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public String obtenerModoAtencion(Long conversacionId) {
        return conversacionRepository.findById(conversacionId)
                .map(ConversacionChat::getModoAtencion)
                .orElse(ConversacionChat.MODO_BOT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MensajeChatResponse> obtenerHistorialRecienteParaIa(Long conversacionId, int maxMensajes) {
        int limite = Math.max(1, maxMensajes);
        List<ChatMensaje> recientes = chatMensajeRepository.findRecientesPorConversacion(
                conversacionId, PageRequest.of(0, limite)
        );
        List<MensajeChatResponse> resultado = new ArrayList<>(recientes.size());
        for (int i = recientes.size() - 1; i >= 0; i--) {
            resultado.add(mapearMensaje(recientes.get(i)));
        }
        return resultado;
    }

    private synchronized Usuario obtenerOCrearUsuarioBot() {
        return usuarioRepository.findByEmail(BOT_EMAIL).orElseGet(() -> {
            asegurarRolAsistente();
            String rawPass = UUID.randomUUID().toString();
            String hash = passwordEncoder != null ? passwordEncoder.encode(rawPass) : "$2a$10$disabledSystemBotAccountHash000000000000000000000000";
            try {
                Usuario bot = new Usuario(
                        null,
                        "Electronix",
                        "AI",
                        null,
                        TipoDocumento.CE,
                        BOT_DOCUMENTO,
                        BOT_EMAIL,
                        hash,
                        Rol.valueOf("ASISTENTE_IA"),
                        false
                );
                return usuarioRepository.saveAndFlush(bot);
            } catch (DataIntegrityViolationException ex) {
                return usuarioRepository.findByEmail(BOT_EMAIL).orElseThrow(() -> ex);
            }
        });
    }

    private void asegurarRolAsistente() {
        if (rolConfigRepository == null) return;
        if (!rolConfigRepository.existsById("ASISTENTE_IA")) {
            try {
                RolConfig rol = new RolConfig();
                rol.setCodigo("ASISTENTE_IA");
                rol.setNombre("Asistente Virtual IA");
                rol.setDescripcion("Asistente inteligente de compras y soporte");
                rol.setSistema(true);
                rol.setActivo(false);
                rolConfigRepository.saveAndFlush(rol);
            } catch (DataIntegrityViolationException ignored) {
                // Otro hilo ya creó el rol
            }
        }
    }

    private ConversacionResponse mapearConversacion(ConversacionChat c) {
        String modo = c.getModoAtencion() != null ? c.getModoAtencion() : ConversacionChat.MODO_BOT;
        boolean iaActiva = geminiProperties == null || geminiProperties.tieneApiKeyConfigurada();
        String adminNombre = c.getAdmin() != null
                ? c.getAdmin().getNombre()
                : (ConversacionChat.MODO_BOT.equals(modo) ? "Electronix AI" : "Sin asignar");

        return new ConversacionResponse(
                c.getId(),
                c.getCliente() != null ? c.getCliente().getId() : null,
                c.getCliente() != null ? c.getCliente().getNombre() : "Cliente",
                c.getAdmin() != null ? c.getAdmin().getId() : null,
                adminNombre,
                c.getEstado(),
                modo,
                iaActiva,
                c.getFechaCreacion(),
                null
        );
    }

    private MensajeChatResponse mapearMensaje(ChatMensaje m) {
        return new MensajeChatResponse(
                m.getId(),
                m.getConversacion().getId(),
                m.getRemitente().getId(),
                m.getRemitente().getNombre(),
                m.getRemitente().getRol(),
                m.getContenido(),
                m.getFechaEnvio()
        );
    }
}
