package com.tienda.ecommerce.crm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class SimularCanjePuntosRequest {

    @NotNull(message = "La cantidad de puntos es obligatoria")
    @Min(value = 1, message = "Debes ingresar al menos 1 punto para simular canje")
    private Integer puntosACanjear;

    @NotNull(message = "El subtotal es obligatorio")
    @DecimalMin(value = "0.01", message = "El subtotal debe ser mayor a 0")
    private BigDecimal subtotalBasePen;

    public SimularCanjePuntosRequest() {
    }

    public SimularCanjePuntosRequest(Integer puntosACanjear, BigDecimal subtotalBasePen) {
        this.puntosACanjear = puntosACanjear;
        this.subtotalBasePen = subtotalBasePen;
    }

    public Integer getPuntosACanjear() {
        return puntosACanjear;
    }

    public void setPuntosACanjear(Integer puntosACanjear) {
        this.puntosACanjear = puntosACanjear;
    }

    public BigDecimal getSubtotalBasePen() {
        return subtotalBasePen;
    }

    public void setSubtotalBasePen(BigDecimal subtotalBasePen) {
        this.subtotalBasePen = subtotalBasePen;
    }
}
