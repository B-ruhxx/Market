package com.tienda.ecommerce.chat;

import com.tienda.ecommerce.chat.dto.ConversacionResponse;
import com.tienda.ecommerce.chat.dto.MensajeChatRequest;
import com.tienda.ecommerce.chat.dto.MensajeChatResponse;

import java.util.List;
import java.util.Optional;

public interface ChatService {
    record ResultadoMensajeChat(MensajeChatResponse mensaje, boolean cambioAModoHumano) {}

    ConversacionResponse obtenerOCrearConversacionCliente(Long clienteId);
    List<ConversacionResponse> listarConversacionesAdmin();
    List<MensajeChatResponse> obtenerHistorial(Long conversacionId, Long usuarioId, boolean esAdmin);
    MensajeChatResponse procesarYGuardarMensaje(MensajeChatRequest request, Long remitenteAutenticadoId, boolean esAdmin);
    default ResultadoMensajeChat procesarYGuardarMensajeConResultado(MensajeChatRequest request, Long remitenteAutenticadoId, boolean esAdmin) {
        return new ResultadoMensajeChat(procesarYGuardarMensaje(request, remitenteAutenticadoId, esAdmin), false);
    }
    ConversacionResponse cambiarModoAtencion(Long conversacionId, String modoAtencion, Long usuarioId, boolean esAdmin);
    Optional<ConversacionResponse> escalarAHumanoDesdeBot(Long conversacionId, String motivo);
    MensajeChatResponse guardarMensajeBot(Long conversacionId, String contenido);
    Optional<MensajeChatResponse> guardarMensajeBotSiModoBot(Long conversacionId, String contenido);
    default Optional<MensajeChatResponse> guardarConfirmacionEscaladoBot(Long conversacionId, String contenido) {
        return Optional.empty();
    }
    String obtenerModoAtencion(Long conversacionId);
    List<MensajeChatResponse> obtenerHistorialRecienteParaIa(Long conversacionId, int maxMensajes);
}
