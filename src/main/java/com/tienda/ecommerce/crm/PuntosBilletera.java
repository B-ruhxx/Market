package com.tienda.ecommerce.crm;

import com.tienda.ecommerce.usuario.Usuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "crm_puntos_billetera",
    indexes = {
        @Index(name = "idx_billetera_usuario", columnList = "usuario_id")
    }
)
public class PuntosBilletera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Min(value = 0, message = "El saldo de puntos no puede ser negativo")
    @Column(name = "saldo_puntos", nullable = false)
    private int saldoPuntos = 0;

    @Min(value = 0, message = "Los puntos históricos no pueden ser negativos")
    @Column(name = "puntos_historicos", nullable = false)
    private int puntosHistoricos = 0;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion = LocalDateTime.now();

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "nivel_codigo", referencedColumnName = "codigo", nullable = false)
    private NivelCliente nivel;

    public PuntosBilletera() {
    }

    public PuntosBilletera(Usuario usuario, int saldoInicial) {
        this.usuario = usuario;
        this.saldoPuntos = Math.max(0, saldoInicial);
        this.puntosHistoricos = Math.max(0, saldoInicial);
        this.fechaActualizacion = LocalDateTime.now();
    }

    public PuntosBilletera(Usuario usuario, int saldoInicial, NivelCliente nivel) {
        this.usuario = usuario;
        this.saldoPuntos = Math.max(0, saldoInicial);
        this.puntosHistoricos = Math.max(0, saldoInicial);
        this.nivel = nivel;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public int getSaldoPuntos() {
        return saldoPuntos;
    }

    public void setSaldoPuntos(int saldoPuntos) {
        this.saldoPuntos = saldoPuntos;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public int getPuntosHistoricos() {
        return puntosHistoricos;
    }

    public void setPuntosHistoricos(int puntosHistoricos) {
        this.puntosHistoricos = puntosHistoricos;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public NivelCliente getNivel() {
        return nivel;
    }

    public void setNivel(NivelCliente nivel) {
        this.nivel = nivel;
    }
}
