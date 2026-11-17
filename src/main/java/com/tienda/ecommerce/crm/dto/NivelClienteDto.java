package com.tienda.ecommerce.crm.dto;

import com.tienda.ecommerce.crm.NivelCliente;
import java.math.BigDecimal;

public class NivelClienteDto {

    private String codigo;
    private String nombre;
    private BigDecimal umbralPenAnual;
    private BigDecimal multiplicador;
    private boolean beneficioEnvio;
    private Integer orden;

    public NivelClienteDto() {
    }

    public NivelClienteDto(String codigo, String nombre, BigDecimal umbralPenAnual, BigDecimal multiplicador, boolean beneficioEnvio, Integer orden) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.umbralPenAnual = umbralPenAnual;
        this.multiplicador = multiplicador;
        this.beneficioEnvio = beneficioEnvio;
        this.orden = orden;
    }

    public static NivelClienteDto fromEntity(NivelCliente entidad) {
        if (entidad == null) {
            return null;
        }
        return new NivelClienteDto(
                entidad.getCodigo(),
                entidad.getNombre(),
                entidad.getUmbralPenAnual(),
                entidad.getMultiplicador(),
                entidad.isBeneficioEnvio(),
                entidad.getOrden()
        );
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
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

    public Integer getOrden() {
        return orden;
    }

    public void setOrden(Integer orden) {
        this.orden = orden;
    }
}
