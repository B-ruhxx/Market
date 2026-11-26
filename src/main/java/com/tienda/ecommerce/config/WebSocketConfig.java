package com.tienda.ecommerce.config;

import com.tienda.ecommerce.chat.ChatAuthChannelInterceptor;
import com.tienda.ecommerce.chat.ChatSessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatAuthChannelInterceptor chatAuthInterceptor;

    @Autowired(required = false)
    private ChatSessionRegistry chatSessionRegistry;

    public WebSocketConfig(ChatAuthChannelInterceptor chatAuthInterceptor) {
        this.chatAuthInterceptor = chatAuthInterceptor;
    }

    @Value("${app.cors.allowed-origins:http://localhost:8080,http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] patterns = CorsOriginValidator.parseAndValidate(allowedOrigins).toArray(String[]::new);

        // Endpoint WS: origenes configurados (incluye tuneles), con JWT en CONNECT
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(patterns)
                .withSockJS();

        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(patterns);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broker simple: envia mensajes a los clientes suscritos
        config.enableSimpleBroker("/topic", "/queue");
        // Prefijo app: recibe mensajes de los clientes
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Interceptor auth: valida JWT en CONNECT del chat
        registration.interceptors(chatAuthInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(16 * 1024);
        registry.setSendBufferSizeLimit(256 * 1024);
        registry.setSendTimeLimit(15_000);
        if (chatSessionRegistry != null) {
            registry.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    chatSessionRegistry.registrarSocket(session);
                    super.afterConnectionEstablished(session);
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                    chatSessionRegistry.removerSocket(session.getId());
                    super.afterConnectionClosed(session, closeStatus);
                }
            });
        }
    }
}
