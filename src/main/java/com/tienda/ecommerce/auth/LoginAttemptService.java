package com.tienda.ecommerce.auth;

import com.tienda.ecommerce.common.TooManyRequestsException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Control de intentos fallidos de inicio de sesión por cuenta e IP.
 * Usa un script Lua atómico en Redis para compartir el estado entre múltiples réplicas
 * sin carreras de TTL ni bloqueos huérfanos, y degrada a un mapa local acotado si Redis cae.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    static final int MAX_INTENTOS = 5;
    static final int MAX_INTENTOS_IP = 20;
    static final long TIEMPO_BLOQUEO_SEGUNDOS = 60;
    static final int MAX_LOCAL_ENTRIES = 10_000;

    private static final String KEY_ATTEMPTS_PREFIX = "electronix:auth:attempts:";
    private static final String KEY_LOCK_PREFIX = "electronix:auth:lock:";

    private static final DefaultRedisScript<Long> INCREMENT_AND_LOCK_SCRIPT;

    static {
        INCREMENT_AND_LOCK_SCRIPT = new DefaultRedisScript<>();
        INCREMENT_AND_LOCK_SCRIPT.setResultType(Long.class);
        INCREMENT_AND_LOCK_SCRIPT.setScriptText(
                "local count = redis.call('INCR', KEYS[1])\n"
                        + "local ttl = redis.call('TTL', KEYS[1])\n"
                        + "if ttl < 0 then\n"
                        + "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))\n"
                        + "end\n"
                        + "if count >= tonumber(ARGV[2]) then\n"
                        + "  redis.call('SET', KEYS[2], '1', 'EX', tonumber(ARGV[1]))\n"
                        + "end\n"
                        + "return count"
        );
    }

    private static class Intento {
        int contador;
        Instant primerIntento;
        Instant bloqueadoHasta;

        Intento() {
            this.contador = 1;
            this.primerIntento = Instant.now();
        }
    }

    private final ConcurrentHashMap<String, Intento> intentos = new ConcurrentHashMap<>();
    private final Object[] stripedLocks = crearStripedLocks();
    private final StringRedisTemplate redisTemplate;
    private final Counter bloqueosCounter;
    private final boolean distributedEnabled;

    public LoginAttemptService() {
        this(null, null, false);
    }

    @Autowired
    public LoginAttemptService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Value("${app.auth.distributed-rate-limit:true}") boolean distributedEnabled
    ) {
        this.redisTemplate = redisTemplate;
        this.distributedEnabled = distributedEnabled && redisTemplate != null;
        this.bloqueosCounter = meterRegistry == null ? null : Counter.builder("electronix.auth.bloqueos")
                .description("Bloqueos de inicio de sesion por multiples intentos fallidos")
                .register(meterRegistry);
    }

    private static Object[] crearStripedLocks() {
        Object[] locks = new Object[1024];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    public Object lockParaClave(String clave) {
        String norm = normalizarClave(clave);
        if (norm == null) {
            return stripedLocks[0];
        }
        return stripedLocks[Math.floorMod(norm.hashCode(), stripedLocks.length)];
    }

    public void verificarBloqueo(String clave) {
        verificarBloqueoConPrefijo("acct:", clave);
    }

    public void registrarFallo(String clave) {
        registrarFalloConPrefijo("acct:", clave, MAX_INTENTOS);
    }

    public void registrarExito(String clave) {
        String norm = normalizarClavePrefijo("acct:", clave);
        if (norm == null) {
            return;
        }
        intentos.remove(norm);
        if (distributedEnabled) {
            try {
                redisTemplate.delete(List.of(KEY_ATTEMPTS_PREFIX + norm, KEY_LOCK_PREFIX + norm));
            } catch (DataAccessException ex) {
                log.warn("Redis no disponible limpiando intentos para '{}': {}", norm, ex.getMessage());
            }
        }
    }

    public void verificarBloqueoIp(String ip) {
        verificarBloqueoConPrefijo("ip:", ip);
    }

    public void registrarFalloIp(String ip) {
        registrarFalloConPrefijo("ip:", ip, MAX_INTENTOS_IP);
    }

    int localTrackedSize() {
        return intentos.size();
    }

    private void verificarBloqueoConPrefijo(String prefijo, String clave) {
        String norm = normalizarClavePrefijo(prefijo, clave);
        if (norm == null) {
            return;
        }
        if (distributedEnabled) {
            Long ttl = null;
            boolean redisOk = false;
            try {
                ttl = redisTemplate.getExpire(KEY_LOCK_PREFIX + norm, TimeUnit.SECONDS);
                redisOk = true;
            } catch (DataAccessException ex) {
                log.warn("Redis no disponible verificando bloqueo para '{}'. Degradando a control local: {}", norm, ex.getMessage());
            }
            if (redisOk) {
                if (ttl != null && ttl > 0) {
                    throw new TooManyRequestsException(
                            "Demasiados intentos fallidos. Intente nuevamente en " + ttl + " segundos."
                    );
                }
                // Verifica también bloqueo local residual ante un failback reciente de Redis
                verificarBloqueoLocal(norm);
                return;
            }
        }
        verificarBloqueoLocal(norm);
    }

    private void registrarFalloConPrefijo(String prefijo, String clave, int maxIntentos) {
        String norm = normalizarClavePrefijo(prefijo, clave);
        if (norm == null) {
            return;
        }
        if (distributedEnabled) {
            try {
                Long count = redisTemplate.execute(
                        INCREMENT_AND_LOCK_SCRIPT,
                        List.of(KEY_ATTEMPTS_PREFIX + norm, KEY_LOCK_PREFIX + norm),
                        String.valueOf(TIEMPO_BLOQUEO_SEGUNDOS),
                        String.valueOf(maxIntentos)
                );
                if (count != null && count == (long) maxIntentos) {
                    incrementarBloqueos();
                }
                return;
            } catch (DataAccessException ex) {
                log.warn("Redis no disponible registrando fallo para '{}'. Degradando a control local: {}", norm, ex.getMessage());
            }
        }
        registrarFalloLocal(norm, maxIntentos);
    }

    private void verificarBloqueoLocal(String clave) {
        Intento intento = intentos.get(clave);
        if (intento == null) {
            return;
        }
        Instant ahora = Instant.now();
        if (intento.bloqueadoHasta != null) {
            if (ahora.isBefore(intento.bloqueadoHasta)) {
                long segundosRestantes = intento.bloqueadoHasta.getEpochSecond() - ahora.getEpochSecond();
                throw new TooManyRequestsException("Demasiados intentos fallidos. Intente nuevamente en " + Math.max(1, segundosRestantes) + " segundos.");
            }
            intentos.remove(clave);
        } else if (intento.primerIntento.plusSeconds(TIEMPO_BLOQUEO_SEGUNDOS).isBefore(ahora)) {
            intentos.remove(clave);
        }
    }

    private void registrarFalloLocal(String clave, int maxIntentos) {
        Instant ahora = Instant.now();
        if (intentos.size() >= MAX_LOCAL_ENTRIES && !intentos.containsKey(clave)) {
            depurarMapaLocal(ahora);
        }
        intentos.compute(clave, (k, intento) -> {
            if (intento == null) {
                return new Intento();
            }
            if (intento.primerIntento.plusSeconds(TIEMPO_BLOQUEO_SEGUNDOS).isBefore(ahora)
                    && (intento.bloqueadoHasta == null || !ahora.isBefore(intento.bloqueadoHasta))) {
                return new Intento();
            }
            intento.contador++;
            if (intento.contador >= maxIntentos) {
                if (intento.bloqueadoHasta == null) {
                    incrementarBloqueos();
                }
                intento.bloqueadoHasta = ahora.plusSeconds(TIEMPO_BLOQUEO_SEGUNDOS);
            }
            return intento;
        });
    }

    private synchronized void depurarMapaLocal(Instant ahora) {
        Iterator<Map.Entry<String, Intento>> it = intentos.entrySet().iterator();
        while (it.hasNext()) {
            Intento val = it.next().getValue();
            boolean expirado = (val.bloqueadoHasta != null && !ahora.isBefore(val.bloqueadoHasta))
                    || (val.bloqueadoHasta == null && val.primerIntento.plusSeconds(TIEMPO_BLOQUEO_SEGUNDOS).isBefore(ahora));
            if (expirado) {
                it.remove();
            }
        }
        // Si aún supera el límite con entradas activas, desalojar en lote los no bloqueados preservando bloqueos activos
        int objetivoTrasTrim = (int) (MAX_LOCAL_ENTRIES * 0.8);
        if (intentos.size() >= MAX_LOCAL_ENTRIES) {
            Iterator<Map.Entry<String, Intento>> trim = intentos.entrySet().iterator();
            while (trim.hasNext() && intentos.size() > objetivoTrasTrim) {
                Intento val = trim.next().getValue();
                if (val.bloqueadoHasta == null || !ahora.isBefore(val.bloqueadoHasta)) {
                    trim.remove();
                }
            }
        }
    }

    private void incrementarBloqueos() {
        if (bloqueosCounter != null) {
            bloqueosCounter.increment();
        }
    }

    private static String normalizarClavePrefijo(String prefijo, String clave) {
        String norm = normalizarClave(clave);
        return norm == null ? null : prefijo + norm;
    }

    private static String normalizarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            return null;
        }
        return clave.trim().toLowerCase(Locale.ROOT);
    }
}
