package com.tienda.ecommerce.crm.dto;

import com.tienda.ecommerce.crm.SegmentoCliente;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ClienteFila360Dto {

    private Long usuarioId;
    private String nombre;
    private String email;
    private String nivel;
    private SegmentoCliente segmento;
    private String segmentoLabel;
    private String segmentoBadgeClass;
    private BigDecimal ltvPen;
    private Integer totalPedidos;
    private Integer diasDesdeUltimaCompra;
    private Integer saldoPuntos;
    private LocalDateTime fechaUltimaCompra;

    public ClienteFila360Dto() {
    }

    public ClienteFila360Dto(Long usuarioId, String nombre, String email, String nivel, SegmentoCliente segmento,
                             BigDecimal ltvPen, Integer totalPedidos, Integer diasDesdeUltimaCompra,
                             Integer saldoPuntos, LocalDateTime fechaUltimaCompra) {
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.email = email;
        this.nivel = nivel;
        this.segmento = segmento;
        if (segmento != null) {
            this.segmentoLabel = segmento.getLabel();
            this.segmentoBadgeClass = segmento.getBadgeClass();
        }
        this.ltvPen = ltvPen;
        this.totalPedidos = totalPedidos;
        this.diasDesdeUltimaCompra = diasDesdeUltimaCompra;
        this.saldoPuntos = saldoPuntos;
        this.fechaUltimaCompra = fechaUltimaCompra;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNivel() {
        return nivel;
    }

    public void setNivel(String nivel) {
        this.nivel = nivel;
    }

    public SegmentoCliente getSegmento() {
        return segmento;
    }

    public void setSegmento(SegmentoCliente segmento) {
        this.segmento = segmento;
        if (segmento != null) {
            this.segmentoLabel = segmento.getLabel();
            this.segmentoBadgeClass = segmento.getBadgeClass();
        }
    }

    public String getSegmentoLabel() {
        return segmentoLabel;
    }

    public void setSegmentoLabel(String segmentoLabel) {
        this.segmentoLabel = segmentoLabel;
    }

    public String getSegmentoBadgeClass() {
        return segmentoBadgeClass;
    }

    public void setSegmentoBadgeClass(String segmentoBadgeClass) {
        this.segmentoBadgeClass = segmentoBadgeClass;
    }

    public BigDecimal getLtvPen() {
        return ltvPen;
    }

    public void setLtvPen(BigDecimal ltvPen) {
        this.ltvPen = ltvPen;
    }

    public Integer getTotalPedidos() {
        return totalPedidos;
    }

    public void setTotalPedidos(Integer totalPedidos) {
        this.totalPedidos = totalPedidos;
    }

    public Integer getDiasDesdeUltimaCompra() {
        return diasDesdeUltimaCompra;
    }

    public void setDiasDesdeUltimaCompra(Integer diasDesdeUltimaCompra) {
        this.diasDesdeUltimaCompra = diasDesdeUltimaCompra;
    }

    public Integer getSaldoPuntos() {
        return saldoPuntos;
    }

    public void setSaldoPuntos(Integer saldoPuntos) {
        this.saldoPuntos = saldoPuntos;
    }

    public LocalDateTime getFechaUltimaCompra() {
        return fechaUltimaCompra;
    }

    public void setFechaUltimaCompra(LocalDateTime fechaUltimaCompra) {
        this.fechaUltimaCompra = fechaUltimaCompra;
    }
}
