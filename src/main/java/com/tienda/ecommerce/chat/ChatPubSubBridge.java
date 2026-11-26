package com.tienda.ecommerce.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.ecommerce.chat.dto.MensajeChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Puente Redis Pub/Sub para propagar mensajes y eventos STOMP entre múltiples réplicas del backend.
 * Si Redis no está disponible, degrada entregando el mensaje a los clientes conectados en la instancia local.
 */
@Component
@Profile("!test")
public class ChatPubSubBridge implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ChatPubSubBridge.class);
    public static final String CHANNEL = "electronix:chat:broadcast";
    public static final String EVENT_CHANNEL = "electronix:chat:events";

    public record ChatBroadcastEvent(String destino, MensajeChatResponse mensaje) {
    }

    public record ChatEstadoBroadcastEvent(String destino, Map<String, Object> payload) {
    }

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public ChatPubSubBridge(
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public void publicar(String destino, MensajeChatResponse mensaje) {
        if (redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(new ChatBroadcastEvent(destino, mensaje));
                redisTemplate.convertAndSend(CHANNEL, json);
                return;
            } catch (Exception ex) {
                log.warn("No se pudo propagar chat por Redis Pub/Sub. Entregando en instancia local: {}", ex.getMessage());
            }
        }
        messagingTemplate.convertAndSend(destino, mensaje);
    }

    public void publicarEvento(String destino, Map<String, Object> payload) {
        if (redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(new ChatEstadoBroadcastEvent(destino, payload));
                redisTemplate.convertAndSend(EVENT_CHANNEL, json);
                return;
            } catch (Exception ex) {
                log.warn("No se pudo propagar evento de chat por Redis Pub/Sub. Entregando en instancia local: {}", ex.getMessage());
            }
        }
        messagingTemplate.convertAndSend(destino, payload);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            byte[] channelBytes = (message.getChannel() != null && message.getChannel().length > 0)
                    ? message.getChannel()
                    : pattern;
            String channel = channelBytes != null ? new String(channelBytes, StandardCharsets.UTF_8) : CHANNEL;
            String raw = new String(message.getBody(), StandardCharsets.UTF_8);
            if (EVENT_CHANNEL.equals(channel)) {
                ChatEstadoBroadcastEvent evento = objectMapper.readValue(raw, ChatEstadoBroadcastEvent.class);
                if (evento.destino() != null && evento.payload() != null) {
                    messagingTemplate.convertAndSend(evento.destino(), evento.payload());
                }
            } else {
                ChatBroadcastEvent evento = objectMapper.readValue(raw, ChatBroadcastEvent.class);
                if (evento.destino() != null && evento.mensaje() != null) {
                    messagingTemplate.convertAndSend(evento.destino(), evento.mensaje());
                }
            }
        } catch (Exception ex) {
            log.warn("Error procesando evento de chat desde Redis Pub/Sub: {}", ex.getMessage());
        }
    }

    @Configuration
    @Profile("!test")
    static class RedisChatListenerConfig {

        @Bean
        RedisMessageListenerContainer chatRedisListenerContainer(
                RedisConnectionFactory connectionFactory,
                ChatPubSubBridge bridge
        ) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.addMessageListener(bridge, new ChannelTopic(CHANNEL));
            container.addMessageListener(bridge, new ChannelTopic(EVENT_CHANNEL));
            return container;
        }
    }
}
