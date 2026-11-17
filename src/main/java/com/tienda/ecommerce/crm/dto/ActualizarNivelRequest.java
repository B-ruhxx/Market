package com.tienda.ecommerce.crm.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class ActualizarNivelRequest {

    @NotBlank(message = "El nombre del nivel es obligatorio")
    @Size(max = 50, message = "El nombre no puede exceder 50 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9ÁÉÍÓÚáéíóúñÑ +_.-]+$", message = "El nombre del nivel contiene caracteres no permitidos")
    private String nombre;

    @NotNull(message = "El umbral de gasto anual es obligatorio")
    @DecimalMin(value = "0.00", message = "El umbral anual no puede ser negativo")
    private BigDecimal umbralPenAnual;

    @NotNull(message = "El multiplicador de puntos es obligatorio")
    @DecimalMin(value = "1.00", message = "El multiplicador mínimo debe ser 1.00")
    @DecimalMax(value = "10.00", message = "El multiplicador máximo permitido es 10.00")
    private BigDecimal multiplicador;

    private boolean beneficioEnvio;

    public ActualizarNivelRequest() {
    }

    public ActualizarNivelRequest(String nombre, BigDecimal umbralPenAnual, BigDecimal multiplicador, boolean beneficioEnvio) {
        this.nombre = nombre;
        this.umbralPenAnual = umbralPenAnual;
        this.multiplicador = multiplicador;
        this.beneficioEnvio = beneficioEnvio;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getUmbralPenAnual() {
        return umbralPenAnual;
    }

    public void setUmbralPenAnual(BigDecimal umbralPenAnual) {
        this.umbralPenAnual = umbralPenAnual;
    }

    public BigDecimal getMultiplicador() {
        return multiplicador;
    }

    public void setMultiplicador(BigDecimal multiplicador) {
        this.multiplicador = multiplicador;
    }

    public boolean isBeneficioEnvio() {
        return beneficioEnvio;
    }

    public void setBeneficioEnvio(boolean beneficioEnvio) {
        this.beneficioEnvio = beneficioEnvio;
    }
}
