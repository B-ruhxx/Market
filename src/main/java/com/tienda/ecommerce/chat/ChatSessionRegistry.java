package com.tienda.ecommerce.chat;

import com.tienda.ecommerce.configuracion.AccesoService;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de sesiones WebSocket activas para revocar suscripciones cuando el JWT expira,
 * cambia la contraseña o se revoca el permiso CHAT_ATENDER sin esperar al siguiente frame inbound.
 */
@Component
public class ChatSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionRegistry.class);
    private static final long CONNECT_TIMEOUT_MS = 15_000L;

    public static final class SessionMetadata {
        private final long connectedAtMs = System.currentTimeMillis();
        private volatile String email;
        private volatile Long jwtIatMs;
        private volatile Long jwtExpMs;
        private volatile boolean suscritoAdminAlerts;
        private volatile boolean requiereChatAtender;

        public long getConnectedAtMs() { return connectedAtMs; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Long getJwtIatMs() { return jwtIatMs; }
        public void setJwtIatMs(Long jwtIatMs) { this.jwtIatMs = jwtIatMs; }
        public Long getJwtExpMs() { return jwtExpMs; }
        public void setJwtExpMs(Long jwtExpMs) { this.jwtExpMs = jwtExpMs; }
        public boolean isSuscritoAdminAlerts() { return suscritoAdminAlerts; }
        public void setSuscritoAdminAlerts(boolean suscritoAdminAlerts) { this.suscritoAdminAlerts = suscritoAdminAlerts; }
        public boolean isRequiereChatAtender() { return requiereChatAtender; }
        public void setRequiereChatAtender(boolean requiereChatAtender) { this.requiereChatAtender = requiereChatAtender; }
    }

    private final Map<String, WebSocketSession> sockets = new ConcurrentHashMap<>();
    private final Map<String, SessionMetadata> metadataMap = new ConcurrentHashMap<>();

    private final UsuarioRepository usuarioRepository;
    private final AccesoService accesoService;

    public ChatSessionRegistry(UsuarioRepository usuarioRepository, AccesoService accesoService) {
        this.usuarioRepository = usuarioRepository;
        this.accesoService = accesoService;
    }

    public void registrarSocket(WebSocketSession session) {
        if (session != null && session.getId() != null) {
            sockets.put(session.getId(), session);
            metadataMap.computeIfAbsent(session.getId(), k -> new SessionMetadata());
        }
    }

    public void removerSocket(String sessionId) {
        if (sessionId != null) {
            sockets.remove(sessionId);
            metadataMap.remove(sessionId);
        }
    }

    public void actualizarAutenticacion(String sessionId, String email, Long iatMs, Long expMs) {
        if (sessionId == null) return;
        SessionMetadata meta = metadataMap.computeIfAbsent(sessionId, k -> new SessionMetadata());
        meta.setEmail(email);
        meta.setJwtIatMs(iatMs);
        meta.setJwtExpMs(expMs);
    }

    public void marcarSuscripcionAdminAlerts(String sessionId) {
        if (sessionId == null) return;
        SessionMetadata meta = metadataMap.computeIfAbsent(sessionId, k -> new SessionMetadata());
        meta.setSuscritoAdminAlerts(true);
        meta.setRequiereChatAtender(true);
    }

    public void marcarSuscripcionChatAtender(String sessionId) {
        if (sessionId == null) return;
        SessionMetadata meta = metadataMap.computeIfAbsent(sessionId, k -> new SessionMetadata());
        meta.setRequiereChatAtender(true);
    }

    @Scheduled(fixedDelay = 30_000)
    public void purgarSesionesRevocadasOExpiradas() {
        long ahora = System.currentTimeMillis();
        for (Map.Entry<String, WebSocketSession> entry : sockets.entrySet()) {
            String sessionId = entry.getKey();
            WebSocketSession ws = entry.getValue();
            if (ws == null || !ws.isOpen()) {
                removerSocket(sessionId);
                continue;
            }
            SessionMetadata meta = metadataMap.get(sessionId);
            if (meta == null || meta.getEmail() == null) {
                if (meta != null && ahora - meta.getConnectedAtMs() > CONNECT_TIMEOUT_MS) {
                    cerrarPorPolitica(sessionId, ws);
                }
                continue;
            }
            if (debeRevocar(meta, ahora)) {
                cerrarPorPolitica(sessionId, ws);
            }
        }
    }

    private boolean debeRevocar(SessionMetadata meta, long ahora) {
        if (meta.getJwtExpMs() != null && ahora > meta.getJwtExpMs()) {
            return true;
        }
        Usuario u = usuarioRepository.findByEmail(meta.getEmail()).orElse(null);
        if (u == null || !u.isActivo()
                || (u.getRol() != null && "ASISTENTE_IA".equals(u.getRol().name()))
                || !accesoService.rolActivo(u)) {
            return true;
        }
        if (meta.getJwtIatMs() != null && u.getPasswordModificadoEn() != null) {
            long modMs = u.getPasswordModificadoEn().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (meta.getJwtIatMs() < modMs - 1000) {
                return true;
            }
        }
        return (meta.isSuscritoAdminAlerts() || meta.isRequiereChatAtender())
                && !accesoService.tiene(u, "CHAT_ATENDER");
    }

    private void cerrarPorPolitica(String sessionId, WebSocketSession ws) {
        try {
            ws.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ex) {
            log.debug("Error cerrando sesión WebSocket revocada {}: {}", sessionId, ex.getMessage());
        } finally {
            removerSocket(sessionId);
        }
    }
}
