package com.tienda.ecommerce.crm;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "crm_cupones",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cupones_codigo", columnNames = {"codigo"})
    },
    indexes = {
        @Index(name = "idx_cupones_activo_fechas", columnList = "activo, fecha_inicio, fecha_fin")
    }
)
public class PromocionCupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El código de cupón es obligatorio")
    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(length = 255)
    private String descripcion;

    @NotNull(message = "El tipo de descuento es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_descuento", nullable = false, length = 20)
    private TipoDescuento tipoDescuento;

    @NotNull(message = "El valor del descuento es obligatorio")
    @DecimalMin(value = "0.0001", message = "El valor debe ser mayor a 0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal valor;

    @Column(name = "monto_minimo_pen", nullable = false, precision = 19, scale = 4)
    private BigDecimal montoMinimoPen = BigDecimal.ZERO;

    @Column(name = "tope_descuento_pen", precision = 19, scale = 4)
    private BigDecimal topeDescuentoPen;

    @Column(name = "limite_usos_total")
    private Integer limiteUsosTotal;

    @Column(name = "usos_actuales", nullable = false)
    private int usosActuales = 0;

    @Column(name = "limite_por_usuario", nullable = false)
    private int limitePorUsuario = 1;

    @Column(name = "permite_combinar_puntos", nullable = false)
    private boolean permiteCombinarPuntos = false;

    @NotNull(message = "La fecha de inicio es obligatoria")
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @NotNull(message = "La fecha de fin es obligatoria")
    @Column(name = "fecha_fin", nullable = false)
    private LocalDateTime fechaFin;

    @Column(nullable = false)
    private boolean activo = true;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public PromocionCupon() {
    }

    public boolean esVigente() {
        LocalDateTime ahora = LocalDateTime.now();
        return activo && (fechaInicio == null || !ahora.isBefore(fechaInicio))
                && (fechaFin == null || !ahora.isAfter(fechaFin));
    }

    public boolean tieneCupo() {
        return limiteUsosTotal == null || usosActuales < limiteUsosTotal;
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
        this.codigo = codigo != null ? codigo.trim().toUpperCase() : null;
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
        this.montoMinimoPen = montoMinimoPen != null ? montoMinimoPen : BigDecimal.ZERO;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
