package com.tienda.ecommerce.chat.ia;

import com.tienda.ecommerce.chat.ChatEventPublisher;
import com.tienda.ecommerce.chat.ChatPubSubBridge;
import com.tienda.ecommerce.chat.ChatRateLimiter;
import com.tienda.ecommerce.chat.ChatService;
import com.tienda.ecommerce.chat.ConversacionChat;
import com.tienda.ecommerce.chat.dto.MensajeChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AsistenteIaService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteIaService.class);
    private static final int MAX_RONDAS_TOOLS = 4;
    private static final int STRIPE_COUNT = 64;
    private static final long LOCK_TIMEOUT_SEGUNDOS = 15L;

    private static final String SYSTEM_PROMPT = """
            Eres 'Electronix AI', el asistente virtual de compras y soporte de la tienda Electronix.
            Reglas de comportamiento:
            1. Responde siempre en español claro, cordial y conciso (máximo 120 palabras salvo comparaciones detalladas). No uses emojis.
            2. Usa tus herramientas para consultar datos REALES del catálogo, pedidos, puntos y cupones. Nunca inventes precios, IDs ni stock.
            3. Cuando recomiendes productos de buscar_productos, incluye su nombre, precio, stock y el enlace relativo exacto (ejemplo: /producto.html?id=12).
            4. Si el cliente confirma que desea añadir un producto específico al carrito, usa agregar_al_carrito con el producto_id exacto.
            5. Si el cliente pide hablar con una persona, asesor, agente humano, o tiene un reclamo complejo/insatisfacción, invoca inmediatamente solicitar_asesor_humano y confírmale que un asesor humano atenderá su chat.
            """;

    private final GeminiProperties properties;
    private final GeminiClient geminiClient;
    private final AsistenteHerramientasService herramientasService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private ChatPubSubBridge chatPubSubBridge;

    @Autowired(required = false)
    private ChatEventPublisher eventPublisher;

    @Autowired(required = false)
    private ChatRateLimiter rateLimiter;

    private final ReentrantLock[] locksPorBanda = new ReentrantLock[STRIPE_COUNT];
    private final Map<Long, AtomicInteger> typingActivoPorSala = new ConcurrentHashMap<>();

    public AsistenteIaService(
            GeminiProperties properties,
            GeminiClient geminiClient,
            AsistenteHerramientasService herramientasService,
            ChatService chatService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.properties = properties;
        this.geminiClient = geminiClient;
        this.herramientasService = herramientasService;
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
        for (int i = 0; i < STRIPE_COUNT; i++) {
            this.locksPorBanda[i] = new ReentrantLock();
        }
    }

    private ReentrantLock lockParaConversacion(Long conversacionId) {
        int idx = Math.floorMod(Long.hashCode(conversacionId), STRIPE_COUNT);
        return locksPorBanda[idx];
    }

    @Async("chatIaExecutor")
    public void procesarMensajeClienteAsync(Long conversacionId, Long clienteId) {
        if (!properties.tieneApiKeyConfigurada() || conversacionId == null) {
            return;
        }

        ReentrantLock lockSala = lockParaConversacion(conversacionId);
        boolean adquirido = false;
        try {
            adquirido = lockSala.tryLock(LOCK_TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!adquirido) {
            log.warn("Timeout esperando lock IA para conversación {}; se omite turno redundante", conversacionId);
            return;
        }

        try {
            if (!ConversacionChat.MODO_BOT.equals(chatService.obtenerModoAtencion(conversacionId))) {
                return;
            }
            List<MensajeChatResponse> historial = chatService.obtenerHistorialRecienteParaIa(
                    conversacionId,
                    properties.getMaxHistorial()
            );
            if (historial.isEmpty()) {
                return;
            }
            MensajeChatResponse ultimoMensaje = historial.get(historial.size() - 1);
            if (ultimoMensaje.getRemitenteRol() != null && "ASISTENTE_IA".equals(ultimoMensaje.getRemitenteRol().name())) {
                // El turno más reciente ya fue respondido por la IA (p. ej. mensajes rápidos del cliente)
                return;
            }

            if (rateLimiter != null && !rateLimiter.permitirConsultaIa(clienteId)) {
                chatService.guardarMensajeBotSiModoBot(
                        conversacionId,
                        "Alcanzaste temporalmente el límite de consultas automáticas por minuto. Espera un momento o solicita un asesor humano."
                ).ifPresent(msg -> publicarMensaje(conversacionId, msg));
                return;
            }

            incrementarTyping(conversacionId);
            try {
                String respuestaTexto = generarRespuestaConHerramientas(historial, conversacionId, clienteId);
                if (respuestaTexto != null && !respuestaTexto.isBlank()) {
                    // Solo guarda y difunde si la conversación sigue en modo BOT y sin toma de control humano
                    chatService.guardarMensajeBotSiModoBot(conversacionId, respuestaTexto)
                            .ifPresent(msg -> publicarMensaje(conversacionId, msg));
                }
            } catch (Exception ex) {
                log.warn("No se pudo generar respuesta de Electronix AI para conversación {}: {}", conversacionId, ex.getMessage());
                try {
                    chatService.guardarMensajeBotSiModoBot(
                            conversacionId,
                            "En este momento tengo inconvenientes para consultar el asistente automático. "
                                    + "Puedes volver a intentarlo en unos segundos o pulsar Asesor para atención humana."
                    ).ifPresent(msg -> publicarMensaje(conversacionId, msg));
                } catch (Exception inner) {
                    log.error("Error enviando fallback de IA en conversación {}: {}", conversacionId, inner.getMessage());
                }
            } finally {
                decrementarTyping(conversacionId);
            }
        } finally {
            lockSala.unlock();
        }
    }

    String generarRespuestaConHerramientas(List<MensajeChatResponse> historial, Long conversacionId, Long clienteId) {
        List<Map<String, Object>> contents = construirContentsAlternados(historial);
        if (contents.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> tools = herramientasService.obtenerDefinicionesTools();

        for (int ronda = 0; ronda < MAX_RONDAS_TOOLS; ronda++) {
            if (!ConversacionChat.MODO_BOT.equals(chatService.obtenerModoAtencion(conversacionId))) {
                return null;
            }

            Map<String, Object> respuesta = geminiClient.generarContenido(contents, tools, SYSTEM_PROMPT);
            Map<String, Object> candidateContent = extraerPrimerContent(respuesta);
            if (candidateContent == null) {
                return "No pude generar una respuesta para esa consulta. ¿Deseas reformularla o hablar con un asesor humano?";
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) candidateContent.get("parts");
            if (parts == null || parts.isEmpty()) {
                return "No pude generar una respuesta para esa consulta. ¿Deseas reformularla o hablar con un asesor humano?";
            }

            List<Map<String, Object>> functionCalls = parts.stream()
                    .filter(p -> p.containsKey("functionCall"))
                    .toList();

            if (functionCalls.isEmpty()) {
                return extraerTextoDeParts(parts);
            }

            contents.add(Map.of("role", "model", "parts", parts));

            List<Map<String, Object>> responseParts = new ArrayList<>();
            boolean escaladoEnEstaRonda = false;
            for (Map<String, Object> part : functionCalls) {
                // Re-verifica modo BOT antes de ejecutar cada herramienta (evita mutaciones tras takeover humano en vuelo)
                if (!ConversacionChat.MODO_BOT.equals(chatService.obtenerModoAtencion(conversacionId))) {
                    return null;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> fc = (Map<String, Object>) part.get("functionCall");
                String nombreTool = String.valueOf(fc.get("name"));
                @SuppressWarnings("unchecked")
                Map<String, Object> args = (Map<String, Object>) fc.get("args");

                Map<String, Object> resultadoTool = herramientasService.ejecutarTool(nombreTool, args, conversacionId, clienteId);
                if ("solicitar_asesor_humano".equals(nombreTool) && Boolean.TRUE.equals(resultadoTool.get("escalado"))) {
                    escaladoEnEstaRonda = true;
                }
                responseParts.add(Map.of(
                        "functionResponse", Map.of(
                                "name", nombreTool,
                                "response", resultadoTool
                        )
                ));
            }
            if (escaladoEnEstaRonda) {
                String textoParte = extraerTextoDeParts(parts);
                String confirmacion = !textoParte.isBlank()
                        ? textoParte
                        : "Hemos transferido tu consulta a nuestro equipo de soporte. Un asesor humano revisará tu caso y te atenderá en breve.";
                chatService.guardarConfirmacionEscaladoBot(conversacionId, confirmacion)
                        .ifPresent(msg -> publicarMensaje(conversacionId, msg));
                return null;
            }
            contents.add(Map.of("role", "user", "parts", responseParts));
        }

        return "He completado la consulta solicitada. ¿Necesitas ayuda adicional o prefieres hablar con un asesor humano?";
    }

    private List<Map<String, Object>> construirContentsAlternados(List<MensajeChatResponse> historial) {
        List<Map<String, Object>> contents = new ArrayList<>();
        String ultimoRol = null;
        List<Map<String, Object>> partesActuales = null;

        for (MensajeChatResponse m : historial) {
            if (m.getContenido() == null || m.getContenido().isBlank()) continue;
            String rolCodigo = m.getRemitenteRol() != null ? m.getRemitenteRol().name() : "CLIENTE";
            boolean esBot = "ASISTENTE_IA".equals(rolCodigo);
            String rolGemini = esBot ? "model" : "user";

            // Atribuye mensajes de asesores humanos previos para evitar confusión tras Devolver a IA
            String texto = (!esBot && !"CLIENTE".equals(rolCodigo))
                    ? "[Asesor humano previo]: " + m.getContenido()
                    : m.getContenido();

            if (contents.isEmpty() && "model".equals(rolGemini)) {
                continue;
            }

            if (rolGemini.equals(ultimoRol) && partesActuales != null) {
                partesActuales.add(Map.of("text", texto));
            } else {
                partesActuales = new ArrayList<>();
                partesActuales.add(Map.of("text", texto));
                Map<String, Object> turno = new LinkedHashMap<>();
                turno.put("role", rolGemini);
                turno.put("parts", partesActuales);
                contents.add(turno);
                ultimoRol = rolGemini;
            }
        }
        if (contents.isEmpty() || "model".equals(ultimoRol)) {
            return List.of();
        }
        return contents;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extraerPrimerContent(Map<String, Object> respuesta) {
        if (respuesta == null) return null;
        Object candidatesObj = respuesta.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) {
            return null;
        }
        Object first = candidates.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) {
            return null;
        }
        Object content = firstMap.get("content");
        return content instanceof Map<?, ?> ? (Map<String, Object>) content : null;
    }

    private String extraerTextoDeParts(List<Map<String, Object>> parts) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> p : parts) {
            if (Boolean.TRUE.equals(p.get("thought"))) {
                continue;
            }
            Object txt = p.get("text");
            if (txt != null) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(txt);
            }
        }
        return sb.toString().trim();
    }

    private void incrementarTyping(Long conversacionId) {
        int activos = typingActivoPorSala.computeIfAbsent(conversacionId, k -> new AtomicInteger(0)).incrementAndGet();
        if (activos == 1) {
            notificarTyping(conversacionId, true);
        }
    }

    private void decrementarTyping(Long conversacionId) {
        AtomicInteger counter = typingActivoPorSala.get(conversacionId);
        if (counter == null || counter.decrementAndGet() <= 0) {
            typingActivoPorSala.remove(conversacionId);
            notificarTyping(conversacionId, false);
        }
    }

    private void notificarTyping(Long conversacionId, boolean escribiendo) {
        Map<String, Object> payload = Map.of(
                "tipo", "TYPING",
                "escribiendo", escribiendo,
                "remitente", "Electronix AI"
        );
        String destino = "/topic/chat/" + conversacionId + "/estado";
        if (eventPublisher != null) {
            eventPublisher.publicarEvento(destino, payload);
        } else {
            messagingTemplate.convertAndSend(destino, payload);
        }
    }

    private void publicarMensaje(Long conversacionId, MensajeChatResponse mensaje) {
        String destino = "/topic/chat/" + conversacionId;
        if (eventPublisher != null) {
            eventPublisher.publicarMensaje(destino, mensaje);
        } else if (chatPubSubBridge != null) {
            chatPubSubBridge.publicar(destino, mensaje);
        } else {
            messagingTemplate.convertAndSend(destino, mensaje);
        }
    }
}
