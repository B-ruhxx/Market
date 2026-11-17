package com.tienda.ecommerce.crm.dto;

import com.tienda.ecommerce.crm.PromocionCupon;
import com.tienda.ecommerce.crm.TipoDescuento;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CuponResponse {

    private Long id;
    private String codigo;
    private String descripcion;
    private TipoDescuento tipoDescuento;
    private BigDecimal valor;
    private BigDecimal montoMinimoPen;
    private BigDecimal topeDescuentoPen;
    private Integer limiteUsosTotal;
    private int usosActuales;
    private int limitePorUsuario;
    private boolean permiteCombinarPuntos;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private boolean activo;
    private boolean vigente;
    private boolean agotado;

    public CuponResponse() {
    }

    public static CuponResponse desdeEntidad(PromocionCupon c) {
        CuponResponse res = new CuponResponse();
        res.setId(c.getId());
        res.setCodigo(c.getCodigo());
        res.setDescripcion(c.getDescripcion());
        res.setTipoDescuento(c.getTipoDescuento());
        res.setValor(c.getValor());
        res.setMontoMinimoPen(c.getMontoMinimoPen());
        res.setTopeDescuentoPen(c.getTopeDescuentoPen());
        res.setLimiteUsosTotal(c.getLimiteUsosTotal());
        res.setUsosActuales(c.getUsosActuales());
        res.setLimitePorUsuario(c.getLimitePorUsuario());
        res.setPermiteCombinarPuntos(c.isPermiteCombinarPuntos());
        res.setFechaInicio(c.getFechaInicio());
        res.setFechaFin(c.getFechaFin());
        res.setActivo(c.isActivo());
        res.setVigente(c.esVigente());
        res.setAgotado(!c.tieneCupo());
        return res;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public int getUsosActuales() {
        return usosActuales;
    }

    public void setUsosActuales(int usosActuales) {
        this.usosActuales = usosActuales;
    }

    public int getLimitePorUsuario() {
        return limitePorUsuario;
    }

    public void setLimitePorUsuario(int limitePorUsuario) {
        this.limitePorUsuario = limitePorUsuario;
    }

    public boolean isPermiteCombinarPuntos() {
        return permiteCombinarPuntos;
    }

    public void setPermiteCombinarPuntos(boolean permiteCombinarPuntos) {
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

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public boolean isVigente() {
        return vigente;
    }

    public void setVigente(boolean vigente) {
        this.vigente = vigente;
    }

    public boolean isAgotado() {
        return agotado;
    }

    public void setAgotado(boolean agotado) {
        this.agotado = agotado;
    }
}
