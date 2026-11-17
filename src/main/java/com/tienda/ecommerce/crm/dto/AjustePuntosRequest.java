package com.tienda.ecommerce.crm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AjustePuntosRequest {

    @NotNull(message = "El usuario es obligatorio")
    private Long usuarioId;

    @NotNull(message = "La cantidad de puntos es obligatoria (puede ser positiva o negativa)")
    @Min(value = -1000000, message = "El ajuste no puede ser menor a -1,000,000")
    @Max(value = 1000000, message = "El ajuste no puede ser mayor a 1,000,000")
    private Integer puntos;

    @NotBlank(message = "El motivo del ajuste es obligatorio")
    @Size(min = 3, max = 250, message = "El motivo debe tener entre 3 y 250 caracteres")
    private String motivo;

    public AjustePuntosRequest() {
    }

    public AjustePuntosRequest(Long usuarioId, Integer puntos, String motivo) {
        this.usuarioId = usuarioId;
        this.puntos = puntos;
        this.motivo = motivo;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Integer getPuntos() {
        return puntos;
    }

    public void setPuntos(Integer puntos) {
        this.puntos = puntos;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
