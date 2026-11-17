package com.tienda.ecommerce.crm.dto;

import com.tienda.ecommerce.crm.PuntosConfig;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class PuntosConfigDto {

    @NotNull(message = "El factor soles por punto es obligatorio")
    @DecimalMin(value = "0.0001", message = "El factor debe ser mayor a 0")
    private BigDecimal factorSolPorPunto;

    @NotNull(message = "El valor por punto es obligatorio")
    @DecimalMin(value = "0.0001", message = "El valor debe ser mayor a 0")
    private BigDecimal valorPenPorPunto;

    @NotNull(message = "El mínimo de puntos es obligatorio")
    @Min(value = 0, message = "El mínimo de puntos no puede ser negativo")
    private Integer minimoPuntosCanje;

    @NotNull(message = "El porcentaje máximo es obligatorio")
    @Min(value = 1, message = "El porcentaje debe estar entre 1 y 100")
    @Max(value = 100, message = "El porcentaje debe estar entre 1 y 100")
    private Integer maxCoberturaPorcentaje;

    @NotNull(message = "Los puntos de bienvenida son obligatorios")
    @Min(value = 0, message = "Los puntos de bienvenida no pueden ser negativos")
    private Integer puntosBienvenida;

    private Boolean activo;

    public PuntosConfigDto() {
    }

    public static PuntosConfigDto desdeEntidad(PuntosConfig c) {
        PuntosConfigDto dto = new PuntosConfigDto();
        dto.setFactorSolPorPunto(c.getFactorSolPorPunto());
        dto.setValorPenPorPunto(c.getValorPenPorPunto());
        dto.setMinimoPuntosCanje(c.getMinimoPuntosCanje());
        dto.setMaxCoberturaPorcentaje(c.getMaxCoberturaPorcentaje());
        dto.setPuntosBienvenida(c.getPuntosBienvenida());
        dto.setActivo(c.isActivo());
        return dto;
    }

    public BigDecimal getFactorSolPorPunto() {
        return factorSolPorPunto;
    }

    public void setFactorSolPorPunto(BigDecimal factorSolPorPunto) {
        this.factorSolPorPunto = factorSolPorPunto;
    }

    public BigDecimal getValorPenPorPunto() {
        return valorPenPorPunto;
    }

    public void setValorPenPorPunto(BigDecimal valorPenPorPunto) {
        this.valorPenPorPunto = valorPenPorPunto;
    }

    public Integer getMinimoPuntosCanje() {
        return minimoPuntosCanje;
    }

    public void setMinimoPuntosCanje(Integer minimoPuntosCanje) {
        this.minimoPuntosCanje = minimoPuntosCanje;
    }

    public Integer getMaxCoberturaPorcentaje() {
        return maxCoberturaPorcentaje;
    }

    public void setMaxCoberturaPorcentaje(Integer maxCoberturaPorcentaje) {
        this.maxCoberturaPorcentaje = maxCoberturaPorcentaje;
    }

    public Integer getPuntosBienvenida() {
        return puntosBienvenida;
    }

    public void setPuntosBienvenida(Integer puntosBienvenida) {
        this.puntosBienvenida = puntosBienvenida;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}
