package com.tienda.ecommerce.crm.dto;

import com.tienda.ecommerce.crm.TipoDescuento;
import java.math.BigDecimal;

public class ValidarCuponResponse {

    private boolean valido;
    private String codigo;
    private String descripcion;
    private TipoDescuento tipoDescuento;
    private BigDecimal valor;
    private BigDecimal descuentoBasePen;
    private BigDecimal totalConDescuentoPen;
    private boolean permiteCombinarPuntos;
    private String mensaje;

    public ValidarCuponResponse() {
    }

    public static ValidarCuponResponse invalido(String codigo, String mensaje) {
        ValidarCuponResponse res = new ValidarCuponResponse();
        res.setValido(false);
        res.setCodigo(codigo);
        res.setDescuentoBasePen(BigDecimal.ZERO);
        res.setMensaje(mensaje);
        return res;
    }

    public static ValidarCuponResponse exitoso(
            String codigo,
            String descripcion,
            TipoDescuento tipoDescuento,
            BigDecimal valor,
            BigDecimal descuentoBasePen,
            BigDecimal totalConDescuentoPen,
            boolean permiteCombinarPuntos,
            String mensaje
    ) {
        ValidarCuponResponse res = new ValidarCuponResponse();
        res.setValido(true);
        res.setCodigo(codigo);
        res.setDescripcion(descripcion);
        res.setTipoDescuento(tipoDescuento);
        res.setValor(valor);
        res.setDescuentoBasePen(descuentoBasePen);
        res.setTotalConDescuentoPen(totalConDescuentoPen);
        res.setPermiteCombinarPuntos(permiteCombinarPuntos);
        res.setMensaje(mensaje);
        return res;
    }

    public boolean isValido() {
        return valido;
    }

    public void setValido(boolean valido) {
        this.valido = valido;
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

    public BigDecimal getDescuentoBasePen() {
        return descuentoBasePen;
    }

    public void setDescuentoBasePen(BigDecimal descuentoBasePen) {
        this.descuentoBasePen = descuentoBasePen;
    }

    public BigDecimal getTotalConDescuentoPen() {
        return totalConDescuentoPen;
    }

    public void setTotalConDescuentoPen(BigDecimal totalConDescuentoPen) {
        this.totalConDescuentoPen = totalConDescuentoPen;
    }

    public boolean isPermiteCombinarPuntos() {
        return permiteCombinarPuntos;
    }

    public void setPermiteCombinarPuntos(boolean permiteCombinarPuntos) {
        this.permiteCombinarPuntos = permiteCombinarPuntos;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
