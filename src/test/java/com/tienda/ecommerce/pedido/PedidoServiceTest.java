package com.tienda.ecommerce.pedido;

import com.tienda.ecommerce.carrito.Carrito;
import com.tienda.ecommerce.carrito.CarritoRepository;
import com.tienda.ecommerce.carrito.CarritoService;
import com.tienda.ecommerce.carrito.ItemCarrito;
import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.common.StockInsuficienteException;
import com.tienda.ecommerce.pedido.dto.CrearPedidoRequest;
import com.tienda.ecommerce.pedido.dto.PedidoResponse;
import com.tienda.ecommerce.producto.Categoria;
import com.tienda.ecommerce.producto.Producto;
import com.tienda.ecommerce.producto.ProductoRepository;
import com.tienda.ecommerce.usuario.Rol;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private CarritoService carritoService;

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock private com.tienda.ecommerce.pago.MetodoPagoConfigRepository metodoPagoConfigRepository;
    @Mock private CotizacionService cotizaciones;
    @Mock private com.tienda.ecommerce.pago.FormularioPagoService formularios;
    @Mock private jakarta.persistence.EntityManager entityManager;
    @Mock private com.tienda.ecommerce.crm.CrmService crmService;

    @InjectMocks
    private PedidoServiceImpl pedidoService;

    private Usuario usuario;
    private Producto producto;
    private Carrito carrito;

    @BeforeEach
    void setUp() {
        usuario = new Usuario(1L, "María Gomez", "maria@ejemplo.com", "pass123", Rol.CLIENTE, true);
        Categoria cat = new Categoria(1L, "Periféricos", "Mouse");
        producto = new Producto(5L, "Mouse Logitech", "Mouse inalámbrico", BigDecimal.valueOf(150.00), 10, "mouse.jpg", cat, true);

        carrito = new Carrito(1L, usuario);
        ItemCarrito item = new ItemCarrito(100L, carrito, producto, 2);
        carrito.agregarItem(item);
        var metodo = new com.tienda.ecommerce.pago.MetodoPagoConfig(); metodo.setCodigo("TARJETA_CREDITO"); metodo.setNombre("Tarjeta simulada"); metodo.setProcesamiento("SIMULADO"); metodo.setPrefijoTransaccion("TX-");metodo.setMonedas(java.util.Set.of("PEN"));
        lenient().when(metodoPagoConfigRepository.findById("TARJETA_CREDITO")).thenReturn(Optional.of(metodo));
        lenient().when(formularios.version(any())).thenReturn("v1");
        lenient().when(formularios.validar(any(),any())).thenReturn("{}");
        var linea = new CotizacionService.Linea(5L,"Mouse",2,new BigDecimal("150.00"),"PEN",BigDecimal.ONE,
                new BigDecimal("150.00"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("150.00"),new BigDecimal("300.00"));
        var cotizacion = new CotizacionService.Datos("00000000-0000-0000-0000-000000000001",1L,"huella","PEN",new BigDecimal("300.00"),BigDecimal.ONE,new BigDecimal("300.00"),java.util.List.of(linea));
        lenient().when(cotizaciones.verificar(any(),any(),any())).thenReturn(cotizacion);
    }

    @Test
    @DisplayName("Debe crear pedido descontando stock y vaciando el carrito")
    void crearDesdeCarrito_exitoso() {
        CrearPedidoRequest request = new CrearPedidoRequest("Av. Principal 123", "987654321");
        request.setMetodoPago("TARJETA_CREDITO");request.setVersionMetodo("v1");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(carritoService.obtenerOCrearCarritoEntity(1L)).thenReturn(carrito);
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> {
            Pedido p = i.getArgument(0);
            p.setId(50L);
            return p;
        });

        PedidoResponse response = pedidoService.crearDesdeCarrito(1L, request);

        assertNotNull(response);
        assertEquals(50L, response.getId());
        assertEquals(EstadoPedido.PENDIENTE, response.getEstado());
        assertEquals(0, new BigDecimal("300.00").compareTo(response.getTotal()));
        assertEquals(8, producto.getStock()); // 10 - 2 = 8

        verify(productoRepository, times(1)).save(producto);
        verify(pedidoRepository, times(1)).save(any(Pedido.class));
        verify(carritoRepository, times(1)).save(carrito);
        assertTrue(carrito.getItems().isEmpty());
    }

    @Test
    @DisplayName("Debe fallar al intentar crear pedido con carrito vacío")
    void crearDesdeCarrito_carritoVacio() {
        carrito.getItems().clear();
        CrearPedidoRequest request = new CrearPedidoRequest("Av. Principal 123", "987654321");
        request.setMetodoPago("TARJETA_CREDITO");request.setVersionMetodo("v1");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(carritoService.obtenerOCrearCarritoEntity(1L)).thenReturn(carrito);

        assertThrows(BadRequestException.class, () -> pedidoService.crearDesdeCarrito(1L, request));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Debe fallar si el stock disponible es menor a la cantidad solicitada")
    void crearDesdeCarrito_stockInsuficiente() {
        producto.setStock(1); // El carrito tiene 2
        CrearPedidoRequest request = new CrearPedidoRequest("Av. Principal 123", "987654321");
        request.setMetodoPago("TARJETA_CREDITO");request.setVersionMetodo("v1");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(carritoService.obtenerOCrearCarritoEntity(1L)).thenReturn(carrito);

        assertThrows(StockInsuficienteException.class, () -> pedidoService.crearDesdeCarrito(1L, request));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Debe cambiar el estado de un pedido")
    void cambiarEstado_exitoso() {
        Pedido pedido = new Pedido(50L, usuario, EstadoPedido.PENDIENTE, BigDecimal.valueOf(300), "Dir", "Tel");
        when(pedidoRepository.findByIdConDetalles(50L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponse response = pedidoService.cambiarEstado(50L, EstadoPedido.EN_PROCESO);

        assertNotNull(response);
        assertEquals(EstadoPedido.EN_PROCESO, response.getEstado());
        verify(pedidoRepository, times(1)).save(pedido);
    }

    @Test
    @DisplayName("Contraentrega ENTREGADO debe quedar cobrado (APROBADO)")
    void cambiarEstado_contraentregaEntregada_cobra() {
        Pedido pedido = new Pedido(51L, usuario, EstadoPedido.ENVIADO, BigDecimal.valueOf(600), "Dir", "Tel");
        com.tienda.ecommerce.pago.Pago pago = new com.tienda.ecommerce.pago.Pago(
                pedido, "PAGO_CONTRAENTREGA", "CONTRA_ENTREGA",
                com.tienda.ecommerce.pago.EstadoPago.PENDIENTE, BigDecimal.valueOf(600), "COD-1");
        pedido.setPago(pago);
        when(pedidoRepository.findByIdConDetalles(51L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponse response = pedidoService.cambiarEstado(51L, EstadoPedido.ENTREGADO);

        assertEquals(EstadoPedido.ENTREGADO, response.getEstado());
        assertEquals(com.tienda.ecommerce.pago.EstadoPago.APROBADO, response.getEstadoPago());
        assertEquals("PAGO_CONTRAENTREGA", response.getMetodoPago());
    }

    @Test
    @DisplayName("Yape ENVIADO no debe auto-aprobarse, requiere verificación")
    void cambiarEstado_yapeNoAutoAprueba() {
        Pedido pedido = new Pedido(52L, usuario, EstadoPedido.PENDIENTE, BigDecimal.valueOf(100), "Dir", "Tel");
        com.tienda.ecommerce.pago.Pago pago = new com.tienda.ecommerce.pago.Pago(
                pedido, "YAPE", "VERIFICACION_MANUAL",
                com.tienda.ecommerce.pago.EstadoPago.PENDIENTE, BigDecimal.valueOf(100), "YAPE-1");
        pago.setReferenciaComprobante("OP-123456");
        pedido.setPago(pago);
        when(pedidoRepository.findByIdConDetalles(52L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponse response = pedidoService.cambiarEstado(52L, EstadoPedido.ENVIADO);

        assertEquals(com.tienda.ecommerce.pago.EstadoPago.PENDIENTE, response.getEstadoPago());
    }

    @Test
    @DisplayName("Verificar pago Yape lo deja APROBADO")
    void verificarPago_aprueba() {
        Pedido pedido = new Pedido(53L, usuario, EstadoPedido.ENVIADO, BigDecimal.valueOf(100), "Dir", "Tel");
        com.tienda.ecommerce.pago.Pago pago = new com.tienda.ecommerce.pago.Pago(
                pedido, "YAPE", "VERIFICACION_MANUAL",
                com.tienda.ecommerce.pago.EstadoPago.PENDIENTE, BigDecimal.valueOf(100), "YAPE-2");
        pedido.setPago(pago);
        when(pedidoRepository.findByIdConDetalles(53L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponse response = pedidoService.verificarPago(53L, com.tienda.ecommerce.pago.EstadoPago.APROBADO);

        assertEquals(com.tienda.ecommerce.pago.EstadoPago.APROBADO, response.getEstadoPago());
    }

    @Test
    @DisplayName("Debe dividir descuento PEN por tasaPen para pedidos en USD y desacoplar guardado de pedido y registro de cupón (SEC-CRM-01, SEC-CRM-02)")
    void crearDesdeCarrito_conCuponUsd_dividePorTasaYDesacoplaRegistro() {
        var metodoUsd = new com.tienda.ecommerce.pago.MetodoPagoConfig();
        metodoUsd.setCodigo("TARJETA_USD");
        metodoUsd.setNombre("Tarjeta USD");
        metodoUsd.setProcesamiento("SIMULADO");
        metodoUsd.setPrefijoTransaccion("TX-");
        metodoUsd.setMonedas(java.util.Set.of("USD"));
        when(metodoPagoConfigRepository.findById("TARJETA_USD")).thenReturn(Optional.of(metodoUsd));

        var linea = new CotizacionService.Linea(5L, "Mouse", 2, new BigDecimal("50.00"), "USD", BigDecimal.ONE,
                new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"), new BigDecimal("100.00"));
        // 100 USD cotizados, tasaPen 3.80 -> totalBasePen = 380.00 PEN
        var cotizacionUsd = new CotizacionService.Datos(
                "00000000-0000-0000-0000-000000000002", 1L, "huella", "USD",
                new BigDecimal("100.00"), new BigDecimal("3.8000"), new BigDecimal("380.00"), java.util.List.of(linea)
        );
        when(cotizaciones.verificar(any(), any(), any())).thenReturn(cotizacionUsd);

        CrearPedidoRequest request = new CrearPedidoRequest("Av. Principal 123", "987654321");
        request.setMetodoPago("TARJETA_USD");
        request.setVersionMetodo("v1");
        request.setCuponCodigoAplicado("CUPON38");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(carritoService.obtenerOCrearCarritoEntity(1L)).thenReturn(carrito);
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        // Descuento de S/ 38.00 PEN. Al dividir por 3.80, debe descontar exactamente $10.00 USD, NO multiplicar a $144.40 USD.
        when(crmService.validarYConsumirCupo("CUPON38", usuario, new BigDecimal("380.00"), false)).thenReturn(new BigDecimal("38.0000"));

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> {
            Pedido p = i.getArgument(0);
            p.setId(99L);
            return p;
        });

        PedidoResponse response = pedidoService.crearDesdeCarrito(1L, request);

        assertNotNull(response);
        assertEquals(99L, response.getId());
        assertEquals("USD", response.getMonedaCodigo());
        // 100.00 USD - 10.00 USD = 90.00 USD
        assertEquals(0, new BigDecimal("90.0000").compareTo(response.getTotal()));
        assertEquals(new BigDecimal("38.0000"), response.getDescuentoPen());
        assertEquals("CUPON38", response.getCuponCodigo());

        // Verificamos que validarYConsumirCupo se invocó ANTES de pedidoRepository.save,
        // y registrarUsoCupon se invocó DESPUÉS con la instancia ya persistida
        org.mockito.InOrder inOrder = inOrder(crmService, pedidoRepository);
        inOrder.verify(crmService).validarYConsumirCupo("CUPON38", usuario, new BigDecimal("380.00"), false);
        inOrder.verify(pedidoRepository).save(any(Pedido.class));
        inOrder.verify(crmService).registrarUsoCupon(eq("CUPON38"), eq(usuario), any(Pedido.class), eq(new BigDecimal("38.0000")));
    }

    @Test
    @DisplayName("Debe revertir cupón al cancelar pedido incluso si el pedido no tiene pago asociado (SEC-CRM-05)")
    void cambiarEstado_canceladoSinPago_revierteCupon() {
        Pedido pedido = new Pedido(54L, usuario, EstadoPedido.PENDIENTE, BigDecimal.valueOf(100), "Dir", "Tel");
        pedido.setPago(null); // Sin pago
        pedido.setCuponCodigo("PROMO10");

        when(pedidoRepository.findByIdConDetalles(54L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponse response = pedidoService.cambiarEstado(54L, EstadoPedido.CANCELADO);

        assertNotNull(response);
        assertEquals(EstadoPedido.CANCELADO, response.getEstado());
        verify(crmService, times(1)).revertirCupon(54L);
        verify(pedidoRepository, times(1)).save(pedido);
    }

    @Test
    @DisplayName("Debe canjear puntos en pedido descontando total y registrando canje")
    void crearDesdeCarrito_conCanjePuntos_descuentaTotalYRegistraCanje() {
        CrearPedidoRequest request = new CrearPedidoRequest("Av. Test 123", "999888777");
        request.setMetodoPago("TARJETA_CREDITO");
        request.setVersionMetodo("v1");
        request.setPuntosACanjear(100);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(carritoService.obtenerOCrearCarritoEntity(1L)).thenReturn(carrito);
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        // Subtotal cotizado: S/ 300.00 PEN. Descuento de 100 puntos = S/ 10.00 PEN
        when(crmService.validarYConsumirPuntos(usuario, 100, new BigDecimal("300.00"))).thenReturn(new BigDecimal("10.0000"));

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> {
            Pedido p = i.getArgument(0);
            p.setId(105L);
            return p;
        });

        PedidoResponse response = pedidoService.crearDesdeCarrito(1L, request);

        assertNotNull(response);
        assertEquals(105L, response.getId());
        assertEquals(100, response.getPuntosCanjeados());
        assertEquals(new BigDecimal("10.0000"), response.getDescuentoPuntosPen());
        // 300.00 PEN - 10.00 PEN = 290.00 PEN
        assertEquals(0, new BigDecimal("290.0000").compareTo(response.getTotal()));

        verify(crmService).validarYConsumirPuntos(usuario, 100, new BigDecimal("300.00"));
        verify(crmService).registrarCanjePuntos(eq(usuario), any(Pedido.class), eq(100), eq(new BigDecimal("10.0000")));
    }

    @Test
    @DisplayName("Debe revertir puntos al cancelar pedido")
    void cambiarEstado_cancelado_reviertePuntos() {
        Pedido pedido = new Pedido(60L, usuario, EstadoPedido.PENDIENTE, BigDecimal.valueOf(100), "Dir", "Tel");
        pedido.setPuntosCanjeados(50);

        when(pedidoRepository.findByIdConDetalles(60L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponse response = pedidoService.cambiarEstado(60L, EstadoPedido.CANCELADO);

        assertNotNull(response);
        assertEquals(EstadoPedido.CANCELADO, response.getEstado());
        verify(crmService).revertirPuntos(60L);
    }

    @Test
    @DisplayName("Debe rechazar creación de pedido si cupón no permite combinar puntos y se envían puntos (HIGH-01)")
    void crearDesdeCarrito_cuponNoPermiteCombinarPuntos_lanzaBadRequest() {
        CrearPedidoRequest request = new CrearPedidoRequest("Av. Test 123", "999888777");
        request.setMetodoPago("TARJETA_CREDITO");
        request.setVersionMetodo("v1");
        request.setCuponCodigoAplicado("EXCLUSIVO50");
        request.setPuntosACanjear(100);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(carritoService.obtenerOCrearCarritoEntity(1L)).thenReturn(carrito);
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        when(crmService.validarYConsumirCupo("EXCLUSIVO50", usuario, new BigDecimal("300.00"), true))
                .thenThrow(new BadRequestException("El cupón 'EXCLUSIVO50' no permite ser combinado con puntos de fidelización"));

        assertThrows(BadRequestException.class, () -> pedidoService.crearDesdeCarrito(1L, request));
    }

    @Test
    @DisplayName("Debe dividir descuento de puntos PEN por tasaPen para pedidos en USD")
    void crearDesdeCarrito_conCanjePuntosEnUsd_dividePorTasaPen() {
        var metodoUsd = new com.tienda.ecommerce.pago.MetodoPagoConfig();
        metodoUsd.setCodigo("TARJETA_USD");
        metodoUsd.setNombre("Tarjeta USD");
        metodoUsd.setProcesamiento("SIMULADO");
        metodoUsd.setPrefijoTransaccion("TX-");
        metodoUsd.setMonedas(java.util.Set.of("USD"));
        when(metodoPagoConfigRepository.findById("TARJETA_USD")).thenReturn(Optional.of(metodoUsd));

        var linea = new CotizacionService.Linea(5L, "Mouse", 2, new BigDecimal("50.00"), "USD", BigDecimal.ONE,
                new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"), new BigDecimal("100.00"));
        // 100 USD cotizados, tasaPen 3.80 -> totalBasePen = 380.00 PEN
        var cotizacionUsd = new CotizacionService.Datos(
                "00000000-0000-0000-0000-000000000003", 1L, "huella", "USD",
                new BigDecimal("100.00"), new BigDecimal("3.8000"), new BigDecimal("380.00"), java.util.List.of(linea)
        );
        when(cotizaciones.verificar(any(), any(), any())).thenReturn(cotizacionUsd);

        CrearPedidoRequest request = new CrearPedidoRequest("Av. Principal 123", "987654321");
        request.setMetodoPago("TARJETA_USD");
        request.setVersionMetodo("v1");
        request.setPuntosACanjear(380);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(carritoService.obtenerOCrearCarritoEntity(1L)).thenReturn(carrito);
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        // Descuento de S/ 38.00 PEN en puntos. Al dividir por 3.80, debe descontar $10.00 USD
        when(crmService.validarYConsumirPuntos(usuario, 380, new BigDecimal("380.00"))).thenReturn(new BigDecimal("38.0000"));

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> {
            Pedido p = i.getArgument(0);
            p.setId(109L);
            return p;
        });

        PedidoResponse response = pedidoService.crearDesdeCarrito(1L, request);

        assertNotNull(response);
        assertEquals(109L, response.getId());
        assertEquals("USD", response.getMonedaCodigo());
        // 100.00 USD - 10.00 USD = 90.00 USD
        assertEquals(0, new BigDecimal("90.0000").compareTo(response.getTotal()));
        assertEquals(new BigDecimal("38.0000"), response.getDescuentoPuntosPen());
        assertEquals(380, response.getPuntosCanjeados());
    }
}
