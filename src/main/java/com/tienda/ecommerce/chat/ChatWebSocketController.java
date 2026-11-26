package com.tienda.ecommerce.chat;

import com.tienda.ecommerce.chat.dto.MensajeChatRequest;
import com.tienda.ecommerce.chat.dto.MensajeChatResponse;
import com.tienda.ecommerce.chat.ia.AsistenteIaService;
import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.common.RecursoNoEncontradoException;
import com.tienda.ecommerce.common.TooManyRequestsException;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UsuarioRepository usuarioRepository;

    @Autowired private com.tienda.ecommerce.configuracion.AccesoService acceso;
    @Autowired(required = false) private ChatEventPublisher eventPublisher;
    @Autowired(required = false) private ChatPubSubBridge chatPubSubBridge;
    @Autowired(required = false) private AsistenteIaService asistenteIaService;
    @Autowired(required = false) private ChatRateLimiter rateLimiter;

    public ChatWebSocketController(ChatService chatService, SimpMessagingTemplate messagingTemplate,
                                   UsuarioRepository usuarioRepository) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
        this.usuarioRepository = usuarioRepository;
    }

    @MessageMapping("/chat.enviar")
    public void enviarMensaje(@Valid @Payload MensajeChatRequest request, Principal principal) {
        // Seguridad WS: exige usuario autenticado por JWT del CONNECT
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Debes iniciar sesion para chatear");
        }
        if (request == null || request.getConversacionId() == null) {
            throw new BadRequestException("Solicitud de mensaje inválida");
        }
        Usuario usuario = usuarioRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con email: " + principal.getName()));
        if (!usuario.isActivo()
                || (usuario.getRol() != null && "ASISTENTE_IA".equals(usuario.getRol().name()))
                || !acceso.rolActivo(usuario)) {
            throw new AccessDeniedException("Cuenta inactiva");
        }

        boolean esAdmin = acceso.tiene(usuario, "CHAT_ATENDER");
        if (rateLimiter != null && !rateLimiter.permitirMensaje(usuario.getId(), esAdmin)) {
            throw new TooManyRequestsException("Has enviado demasiados mensajes. Espera unos segundos.");
        }

        // Seguridad WS: guarda con el id autenticado y detecta transición a modo humano bajo lock
        ChatService.ResultadoMensajeChat resultado = chatService.procesarYGuardarMensajeConResultado(
                request, usuario.getId(), esAdmin
        );
        MensajeChatResponse mensajeGuardado = resultado.mensaje();

        // Difunde al topico de la conversacion activa (multi-replica via Redis Pub/Sub cuando esta activo)
        String destino = "/topic/chat/" + request.getConversacionId();
        if (eventPublisher != null) {
            eventPublisher.publicarMensaje(destino, mensajeGuardado);
        } else if (chatPubSubBridge != null) {
            chatPubSubBridge.publicar(destino, mensajeGuardado);
        } else {
            messagingTemplate.convertAndSend(destino, mensajeGuardado);
        }

        if (esAdmin) {
            if (resultado.cambioAModoHumano()) {
                Map<String, Object> eventoModo = Map.of(
                        "tipo", "MODO_CAMBIADO",
                        "conversacionId", request.getConversacionId(),
                        "modoAtencion", ConversacionChat.MODO_HUMANO_ACTIVO,
                        "adminNombre", usuario.getNombre()
                );
                if (eventPublisher != null) {
                    eventPublisher.publicarEvento(destino + "/estado", eventoModo);
                    eventPublisher.publicarEvento("/topic/admin/chat-alerts", eventoModo);
                } else if (chatPubSubBridge != null) {
                    chatPubSubBridge.publicarEvento(destino + "/estado", eventoModo);
                    chatPubSubBridge.publicarEvento("/topic/admin/chat-alerts", eventoModo);
                } else {
                    messagingTemplate.convertAndSend(destino + "/estado", eventoModo);
                    messagingTemplate.convertAndSend("/topic/admin/chat-alerts", eventoModo);
                }
            }
        } else if (asistenteIaService != null) {
            asistenteIaService.procesarMensajeClienteAsync(request.getConversacionId(), usuario.getId());
        }
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, Object> manejarErrorWs(Exception ex) {
        log.debug("Error en mensaje STOMP: {}", ex.getMessage());
        boolean esMensajeSeguro = ex instanceof BadRequestException
                || ex instanceof TooManyRequestsException
                || ex instanceof AccessDeniedException
                || ex instanceof RecursoNoEncontradoException
                || ex instanceof MethodArgumentNotValidException;
        String texto = (esMensajeSeguro && ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "No se pudo procesar el mensaje";
        return Map.of(
                "error", true,
                "mensaje", texto
        );
    }
}
