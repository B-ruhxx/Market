package com.tienda.ecommerce.crm.dto;

import com.tienda.ecommerce.crm.TipoDescuento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CrearCuponRequest {

    @NotBlank(message = "El código de cupón es obligatorio")
    private String codigo;

    private String descripcion;

    @NotNull(message = "El tipo de descuento es obligatorio")
    private TipoDescuento tipoDescuento;

    @NotNull(message = "El valor del descuento es obligatorio")
    @DecimalMin(value = "0.0001", message = "El valor debe ser mayor a 0")
    private BigDecimal valor;

    private BigDecimal montoMinimoPen;

    private BigDecimal topeDescuentoPen;

    private Integer limiteUsosTotal;

    @NotNull(message = "El límite por usuario es obligatorio")
    @Min(value = 1, message = "El límite por usuario debe ser al menos 1")
    private Integer limitePorUsuario = 1;

    private Boolean permiteCombinarPuntos = false;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDateTime fechaInicio;

    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDateTime fechaFin;

    private Boolean activo = true;

    public CrearCuponRequest() {
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public TipoDescuento getTipoDescuento() {
        return tipoDescuento;
    }

    public void setTipoDescuento(TipoDescuento tipoDescuento) {
        this.tipoDescuento = tipoDescuento;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public BigDecimal getMontoMinimoPen() {
        return montoMinimoPen;
    }

    public void setMontoMinimoPen(BigDecimal montoMinimoPen) {
        this.montoMinimoPen = montoMinimoPen;
    }

    public BigDecimal getTopeDescuentoPen() {
        return topeDescuentoPen;
    }

    public void setTopeDescuentoPen(BigDecimal topeDescuentoPen) {
        this.topeDescuentoPen = topeDescuentoPen;
    }

    public Integer getLimiteUsosTotal() {
        return limiteUsosTotal;
    }

    public void setLimiteUsosTotal(Integer limiteUsosTotal) {
        this.limiteUsosTotal = limiteUsosTotal;
    }

    public Integer getLimitePorUsuario() {
        return limitePorUsuario;
    }

    public void setLimitePorUsuario(Integer limitePorUsuario) {
        this.limitePorUsuario = limitePorUsuario;
    }

    public Boolean getPermiteCombinarPuntos() {
        return permiteCombinarPuntos;
    }

    public void setPermiteCombinarPuntos(Boolean permiteCombinarPuntos) {
        this.permiteCombinarPuntos = permiteCombinarPuntos;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDateTime fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDateTime getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDateTime fechaFin) {
        this.fechaFin = fechaFin;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}
