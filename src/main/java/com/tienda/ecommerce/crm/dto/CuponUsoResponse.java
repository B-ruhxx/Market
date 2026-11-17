package com.tienda.ecommerce.crm.dto;

import com.tienda.ecommerce.crm.CuponUso;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CuponUsoResponse {

    private Long id;
    private Long cuponId;
    private String cuponCodigo;
    private Long usuarioId;
    private String usuarioNombre;
    private String usuarioEmail;
    private Long pedidoId;
    private BigDecimal descuentoAplicadoPen;
    private boolean activo;
    private LocalDateTime fechaUso;

    public CuponUsoResponse() {
    }

    public static CuponUsoResponse desdeEntidad(CuponUso u) {
        CuponUsoResponse res = new CuponUsoResponse();
        res.setId(u.getId());
        res.setCuponId(u.getCupon() != null ? u.getCupon().getId() : null);
        res.setCuponCodigo(u.getCupon() != null ? u.getCupon().getCodigo() : null);
        res.setUsuarioId(u.getUsuario() != null ? u.getUsuario().getId() : null);
        res.setUsuarioNombre(u.getUsuario() != null ? u.getUsuario().getNombreCompleto() : null);
        res.setUsuarioEmail(u.getUsuario() != null ? u.getUsuario().getEmail() : null);
        res.setPedidoId(u.getPedido() != null ? u.getPedido().getId() : null);
        res.setDescuentoAplicadoPen(u.getDescuentoAplicadoPen());
        res.setActivo(u.isActivo());
        res.setFechaUso(u.getFechaUso());
        return res;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCuponId() {
        return cuponId;
    }

    public void setCuponId(Long cuponId) {
        this.cuponId = cuponId;
    }

    public String getCuponCodigo() {
        return cuponCodigo;
    }

    public void setCuponCodigo(String cuponCodigo) {
        this.cuponCodigo = cuponCodigo;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getUsuarioNombre() {
        return usuarioNombre;
    }

    public void setUsuarioNombre(String usuarioNombre) {
        this.usuarioNombre = usuarioNombre;
    }

    public String getUsuarioEmail() {
        return usuarioEmail;
    }

    public void setUsuarioEmail(String usuarioEmail) {
        this.usuarioEmail = usuarioEmail;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(Long pedidoId) {
        this.pedidoId = pedidoId;
    }

    public BigDecimal getDescuentoAplicadoPen() {
        return descuentoAplicadoPen;
    }

    public void setDescuentoAplicadoPen(BigDecimal descuentoAplicadoPen) {
        this.descuentoAplicadoPen = descuentoAplicadoPen;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaUso() {
        return fechaUso;
    }

    public void setFechaUso(LocalDateTime fechaUso) {
        this.fechaUso = fechaUso;
    }
}
