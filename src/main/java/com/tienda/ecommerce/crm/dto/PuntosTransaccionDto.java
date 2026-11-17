package com.tienda.ecommerce.crm.dto;

import com.tienda.ecommerce.crm.PuntosTransaccion;
import com.tienda.ecommerce.crm.TipoTransaccionPuntos;
import java.time.LocalDateTime;

public class PuntosTransaccionDto {

    private Long id;
    private TipoTransaccionPuntos tipo;
    private int puntos;
    private int saldoResultante;
    private Long pedidoId;
    private String descripcion;
    private LocalDateTime fecha;

    public PuntosTransaccionDto() {
    }

    public static PuntosTransaccionDto desdeEntidad(PuntosTransaccion t) {
        PuntosTransaccionDto dto = new PuntosTransaccionDto();
        dto.setId(t.getId());
        dto.setTipo(t.getTipo());
        dto.setPuntos(t.getPuntos());
        dto.setSaldoResultante(t.getSaldoResultante());
        dto.setPedidoId(t.getPedido() != null ? t.getPedido().getId() : null);
        dto.setDescripcion(t.getDescripcion());
        dto.setFecha(t.getFecha());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(Long pedidoId) {
        this.pedidoId = pedidoId;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
