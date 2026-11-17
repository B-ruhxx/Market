package com.tienda.ecommerce.crm.dto;

import com.tienda.ecommerce.crm.SegmentoCliente;
import com.tienda.ecommerce.pedido.dto.PedidoResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ClienteResumen360Dto {

    // Identidad y datos de contacto
    private Long usuarioId;
    private String nombre;
    private String email;
    private String telefono;
    private String tipoDocumento;
    private String numeroDocumento;
    private LocalDateTime fechaRegistro;

    // Nivel VIP de Fidelización
    private String nivelActual;
    private BigDecimal multiplicador;
    private boolean beneficioEnvioGratis;

    // Métricas de Comportamiento Comercial (RFM / LTV)
    private BigDecimal ltvPen;
    private Integer totalPedidos;
    private BigDecimal ticketPromedioPen;
    private Integer diasDesdeUltimaCompra;
    private LocalDateTime fechaUltimaCompra;
    private SegmentoCliente segmento;
    private String segmentoLabel;
    private String segmentoBadgeClass;

    // Métricas CRM de Puntos y Promociones
    private Integer saldoPuntos;
    private BigDecimal valorEstimadoPuntosPen;
    private Integer puntosHistoricos;
    private Integer cuponesUsados;
    private BigDecimal totalDescontadoCuponesPen;

    // Listados de Actividad
    private List<PedidoResponse> ultimosPedidos = new ArrayList<>();
    private List<PuntosTransaccionDto> ultimasTransacciones = new ArrayList<>();
    private List<ClienteNotaDto> notas = new ArrayList<>();

    public ClienteResumen360Dto() {
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

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public String getNivelActual() {
        return nivelActual;
    }

    public void setNivelActual(String nivelActual) {
        this.nivelActual = nivelActual;
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

    public BigDecimal getTicketPromedioPen() {
        return ticketPromedioPen;
    }

    public void setTicketPromedioPen(BigDecimal ticketPromedioPen) {
        this.ticketPromedioPen = ticketPromedioPen;
    }

    public Integer getDiasDesdeUltimaCompra() {
        return diasDesdeUltimaCompra;
    }

    public void setDiasDesdeUltimaCompra(Integer diasDesdeUltimaCompra) {
        this.diasDesdeUltimaCompra = diasDesdeUltimaCompra;
    }

    public LocalDateTime getFechaUltimaCompra() {
        return fechaUltimaCompra;
    }

    public void setFechaUltimaCompra(LocalDateTime fechaUltimaCompra) {
        this.fechaUltimaCompra = fechaUltimaCompra;
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

    public Integer getSaldoPuntos() {
        return saldoPuntos;
    }

    public void setSaldoPuntos(Integer saldoPuntos) {
        this.saldoPuntos = saldoPuntos;
    }

    public BigDecimal getValorEstimadoPuntosPen() {
        return valorEstimadoPuntosPen;
    }

    public void setValorEstimadoPuntosPen(BigDecimal valorEstimadoPuntosPen) {
        this.valorEstimadoPuntosPen = valorEstimadoPuntosPen;
    }

    public Integer getPuntosHistoricos() {
        return puntosHistoricos;
    }

    public void setPuntosHistoricos(Integer puntosHistoricos) {
        this.puntosHistoricos = puntosHistoricos;
    }

    public Integer getCuponesUsados() {
        return cuponesUsados;
    }

    public void setCuponesUsados(Integer cuponesUsados) {
        this.cuponesUsados = cuponesUsados;
    }

    public BigDecimal getTotalDescontadoCuponesPen() {
        return totalDescontadoCuponesPen;
    }

    public void setTotalDescontadoCuponesPen(BigDecimal totalDescontadoCuponesPen) {
        this.totalDescontadoCuponesPen = totalDescontadoCuponesPen;
    }

    public List<PedidoResponse> getUltimosPedidos() {
        return ultimosPedidos;
    }

    public void setUltimosPedidos(List<PedidoResponse> ultimosPedidos) {
        this.ultimosPedidos = ultimosPedidos;
    }

    public List<PuntosTransaccionDto> getUltimasTransacciones() {
        return ultimasTransacciones;
    }

    public void setUltimasTransacciones(List<PuntosTransaccionDto> ultimasTransacciones) {
        this.ultimasTransacciones = ultimasTransacciones;
    }

    public List<ClienteNotaDto> getNotas() {
        return notas;
    }

    public void setNotas(List<ClienteNotaDto> notas) {
        this.notas = notas;
    }
}
