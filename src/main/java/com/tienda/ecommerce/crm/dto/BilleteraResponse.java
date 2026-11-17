package com.tienda.ecommerce.crm.dto;

import java.math.BigDecimal;
import java.util.List;

public class BilleteraResponse {

    private Long usuarioId;
    private String usuarioNombre;
    private String usuarioEmail;
    private int saldoPuntos;
    private BigDecimal equivalentePen;
    private int puntosHistoricos;
    private int minimoPuntosCanje;
    private int maxCoberturaPorcentaje;
    private BigDecimal valorPenPorPunto;
    private List<PuntosTransaccionDto> transacciones;

    // Campos de Nivel de Fidelidad (Fase 3)
    private String nivelCodigo;
    private String nivelNombre;
    private BigDecimal multiplicador;
    private boolean beneficioEnvioGratis;
    private BigDecimal gastoAnualPen;
    private String siguienteNivelNombre;
    private BigDecimal umbralSiguienteNivelPen;
    private BigDecimal montoFaltanteSiguienteNivelPen;
    private BigDecimal porcentajeProgresoNivel;

    public BilleteraResponse() {
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

    public int getSaldoPuntos() {
        return saldoPuntos;
    }

    public void setSaldoPuntos(int saldoPuntos) {
        this.saldoPuntos = saldoPuntos;
    }

    public BigDecimal getEquivalentePen() {
        return equivalentePen;
    }

    public void setEquivalentePen(BigDecimal equivalentePen) {
        this.equivalentePen = equivalentePen;
    }

    // Alias para compatibilidad de contrato con frontend
    public BigDecimal getValorEstimadoPen() {
        return equivalentePen;
    }

    public void setValorEstimadoPen(BigDecimal valorEstimadoPen) {
        this.equivalentePen = valorEstimadoPen;
    }

    public int getPuntosHistoricos() {
        return puntosHistoricos;
    }

    public void setPuntosHistoricos(int puntosHistoricos) {
        this.puntosHistoricos = puntosHistoricos;
    }

    public int getMinimoPuntosCanje() {
        return minimoPuntosCanje;
    }

    public void setMinimoPuntosCanje(int minimoPuntosCanje) {
        this.minimoPuntosCanje = minimoPuntosCanje;
    }

    public int getMaxCoberturaPorcentaje() {
        return maxCoberturaPorcentaje;
    }

    public void setMaxCoberturaPorcentaje(int maxCoberturaPorcentaje) {
        this.maxCoberturaPorcentaje = maxCoberturaPorcentaje;
    }

    public BigDecimal getValorPenPorPunto() {
        return valorPenPorPunto;
    }

    public void setValorPenPorPunto(BigDecimal valorPenPorPunto) {
        this.valorPenPorPunto = valorPenPorPunto;
    }

    public List<PuntosTransaccionDto> getTransacciones() {
        return transacciones;
    }

    public void setTransacciones(List<PuntosTransaccionDto> transacciones) {
        this.transacciones = transacciones;
    }

    // Alias para compatibilidad de contrato con frontend
    public List<PuntosTransaccionDto> getTransaccionesRecientes() {
        return transacciones;
    }

    public void setTransaccionesRecientes(List<PuntosTransaccionDto> transaccionesRecientes) {
        this.transacciones = transaccionesRecientes;
    }

    public String getNivelCodigo() {
        return nivelCodigo;
    }

    public void setNivelCodigo(String nivelCodigo) {
        this.nivelCodigo = nivelCodigo;
    }

    public String getNivelNombre() {
        return nivelNombre;
    }

    public void setNivelNombre(String nivelNombre) {
        this.nivelNombre = nivelNombre;
    }

    public BigDecimal getMultiplicador() {
        return multiplicador;
    }

    public void setMultiplicador(BigDecimal multiplicador) {
        this.multiplicador = multiplicador;
    }

    public boolean isBeneficioEnvioGratis() {
        return beneficioEnvioGratis;
    }

    public void setBeneficioEnvioGratis(boolean beneficioEnvioGratis) {
        this.beneficioEnvioGratis = beneficioEnvioGratis;
    }

    public BigDecimal getGastoAnualPen() {
        return gastoAnualPen;
    }

    public void setGastoAnualPen(BigDecimal gastoAnualPen) {
        this.gastoAnualPen = gastoAnualPen;
    }

    public String getSiguienteNivelNombre() {
        return siguienteNivelNombre;
    }

    public void setSiguienteNivelNombre(String siguienteNivelNombre) {
        this.siguienteNivelNombre = siguienteNivelNombre;
    }

    public BigDecimal getUmbralSiguienteNivelPen() {
        return umbralSiguienteNivelPen;
    }

    public void setUmbralSiguienteNivelPen(BigDecimal umbralSiguienteNivelPen) {
        this.umbralSiguienteNivelPen = umbralSiguienteNivelPen;
    }

    public BigDecimal getMontoFaltanteSiguienteNivelPen() {
        return montoFaltanteSiguienteNivelPen;
    }

    public void setMontoFaltanteSiguienteNivelPen(BigDecimal montoFaltanteSiguienteNivelPen) {
        this.montoFaltanteSiguienteNivelPen = montoFaltanteSiguienteNivelPen;
    }

    public BigDecimal getPorcentajeProgresoNivel() {
        return porcentajeProgresoNivel;
    }

    public void setPorcentajeProgresoNivel(BigDecimal porcentajeProgresoNivel) {
        this.porcentajeProgresoNivel = porcentajeProgresoNivel;
    }
}
