package com.tienda.ecommerce.crm;

import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.crm.dto.*;
import com.tienda.ecommerce.pedido.Pedido;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrmServiceTest {

    @Mock
    private PromocionCuponRepository cuponRepository;

    @Mock
    private CuponUsoRepository cuponUsoRepository;

    @Mock
    private PuntosBilleteraRepository puntosBilleteraRepository;

    @Mock
    private PuntosTransaccionRepository puntosTransaccionRepository;

    @Mock
    private PuntosConfigRepository puntosConfigRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NivelClienteRepository nivelClienteRepository;

    @Mock
    private com.tienda.ecommerce.pedido.PedidoRepository pedidoRepository;

    @Mock
    private ClienteNotaRepository clienteNotaRepository;

    @InjectMocks
    private CrmServiceImpl crmService;

    private PromocionCupon cuponPorcentaje;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(10L);
        usuario.setEmail("test@crm.com");

        cuponPorcentaje = new PromocionCupon();
        cuponPorcentaje.setId(1L);
        cuponPorcentaje.setCodigo("PROMO10");
        cuponPorcentaje.setDescripcion("10% de descuento");
        cuponPorcentaje.setTipoDescuento(TipoDescuento.PORCENTAJE);
        cuponPorcentaje.setValor(new BigDecimal("10.00"));
        cuponPorcentaje.setMontoMinimoPen(new BigDecimal("100.00"));
        cuponPorcentaje.setTopeDescuentoPen(new BigDecimal("50.00"));
        cuponPorcentaje.setLimiteUsosTotal(100);
        cuponPorcentaje.setUsosActuales(5);
        cuponPorcentaje.setLimitePorUsuario(1);
        cuponPorcentaje.setFechaInicio(LocalDateTime.now().minusDays(1));
        cuponPorcentaje.setFechaFin(LocalDateTime.now().plusDays(10));
        cuponPorcentaje.setActivo(true);

        lenient().when(nivelClienteRepository.findById("BRONCE")).thenReturn(Optional.of(
                new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1)
        ));
        lenient().when(pedidoRepository.calcularGastoAnualPen(any(), any())).thenReturn(BigDecimal.ZERO);
        lenient().when(nivelClienteRepository.findByUmbralPenAnualLessThanEqualOrderByOrdenDesc(any())).thenReturn(
                List.of(new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1))
        );
    }

    @Test
    @DisplayName("Debe validar cupón porcentual exitosamente")
    void validarCupon_exitoso_porcentaje() {
        when(cuponRepository.findByCodigoIgnoreCase("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));
        when(cuponUsoRepository.countByCuponIdAndUsuarioIdAndActivoTrue(1L, 10L)).thenReturn(0L);

        ValidarCuponResponse res = crmService.validarCupon("PROMO10", 10L, new BigDecimal("200.00"));

        assertTrue(res.isValido());
        assertEquals("PROMO10", res.getCodigo());
        assertEquals(new BigDecimal("20.0000"), res.getDescuentoBasePen());
        assertEquals(new BigDecimal("180.0000"), res.getTotalConDescuentoPen());
    }

    @Test
    @DisplayName("Debe limitar el descuento al tope configurado")
    void validarCupon_conTope_limitaDescuento() {
        when(cuponRepository.findByCodigoIgnoreCase("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));
        when(cuponUsoRepository.countByCuponIdAndUsuarioIdAndActivoTrue(1L, 10L)).thenReturn(0L);

        // 10% de 1000 = 100, pero el tope es 50
        ValidarCuponResponse res = crmService.validarCupon("PROMO10", 10L, new BigDecimal("1000.00"));

        assertTrue(res.isValido());
        assertEquals(new BigDecimal("50.0000"), res.getDescuentoBasePen());
        assertEquals(new BigDecimal("950.0000"), res.getTotalConDescuentoPen());
    }

    @Test
    @DisplayName("Debe rechazar si no se alcanza la compra mínima")
    void validarCupon_montoMinimoNoAlcanzado_invalido() {
        when(cuponRepository.findByCodigoIgnoreCase("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));

        ValidarCuponResponse res = crmService.validarCupon("PROMO10", 10L, new BigDecimal("50.00"));

        assertFalse(res.isValido());
        assertTrue(res.getMensaje().contains("monto mínimo"));
    }

    @Test
    @DisplayName("Debe rechazar cupón si está expirado")
    void validarCupon_expirado_invalido() {
        cuponPorcentaje.setFechaFin(LocalDateTime.now().minusHours(1));
        when(cuponRepository.findByCodigoIgnoreCase("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));

        ValidarCuponResponse res = crmService.validarCupon("PROMO10", 10L, new BigDecimal("200.00"));

        assertFalse(res.isValido());
        assertTrue(res.getMensaje().contains("expirado"));
    }

    @Test
    @DisplayName("Debe rechazar cupón si el usuario superó su límite de usos")
    void validarCupon_limiteUsuarioAlcanzado_invalido() {
        when(cuponRepository.findByCodigoIgnoreCase("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));
        when(cuponUsoRepository.countByCuponIdAndUsuarioIdAndActivoTrue(1L, 10L)).thenReturn(1L);

        ValidarCuponResponse res = crmService.validarCupon("PROMO10", 10L, new BigDecimal("200.00"));

        assertFalse(res.isValido());
        assertTrue(res.getMensaje().contains("límite de usos"));
    }

    @Test
    @DisplayName("Debe crear un nuevo cupón con código normalizado a mayúsculas")
    void crearCupon_exitoso() {
        CrearCuponRequest req = new CrearCuponRequest();
        req.setCodigo("navidad2026");
        req.setTipoDescuento(TipoDescuento.MONTO_FIJO);
        req.setValor(new BigDecimal("30.00"));
        req.setFechaInicio(LocalDateTime.now());
        req.setFechaFin(LocalDateTime.now().plusDays(15));

        when(cuponRepository.existsByCodigoIgnoreCase("NAVIDAD2026")).thenReturn(false);
        when(cuponRepository.save(any(PromocionCupon.class))).thenAnswer(inv -> {
            PromocionCupon c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });

        CuponResponse res = crmService.crearCupon(req);

        assertNotNull(res);
        assertEquals("NAVIDAD2026", res.getCodigo());
        assertEquals(TipoDescuento.MONTO_FIJO, res.getTipoDescuento());
    }

    @Test
    @DisplayName("Debe fallar al crear cupón si fechaFin es igual o menor a fechaInicio")
    void crearCupon_fechaFinInvalida_lanzaBadRequest() {
        LocalDateTime ahora = LocalDateTime.now();
        CrearCuponRequest req = new CrearCuponRequest();
        req.setCodigo("PROMOFECHA");
        req.setTipoDescuento(TipoDescuento.MONTO_FIJO);
        req.setValor(new BigDecimal("10.00"));
        req.setFechaInicio(ahora);
        req.setFechaFin(ahora); // Igual

        assertThrows(BadRequestException.class, () -> crmService.crearCupon(req));
    }

    @Test
    @DisplayName("Debe fallar al crear cupón con limitePorUsuario menor a 1")
    void crearCupon_limiteUsuarioInvalido_lanzaBadRequest() {
        LocalDateTime ahora = LocalDateTime.now();
        CrearCuponRequest req = new CrearCuponRequest();
        req.setCodigo("PROMOLIM");
        req.setTipoDescuento(TipoDescuento.MONTO_FIJO);
        req.setValor(new BigDecimal("10.00"));
        req.setFechaInicio(ahora);
        req.setFechaFin(ahora.plusDays(5));
        req.setLimitePorUsuario(0);

        assertThrows(BadRequestException.class, () -> crmService.crearCupon(req));
    }

    @Test
    @DisplayName("Debe rechazar creación de cupón con ENVIO_GRATIS")
    void crearCupon_envioGratis_lanzaBadRequest() {
        LocalDateTime ahora = LocalDateTime.now();
        CrearCuponRequest req = new CrearCuponRequest();
        req.setCodigo("PROMOENVIO");
        req.setTipoDescuento(TipoDescuento.ENVIO_GRATIS);
        req.setValor(new BigDecimal("10.00"));
        req.setFechaInicio(ahora);
        req.setFechaFin(ahora.plusDays(5));

        assertThrows(BadRequestException.class, () -> crmService.crearCupon(req));
    }

    @Test
    @DisplayName("Debe rechazar validación de cupón ENVIO_GRATIS sin consumir cupo")
    void validarCupon_envioGratis_invalido() {
        cuponPorcentaje.setTipoDescuento(TipoDescuento.ENVIO_GRATIS);
        when(cuponRepository.findByCodigoIgnoreCase("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));

        ValidarCuponResponse res = crmService.validarCupon("PROMO10", 10L, new BigDecimal("200.00"));

        assertFalse(res.isValido());
        assertTrue(res.getMensaje().contains("envío gratis"));
    }

    @Test
    @DisplayName("Debe rechazar validarYConsumirCupo con ENVIO_GRATIS")
    void validarYConsumirCupo_envioGratis_lanzaBadRequest() {
        cuponPorcentaje.setTipoDescuento(TipoDescuento.ENVIO_GRATIS);
        when(cuponRepository.findByCodigoConLock("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));

        assertThrows(BadRequestException.class, () -> crmService.validarYConsumirCupo("PROMO10", usuario, new BigDecimal("200.00")));
    }

    @Test
    @DisplayName("Debe desacoplar validarYConsumirCupo y registrarUsoCupon correctamente")
    void flujoDesacoplado_validarYConsumir_luegoRegistrarUso() {
        Pedido pedido = new Pedido();
        pedido.setId(500L);

        when(cuponRepository.findByCodigoConLock("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));
        when(cuponUsoRepository.countByCuponIdAndUsuarioIdAndActivoTrue(1L, 10L)).thenReturn(0L);
        when(cuponRepository.findByCodigoIgnoreCase("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));

        BigDecimal desc = crmService.validarYConsumirCupo("PROMO10", usuario, new BigDecimal("200.00"));

        assertEquals(new BigDecimal("20.0000"), desc);
        assertEquals(6, cuponPorcentaje.getUsosActuales());
        verify(cuponRepository).save(cuponPorcentaje);

        // Registro del uso vinculando el pedido ya persistido
        crmService.registrarUsoCupon("PROMO10", usuario, pedido, desc);
        verify(cuponUsoRepository).save(any(CuponUso.class));
    }

    @Test
    @DisplayName("Debe aplicar cupón atómicamente incrementando usos y registrando CuponUso")
    void aplicarCuponAtorico_incrementaUsosYRegistraUso() {
        Pedido pedido = new Pedido();
        pedido.setId(500L);

        when(cuponRepository.findByCodigoConLock("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));
        when(cuponUsoRepository.countByCuponIdAndUsuarioIdAndActivoTrue(1L, 10L)).thenReturn(0L);
        when(cuponRepository.findByCodigoIgnoreCase("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));

        BigDecimal desc = crmService.aplicarCuponAtomico("PROMO10", usuario, pedido, new BigDecimal("200.00"));

        assertEquals(new BigDecimal("20.0000"), desc);
        assertEquals(6, cuponPorcentaje.getUsosActuales());
        verify(cuponRepository).save(cuponPorcentaje);
        verify(cuponUsoRepository).save(any(CuponUso.class));
    }

    @Test
    @DisplayName("Debe revertir cupón al cancelar pedido decrementando usos con bloqueo pesimista")
    void revertirCupon_decrementaUsosYDesactivaUso() {
        CuponUso uso = new CuponUso(cuponPorcentaje, usuario, new Pedido(), new BigDecimal("20.00"));
        uso.setId(88L);
        uso.setActivo(true);
        cuponPorcentaje.setUsosActuales(6);

        when(cuponUsoRepository.findByPedidoId(500L)).thenReturn(Optional.of(uso));
        when(cuponRepository.findByCodigoConLock("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));

        crmService.revertirCupon(500L);

        assertFalse(uso.isActivo());
        assertEquals(5, cuponPorcentaje.getUsosActuales());
        verify(cuponUsoRepository).save(uso);
        verify(cuponRepository).save(cuponPorcentaje);
    }

    @Test
    @DisplayName("Debe hacer soft-delete al eliminar cupón con usos históricos aunque usosActuales sea 0")
    void eliminarCupon_conUsosHistoricos_softDelete() {
        cuponPorcentaje.setUsosActuales(0);
        when(cuponRepository.findById(1L)).thenReturn(Optional.of(cuponPorcentaje));
        when(cuponUsoRepository.existsByCuponId(1L)).thenReturn(true);

        crmService.eliminarCupon(1L);

        assertFalse(cuponPorcentaje.isActivo());
        verify(cuponRepository).save(cuponPorcentaje);
        verify(cuponRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Debe hacer hard-delete al eliminar cupón sin usos históricos")
    void eliminarCupon_sinUsosHistoricos_hardDelete() {
        cuponPorcentaje.setUsosActuales(0);
        when(cuponRepository.findById(1L)).thenReturn(Optional.of(cuponPorcentaje));
        when(cuponUsoRepository.existsByCuponId(1L)).thenReturn(false);

        crmService.eliminarCupon(1L);

        verify(cuponRepository).delete(cuponPorcentaje);
    }

    // =========================================================================
    // TESTS PUNTOS Y RECOMPENSAS (FASE 2)
    // =========================================================================

    private PuntosConfig crearConfigPuntosMock() {
        PuntosConfig cfg = new PuntosConfig();
        cfg.setId(1);
        cfg.setFactorSolPorPunto(new BigDecimal("10.00")); // S/ 10 gastados = 1 punto
        cfg.setValorPenPorPunto(new BigDecimal("0.10"));   // 1 punto = S/ 0.10 de descuento
        cfg.setMinimoPuntosCanje(50);                      // Mínimo 50 puntos para canjear
        cfg.setMaxCoberturaPorcentaje(30);                 // Máximo 30% del subtotal
        cfg.setPuntosBienvenida(100);
        cfg.setActivo(true);
        return cfg;
    }

    @Test
    @DisplayName("Debe simular canje de puntos exitosamente")
    void simularCanje_exitoso() {
        PuntosConfig cfg = crearConfigPuntosMock();
        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));

        PuntosBilletera billetera = new PuntosBilletera(usuario, 200);
        when(puntosBilleteraRepository.findByUsuarioId(10L)).thenReturn(Optional.of(billetera));

        // Subtotal S/ 100. Puntos solicitados: 100 -> Descuento S/ 10.00 (10% <= 30% de cobertura)
        var res = crmService.simularCanje(10L, 100, new BigDecimal("100.00"));

        assertTrue(res.isValido());
        assertEquals(200, res.getPuntosDisponibles());
        assertEquals(100, res.getPuntosSolicitados());
        assertEquals(100, res.getPuntosPermitidos());
        assertEquals(new BigDecimal("10.0000"), res.getDescuentoBasePen());
        assertEquals(new BigDecimal("90.0000"), res.getTotalConDescuentoPen());
    }

    @Test
    @DisplayName("Debe topar canje de puntos al porcentaje máximo de cobertura (30%)")
    void simularCanje_topeCobertura() {
        PuntosConfig cfg = crearConfigPuntosMock();
        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));

        PuntosBilletera billetera = new PuntosBilletera(usuario, 500); // S/ 50 en puntos
        when(puntosBilleteraRepository.findByUsuarioId(10L)).thenReturn(Optional.of(billetera));

        // Subtotal S/ 100. Máximo 30% = S/ 30.00 = 300 puntos.
        // El usuario solicita 500 puntos. El sistema debe topar a 300 puntos.
        var res = crmService.simularCanje(10L, 500, new BigDecimal("100.00"));

        assertTrue(res.isValido());
        assertEquals(500, res.getPuntosDisponibles());
        assertEquals(500, res.getPuntosSolicitados());
        assertEquals(300, res.getPuntosPermitidos());
        assertEquals(new BigDecimal("30.0000"), res.getDescuentoBasePen());
        assertEquals(new BigDecimal("70.0000"), res.getTotalConDescuentoPen());
    }

    @Test
    @DisplayName("Debe rechazar simulación si el saldo no alcanza el mínimo de canje")
    void simularCanje_saldoInsuficienteParaMinimo() {
        PuntosConfig cfg = crearConfigPuntosMock();
        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));

        PuntosBilletera billetera = new PuntosBilletera(usuario, 30); // Requiere mínimo 50
        when(puntosBilleteraRepository.findByUsuarioId(10L)).thenReturn(Optional.of(billetera));

        var res = crmService.simularCanje(10L, 30, new BigDecimal("100.00"));

        assertFalse(res.isValido());
        assertTrue(res.getMensaje().contains("al menos"));
    }

    @Test
    @DisplayName("Debe validar y consumir puntos descontando saldo con bloqueo pesimista")
    void validarYConsumirPuntos_exitoso_descuentaSaldo() {
        PuntosConfig cfg = crearConfigPuntosMock();
        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));

        PuntosBilletera billetera = new PuntosBilletera(usuario, 150);
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));

        BigDecimal desc = crmService.validarYConsumirPuntos(usuario, 100, new BigDecimal("200.00"));

        assertEquals(new BigDecimal("10.0000"), desc);
        assertEquals(50, billetera.getSaldoPuntos());
        verify(puntosBilleteraRepository).save(billetera);
    }

    @Test
    @DisplayName("Debe fallar validarYConsumirPuntos si excede cobertura máxima")
    void validarYConsumirPuntos_excedeCobertura_lanzaError() {
        PuntosConfig cfg = crearConfigPuntosMock();
        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));

        PuntosBilletera billetera = new PuntosBilletera(usuario, 400);
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));

        // Subtotal S/ 100. 30% = S/ 30. Puntos pedidos: 400 (S/ 40)
        assertThrows(BadRequestException.class, () ->
                crmService.validarYConsumirPuntos(usuario, 400, new BigDecimal("100.00"))
        );
    }

    @Test
    @DisplayName("Debe acreditar puntos por compra de forma idempotente")
    void acreditarPuntosPorCompra_calculoCorrectoEIdempotente() {
        PuntosConfig cfg = crearConfigPuntosMock();
        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));

        Pedido pedido = new Pedido();
        pedido.setId(77L);
        pedido.setUsuario(usuario);
        pedido.setTotalBasePen(new BigDecimal("250.00")); // 250 / 10 = 25 puntos

        PuntosBilletera billetera = new PuntosBilletera(usuario, 50);
        billetera.setPuntosHistoricos(100);

        when(puntosTransaccionRepository.existsByPedidoIdAndTipo(77L, TipoTransaccionPuntos.ACREDITADO_COMPRA)).thenReturn(false);
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));

        crmService.acreditarPuntosPorCompra(pedido);

        assertEquals(75, billetera.getSaldoPuntos());
        assertEquals(125, billetera.getPuntosHistoricos());
        verify(puntosBilleteraRepository).save(billetera);
        verify(puntosTransaccionRepository).save(any(PuntosTransaccion.class));

        // Segunda llamada simulada (idempotencia)
        reset(puntosBilleteraRepository, puntosTransaccionRepository);
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));
        when(puntosTransaccionRepository.existsByPedidoIdAndTipo(77L, TipoTransaccionPuntos.ACREDITADO_COMPRA)).thenReturn(true);

        crmService.acreditarPuntosPorCompra(pedido);

        verify(puntosBilleteraRepository, never()).save(any());
        verify(puntosTransaccionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe revertir puntos canjeados y acreditados al cancelar pedido")
    void revertirPuntos_devuelveCanjeadosYDescuentaAcreditados() {
        PuntosBilletera billetera = new PuntosBilletera(usuario, 100);
        billetera.setPuntosHistoricos(150);

        Pedido pedido = new Pedido();
        pedido.setId(88L);
        pedido.setUsuario(usuario);

        PuntosTransaccion canjeTx = new PuntosTransaccion(billetera, TipoTransaccionPuntos.CANJEADO_PEDIDO, -50, 50, pedido, "Canje", null);
        PuntosTransaccion acreditadoTx = new PuntosTransaccion(billetera, TipoTransaccionPuntos.ACREDITADO_COMPRA, 20, 70, pedido, "Compra", null);

        when(puntosTransaccionRepository.findByPedidoIdOrderByIdAsc(88L)).thenReturn(java.util.List.of(canjeTx, acreditadoTx));
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));

        crmService.revertirPuntos(88L);

        // Devolvió 50 canjeados, restó 20 acreditados: Saldo neto = 100 + 50 - 20 = 130
        assertEquals(130, billetera.getSaldoPuntos());
        assertEquals(130, billetera.getPuntosHistoricos());
        verify(puntosTransaccionRepository, times(2)).save(any(PuntosTransaccion.class));
    }

    @Test
    @DisplayName("Debe mantener consistencia matemática del ledger cuando el saldo es menor a los puntos a descontar (MEDIUM-01)")
    void revertirPuntos_saldoMenorAPuntosADescontar_mantieneConsistenciaMatematicaDelLedger() {
        PuntosBilletera billetera = new PuntosBilletera(usuario, 10);
        billetera.setPuntosHistoricos(100);

        Pedido pedido = new Pedido();
        pedido.setId(99L);
        pedido.setUsuario(usuario);

        // Compra acreditó 100 puntos, pero el cliente ya gastó 90 en otra compra (le quedan 10)
        PuntosTransaccion acreditadoTx = new PuntosTransaccion(billetera, TipoTransaccionPuntos.ACREDITADO_COMPRA, 100, 100, pedido, "Compra", null);

        when(puntosTransaccionRepository.findByPedidoIdOrderByIdAsc(99L)).thenReturn(java.util.List.of(acreditadoTx));
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));

        crmService.revertirPuntos(99L);

        // Solo se descuentan los 10 disponibles para no romper la ecuación del ledger
        assertEquals(0, billetera.getSaldoPuntos());
        assertEquals(90, billetera.getPuntosHistoricos());
        verify(puntosTransaccionRepository).save(argThat(tx ->
                tx.getPuntos() == -10 && tx.getSaldoResultante() == 0
        ));
    }

    @Test
    @DisplayName("Debe invalidar simulación si tras tope de cobertura los puntos no alcanzan el mínimo (MEDIUM-02)")
    void simularCanje_topeCoberturaInferiorAMinimo_retornaInvalido() {
        PuntosConfig cfg = crearConfigPuntosMock();
        cfg.setMinimoPuntosCanje(100); // Mínimo 100
        cfg.setMaxCoberturaPorcentaje(20); // 20%
        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));

        PuntosBilletera billetera = new PuntosBilletera(usuario, 500);
        when(puntosBilleteraRepository.findByUsuarioId(10L)).thenReturn(Optional.of(billetera));

        // Subtotal S/ 10.00 -> Máximo 20% = S/ 2.00 = 20 puntos (< 100 puntos mínimos)
        var res = crmService.simularCanje(10L, 200, new BigDecimal("10.00"));

        assertFalse(res.isValido());
        assertTrue(res.getMensaje().contains("tope de cobertura"));
    }

    @Test
    @DisplayName("Debe rechazar validarYConsumirCupo con puntos si el cupón no permite combinar (HIGH-01)")
    void validarYConsumirCupo_conPuntos_noPermiteCombinar_lanzaBadRequest() {
        cuponPorcentaje.setPermiteCombinarPuntos(false);
        when(cuponRepository.findByCodigoConLock("PROMO10")).thenReturn(Optional.of(cuponPorcentaje));

        assertThrows(BadRequestException.class, () ->
                crmService.validarYConsumirCupo("PROMO10", usuario, new BigDecimal("200.00"), true)
        );
    }

    @Test
    @DisplayName("Debe rechazar ajuste manual con cero puntos (LOW-02)")
    void ajustarPuntosManual_puntosCero_lanzaBadRequest() {
        var req = new com.tienda.ecommerce.crm.dto.AjustePuntosRequest(10L, 0, "Ajuste nulo");
        assertThrows(BadRequestException.class, () -> crmService.ajustarPuntosManual(req, 1L));
    }

    @Test
    @DisplayName("Debe realizar ajuste manual de puntos por administrador evitando saldo negativo")
    void ajustarPuntosManual_evitaSaldoNegativo() {
        PuntosBilletera billetera = new PuntosBilletera(usuario, 30);

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));

        var req = new com.tienda.ecommerce.crm.dto.AjustePuntosRequest(10L, -50, "Corrección de saldo");

        assertThrows(BadRequestException.class, () -> crmService.ajustarPuntosManual(req, 1L));
    }

    @Test
    @DisplayName("Debe inicializar billetera con bono de bienvenida")
    void inicializarBilleteraBienvenida_creaBilleteraYTransaccion() {
        PuntosConfig cfg = crearConfigPuntosMock();
        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(puntosBilleteraRepository.findByUsuarioId(10L)).thenReturn(Optional.empty());

        crmService.inicializarBilleteraBienvenida(usuario);

        verify(puntosBilleteraRepository).save(any(PuntosBilletera.class));
        verify(puntosTransaccionRepository).save(any(PuntosTransaccion.class));
    }

    // =========================================================================
    // TESTS FASE 3: NIVELES DE FIDELIDAD (TIERS VIP)
    // =========================================================================

    @Test
    @DisplayName("AC-3.1: Usuario con gasto anual >= S/ 600 es promovido automáticamente a nivel PLATA")
    void evaluarNivelUsuario_promueveAPlataConGastoAnualSuficiente() {
        NivelCliente bronce = new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);
        NivelCliente plata = new NivelCliente("PLATA", "Plata", new BigDecimal("600.00"), new BigDecimal("1.25"), false, 2);

        PuntosBilletera billetera = new PuntosBilletera(usuario, 100, bronce);

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));
        when(pedidoRepository.calcularGastoAnualPen(eq(10L), any())).thenReturn(new BigDecimal("650.00"));
        when(nivelClienteRepository.findByUmbralPenAnualLessThanEqualOrderByOrdenDesc(new BigDecimal("650.00")))
                .thenReturn(List.of(plata, bronce));

        NivelCliente nivelResultante = crmService.evaluarNivelUsuario(10L);

        assertEquals("PLATA", nivelResultante.getCodigo());
        assertEquals("PLATA", billetera.getNivel().getCodigo());
        verify(puntosBilleteraRepository).save(billetera);
    }

    @Test
    @DisplayName("AC-3.2: Usuario PLATA que realiza compra de S/ 100 acumula 25 puntos (100 / 5 * 1.25)")
    void acreditarPuntosPorCompra_aplicaMultiplicadorNivelPlata() {
        PuntosConfig cfg = crearConfigPuntosMock();
        cfg.setFactorSolPorPunto(new BigDecimal("5.00"));

        NivelCliente plata = new NivelCliente("PLATA", "Plata", new BigDecimal("600.00"), new BigDecimal("1.25"), false, 2);
        PuntosBilletera billetera = new PuntosBilletera(usuario, 50, plata);

        Pedido pedido = new Pedido();
        pedido.setId(101L);
        pedido.setUsuario(usuario);
        pedido.setTotalBasePen(new BigDecimal("100.00"));

        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));
        when(puntosTransaccionRepository.existsByPedidoIdAndTipo(101L, TipoTransaccionPuntos.ACREDITADO_COMPRA)).thenReturn(false);
        when(pedidoRepository.calcularGastoAnualPen(eq(10L), any())).thenReturn(new BigDecimal("700.00"));
        when(nivelClienteRepository.findByUmbralPenAnualLessThanEqualOrderByOrdenDesc(any())).thenReturn(List.of(plata));
        when(nivelClienteRepository.findById("PLATA")).thenReturn(Optional.of(plata));

        crmService.acreditarPuntosPorCompra(pedido);

        // 100 / 5 = 20 pts base * 1.25 = 25 puntos ganados
        assertEquals(75, billetera.getSaldoPuntos());
        verify(puntosBilleteraRepository).save(billetera);
        verify(puntosTransaccionRepository).save(argThat(tx ->
                tx.getPuntos() == 25 &&
                tx.getTipo() == TipoTransaccionPuntos.ACREDITADO_COMPRA &&
                tx.getDescripcion().contains("Plata")
        ));
    }

    @Test
    @DisplayName("AC-3.3: Cambio dinámico de multiplicador de 1.25 a 1.30 surte efecto inmediato sin reinicio")
    void acreditarPuntosPorCompra_reflejaMultiplicadorActualizadoSinReinicio() {
        PuntosConfig cfg = crearConfigPuntosMock();
        cfg.setFactorSolPorPunto(new BigDecimal("5.00"));

        // Nivel en BD actualizado con multiplicador 1.30
        NivelCliente plataActualizado = new NivelCliente("PLATA", "Plata", new BigDecimal("600.00"), new BigDecimal("1.30"), false, 2);
        PuntosBilletera billetera = new PuntosBilletera(usuario, 0, plataActualizado);

        Pedido pedido = new Pedido();
        pedido.setId(102L);
        pedido.setUsuario(usuario);
        pedido.setTotalBasePen(new BigDecimal("100.00"));

        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));
        when(puntosTransaccionRepository.existsByPedidoIdAndTipo(102L, TipoTransaccionPuntos.ACREDITADO_COMPRA)).thenReturn(false);
        when(pedidoRepository.calcularGastoAnualPen(eq(10L), any())).thenReturn(new BigDecimal("700.00"));
        when(nivelClienteRepository.findByUmbralPenAnualLessThanEqualOrderByOrdenDesc(any())).thenReturn(List.of(plataActualizado));
        when(nivelClienteRepository.findById("PLATA")).thenReturn(Optional.of(plataActualizado));

        crmService.acreditarPuntosPorCompra(pedido);

        // 100 / 5 = 20 pts base * 1.30 = 26 puntos ganados
        assertEquals(26, billetera.getSaldoPuntos());
        verify(puntosTransaccionRepository).save(argThat(tx -> tx.getPuntos() == 26));
    }

    @Test
    @DisplayName("Política de lealtad anual: el cliente NO desciende de nivel en tiempo real si su gasto disminuye")
    void evaluarNivelUsuario_noDesciendeNivelEnTiempoReal() {
        NivelCliente bronce = new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);
        NivelCliente oro = new NivelCliente("ORO", "Oro", new BigDecimal("1800.00"), new BigDecimal("1.50"), true, 3);

        PuntosBilletera billetera = new PuntosBilletera(usuario, 500, oro);

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));
        when(pedidoRepository.calcularGastoAnualPen(eq(10L), any())).thenReturn(new BigDecimal("200.00")); // gasto bajo
        when(nivelClienteRepository.findByUmbralPenAnualLessThanEqualOrderByOrdenDesc(new BigDecimal("200.00")))
                .thenReturn(List.of(bronce));

        NivelCliente resultante = crmService.evaluarNivelUsuario(10L);

        assertEquals("ORO", resultante.getCodigo());
        assertEquals("ORO", billetera.getNivel().getCodigo());
        verify(puntosBilleteraRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe listar niveles ordenados ascendentemente por orden")
    void listarNiveles_retornaNivelesOrdenados() {
        NivelCliente b = new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);
        NivelCliente p = new NivelCliente("PLATA", "Plata", new BigDecimal("600.00"), new BigDecimal("1.25"), false, 2);

        when(nivelClienteRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of(b, p));

        var lista = crmService.listarNiveles();

        assertEquals(2, lista.size());
        assertEquals("BRONCE", lista.get(0).getCodigo());
        assertEquals("PLATA", lista.get(1).getCodigo());
    }

    @Test
    @DisplayName("Debe actualizar configuración de un nivel VIP exitosamente respetando monotonía")
    void actualizarNivel_modificaMultiplicadorYBeneficios() {
        NivelCliente bronce = new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);
        NivelCliente plata = new NivelCliente("PLATA", "Plata", new BigDecimal("600.00"), new BigDecimal("1.25"), false, 2);
        NivelCliente oro = new NivelCliente("ORO", "Oro", new BigDecimal("1800.00"), new BigDecimal("1.50"), true, 3);

        when(nivelClienteRepository.findById("PLATA")).thenReturn(Optional.of(plata));
        when(nivelClienteRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of(bronce, plata, oro));
        when(nivelClienteRepository.save(any(NivelCliente.class))).thenAnswer(i -> i.getArgument(0));

        var req = new com.tienda.ecommerce.crm.dto.ActualizarNivelRequest(
                "Plata Plus",
                new BigDecimal("650.00"),
                new BigDecimal("1.35"),
                true
        );

        var dto = crmService.actualizarNivel("PLATA", req);

        assertEquals("Plata Plus", dto.getNombre());
        assertEquals(new BigDecimal("650.00"), dto.getUmbralPenAnual());
        assertEquals(new BigDecimal("1.35"), dto.getMultiplicador());
        assertTrue(dto.isBeneficioEnvio());
    }

    @Test
    @DisplayName("HIGH-02: Debe rechazar modificar umbral de Bronce a valor distinto de 0.00")
    void actualizarNivel_rechazaModificarUmbralDeBronce() {
        NivelCliente bronce = new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);
        when(nivelClienteRepository.findById("BRONCE")).thenReturn(Optional.of(bronce));

        var req = new com.tienda.ecommerce.crm.dto.ActualizarNivelRequest(
                "Bronce",
                new BigDecimal("100.00"),
                new BigDecimal("1.00"),
                false
        );

        assertThrows(BadRequestException.class, () -> crmService.actualizarNivel("BRONCE", req));
    }

    @Test
    @DisplayName("HIGH-02: Debe rechazar umbral invertido que rompa la jerarquía entre niveles")
    void actualizarNivel_rechazaUmbralesInvertidos() {
        NivelCliente bronce = new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);
        NivelCliente plata = new NivelCliente("PLATA", "Plata", new BigDecimal("600.00"), new BigDecimal("1.25"), false, 2);
        NivelCliente oro = new NivelCliente("ORO", "Oro", new BigDecimal("1800.00"), new BigDecimal("1.50"), true, 3);

        when(nivelClienteRepository.findById("ORO")).thenReturn(Optional.of(oro));
        when(nivelClienteRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of(bronce, plata, oro));

        // Intento de asignar umbral S/ 500 a Oro (menor que Plata con S/ 600)
        var req = new com.tienda.ecommerce.crm.dto.ActualizarNivelRequest(
                "Oro VIP",
                new BigDecimal("500.00"),
                new BigDecimal("1.50"),
                true
        );

        assertThrows(BadRequestException.class, () -> crmService.actualizarNivel("ORO", req));
    }

    @Test
    @DisplayName("MEDIUM-01: obtenerBilleteraUsuario expone nivel efectivo coherente si gasto anual ya califica a nivel superior")
    void obtenerBilleteraUsuario_retornaNivelEfectivoSiGastoAnualSuperaUmbral() {
        NivelCliente bronce = new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);
        NivelCliente plata = new NivelCliente("PLATA", "Plata", new BigDecimal("600.00"), new BigDecimal("1.25"), false, 2);
        NivelCliente oro = new NivelCliente("ORO", "Oro", new BigDecimal("1800.00"), new BigDecimal("1.50"), true, 3);

        PuntosBilletera billetera = new PuntosBilletera(usuario, 20, bronce);
        billetera.setId(1L);

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        when(puntosBilleteraRepository.findByUsuarioId(10L)).thenReturn(Optional.of(billetera));
        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(crearConfigPuntosMock()));
        when(pedidoRepository.calcularGastoAnualPen(eq(10L), any())).thenReturn(new BigDecimal("750.00"));
        when(nivelClienteRepository.findById("BRONCE")).thenReturn(Optional.of(bronce));
        when(nivelClienteRepository.findByUmbralPenAnualLessThanEqualOrderByOrdenDesc(new BigDecimal("750.00")))
                .thenReturn(List.of(plata, bronce));
        when(nivelClienteRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of(bronce, plata, oro));

        var res = crmService.obtenerBilleteraUsuario(10L);

        // Nivel efectivo debe ser PLATA y siguiente debe ser ORO
        assertEquals("PLATA", res.getNivelCodigo());
        assertEquals("Oro", res.getSiguienteNivelNombre());
        assertEquals(new BigDecimal("1050.00"), res.getMontoFaltanteSiguienteNivelPen()); // 1800 - 750
    }

    @Test
    @DisplayName("MEDIUM-02: acreditarPuntosPorCompra persiste ascenso de nivel aun cuando puntos ganados sean cero")
    void acreditarPuntosPorCompra_persisteAscensoAunquePuntosSeanCero() {
        PuntosConfig cfg = crearConfigPuntosMock();
        cfg.setFactorSolPorPunto(new BigDecimal("10.00")); // factor S/ 10

        NivelCliente bronce = new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);
        NivelCliente plata = new NivelCliente("PLATA", "Plata", new BigDecimal("600.00"), new BigDecimal("1.25"), false, 2);

        PuntosBilletera billetera = new PuntosBilletera(usuario, 0, bronce);

        Pedido pedido = new Pedido();
        pedido.setId(205L);
        pedido.setUsuario(usuario);
        pedido.setTotalBasePen(new BigDecimal("4.00")); // 4 / 10 = 0 puntos

        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(puntosBilleteraRepository.findByUsuarioIdConLock(10L)).thenReturn(Optional.of(billetera));
        when(puntosTransaccionRepository.existsByPedidoIdAndTipo(205L, TipoTransaccionPuntos.ACREDITADO_COMPRA)).thenReturn(false);
        when(pedidoRepository.calcularGastoAnualPen(eq(10L), any())).thenReturn(new BigDecimal("650.00")); // califica a Plata
        when(nivelClienteRepository.findByUmbralPenAnualLessThanEqualOrderByOrdenDesc(new BigDecimal("650.00")))
                .thenReturn(List.of(plata, bronce));
        when(nivelClienteRepository.findById("PLATA")).thenReturn(Optional.of(plata));

        crmService.acreditarPuntosPorCompra(pedido);

        // Billetera debe haber sido guardada con nivel Plata aunque puntos sean 0
        assertEquals("PLATA", billetera.getNivel().getCodigo());
        verify(puntosBilleteraRepository).save(billetera);
        verify(puntosTransaccionRepository, never()).save(any());
    }

    // =========================================================================
    // FICHA 360° DEL CONSUMIDOR (FASE 4)
    // =========================================================================

    @Test
    @DisplayName("AC-4.1: calcularSegmento clasifica correctamente según RFM y nivel")
    void calcularSegmento_clasificacionesCorrectas() {
        // Prospecto: 0 compras
        assertEquals(SegmentoCliente.PROSPECTO,
                crmService.calcularSegmento(0, null, "BRONCE", BigDecimal.ZERO));

        // Activo: compra reciente <= 30 días
        assertEquals(SegmentoCliente.ACTIVO,
                crmService.calcularSegmento(3, 15, "BRONCE", new BigDecimal("500.00")));

        // En riesgo: 45 días sin comprar y al menos 2 pedidos (AC-4.1)
        assertEquals(SegmentoCliente.EN_RIESGO,
                crmService.calcularSegmento(3, 45, "PLATA", new BigDecimal("800.00")));

        // Inactivo: más de 90 días sin comprar
        assertEquals(SegmentoCliente.INACTIVO,
                crmService.calcularSegmento(2, 95, "BRONCE", new BigDecimal("300.00")));

        // Inactivo: 1 sola compra hace 35 días
        assertEquals(SegmentoCliente.INACTIVO,
                crmService.calcularSegmento(1, 35, "BRONCE", new BigDecimal("100.00")));

        // VIP por nivel ORO
        assertEquals(SegmentoCliente.VIP,
                crmService.calcularSegmento(5, 50, "ORO", new BigDecimal("1500.00")));

        // VIP por LTV >= 3000 PEN
        assertEquals(SegmentoCliente.VIP,
                crmService.calcularSegmento(10, 20, "PLATA", new BigDecimal("3200.00")));
    }

    @Test
    @DisplayName("AC-4.2: obtenerResumen360 calcula LTV, métricas RFM, puntos y notas unificadas")
    void obtenerResumen360_calculaMetricasCorrectamente() {
        PuntosConfig cfg = crearConfigPuntosMock();
        NivelCliente bronce = new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);
        PuntosBilletera billetera = new PuntosBilletera(usuario, 150, bronce);
        billetera.setId(1L);
        billetera.setPuntosHistoricos(300);

        // 2 pedidos aprobados
        usuario.setNumeroDocumento("44556677");
        Pedido p1 = new Pedido();
        p1.setId(101L);
        p1.setTotalBasePen(new BigDecimal("200.00"));
        p1.setMonedaCodigo("PEN");
        p1.setTotal(new BigDecimal("200.00"));
        p1.setFecha(LocalDateTime.now().minusDays(10));
        p1.setEstado(com.tienda.ecommerce.pedido.EstadoPedido.ENTREGADO);
        p1.setTelefono("987654321");

        Pedido p2 = new Pedido();
        p2.setId(102L);
        p2.setTotalBasePen(new BigDecimal("300.00"));
        p2.setMonedaCodigo("PEN");
        p2.setTotal(new BigDecimal("300.00"));
        p2.setFecha(LocalDateTime.now().minusDays(40));
        p2.setEstado(com.tienda.ecommerce.pedido.EstadoPedido.ENTREGADO);

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(puntosBilleteraRepository.findByUsuarioId(10L)).thenReturn(Optional.of(billetera));
        when(pedidoRepository.findPedidosAprobadosPorUsuario(10L)).thenReturn(List.of(p1, p2));
        when(cuponUsoRepository.findByUsuarioIdAndActivoTrueOrderByFechaUsoDesc(10L)).thenReturn(List.of());
        when(clienteNotaRepository.findByUsuarioIdOrderByFechaDesc(10L)).thenReturn(List.of());
        when(puntosTransaccionRepository.findByBilleteraIdOrderByFechaDesc(1L)).thenReturn(List.of());

        ClienteResumen360Dto resumen = crmService.obtenerResumen360(10L);

        assertNotNull(resumen);
        assertEquals(10L, resumen.getUsuarioId());
        assertEquals("44556677", resumen.getNumeroDocumento());
        assertEquals("987654321", resumen.getTelefono()); // Finding 3: extraído del pedido y no del documento
        assertEquals(2, resumen.getTotalPedidos());
        assertEquals(new BigDecimal("500.00"), resumen.getLtvPen()); // 200 + 300
        assertEquals(new BigDecimal("250.00"), resumen.getTicketPromedioPen()); // 500 / 2
        assertEquals(10, resumen.getDiasDesdeUltimaCompra());
        assertEquals(SegmentoCliente.ACTIVO, resumen.getSegmento());
        assertEquals(150, resumen.getSaldoPuntos());
        assertEquals(new BigDecimal("15.00"), resumen.getValorEstimadoPuntosPen()); // 150 * 0.10
    }

    @Test
    @DisplayName("obtenerResumen360 para cliente nuevo sin pedidos ni billetera retorna valores por defecto seguros")
    void obtenerResumen360_clienteNuevoSinPedidosNiBilletera_seguro() {
        PuntosConfig cfg = crearConfigPuntosMock();
        NivelCliente bronce = new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        when(puntosConfigRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(puntosBilleteraRepository.findByUsuarioId(10L)).thenReturn(Optional.empty());
        when(nivelClienteRepository.findById("BRONCE")).thenReturn(Optional.of(bronce));
        when(pedidoRepository.findPedidosAprobadosPorUsuario(10L)).thenReturn(List.of());
        when(cuponUsoRepository.findByUsuarioIdAndActivoTrueOrderByFechaUsoDesc(10L)).thenReturn(List.of());
        when(clienteNotaRepository.findByUsuarioIdOrderByFechaDesc(10L)).thenReturn(List.of());

        ClienteResumen360Dto resumen = crmService.obtenerResumen360(10L);

        assertNotNull(resumen);
        assertEquals(10L, resumen.getUsuarioId());
        assertNull(resumen.getTelefono());
        assertEquals(0, resumen.getTotalPedidos());
        assertEquals(BigDecimal.ZERO.setScale(2), resumen.getLtvPen());
        assertEquals(BigDecimal.ZERO.setScale(2), resumen.getTicketPromedioPen());
        assertNull(resumen.getDiasDesdeUltimaCompra());
        assertEquals(SegmentoCliente.PROSPECTO, resumen.getSegmento());
        assertEquals("Bronce", resumen.getNivelActual());
        assertEquals(0, resumen.getSaldoPuntos());
        assertEquals(0, resumen.getCuponesUsados());
    }

    @Test
    @DisplayName("AC-4.3: agregarNota registra nota administrativa por admin autenticado y es auditable")
    void agregarNota_exitosoYAuditable() {
        Usuario admin = new Usuario();
        admin.setId(99L);
        admin.setNombre("Admin Supervisor");
        admin.setEmail("admin@tienda.com");

        CrearClienteNotaRequest request = new CrearClienteNotaRequest("Cliente solicitó cambio de dirección vía llamada telefónica");

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(clienteNotaRepository.save(any(ClienteNota.class))).thenAnswer(inv -> {
            ClienteNota n = inv.getArgument(0);
            n.setId(1L);
            n.setFecha(LocalDateTime.now());
            return n;
        });

        ClienteNotaDto notaDto = crmService.agregarNota(10L, 99L, request);

        assertNotNull(notaDto);
        assertEquals(10L, notaDto.getUsuarioId());
        assertEquals("Admin Supervisor", notaDto.getAdminNombre());
        assertEquals("Cliente solicitó cambio de dirección vía llamada telefónica", notaDto.getNota());
        verify(clienteNotaRepository).save(any(ClienteNota.class));
    }

    @Test
    @DisplayName("agregarNota rechaza nota vacía o mayor a 2000 caracteres")
    void agregarNota_validaciones() {
        assertThrows(BadRequestException.class, () ->
                crmService.agregarNota(10L, 99L, new CrearClienteNotaRequest("   ")));

        assertThrows(BadRequestException.class, () ->
                crmService.agregarNota(10L, 99L, new CrearClienteNotaRequest("a".repeat(2001))));

        assertThrows(BadRequestException.class, () ->
                crmService.agregarNota(null, 99L, new CrearClienteNotaRequest("Nota válida")));
    }

    @Test
    @DisplayName("listarClientes360 filtra adecuadamente por segmento solicitado")
    void listarClientes360_filtroSegmento() {
        Usuario u1 = new Usuario();
        u1.setId(1L);
        u1.setNombre("Cliente Prospecto");
        u1.setEmail("prospecto@test.com");

        Usuario u2 = new Usuario();
        u2.setId(2L);
        u2.setNombre("Cliente Activo");
        u2.setEmail("activo@test.com");

        NivelCliente bronce = new NivelCliente("BRONCE", "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);
        when(usuarioRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(u1, u2));
        when(puntosBilleteraRepository.findAll()).thenReturn(List.of(
                new PuntosBilletera(u1, 0, bronce),
                new PuntosBilletera(u2, 50, bronce)
        ));

        // u1 no tiene compras registradas en metricas raw
        // u2 tiene 1 compra reciente de S/ 150 hace 5 dias
        when(pedidoRepository.calcularMetricasRfmPorUsuario()).thenReturn(List.<Object[]>of(
                new Object[] { 2L, new BigDecimal("150.00"), 1, LocalDateTime.now().minusDays(5) }
        ));

        // Filtrar solo PROSPECTO
        List<ClienteFila360Dto> prospectos = crmService.listarClientes360(SegmentoCliente.PROSPECTO);
        assertEquals(1, prospectos.size());
        assertEquals(1L, prospectos.get(0).getUsuarioId());
        assertEquals(SegmentoCliente.PROSPECTO, prospectos.get(0).getSegmento());

        // Filtrar solo ACTIVO
        List<ClienteFila360Dto> activos = crmService.listarClientes360(SegmentoCliente.ACTIVO);
        assertEquals(1, activos.size());
        assertEquals(2L, activos.get(0).getUsuarioId());
        assertEquals(SegmentoCliente.ACTIVO, activos.get(0).getSegmento());
    }
}
