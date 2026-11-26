package com.tienda.ecommerce.chat.dto;

import jakarta.validation.constraints.NotBlank;

public class CambiarModoChatRequest {

    @NotBlank(message = "El modo de atención es obligatorio")
    private String modoAtencion;

    public CambiarModoChatRequest() {
    }

    public CambiarModoChatRequest(String modoAtencion) {
        this.modoAtencion = modoAtencion;
    }

    public String getModoAtencion() {
        return modoAtencion;
    }

    public void setModoAtencion(String modoAtencion) {
        this.modoAtencion = modoAtencion;
    }
}
