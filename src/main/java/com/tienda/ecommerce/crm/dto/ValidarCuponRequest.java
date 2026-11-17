package com.tienda.ecommerce.crm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class ValidarCuponRequest {

    @NotBlank(message = "El código es obligatorio")
    private String codigo;

    @NotNull(message = "El subtotal es obligatorio")
    @DecimalMin(value = "0.00", message = "El subtotal no puede ser negativo")
    private BigDecimal subtotalBasePen;

    public ValidarCuponRequest() {
    }

    public ValidarCuponRequest(String codigo, BigDecimal subtotalBasePen) {
        this.codigo = codigo;
        this.subtotalBasePen = subtotalBasePen;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public BigDecimal getSubtotalBasePen() {
        return subtotalBasePen;
    }

    public void setSubtotalBasePen(BigDecimal subtotalBasePen) {
        this.subtotalBasePen = subtotalBasePen;
    }
}
