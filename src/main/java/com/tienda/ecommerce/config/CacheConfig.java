package com.tienda.ecommerce.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tienda.ecommerce.producto.dto.CategoriaResponse;
import com.tienda.ecommerce.producto.dto.ProductoResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.cache.CacheMetricsRegistrar;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuración de Caché Distribuido con Redis (Patrón Cache-Aside transaccional).
 * - transactionAware(): difiere invalidaciones y escrituras hasta después del commit de PostgreSQL.
 * - Serialización tipada estricta sin DefaultTyping polimórfico (elimina vectores RCE / type-confusion).
 * - Graceful Degradation en GET, PUT y EVICT para mantener disponibilidad ante caídas de Redis.
 */
@Configuration
@EnableCaching
@Profile("!test")
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    public static final String CACHE_CATEGORIAS = "categorias";
    public static final String CACHE_PRODUCTOS = "productos";
    public static final String CACHE_PRODUCTOS_DESTACADOS = "productos_destacados";

    private final Counter evictErrorsCounter;

    public CacheConfig() {
        this((MeterRegistry) null);
    }

    @Autowired
    public CacheConfig(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.evictErrorsCounter = meterRegistry == null ? null : Counter.builder("electronix.cache.evict.errors")
                .description("Errores degradados al invalidar claves en Redis")
                .register(meterRegistry);
    }

    private static ObjectMapper crearObjectMapperTipado() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Serializador estricto para {@code categorias} (admite {@link CategoriaResponse} y {@code List<CategoriaResponse>})
     * sin habilitar deserialización polimórfica ({@code @class}).
     */
    private static final class TypedCategoriaRedisSerializer implements RedisSerializer<Object> {
        private final ObjectMapper mapper;
        private final JavaType listType;

        TypedCategoriaRedisSerializer(ObjectMapper mapper) {
            this.mapper = mapper;
            this.listType = mapper.getTypeFactory().constructCollectionType(List.class, CategoriaResponse.class);
        }

        @Override
        public byte[] serialize(Object value) throws SerializationException {
            if (value == null) {
                return new byte[0];
            }
            if (!(value instanceof CategoriaResponse) && !(value instanceof List<?>)) {
                throw new SerializationException("Tipo no permitido para caché de categorías: " + value.getClass().getName());
            }
            try {
                return mapper.writeValueAsBytes(value);
            } catch (Exception ex) {
                throw new SerializationException("Error serializando categoría en caché", ex);
            }
        }

        @Override
        public Object deserialize(byte[] bytes) throws SerializationException {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            int idx = 0;
            while (idx < bytes.length && Character.isWhitespace(bytes[idx])) {
                idx++;
            }
            if (idx >= bytes.length) {
                return null;
            }
            try {
                if (bytes[idx] == '[') {
                    return mapper.readValue(bytes, listType);
                }
                if (bytes[idx] == '{') {
                    return mapper.readValue(bytes, CategoriaResponse.class);
                }
                throw new SerializationException("Formato JSON inesperado en caché de categorías");
            } catch (SerializationException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new SerializationException("Error deserializando categoría desde caché", ex);
            }
        }
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper plainMapper = crearObjectMapperTipado();

        RedisSerializer<Object> categoriasSerializer = new TypedCategoriaRedisSerializer(plainMapper);
        Jackson2JsonRedisSerializer<ProductoResponse> productoSerializer =
                new Jackson2JsonRedisSerializer<>(plainMapper, ProductoResponse.class);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(categoriasSerializer));

        RedisCacheConfiguration productoConfig = defaultConfig
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(productoSerializer));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(CACHE_CATEGORIAS, defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put(CACHE_PRODUCTOS, productoConfig);
        cacheConfigurations.put(CACHE_PRODUCTOS_DESTACADOS, productoConfig.entryTtl(Duration.ofMinutes(5)));

        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory)
                .withStatisticsCollector(CacheStatisticsCollector.create());
        CacheErrorHandler resilientHandler = errorHandler();

        RedisCacheManager manager = new RedisCacheManager(writer, defaultConfig, cacheConfigurations) {
            @Override
            protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
                RedisCacheConfiguration effective = cacheConfig != null ? cacheConfig : defaultConfig;
                return new ResilientRedisCache(name, writer, effective, resilientHandler);
            }
        };
        manager.setTransactionAware(true);
        manager.afterPropertiesSet();
        return manager;
    }

    /**
     * Subclase de RedisCache colocada dentro de TransactionAwareCacheDecorator para que
     * errores de Redis ocurridos en afterCommit() (tras commit en PostgreSQL) degraden
     * con métrica/log sin devolver HTTP 500 al cliente.
     */
    private static final class ResilientRedisCache extends RedisCache {
        private final CacheErrorHandler handler;

        private ResilientRedisCache(
                String name,
                RedisCacheWriter cacheWriter,
                RedisCacheConfiguration cacheConfig,
                CacheErrorHandler handler
        ) {
            super(name, cacheWriter, cacheConfig);
            this.handler = handler;
        }

        @Override
        protected Object lookup(Object key) {
            try {
                return super.lookup(key);
            } catch (RuntimeException ex) {
                handler.handleCacheGetError(ex, this, key);
                return null;
            }
        }

        @Override
        public void put(Object key, Object value) {
            try {
                super.put(key, value);
            } catch (RuntimeException ex) {
                handler.handleCachePutError(ex, this, key, value);
            }
        }

        @Override
        public Cache.ValueWrapper putIfAbsent(Object key, Object value) {
            try {
                return super.putIfAbsent(key, value);
            } catch (RuntimeException ex) {
                handler.handleCachePutError(ex, this, key, value);
                return null;
            }
        }

        @Override
        public void evict(Object key) {
            try {
                super.evict(key);
            } catch (RuntimeException ex) {
                handler.handleCacheEvictError(ex, this, key);
            }
        }

        @Override
        public boolean evictIfPresent(Object key) {
            try {
                return super.evictIfPresent(key);
            } catch (RuntimeException ex) {
                handler.handleCacheEvictError(ex, this, key);
                return false;
            }
        }

        @Override
        public void clear() {
            try {
                super.clear();
            } catch (RuntimeException ex) {
                handler.handleCacheClearError(ex, this);
            }
        }
    }

    @Bean
    SmartInitializingSingleton redisCacheMetricsBinder(
            RedisCacheManager cacheManager,
            ObjectProvider<CacheMetricsRegistrar> metricsRegistrarProvider
    ) {
        return () -> {
            CacheMetricsRegistrar registrar = metricsRegistrarProvider.getIfAvailable();
            if (registrar == null) {
                return;
            }
            for (String cacheName : List.of(CACHE_CATEGORIAS, CACHE_PRODUCTOS, CACHE_PRODUCTOS_DESTACADOS)) {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    registrar.bindCacheToRegistry(cache);
                }
            }
        };
    }

    /**
     * Tolerancia a fallas: si Redis no responde en GET, PUT o EVICT, registra métrica de alerta
     * y degrada suavemente para no tumbar el checkout ni la administración.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.warn("Redis GET error en cache '{}' para key '{}'. Degradando a PostgreSQL. Causa: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
                log.warn("Redis PUT error en cache '{}' para key '{}'. Causa: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                if (evictErrorsCounter != null) {
                    evictErrorsCounter.increment();
                }
                log.error("Error desalojando clave '{}' de cache '{}' (operación degradada sin abortar BD): {}",
                        key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                if (evictErrorsCounter != null) {
                    evictErrorsCounter.increment();
                }
                log.error("Error limpiando cache '{}' (operación degradada sin abortar BD): {}",
                        cache.getName(), exception.getMessage());
            }
        };
    }
}
