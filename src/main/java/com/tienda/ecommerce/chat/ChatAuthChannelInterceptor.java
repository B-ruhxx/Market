package com.tienda.ecommerce.chat;

import com.tienda.ecommerce.auth.JwtService;
import com.tienda.ecommerce.usuario.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

// Interceptor WS: valida el JWT del header STOMP y fija el usuario autenticado.
@Component
public class ChatAuthChannelInterceptor implements ChannelInterceptor {

    private static final String ATTR_IAT = "jwtIatMs";
    private static final String ATTR_EXP = "jwtExpMs";
    private static final String ATTR_ADMIN_ALERTS = "suscritoAdminAlerts";
    private static final String ATTR_REQUIRES_CHAT_ATENDER = "requiereChatAtender";

    private final JwtService jwtService;
    private final com.tienda.ecommerce.usuario.UsuarioRepository usuarios;
    private final com.tienda.ecommerce.configuracion.AccesoService acceso;
    private final ConversacionChatRepository conversaciones;

    @Autowired(required = false)
    private ChatSessionRegistry sessionRegistry;

    public ChatAuthChannelInterceptor(JwtService jwtService, com.tienda.ecommerce.usuario.UsuarioRepository usuarios,
            com.tienda.ecommerce.configuracion.AccesoService acceso, ConversacionChatRepository conversaciones) {
        this.jwtService = jwtService;
        this.usuarios = usuarios;
        this.acceso = acceso;
        this.conversaciones = conversaciones;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Exige JWT explícito en el frame STOMP CONNECT (ignora cualquier Principal del handshake HTTP)
            String token = extraerToken(accessor);
            if (token == null || !jwtService.esTokenValido(token)) {
                throw new AccessDeniedException("Sesión requerida");
            }
            String email = jwtService.extraerEmail(token);
            if (email == null) {
                throw new AccessDeniedException("Sesión requerida");
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(email, null, List.of());
            accessor.setUser(auth);
            java.util.Date iat = jwtService.extraerIssuedAt(token);
            java.util.Date exp = jwtService.extraerExpiration(token);
            Long iatMs = iat != null ? iat.getTime() : null;
            Long expMs = exp != null ? exp.getTime() : null;
            if (accessor.getSessionAttributes() != null) {
                if (iatMs != null) accessor.getSessionAttributes().put(ATTR_IAT, iatMs);
                if (expMs != null) accessor.getSessionAttributes().put(ATTR_EXP, expMs);
            }
            if (sessionRegistry != null) {
                sessionRegistry.actualizarAutenticacion(accessor.getSessionId(), email, iatMs, expMs);
            }
        }

        if (accessor.getCommand() != null && accessor.getCommand() != StompCommand.DISCONNECT) {
            Principal principal = accessor.getUser();
            Usuario usuario = principal == null ? null : usuarios.findByEmail(principal.getName()).orElse(null);
            if (usuario == null || !usuario.isActivo()
                    || (usuario.getRol() != null && "ASISTENTE_IA".equals(usuario.getRol().name()))
                    || !acceso.rolActivo(usuario)) {
                throw new AccessDeniedException("Sesión inactiva");
            }

            Long expMs = extraerExpMs(accessor, principal);
            if (expMs != null && System.currentTimeMillis() > expMs) {
                throw new AccessDeniedException("Sesión expirada");
            }

            Long iatMs = extraerIatMs(accessor, principal);
            if (iatMs != null && usuario.getPasswordModificadoEn() != null) {
                long modificadoMs = usuario.getPasswordModificadoEn().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (iatMs < modificadoMs - 1000) {
                    throw new AccessDeniedException("Sesión revocada");
                }
            }

            if (accessor.getSessionAttributes() != null
                    && (Boolean.TRUE.equals(accessor.getSessionAttributes().get(ATTR_ADMIN_ALERTS))
                    || Boolean.TRUE.equals(accessor.getSessionAttributes().get(ATTR_REQUIRES_CHAT_ATENDER)))
                    && !acceso.tiene(usuario, "CHAT_ATENDER")) {
                throw new AccessDeniedException("Permiso administrativo revocado");
            }

            if (accessor.getCommand() == StompCommand.SEND) {
                String destino = accessor.getDestination();
                if (destino == null || !destino.startsWith("/app/")) {
                    throw new AccessDeniedException("Destino no autorizado");
                }
            }

            if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
                String destino = accessor.getDestination();
                if (destino == null) throw new AccessDeniedException("Destino no autorizado");

                if ("/user/queue/errors".equals(destino)) {
                    // Canal privado de errores STOMP del usuario autenticado
                } else if ("/topic/admin/chat-alerts".equals(destino)) {
                    if (!acceso.tiene(usuario, "CHAT_ATENDER")) {
                        throw new AccessDeniedException("Destino administrativo no autorizado");
                    }
                    if (accessor.getSessionAttributes() != null) {
                        accessor.getSessionAttributes().put(ATTR_ADMIN_ALERTS, Boolean.TRUE);
                    }
                    if (sessionRegistry != null) {
                        sessionRegistry.marcarSuscripcionAdminAlerts(accessor.getSessionId());
                    }
                } else if (destino.matches("/topic/chat/[0-9]+(/estado)?")) {
                    String sinPrefijo = destino.substring("/topic/chat/".length());
                    int slashIdx = sinPrefijo.indexOf('/');
                    String idTexto = slashIdx >= 0 ? sinPrefijo.substring(0, slashIdx) : sinPrefijo;
                    Long conversacionId = Long.valueOf(idTexto);
                    var conversacion = conversaciones.findByIdConUsuarios(conversacionId)
                            .or(() -> conversaciones.findById(conversacionId))
                            .orElseThrow(() -> new AccessDeniedException("Conversación inexistente o no autorizada"));
                    boolean esClienteDueno = conversacion.getCliente().getId().equals(usuario.getId());
                    if (!esClienteDueno && !acceso.tiene(usuario, "CHAT_ATENDER")) {
                        throw new AccessDeniedException("Conversación no autorizada");
                    }
                    if (!esClienteDueno) {
                        if (accessor.getSessionAttributes() != null) {
                            accessor.getSessionAttributes().put(ATTR_REQUIRES_CHAT_ATENDER, Boolean.TRUE);
                        }
                        if (sessionRegistry != null) {
                            sessionRegistry.marcarSuscripcionChatAtender(accessor.getSessionId());
                        }
                    }
                } else {
                    throw new AccessDeniedException("Destino no autorizado");
                }
            }
        }
        return message;
    }

    private Long extraerIatMs(StompHeaderAccessor accessor, Principal principal) {
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extraerToken(accessor);
            if (token != null) {
                java.util.Date iat = jwtService.extraerIssuedAt(token);
                return iat != null ? iat.getTime() : null;
            }
        } else {
            String token = extraerToken(accessor);
            if (token != null) {
                if (!jwtService.esTokenValido(token)) {
                    throw new AccessDeniedException("Token inválido");
                }
                String email = jwtService.extraerEmail(token);
                if (principal == null || email == null || !email.equalsIgnoreCase(principal.getName())) {
                    throw new AccessDeniedException("Token no corresponde a la sesión");
                }
                java.util.Date iat = jwtService.extraerIssuedAt(token);
                return iat != null ? iat.getTime() : null;
            }
            if (accessor.getSessionAttributes() != null) {
                Object val = accessor.getSessionAttributes().get(ATTR_IAT);
                if (val instanceof Long l) return l;
            }
        }
        return null;
    }

    private Long extraerExpMs(StompHeaderAccessor accessor, Principal principal) {
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extraerToken(accessor);
            if (token != null) {
                java.util.Date exp = jwtService.extraerExpiration(token);
                return exp != null ? exp.getTime() : null;
            }
        } else {
            String token = extraerToken(accessor);
            if (token != null) {
                if (!jwtService.esTokenValido(token)) {
                    throw new AccessDeniedException("Token inválido o expirado");
                }
                String email = jwtService.extraerEmail(token);
                if (principal == null || email == null || !email.equalsIgnoreCase(principal.getName())) {
                    throw new AccessDeniedException("Token no corresponde a la sesión");
                }
                java.util.Date exp = jwtService.extraerExpiration(token);
                return exp != null ? exp.getTime() : null;
            }
            if (accessor.getSessionAttributes() != null) {
                Object val = accessor.getSessionAttributes().get(ATTR_EXP);
                if (val instanceof Long l) return l;
            }
        }
        return null;
    }

    private String extraerToken(StompHeaderAccessor accessor) {
        String auth = accessor.getFirstNativeHeader("Authorization");
        if (auth == null || auth.isBlank()) {
            auth = accessor.getFirstNativeHeader("authorization");
        }
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }
}
