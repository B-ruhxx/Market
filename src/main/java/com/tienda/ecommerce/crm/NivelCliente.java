package com.tienda.ecommerce.crm;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(
    name = "crm_niveles",
    indexes = {
        @Index(name = "idx_niveles_orden", columnList = "orden")
    }
)
public class NivelCliente {

    public static final String BRONCE = "BRONCE";
    public static final String PLATA = "PLATA";
    public static final String ORO = "ORO";

    @Id
    @Column(name = "codigo", length = 20, nullable = false)
    private String codigo;

    @NotBlank
    @Column(name = "nombre", length = 50, nullable = false)
    private String nombre;

    @NotNull
    @Min(0)
    @Column(name = "umbral_pen_anual", precision = 19, scale = 4, nullable = false)
    private BigDecimal umbralPenAnual = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("1.00")
    @Column(name = "multiplicador", precision = 5, scale = 2, nullable = false)
    private BigDecimal multiplicador = BigDecimal.ONE;

    @Column(name = "beneficio_envio", nullable = false)
    private boolean beneficioEnvio = false;

    @NotNull
    @Column(name = "orden", nullable = false)
    private Integer orden = 1;

    public NivelCliente() {
    }

    public NivelCliente(String codigo, String nombre, BigDecimal umbralPenAnual, BigDecimal multiplicador, boolean beneficioEnvio, Integer orden) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.umbralPenAnual = umbralPenAnual != null ? umbralPenAnual : BigDecimal.ZERO;
        this.multiplicador = multiplicador != null ? multiplicador : BigDecimal.ONE;
        this.beneficioEnvio = beneficioEnvio;
        this.orden = orden != null ? orden : 1;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NivelCliente that = (NivelCliente) o;
        return Objects.equals(codigo, that.codigo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codigo);
    }
}
