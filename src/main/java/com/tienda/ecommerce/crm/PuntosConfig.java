package com.tienda.ecommerce.crm;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Entity
@Table(name = "crm_puntos_config")
public class PuntosConfig {

    @Id
    private Integer id = 1;

    @NotNull
    @DecimalMin(value = "0.0001", message = "El factor de soles por punto debe ser mayor a 0")
    @Column(name = "factor_sol_por_punto", nullable = false, precision = 10, scale = 4)
    private BigDecimal factorSolPorPunto = new BigDecimal("5.0000");

    @NotNull
    @DecimalMin(value = "0.0001", message = "El valor en soles por punto debe ser mayor a 0")
    @Column(name = "valor_pen_por_punto", nullable = false, precision = 10, scale = 4)
    private BigDecimal valorPenPorPunto = new BigDecimal("0.0500");

    @NotNull
    @Min(value = 0, message = "El mínimo de puntos para canje no puede ser negativo")
    @Column(name = "minimo_puntos_canje", nullable = false)
    private int minimoPuntosCanje = 100;

    @NotNull
    @Min(value = 1, message = "El porcentaje máximo de cobertura debe ser entre 1 y 100")
    @Max(value = 100, message = "El porcentaje máximo de cobertura debe ser entre 1 y 100")
    @Column(name = "max_cobertura_porcentaje", nullable = false)
    private int maxCoberturaPorcentaje = 50;

    @NotNull
    @Min(value = 0, message = "Los puntos de bienvenida no pueden ser negativos")
    @Column(name = "puntos_bienvenida", nullable = false)
    private int puntosBienvenida = 50;

    @Column(nullable = false)
    private boolean activo = true;

    public PuntosConfig() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public BigDecimal getFactorSolPorPunto() {
        return factorSolPorPunto;
    }

    public void setFactorSolPorPunto(BigDecimal factorSolPorPunto) {
        this.factorSolPorPunto = factorSolPorPunto;
    }

    public BigDecimal getValorPenPorPunto() {
        return valorPenPorPunto;
    }

    public void setValorPenPorPunto(BigDecimal valorPenPorPunto) {
        this.valorPenPorPunto = valorPenPorPunto;
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

    public int getPuntosBienvenida() {
        return puntosBienvenida;
    }

    public void setPuntosBienvenida(int puntosBienvenida) {
        this.puntosBienvenida = puntosBienvenida;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
