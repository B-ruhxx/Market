package com.tienda.ecommerce.crm;

import com.tienda.ecommerce.pedido.Pedido;
import com.tienda.ecommerce.usuario.Usuario;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "crm_cupones_usos",
    indexes = {
        @Index(name = "idx_cupon_usos_usuario", columnList = "usuario_id"),
        @Index(name = "idx_cupon_usos_cupon", columnList = "cupon_id")
    }
)
public class CuponUso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cupon_id", nullable = false)
    private PromocionCupon cupon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @Column(name = "descuento_aplicado_pen", nullable = false, precision = 19, scale = 4)
    private BigDecimal descuentoAplicadoPen;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_uso", nullable = false, updatable = false)
    private LocalDateTime fechaUso = LocalDateTime.now();

    public CuponUso() {
    }

    public CuponUso(PromocionCupon cupon, Usuario usuario, Pedido pedido, BigDecimal descuentoAplicadoPen) {
        this.cupon = cupon;
        this.usuario = usuario;
        this.pedido = pedido;
        this.descuentoAplicadoPen = descuentoAplicadoPen;
        this.activo = true;
        this.fechaUso = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PromocionCupon getCupon() {
        return cupon;
    }

    public void setCupon(PromocionCupon cupon) {
        this.cupon = cupon;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
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
