package com.tienda.ecommerce.chat;

import com.tienda.ecommerce.chat.dto.MensajeChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Publica mensajes y eventos de estado del chat únicamente después de confirmar
 * la transacción activa (afterCommit) y los propaga a todas las réplicas vía Redis Pub/Sub.
 */
@Component
public class ChatEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatPubSubBridge chatPubSubBridge;

    public ChatEventPublisher(
            @Autowired(required = false) SimpMessagingTemplate messagingTemplate,
            @Autowired(required = false) ChatPubSubBridge chatPubSubBridge
    ) {
        this.messagingTemplate = messagingTemplate;
        this.chatPubSubBridge = chatPubSubBridge;
    }

    public void publicarMensaje(String destino, MensajeChatResponse mensaje) {
        ejecutarTrasCommit(() -> {
            if (chatPubSubBridge != null) {
                chatPubSubBridge.publicar(destino, mensaje);
            } else if (messagingTemplate != null) {
                messagingTemplate.convertAndSend(destino, mensaje);
            }
        });
    }

    public void publicarEvento(String destino, Map<String, Object> payload) {
        ejecutarTrasCommit(() -> {
            if (chatPubSubBridge != null) {
                chatPubSubBridge.publicarEvento(destino, payload);
            } else if (messagingTemplate != null) {
                messagingTemplate.convertAndSend(destino, payload);
            }
        });
    }

    private void ejecutarTrasCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
