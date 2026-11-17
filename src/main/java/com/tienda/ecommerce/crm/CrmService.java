package com.tienda.ecommerce.crm;

import com.tienda.ecommerce.crm.dto.*;
import com.tienda.ecommerce.pedido.Pedido;
import com.tienda.ecommerce.usuario.Usuario;

import java.math.BigDecimal;
import java.util.List;

public interface CrmService {

    // --- Cupones (Fase 1) ---
    ValidarCuponResponse validarCupon(String codigo, Long usuarioId, BigDecimal subtotalBasePen);

    CuponResponse crearCupon(CrearCuponRequest request);

    List<CuponResponse> listarCupones();

    CuponResponse cambiarEstado(Long id, boolean activo);

    void eliminarCupon(Long id);

    List<CuponUsoResponse> listarUsos(Long cuponId);

    BigDecimal validarYConsumirCupo(String codigo, Usuario usuario, BigDecimal subtotalBasePen);

    BigDecimal validarYConsumirCupo(String codigo, Usuario usuario, BigDecimal subtotalBasePen, boolean combinaConPuntos);

    void registrarUsoCupon(String codigo, Usuario usuario, Pedido pedido, BigDecimal descuentoPen);

    BigDecimal aplicarCuponAtomico(String codigo, Usuario usuario, Pedido pedido, BigDecimal subtotalBasePen);

    @Deprecated
    default BigDecimal aplicarCuponAtorico(String codigo, Usuario usuario, Pedido pedido, BigDecimal subtotalBasePen) {
        return aplicarCuponAtomico(codigo, usuario, pedido, subtotalBasePen);
    }

    void revertirCupon(Long pedidoId);

    // --- Puntos y Fidelización (Fase 2) ---
    BilleteraResponse obtenerBilleteraUsuario(Long usuarioId);

    SimularCanjePuntosResponse simularCanje(Long usuarioId, Integer puntosACanjear, BigDecimal subtotalBasePen);

    BigDecimal validarYConsumirPuntos(Usuario usuario, Integer puntosACanjear, BigDecimal subtotalBasePen);

    void registrarCanjePuntos(Usuario usuario, Pedido pedido, Integer puntosCanjeados, BigDecimal descuentoPen);

    void acreditarPuntosPorCompra(Pedido pedido);

    void revertirPuntos(Long pedidoId);

    void inicializarBilleteraBienvenida(Usuario usuario);

    PuntosConfigDto obtenerConfigPuntos();

    PuntosConfigDto actualizarConfigPuntos(PuntosConfigDto dto);

    BilleteraResponse ajustarPuntosManual(AjustePuntosRequest request, Long adminId);

    // --- Niveles de Fidelidad (Fase 3) ---
    List<NivelClienteDto> listarNiveles();

    NivelClienteDto actualizarNivel(String codigo, ActualizarNivelRequest request);

    NivelCliente evaluarNivelUsuario(Long usuarioId);

    // --- Ficha 360° del Consumidor & CRM (Fase 4) ---
    ClienteResumen360Dto obtenerResumen360(Long usuarioId);

    List<ClienteFila360Dto> listarClientes360(SegmentoCliente filtroSegmento);

    ClienteNotaDto agregarNota(Long usuarioId, Long adminId, CrearClienteNotaRequest request);

    List<ClienteNotaDto> listarNotas(Long usuarioId);

    SegmentoCliente calcularSegmento(int totalPedidos, Integer diasDesdeUltimaCompra, String nivelCodigo, BigDecimal ltvPen);
}
