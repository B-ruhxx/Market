package com.tienda.ecommerce.crm.dto;

import java.math.BigDecimal;

public class SimularCanjePuntosResponse {

    private boolean valido;
    private int puntosDisponibles;
    private int puntosSolicitados;
    private int puntosPermitidos;
    private BigDecimal descuentoBasePen;
    private BigDecimal totalConDescuentoPen;
    private String mensaje;

    public SimularCanjePuntosResponse() {
    }

    public static SimularCanjePuntosResponse invalido(int puntosDisponibles, int puntosSolicitados, String mensaje) {
        SimularCanjePuntosResponse res = new SimularCanjePuntosResponse();
        res.setValido(false);
        res.setPuntosDisponibles(puntosDisponibles);
        res.setPuntosSolicitados(puntosSolicitados);
        res.setPuntosPermitidos(0);
        res.setDescuentoBasePen(BigDecimal.ZERO);
        res.setMensaje(mensaje);
        return res;
    }

    public static SimularCanjePuntosResponse exitoso(
            int puntosDisponibles,
            int puntosSolicitados,
            int puntosPermitidos,
            BigDecimal descuentoBasePen,
            BigDecimal totalConDescuentoPen,
            String mensaje
    ) {
        SimularCanjePuntosResponse res = new SimularCanjePuntosResponse();
        res.setValido(true);
        res.setPuntosDisponibles(puntosDisponibles);
        res.setPuntosSolicitados(puntosSolicitados);
        res.setPuntosPermitidos(puntosPermitidos);
        res.setDescuentoBasePen(descuentoBasePen);
        res.setTotalConDescuentoPen(totalConDescuentoPen);
        res.setMensaje(mensaje);
        return res;
    }

    public boolean isValido() {
        return valido;
    }

    public void setValido(boolean valido) {
        this.valido = valido;
    }

    public int getPuntosDisponibles() {
        return puntosDisponibles;
    }

    public void setPuntosDisponibles(int puntosDisponibles) {
        this.puntosDisponibles = puntosDisponibles;
    }

    public int getPuntosSolicitados() {
        return puntosSolicitados;
    }

    public void setPuntosSolicitados(int puntosSolicitados) {
        this.puntosSolicitados = puntosSolicitados;
    }

    public int getPuntosPermitidos() {
        return puntosPermitidos;
    }

    public void setPuntosPermitidos(int puntosPermitidos) {
        this.puntosPermitidos = puntosPermitidos;
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

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
