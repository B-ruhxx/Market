package com.tienda.ecommerce.crm;

import com.tienda.ecommerce.pedido.Pedido;
import com.tienda.ecommerce.usuario.Usuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "crm_puntos_transacciones",
    indexes = {
        @Index(name = "idx_puntos_tx_billetera", columnList = "billetera_id, fecha"),
        @Index(name = "idx_puntos_tx_pedido", columnList = "pedido_id")
    }
)
public class PuntosTransaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billetera_id", nullable = false)
    private PuntosBilletera billetera;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoTransaccionPuntos tipo;

    @Column(nullable = false)
    private int puntos;

    @Min(value = 0, message = "El saldo resultante no puede ser negativo")
    @Column(name = "saldo_resultante", nullable = false)
    private int saldoResultante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @Column(length = 255)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_usuario_id")
    private Usuario adminUsuario;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public PuntosTransaccion() {
    }

    public PuntosTransaccion(PuntosBilletera billetera, TipoTransaccionPuntos tipo, int puntos, int saldoResultante, Pedido pedido, String descripcion, Usuario adminUsuario) {
        this.billetera = billetera;
        this.tipo = tipo;
        this.puntos = puntos;
        this.saldoResultante = saldoResultante;
        this.pedido = pedido;
        this.descripcion = descripcion;
        this.adminUsuario = adminUsuario;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PuntosBilletera getBilletera() {
        return billetera;
    }

    public void setBilletera(PuntosBilletera billetera) {
        this.billetera = billetera;
    }

    public TipoTransaccionPuntos getTipo() {
        return tipo;
    }

    public void setTipo(TipoTransaccionPuntos tipo) {
        this.tipo = tipo;
    }

    public int getPuntos() {
        return puntos;
    }

    public void setPuntos(int puntos) {
        this.puntos = puntos;
    }

    public int getSaldoResultante() {
        return saldoResultante;
    }

    public void setSaldoResultante(int saldoResultante) {
        this.saldoResultante = saldoResultante;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Usuario getAdminUsuario() {
        return adminUsuario;
    }

    public void setAdminUsuario(Usuario adminUsuario) {
        this.adminUsuario = adminUsuario;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
