package com.tienda.ecommerce.chat.ia;

import com.tienda.ecommerce.carrito.CarritoService;
import com.tienda.ecommerce.carrito.dto.CarritoResponse;
import com.tienda.ecommerce.carrito.dto.ItemCarritoRequest;
import com.tienda.ecommerce.chat.ChatEventPublisher;
import com.tienda.ecommerce.chat.ChatService;
import com.tienda.ecommerce.chat.ConversacionChat;
import com.tienda.ecommerce.chat.dto.ConversacionResponse;
import com.tienda.ecommerce.configuracion.AccesoService;
import com.tienda.ecommerce.crm.CrmService;
import com.tienda.ecommerce.crm.CuponUsoRepository;
import com.tienda.ecommerce.crm.PromocionCupon;
import com.tienda.ecommerce.crm.PromocionCuponRepository;
import com.tienda.ecommerce.crm.TipoDescuento;
import com.tienda.ecommerce.crm.dto.BilleteraResponse;
import com.tienda.ecommerce.pedido.Pedido;
import com.tienda.ecommerce.pedido.PedidoRepository;
import com.tienda.ecommerce.producto.Categoria;
import com.tienda.ecommerce.producto.CategoriaRepository;
import com.tienda.ecommerce.producto.Producto;
import com.tienda.ecommerce.producto.ProductoRepository;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class AsistenteHerramientasService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final PedidoRepository pedidoRepository;
    private final CarritoService carritoService;
    private final CrmService crmService;
    private final PromocionCuponRepository cuponRepository;
    private final ChatService chatService;

    @Autowired(required = false)
    private UsuarioRepository usuarioRepository;

    @Autowired(required = false)
    private AccesoService accesoService;

    @Autowired(required = false)
    private CuponUsoRepository cuponUsoRepository;

    @Autowired(required = false)
    private ChatEventPublisher eventPublisher;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    public AsistenteHerramientasService(
            ProductoRepository productoRepository,
            CategoriaRepository categoriaRepository,
            PedidoRepository pedidoRepository,
            CarritoService carritoService,
            CrmService crmService,
            PromocionCuponRepository cuponRepository,
            ChatService chatService
    ) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.pedidoRepository = pedidoRepository;
        this.carritoService = carritoService;
        this.crmService = crmService;
        this.cuponRepository = cuponRepository;
        this.chatService = chatService;
    }

    public List<Map<String, Object>> obtenerDefinicionesTools() {
        List<Map<String, Object>> declaraciones = List.of(
                Map.of(
                        "name", "buscar_productos",
                        "description", "Busca productos disponibles en el catálogo de Electronix por palabra clave, categoría o precio máximo.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "termino", Map.of("type", "STRING", "description", "Nombre o palabra clave del producto, ej: laptop, mouse, monitor, audífonos"),
                                        "categoria", Map.of("type", "STRING", "description", "Nombre opcional de categoría"),
                                        "precio_maximo", Map.of("type", "NUMBER", "description", "Presupuesto máximo opcional")
                                )
                        )
                ),
                Map.of(
                        "name", "agregar_al_carrito",
                        "description", "Agrega un producto específico al carrito de compras del cliente autenticado. Úsala solo cuando el usuario confirme que quiere añadirlo.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "producto_id", Map.of("type", "INTEGER", "description", "ID numérico exacto del producto obtenido en buscar_productos"),
                                        "cantidad", Map.of("type", "INTEGER", "description", "Cantidad a agregar (mínimo 1)")
                                ),
                                "required", List.of("producto_id", "cantidad")
                        )
                ),
                Map.of(
                        "name", "consultar_mis_pedidos",
                        "description", "Consulta el estado de los pedidos recientes del cliente o de un pedido específico por ID.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "pedido_id", Map.of("type", "INTEGER", "description", "ID opcional del pedido")
                                )
                        )
                ),
                Map.of(
                        "name", "consultar_mis_puntos_y_cupones",
                        "description", "Consulta los puntos de fidelidad, nivel del cliente y promociones públicas vigentes disponibles para su cuenta.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of()
                        )
                ),
                Map.of(
                        "name", "solicitar_asesor_humano",
                        "description", "Transfiere la conversación a un asesor humano de soporte cuando el cliente pide hablar con una persona, asesor, agente o reporta un problema complejo.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "motivo", Map.of("type", "STRING", "description", "Motivo breve del traspaso")
                                ),
                                "required", List.of("motivo")
                        )
                )
        );

        return List.of(Map.of("functionDeclarations", declaraciones));
    }

    // Sin @Transactional envolvente para que errores de negocio en agregarItem no marquen rollbackOnly en un nivel externo
    public Map<String, Object> ejecutarTool(String nombre, Map<String, Object> args, Long conversacionId, Long clienteId) {
        Map<String, Object> safeArgs = args != null ? args : Map.of();

        return switch (nombre) {
            case "buscar_productos" -> ejecutarBuscarProductos(safeArgs);
            case "agregar_al_carrito" -> ejecutarAgregarAlCarrito(safeArgs, clienteId);
            case "consultar_mis_pedidos" -> ejecutarConsultarPedidos(safeArgs, clienteId);
            case "consultar_mis_puntos_y_cupones" -> ejecutarConsultarPuntosYCupones(clienteId);
            case "solicitar_asesor_humano" -> ejecutarSolicitarAsesorHumano(safeArgs, conversacionId);
            default -> Map.of("error", "Herramienta no reconocida: " + nombre);
        };
    }

    private boolean tienePermiso(Long usuarioId, String permiso) {
        if (usuarioId == null || usuarioRepository == null || accesoService == null) {
            return false;
        }
        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        return usuario != null && usuario.isActivo() && accesoService.tiene(usuario, permiso);
    }

    private Map<String, Object> ejecutarBuscarProductos(Map<String, Object> args) {
        String termino = stringArg(args.get("termino"));
        String categoriaNombre = stringArg(args.get("categoria"));
        BigDecimal precioMax = decimalArg(args.get("precio_maximo"));

        Long categoriaId = null;
        if (categoriaNombre != null) {
            categoriaId = categoriaRepository.findByNombreIgnoreCase(categoriaNombre)
                    .map(Categoria::getId)
                    .orElse(null);
        }

        List<Producto> encontrados = productoRepository.buscarActivosConFiltros(
                termino,
                categoriaId,
                null,
                precioMax,
                null,
                PageRequest.of(0, 5)
        ).getContent();

        if (encontrados.isEmpty() && (termino != null || categoriaId != null)) {
            encontrados = productoRepository.buscarActivosConFiltros(
                    null, null, null, precioMax, null, PageRequest.of(0, 5)
            ).getContent();
        }

        List<Map<String, Object>> productosDto = new ArrayList<>();
        for (Producto p : encontrados) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("nombre", p.getNombre());
            item.put("categoria", p.getCategoria() != null ? p.getCategoria().getNombre() : "");
            item.put("precio", p.getPrecio());
            if ("OFERTA".equalsIgnoreCase(p.getEtiqueta()) && p.getPrecioOferta() != null) {
                item.put("precioOferta", p.getPrecioOferta());
            }
            item.put("moneda", p.getMonedaCodigo());
            item.put("stock", p.getStock());
            item.put("url", "/producto.html?id=" + p.getId());
            String desc = p.getDescripcion() != null ? p.getDescripcion() : "";
            item.put("descripcion", desc.length() > 140 ? desc.substring(0, 140) + "..." : desc);
            productosDto.add(item);
        }

        return Map.of(
                "total", productosDto.size(),
                "productos", productosDto
        );
    }

    private Map<String, Object> ejecutarAgregarAlCarrito(Map<String, Object> args, Long clienteId) {
        if (!tienePermiso(clienteId, "COMPRAR")) {
            return Map.of("exito", false, "mensaje", "Tu cuenta no tiene permiso de compra para modificar el carrito.");
        }
        Long productoId = longArg(args.get("producto_id"));
        Integer cantidad = intArg(args.get("cantidad"), 1);
        if (productoId == null || cantidad == null || cantidad <= 0 || cantidad > 100) {
            return Map.of("exito", false, "mensaje", "ID de producto o cantidad inválidos (1-100).");
        }
        try {
            CarritoResponse carrito = carritoService.agregarItem(clienteId, new ItemCarritoRequest(productoId, cantidad));
            return Map.of(
                    "exito", true,
                    "mensaje", "Producto agregado al carrito correctamente.",
                    "totalItemsCarrito", carrito.getItems() != null ? carrito.getItems().size() : 0,
                    "subtotal", carrito.getTotal() != null ? carrito.getTotal() : BigDecimal.ZERO
            );
        } catch (Exception ex) {
            return Map.of("exito", false, "mensaje", ex.getMessage());
        }
    }

    private Map<String, Object> ejecutarConsultarPedidos(Map<String, Object> args, Long clienteId) {
        if (!tienePermiso(clienteId, "COMPRAR")) {
            return Map.of("encontrado", false, "mensaje", "Tu cuenta no tiene permiso para consultar pedidos.");
        }
        Long pedidoId = longArg(args.get("pedido_id"));
        List<Pedido> pedidosUsuario = pedidoRepository.findByUsuarioIdOrderByFechaDesc(clienteId);

        if (pedidoId != null) {
            Optional<Pedido> encontrado = pedidosUsuario.stream()
                    .filter(p -> p.getId().equals(pedidoId))
                    .findFirst();
            if (encontrado.isEmpty()) {
                return Map.of("encontrado", false, "mensaje", "No encontré el pedido #" + pedidoId + " en tu cuenta.");
            }
            return Map.of("encontrado", true, "pedido", mapearPedido(encontrado.get()));
        }

        List<Map<String, Object>> recientes = pedidosUsuario.stream()
                .limit(3)
                .map(this::mapearPedido)
                .toList();

        return Map.of(
                "totalPedidos", pedidosUsuario.size(),
                "recientes", recientes
        );
    }

    private Map<String, Object> mapearPedido(Pedido p) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", p.getId());
        dto.put("estado", p.getEstado().name());
        dto.put("total", p.getTotal());
        dto.put("moneda", p.getMonedaCodigo());
        dto.put("fecha", p.getFecha() != null ? p.getFecha().toString() : "");
        if (p.getPago() != null && p.getPago().getEstadoPago() != null) {
            dto.put("estadoPago", p.getPago().getEstadoPago().name());
        }
        return dto;
    }

    private Map<String, Object> ejecutarConsultarPuntosYCupones(Long clienteId) {
        if (!tienePermiso(clienteId, "CRM_VER_PUNTOS")) {
            return Map.of("disponible", false, "mensaje", "Tu cuenta no tiene permiso para consultar puntos de fidelidad.");
        }
        BilleteraResponse billetera = crmService.obtenerBilleteraUsuario(clienteId);
        List<Map<String, Object>> cuponesDisponibles = cuponRepository.findAll().stream()
                .filter(c -> c.esVigente()
                        && c.tieneCupo()
                        && c.getTipoDescuento() != TipoDescuento.ENVIO_GRATIS
                        && (c.getLimiteUsosTotal() == null || c.getLimiteUsosTotal() > 1))
                .filter(c -> cuponUsoRepository == null
                        || cuponUsoRepository.countByCuponIdAndUsuarioIdAndActivoTrue(c.getId(), clienteId) < c.getLimitePorUsuario())
                .limit(5)
                .map(c -> Map.<String, Object>of(
                        "codigo", c.getCodigo(),
                        "descripcion", c.getDescripcion() != null ? c.getDescripcion() : "",
                        "tipoDescuento", c.getTipoDescuento().name(),
                        "valor", c.getValor()
                ))
                .toList();

        return Map.of(
                "saldoPuntos", billetera.getSaldoPuntos(),
                "equivalentePen", billetera.getEquivalentePen() != null ? billetera.getEquivalentePen() : BigDecimal.ZERO,
                "nivelActual", billetera.getNivelNombre() != null ? billetera.getNivelNombre() : "Estándar",
                "envioGratis", billetera.isBeneficioEnvioGratis(),
                "cuponesVigentes", cuponesDisponibles
        );
    }

    private Map<String, Object> ejecutarSolicitarAsesorHumano(Map<String, Object> args, Long conversacionId) {
        String motivo = stringArg(args.get("motivo"));
        Optional<ConversacionResponse> escalada = chatService.escalarAHumanoDesdeBot(conversacionId, motivo);
        if (escalada.isEmpty()) {
            return Map.of(
                    "escalado", false,
                    "mensaje", "La conversación ya está siendo atendida por un asesor humano o no admite traspaso."
            );
        }

        ConversacionResponse actualizada = escalada.get();
        Map<String, Object> eventoEstado = Map.of(
                "tipo", "MODO_CAMBIADO",
                "conversacionId", conversacionId,
                "modoAtencion", ConversacionChat.MODO_ESPERANDO_HUMANO,
                "adminNombre", "Esperando asesor"
        );
        Map<String, Object> alertaAdmin = Map.of(
                "tipo", "ESCALADO_HUMANO",
                "conversacionId", conversacionId,
                "clienteNombre", actualizada.getClienteNombre() != null ? actualizada.getClienteNombre() : "Cliente",
                "motivo", motivo != null ? motivo : "Solicitud de atención humana"
        );

        if (eventPublisher != null) {
            eventPublisher.publicarEvento("/topic/chat/" + conversacionId + "/estado", eventoEstado);
            eventPublisher.publicarEvento("/topic/admin/chat-alerts", alertaAdmin);
        } else if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/chat/" + conversacionId + "/estado", eventoEstado);
            messagingTemplate.convertAndSend("/topic/admin/chat-alerts", alertaAdmin);
        }

        return Map.of(
                "escalado", true,
                "modoAtencion", ConversacionChat.MODO_ESPERANDO_HUMANO,
                "mensaje", "Conversación marcada como ESPERANDO_HUMANO y notificada al panel de soporte."
        );
    }

    private String stringArg(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private BigDecimal decimalArg(Object val) {
        if (val == null) return null;
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long longArg(Object val) {
        if (val == null) return null;
        try {
            if (val instanceof Number n) return n.longValue();
            return Long.parseLong(val.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer intArg(Object val, int defecto) {
        if (val == null) return defecto;
        try {
            long parsed = (val instanceof Number n) ? n.longValue() : Long.parseLong(val.toString().trim());
            if (parsed < 1 || parsed > 1000) return null;
            return (int) parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
