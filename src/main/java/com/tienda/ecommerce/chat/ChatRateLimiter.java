package com.tienda.ecommerce.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de tasa por usuario para mensajes de chat y consultas a Gemini.
 * Usa un script Lua atómico en Redis cuando está disponible y degrada de forma segura a ventana deslizante local.
 */
@Component
public class ChatRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ChatRateLimiter.class);
    private static final int MAX_MENSAJES_POR_MINUTO = 20;
    private static final int MAX_MENSAJES_ADMIN_POR_MINUTO = 120;
    private static final int MAX_CONSULTAS_IA_POR_MINUTO = 10;
    private static final int MAX_ENTRADAS_LOCALES = 10_000;
    private static final long VENTANA_MS = 60_000L;

    private static final DefaultRedisScript<Long> INCR_EXPIRE_SCRIPT = new DefaultRedisScript<>(
            """
            local c = redis.call('INCR', KEYS[1])
            local t = redis.call('TTL', KEYS[1])
            if c == 1 or t < 0 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            end
            return c
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final Map<String, Deque<Long>> ventanasLocales = new ConcurrentHashMap<>();

    public ChatRateLimiter(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean permitirMensaje(Long usuarioId) {
        return permitirMensaje(usuarioId, false);
    }

    public boolean permitirMensaje(Long usuarioId, boolean esAdmin) {
        int limite = esAdmin ? MAX_MENSAJES_ADMIN_POR_MINUTO : MAX_MENSAJES_POR_MINUTO;
        return permitir("msg:" + usuarioId, limite);
    }

    public boolean permitirConsultaIa(Long usuarioId) {
        return permitir("ia:" + usuarioId, MAX_CONSULTAS_IA_POR_MINUTO);
    }

    private boolean permitir(String clave, int limite) {
        if (redisTemplate != null) {
            try {
                String redisKey = "electronix:ratelimit:chat:" + clave;
                Long count = redisTemplate.execute(INCR_EXPIRE_SCRIPT, List.of(redisKey), "60");
                return count == null || count <= limite;
            } catch (Exception ex) {
                log.debug("Redis rate-limit degradado a memoria local: {}", ex.getMessage());
            }
        }
        return permitirLocal(clave, limite);
    }

    private boolean permitirLocal(String clave, int limite) {
        long ahora = System.currentTimeMillis();
        if (ventanasLocales.size() > MAX_ENTRADAS_LOCALES) {
            ventanasLocales.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    e.getValue().removeIf(ts -> ahora - ts > VENTANA_MS);
                    return e.getValue().isEmpty();
                }
            });
        }
        Deque<Long> deque = ventanasLocales.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && ahora - deque.peekFirst() > VENTANA_MS) {
                deque.pollFirst();
            }
            if (deque.size() >= limite) {
                return false;
            }
            deque.addLast(ahora);
            return true;
        }
    }
}
