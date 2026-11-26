package com.tienda.ecommerce.crm;

import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.common.RecursoNoEncontradoException;
import com.tienda.ecommerce.crm.dto.*;
import com.tienda.ecommerce.pedido.Pedido;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CrmServiceImpl implements CrmService {

    private final PromocionCuponRepository cuponRepository;
    private final CuponUsoRepository cuponUsoRepository;
    private final PuntosBilleteraRepository puntosBilleteraRepository;
    private final PuntosTransaccionRepository puntosTransaccionRepository;
    private final PuntosConfigRepository puntosConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final NivelClienteRepository nivelClienteRepository;
    private final com.tienda.ecommerce.pedido.PedidoRepository pedidoRepository;
    private final ClienteNotaRepository clienteNotaRepository;

    public CrmServiceImpl(
            PromocionCuponRepository cuponRepository,
            CuponUsoRepository cuponUsoRepository,
            PuntosBilleteraRepository puntosBilleteraRepository,
            PuntosTransaccionRepository puntosTransaccionRepository,
            PuntosConfigRepository puntosConfigRepository,
            UsuarioRepository usuarioRepository,
            NivelClienteRepository nivelClienteRepository,
            com.tienda.ecommerce.pedido.PedidoRepository pedidoRepository,
            ClienteNotaRepository clienteNotaRepository
    ) {
        this.cuponRepository = cuponRepository;
        this.cuponUsoRepository = cuponUsoRepository;
        this.puntosBilleteraRepository = puntosBilleteraRepository;
        this.puntosTransaccionRepository = puntosTransaccionRepository;
        this.puntosConfigRepository = puntosConfigRepository;
        this.usuarioRepository = usuarioRepository;
        this.nivelClienteRepository = nivelClienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.clienteNotaRepository = clienteNotaRepository;
    }

    // =========================================================================
    // CUPONES Y PROMOCIONES (FASE 1)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public ValidarCuponResponse validarCupon(String codigo, Long usuarioId, BigDecimal subtotalBasePen) {
        if (codigo == null || codigo.isBlank()) {
            return ValidarCuponResponse.invalido(codigo, "El código de cupón no puede estar vacío");
        }
        if (subtotalBasePen == null || subtotalBasePen.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidarCuponResponse.invalido(codigo, "El subtotal debe ser mayor a cero");
        }

        String codigoNorm = codigo.trim().toUpperCase();
        PromocionCupon cupon = cuponRepository.findByCodigoIgnoreCase(codigoNorm).orElse(null);
        if (cupon == null) {
            return ValidarCuponResponse.invalido(codigoNorm, "El cupón '" + codigoNorm + "' no existe");
        }

        if (!cupon.isActivo()) {
            return ValidarCuponResponse.invalido(codigoNorm, "El cupón se encuentra inactivo");
        }

        if (cupon.getTipoDescuento() == TipoDescuento.ENVIO_GRATIS) {
            return ValidarCuponResponse.invalido(codigoNorm, "Los cupones de envío gratis no están disponibles actualmente ya que los envíos no tienen costo adicional");
        }

        LocalDateTime ahora = LocalDateTime.now();
        if (cupon.getFechaInicio() != null && ahora.isBefore(cupon.getFechaInicio())) {
            return ValidarCuponResponse.invalido(codigoNorm, "El cupón aún no entra en vigencia");
        }
        if (cupon.getFechaFin() != null && ahora.isAfter(cupon.getFechaFin())) {
            return ValidarCuponResponse.invalido(codigoNorm, "El cupón ha expirado");
        }

        if (!cupon.tieneCupo()) {
            return ValidarCuponResponse.invalido(codigoNorm, "El cupón ha agotado su límite de usos disponibles");
        }

        if (usuarioId != null) {
            long usosUsuario = cuponUsoRepository.countByCuponIdAndUsuarioIdAndActivoTrue(cupon.getId(), usuarioId);
            if (usosUsuario >= cupon.getLimitePorUsuario()) {
                return ValidarCuponResponse.invalido(codigoNorm, "Ya has alcanzado el límite de usos permitidos para este cupón");
            }
        }

        if (cupon.getMontoMinimoPen() != null && subtotalBasePen.compareTo(cupon.getMontoMinimoPen()) < 0) {
            return ValidarCuponResponse.invalido(
                    codigoNorm,
                    String.format("El monto mínimo de compra para este cupón es S/ %.2f", cupon.getMontoMinimoPen())
            );
        }

        BigDecimal descuento = calcularDescuento(cupon, subtotalBasePen);
        BigDecimal totalConDescuento = subtotalBasePen.subtract(descuento).max(BigDecimal.ZERO);

        return ValidarCuponResponse.exitoso(
                cupon.getCodigo(),
                cupon.getDescripcion(),
                cupon.getTipoDescuento(),
                cupon.getValor(),
                descuento,
                totalConDescuento,
                cupon.isPermiteCombinarPuntos(),
                "Cupón aplicado correctamente"
        );
    }

    @Override
    @Transactional
    public CuponResponse crearCupon(CrearCuponRequest request) {
        String codigoNorm = request.getCodigo().trim().toUpperCase();
        if (cuponRepository.existsByCodigoIgnoreCase(codigoNorm)) {
            throw new BadRequestException("Ya existe un cupón con el código: " + codigoNorm);
        }

        if (request.getTipoDescuento() == TipoDescuento.ENVIO_GRATIS) {
            throw new BadRequestException("El tipo de descuento ENVIO_GRATIS no está disponible actualmente ya que los envíos no tienen costo adicional");
        }

        if (!request.getFechaFin().isAfter(request.getFechaInicio())) {
            throw new BadRequestException("La fecha de fin debe ser posterior a la fecha de inicio");
        }

        if (request.getLimitePorUsuario() != null && request.getLimitePorUsuario() < 1) {
            throw new BadRequestException("El límite por usuario debe ser al menos 1");
        }

        if (request.getTipoDescuento() == TipoDescuento.PORCENTAJE) {
            if (request.getValor().compareTo(BigDecimal.ZERO) <= 0 || request.getValor().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BadRequestException("Para descuentos porcentuales, el valor debe estar entre 0.01 y 100%");
            }
        }

        PromocionCupon cupon = new PromocionCupon();
        cupon.setCodigo(codigoNorm);
        cupon.setDescripcion(request.getDescripcion() != null ? request.getDescripcion().trim() : null);
        cupon.setTipoDescuento(request.getTipoDescuento());
        cupon.setValor(request.getValor());
        cupon.setMontoMinimoPen(request.getMontoMinimoPen() != null ? request.getMontoMinimoPen() : BigDecimal.ZERO);
        cupon.setTopeDescuentoPen(request.getTopeDescuentoPen());
        cupon.setLimiteUsosTotal(request.getLimiteUsosTotal());
        cupon.setLimitePorUsuario(request.getLimitePorUsuario() != null ? request.getLimitePorUsuario() : 1);
        cupon.setPermiteCombinarPuntos(request.getPermiteCombinarPuntos() != null && request.getPermiteCombinarPuntos());
        cupon.setFechaInicio(request.getFechaInicio());
        cupon.setFechaFin(request.getFechaFin());
        cupon.setActivo(request.getActivo() != null ? request.getActivo() : true);
        cupon.setUsosActuales(0);

        PromocionCupon guardado = cuponRepository.save(cupon);
        return CuponResponse.desdeEntidad(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuponResponse> listarCupones() {
        return cuponRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(CuponResponse::desdeEntidad)
                .toList();
    }

    @Override
    @Transactional
    public CuponResponse cambiarEstado(Long id, boolean activo) {
        PromocionCupon cupon = cuponRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cupón no encontrado con id: " + id));
        cupon.setActivo(activo);
        PromocionCupon guardado = cuponRepository.save(cupon);
        return CuponResponse.desdeEntidad(guardado);
    }

    @Override
    @Transactional
    public void eliminarCupon(Long id) {
        PromocionCupon cupon = cuponRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cupón no encontrado con id: " + id));
        if (cupon.getUsosActuales() > 0 || cuponUsoRepository.existsByCuponId(id)) {
            // Soft delete para no romper integridad histórica ni violar FK
            cupon.setActivo(false);
            cuponRepository.save(cupon);
        } else {
            cuponRepository.delete(cupon);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuponUsoResponse> listarUsos(Long cuponId) {
        if (!cuponRepository.existsById(cuponId)) {
            throw new RecursoNoEncontradoException("Cupón no encontrado con id: " + cuponId);
        }
        return cuponUsoRepository.findByCuponIdOrderByFechaUsoDesc(cuponId).stream()
                .map(CuponUsoResponse::desdeEntidad)
                .toList();
    }

    @Override
    @Transactional
    public BigDecimal validarYConsumirCupo(String codigo, Usuario usuario, BigDecimal subtotalBasePen) {
        return validarYConsumirCupo(codigo, usuario, subtotalBasePen, false);
    }

    @Override
    @Transactional
    public BigDecimal validarYConsumirCupo(String codigo, Usuario usuario, BigDecimal subtotalBasePen, boolean combinaConPuntos) {
        if (codigo == null || codigo.isBlank()) {
            return BigDecimal.ZERO;
        }

        String codigoNorm = codigo.trim().toUpperCase();
        PromocionCupon cupon = cuponRepository.findByCodigoConLock(codigoNorm)
                .orElseThrow(() -> new BadRequestException("El cupón '" + codigoNorm + "' no existe"));

        if (!cupon.isActivo()) {
            throw new BadRequestException("El cupón '" + codigoNorm + "' está desactivado");
        }

        if (combinaConPuntos && !cupon.isPermiteCombinarPuntos()) {
            throw new BadRequestException("El cupón '" + cupon.getCodigo() + "' no permite ser combinado con puntos de fidelización");
        }

        LocalDateTime ahora = LocalDateTime.now();
        if (cupon.getFechaInicio() != null && ahora.isBefore(cupon.getFechaInicio())) {
            throw new BadRequestException("El cupón aún no entra en vigencia");
        }
        if (cupon.getFechaFin() != null && ahora.isAfter(cupon.getFechaFin())) {
            throw new BadRequestException("El cupón ha expirado");
        }

        if (cupon.getTipoDescuento() == TipoDescuento.ENVIO_GRATIS) {
            throw new BadRequestException("Los cupones de envío gratis no están disponibles actualmente ya que los envíos no tienen costo adicional");
        }

        if (!cupon.tieneCupo()) {
            throw new BadRequestException("El cupón ha agotado su límite de usos disponibles");
        }

        long usosUsuario = cuponUsoRepository.countByCuponIdAndUsuarioIdAndActivoTrue(cupon.getId(), usuario.getId());
        if (usosUsuario >= cupon.getLimitePorUsuario()) {
            throw new BadRequestException("Ya has alcanzado el límite de usos permitidos para este cupón");
        }

        if (cupon.getMontoMinimoPen() != null && subtotalBasePen.compareTo(cupon.getMontoMinimoPen()) < 0) {
            throw new BadRequestException(
                    String.format("El monto mínimo de compra para este cupón es S/ %.2f", cupon.getMontoMinimoPen())
            );
        }

        BigDecimal descuentoPen = calcularDescuento(cupon, subtotalBasePen);

        // Incrementar uso de manera atómica bajo bloqueo pesimista
        cupon.setUsosActuales(cupon.getUsosActuales() + 1);
        cuponRepository.save(cupon);

        return descuentoPen;
    }

    @Override
    @Transactional
    public void registrarUsoCupon(String codigo, Usuario usuario, Pedido pedido, BigDecimal descuentoPen) {
        if (codigo == null || codigo.isBlank()) {
            return;
        }
        String codigoNorm = codigo.trim().toUpperCase();
        PromocionCupon cupon = cuponRepository.findByCodigoIgnoreCase(codigoNorm)
                .orElseThrow(() -> new BadRequestException("El cupón '" + codigoNorm + "' no existe"));

        CuponUso uso = new CuponUso(cupon, usuario, pedido, descuentoPen != null ? descuentoPen : BigDecimal.ZERO);
        cuponUsoRepository.save(uso);
    }

    @Override
    @Transactional
    public BigDecimal aplicarCuponAtomico(String codigo, Usuario usuario, Pedido pedido, BigDecimal subtotalBasePen) {
        BigDecimal descuentoPen = validarYConsumirCupo(codigo, usuario, subtotalBasePen);
        if (pedido != null && pedido.getId() != null) {
            registrarUsoCupon(codigo, usuario, pedido, descuentoPen);
        }
        return descuentoPen;
    }

    @Override
    @Transactional
    public void revertirCupon(Long pedidoId) {
        cuponUsoRepository.findByPedidoId(pedidoId).ifPresent(uso -> {
            if (uso.isActivo()) {
                uso.setActivo(false);
                cuponUsoRepository.save(uso);

                PromocionCupon cuponRel = uso.getCupon();
                if (cuponRel != null && cuponRel.getCodigo() != null) {
                    cuponRepository.findByCodigoConLock(cuponRel.getCodigo()).ifPresent(cuponLocked -> {
                        if (cuponLocked.getUsosActuales() > 0) {
                            cuponLocked.setUsosActuales(cuponLocked.getUsosActuales() - 1);
                            cuponRepository.save(cuponLocked);
                        }
                    });
                }
            }
        });
    }

    private BigDecimal calcularDescuento(PromocionCupon cupon, BigDecimal subtotalBasePen) {
        BigDecimal descuento = BigDecimal.ZERO;
        if (cupon.getTipoDescuento() == TipoDescuento.PORCENTAJE) {
            descuento = subtotalBasePen.multiply(cupon.getValor())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            if (cupon.getTopeDescuentoPen() != null && descuento.compareTo(cupon.getTopeDescuentoPen()) > 0) {
                descuento = cupon.getTopeDescuentoPen();
            }
        } else if (cupon.getTipoDescuento() == TipoDescuento.MONTO_FIJO) {
            descuento = cupon.getValor();
            if (descuento.compareTo(subtotalBasePen) > 0) {
                descuento = subtotalBasePen;
            }
        } else if (cupon.getTipoDescuento() == TipoDescuento.ENVIO_GRATIS) {
            descuento = BigDecimal.ZERO;
        }
        return descuento.setScale(4, RoundingMode.HALF_UP);
    }

    // =========================================================================
    // PUNTOS Y FIDELIZACIÓN (FASE 2)
    // =========================================================================

    private PuntosConfig obtenerOCrearConfig() {
        return puntosConfigRepository.findById(1).orElseGet(() -> {
            PuntosConfig defecto = new PuntosConfig();
            defecto.setId(1);
            return puntosConfigRepository.save(defecto);
        });
    }

    private NivelCliente obtenerNivelPorDefecto() {
        return nivelClienteRepository.findById(NivelCliente.BRONCE).orElseGet(() -> {
            NivelCliente bronce = new NivelCliente(NivelCliente.BRONCE, "Bronce", BigDecimal.ZERO, BigDecimal.ONE, false, 1);
            return nivelClienteRepository.save(bronce);
        });
    }

    private PuntosBilletera obtenerOCrearBilletera(Usuario usuario) {
        return puntosBilleteraRepository.findByUsuarioId(usuario.getId()).orElseGet(() -> {
            PuntosConfig cfg = obtenerOCrearConfig();
            int bono = cfg.isActivo() ? cfg.getPuntosBienvenida() : 0;
            NivelCliente nivelDefecto = obtenerNivelPorDefecto();
            PuntosBilletera nueva = new PuntosBilletera(usuario, bono, nivelDefecto);
            PuntosBilletera guardada = puntosBilleteraRepository.save(nueva);
            if (bono > 0) {
                PuntosTransaccion tx = new PuntosTransaccion(
                        guardada,
                        TipoTransaccionPuntos.BONO_BIENVENIDA,
                        bono,
                        bono,
                        null,
                        "Bono de bienvenida al programa de fidelización",
                        null
                );
                puntosTransaccionRepository.save(tx);
            }
            return guardada;
        });
    }

    @Override
    @Transactional
    public void inicializarBilleteraBienvenida(Usuario usuario) {
        if (!puntosBilleteraRepository.existsByUsuarioId(usuario.getId())) {
            obtenerOCrearBilletera(usuario);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BilleteraResponse obtenerBilleteraUsuario(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id: " + usuarioId));

        PuntosBilletera billetera = puntosBilleteraRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> new PuntosBilletera(usuario, 0, obtenerNivelPorDefecto()));

        PuntosConfig cfg = obtenerOCrearConfig();
        BigDecimal equivPen = BigDecimal.valueOf(billetera.getSaldoPuntos())
                .multiply(cfg.getValorPenPorPunto())
                .setScale(2, RoundingMode.HALF_UP);

        List<PuntosTransaccionDto> txs = billetera.getId() != null
                ? puntosTransaccionRepository.findByBilleteraIdOrderByFechaDesc(billetera.getId()).stream()
                    .map(PuntosTransaccionDto::desdeEntidad)
                    .toList()
                : List.of();

        // Cálculo de gasto anual en pedidos aprobados últimos 365 días
        BigDecimal gastoAnualPen = pedidoRepository.calcularGastoAnualPen(usuarioId, LocalDateTime.now().minusDays(365));
        if (gastoAnualPen == null) {
            gastoAnualPen = BigDecimal.ZERO;
        }

        NivelCliente nivelActual = billetera.getNivel() != null
                ? nivelClienteRepository.findById(billetera.getNivel().getCodigo()).orElse(billetera.getNivel())
                : obtenerNivelPorDefecto();

        // Determinar nivel efectivo alcanzado por gasto anual para consistencia informativa (MEDIUM-01)
        List<NivelCliente> aplicables = nivelClienteRepository.findByUmbralPenAnualLessThanEqualOrderByOrdenDesc(gastoAnualPen);
        NivelCliente mejorNivel = aplicables.isEmpty() ? obtenerNivelPorDefecto() : aplicables.get(0);
        if (mejorNivel.getOrden() > nivelActual.getOrden()) {
            nivelActual = mejorNivel;
        }

        // Buscar siguiente nivel disponible
        List<NivelCliente> todosNiveles = nivelClienteRepository.findAllByOrderByOrdenAsc();
        NivelCliente siguienteNivel = null;
        int ordenActual = nivelActual.getOrden();
        for (NivelCliente n : todosNiveles) {
            if (n.getOrden() > ordenActual) {
                siguienteNivel = n;
                break;
            }
        }

        BilleteraResponse res = new BilleteraResponse();
        res.setUsuarioId(usuario.getId());
        res.setUsuarioNombre(usuario.getNombreCompleto());
        res.setUsuarioEmail(usuario.getEmail());
        res.setSaldoPuntos(billetera.getSaldoPuntos());
        res.setEquivalentePen(equivPen);
        res.setPuntosHistoricos(billetera.getPuntosHistoricos());
        res.setMinimoPuntosCanje(cfg.getMinimoPuntosCanje());
        res.setMaxCoberturaPorcentaje(cfg.getMaxCoberturaPorcentaje());
        res.setValorPenPorPunto(cfg.getValorPenPorPunto());
        res.setTransacciones(txs);

        // Atributos de nivel y progreso anual
        res.setNivelCodigo(nivelActual.getCodigo());
        res.setNivelNombre(nivelActual.getNombre());
        res.setMultiplicador(nivelActual.getMultiplicador());
        res.setBeneficioEnvioGratis(nivelActual.isBeneficioEnvio());
        res.setGastoAnualPen(gastoAnualPen.setScale(2, RoundingMode.HALF_UP));

        if (siguienteNivel != null) {
            res.setSiguienteNivelNombre(siguienteNivel.getNombre());
            res.setUmbralSiguienteNivelPen(siguienteNivel.getUmbralPenAnual().setScale(2, RoundingMode.HALF_UP));
            BigDecimal faltante = siguienteNivel.getUmbralPenAnual().subtract(gastoAnualPen).max(BigDecimal.ZERO);
            res.setMontoFaltanteSiguienteNivelPen(faltante.setScale(2, RoundingMode.HALF_UP));

            if (siguienteNivel.getUmbralPenAnual().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal progreso = gastoAnualPen.divide(siguienteNivel.getUmbralPenAnual(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .min(BigDecimal.valueOf(100.00))
                        .setScale(1, RoundingMode.HALF_UP);
                res.setPorcentajeProgresoNivel(progreso);
            } else {
                res.setPorcentajeProgresoNivel(BigDecimal.valueOf(100.0).setScale(1, RoundingMode.HALF_UP));
            }
        } else {
            // Máximo nivel alcanzado
            res.setSiguienteNivelNombre(null);
            res.setUmbralSiguienteNivelPen(null);
            res.setMontoFaltanteSiguienteNivelPen(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            res.setPorcentajeProgresoNivel(BigDecimal.valueOf(100.0).setScale(1, RoundingMode.HALF_UP));
        }

        return res;
    }

    @Override
    @Transactional(readOnly = true)
    public SimularCanjePuntosResponse simularCanje(Long usuarioId, Integer puntosACanjear, BigDecimal subtotalBasePen) {
        if (puntosACanjear == null || puntosACanjear <= 0) {
            return SimularCanjePuntosResponse.invalido(0, 0, "Ingresa una cantidad de puntos válida a canjear");
        }
        if (subtotalBasePen == null || subtotalBasePen.compareTo(BigDecimal.ZERO) <= 0) {
            return SimularCanjePuntosResponse.invalido(0, puntosACanjear, "El subtotal debe ser mayor a cero");
        }

        PuntosConfig cfg = obtenerOCrearConfig();
        if (!cfg.isActivo()) {
            return SimularCanjePuntosResponse.invalido(0, puntosACanjear, "El programa de canje de puntos se encuentra temporalmente inactivo");
        }

        PuntosBilletera billetera = puntosBilleteraRepository.findByUsuarioId(usuarioId).orElse(null);
        int saldo = billetera != null ? billetera.getSaldoPuntos() : 0;

        if (saldo < cfg.getMinimoPuntosCanje()) {
            return SimularCanjePuntosResponse.invalido(
                    saldo,
                    puntosACanjear,
                    String.format("Necesitas al menos %d puntos para realizar un canje (Tienes %d)", cfg.getMinimoPuntosCanje(), saldo)
            );
        }

        if (puntosACanjear < cfg.getMinimoPuntosCanje()) {
            return SimularCanjePuntosResponse.invalido(
                    saldo,
                    puntosACanjear,
                    String.format("El mínimo de puntos a canjear es de %d puntos", cfg.getMinimoPuntosCanje())
            );
        }

        if (puntosACanjear > saldo) {
            return SimularCanjePuntosResponse.invalido(
                    saldo,
                    puntosACanjear,
                    String.format("No cuentas con suficientes puntos. Tu saldo disponible es de %d puntos", saldo)
            );
        }

        // Calcular tope de cobertura (ej. máx 50% del subtotal)
        BigDecimal maxDescuentoPermitido = subtotalBasePen.multiply(BigDecimal.valueOf(cfg.getMaxCoberturaPorcentaje()))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        BigDecimal descuentoCalculado = BigDecimal.valueOf(puntosACanjear)
                .multiply(cfg.getValorPenPorPunto())
                .setScale(4, RoundingMode.HALF_UP);

        int puntosEfectivos = puntosACanjear;
        BigDecimal descuentoEfectivo = descuentoCalculado;

        if (descuentoCalculado.compareTo(maxDescuentoPermitido) > 0) {
            // Ajustar al tope máximo permitido
            int maxPuntos = maxDescuentoPermitido.divide(cfg.getValorPenPorPunto(), 0, RoundingMode.FLOOR).intValue();
            puntosEfectivos = Math.min(maxPuntos, saldo);
            descuentoEfectivo = BigDecimal.valueOf(puntosEfectivos)
                    .multiply(cfg.getValorPenPorPunto())
                    .setScale(4, RoundingMode.HALF_UP);
        }

        if (puntosEfectivos < cfg.getMinimoPuntosCanje()) {
            return SimularCanjePuntosResponse.invalido(
                    saldo,
                    puntosACanjear,
                    String.format("El monto de la compra no permite canjear el mínimo requerido de %d puntos respetando el tope de cobertura del %d%%",
                            cfg.getMinimoPuntosCanje(), cfg.getMaxCoberturaPorcentaje())
            );
        }

        BigDecimal totalRestante = subtotalBasePen.subtract(descuentoEfectivo).max(BigDecimal.ZERO);

        return SimularCanjePuntosResponse.exitoso(
                saldo,
                puntosACanjear,
                puntosEfectivos,
                descuentoEfectivo,
                totalRestante,
                String.format("Puedes canjear %d puntos por un descuento de S/ %.2f", puntosEfectivos, descuentoEfectivo)
        );
    }

    @Override
    @Transactional
    public BigDecimal validarYConsumirPuntos(Usuario usuario, Integer puntosACanjear, BigDecimal subtotalBasePen) {
        if (puntosACanjear == null || puntosACanjear <= 0) {
            return BigDecimal.ZERO;
        }

        PuntosConfig cfg = obtenerOCrearConfig();
        if (!cfg.isActivo()) {
            throw new BadRequestException("El programa de canje de puntos se encuentra desactivado");
        }

        // Bloqueo pesimista para evitar doble gasto concurrente
        PuntosBilletera billetera = puntosBilleteraRepository.findByUsuarioIdConLock(usuario.getId())
                .orElseThrow(() -> new BadRequestException("El usuario no tiene una billetera de puntos activa"));

        if (billetera.getSaldoPuntos() < cfg.getMinimoPuntosCanje()) {
            throw new BadRequestException(
                    String.format("Necesitas un saldo mínimo de %d puntos para canjear (Tienes %d)", cfg.getMinimoPuntosCanje(), billetera.getSaldoPuntos())
            );
        }

        if (puntosACanjear < cfg.getMinimoPuntosCanje()) {
            throw new BadRequestException(
                    String.format("El canje mínimo permitido es de %d puntos", cfg.getMinimoPuntosCanje())
            );
        }

        if (puntosACanjear > billetera.getSaldoPuntos()) {
            throw new BadRequestException(
                    String.format("Saldo insuficiente de puntos. Disponibles: %d, Solicitados: %d", billetera.getSaldoPuntos(), puntosACanjear)
            );
        }

        // Calcular límite por política de cobertura máxima
        BigDecimal maxDescuentoPermitido = subtotalBasePen.multiply(BigDecimal.valueOf(cfg.getMaxCoberturaPorcentaje()))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        BigDecimal descuentoCalculado = BigDecimal.valueOf(puntosACanjear)
                .multiply(cfg.getValorPenPorPunto())
                .setScale(4, RoundingMode.HALF_UP);

        if (descuentoCalculado.compareTo(maxDescuentoPermitido) > 0) {
            throw new BadRequestException(
                    String.format("Los puntos no pueden cubrir más del %d%% del subtotal (Máximo descuento: S/ %.2f)",
                            cfg.getMaxCoberturaPorcentaje(), maxDescuentoPermitido)
            );
        }

        // Deducir saldo de manera atómica bajo bloqueo pesimista
        billetera.setSaldoPuntos(billetera.getSaldoPuntos() - puntosACanjear);
        puntosBilleteraRepository.save(billetera);

        return descuentoCalculado;
    }

    @Override
    @Transactional
    public void registrarCanjePuntos(Usuario usuario, Pedido pedido, Integer puntosCanjeados, BigDecimal descuentoPen) {
        if (puntosCanjeados == null || puntosCanjeados <= 0) {
            return;
        }

        PuntosBilletera billetera = puntosBilleteraRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalStateException("Billetera no encontrada para registrar canje"));

        PuntosTransaccion tx = new PuntosTransaccion(
                billetera,
                TipoTransaccionPuntos.CANJEADO_PEDIDO,
                -puntosCanjeados,
                billetera.getSaldoPuntos(),
                pedido,
                String.format("Canje de %d puntos en Pedido #%d (-S/ %.2f)", puntosCanjeados, pedido.getId(), descuentoPen),
                null
        );
        puntosTransaccionRepository.save(tx);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void acreditarPuntosPorCompra(Pedido pedido) {
        if (pedido == null || pedido.getId() == null || pedido.getUsuario() == null) {
            return;
        }

        PuntosConfig cfg = obtenerOCrearConfig();
        if (!cfg.isActivo() || cfg.getFactorSolPorPunto().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Se usa total_base_pen (monto bruto antes de descuentos de cupón o puntos)
        BigDecimal basePen = pedido.getTotalBasePen() != null ? pedido.getTotalBasePen() : pedido.getTotal();
        if (basePen == null || basePen.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Adquirir bloqueo pesimista primero para evitar carreras TOCTOU (HIGH-02)
        PuntosBilletera billetera = puntosBilleteraRepository.findByUsuarioIdConLock(pedido.getUsuario().getId())
                .orElseGet(() -> obtenerOCrearBilletera(pedido.getUsuario()));

        // Idempotencia bajo bloqueo pesimista: evitar acreditar dos veces por el mismo pedido
        if (puntosTransaccionRepository.existsByPedidoIdAndTipo(pedido.getId(), TipoTransaccionPuntos.ACREDITADO_COMPRA)) {
            return;
        }

        // Evaluar nivel VIP del usuario en base a su gasto anual acumulado
        BigDecimal gastoAnualPen = pedidoRepository.calcularGastoAnualPen(pedido.getUsuario().getId(), LocalDateTime.now().minusDays(365));
        if (gastoAnualPen == null) {
            gastoAnualPen = BigDecimal.ZERO;
        }

        List<NivelCliente> aplicables = nivelClienteRepository.findByUmbralPenAnualLessThanEqualOrderByOrdenDesc(gastoAnualPen);
        NivelCliente mejorNivel = aplicables.isEmpty()
                ? obtenerNivelPorDefecto()
                : aplicables.get(0);

        boolean nivelAscendido = false;
        NivelCliente nivelActual = billetera.getNivel() != null ? billetera.getNivel() : obtenerNivelPorDefecto();
        if (mejorNivel.getOrden() > nivelActual.getOrden()) {
            billetera.setNivel(mejorNivel);
            nivelActual = mejorNivel;
            nivelAscendido = true;
        }

        // Obtener multiplicador fresco de la base de datos (AC-3.3)
        NivelCliente nivelFresco = nivelClienteRepository.findById(nivelActual.getCodigo()).orElse(nivelActual);
        BigDecimal multiplicador = (nivelFresco != null && nivelFresco.getMultiplicador() != null)
                ? nivelFresco.getMultiplicador()
                : BigDecimal.ONE;

        int puntosBase = basePen.divide(cfg.getFactorSolPorPunto(), 0, RoundingMode.FLOOR).intValue();
        int puntosGanados = BigDecimal.valueOf(puntosBase).multiply(multiplicador).setScale(0, RoundingMode.FLOOR).intValue();
        if (puntosGanados <= 0) {
            if (nivelAscendido) {
                puntosBilleteraRepository.save(billetera);
            }
            return;
        }

        billetera.setSaldoPuntos(billetera.getSaldoPuntos() + puntosGanados);
        billetera.setPuntosHistoricos(billetera.getPuntosHistoricos() + puntosGanados);
        puntosBilleteraRepository.save(billetera);

        String infoNivel = multiplicador.compareTo(BigDecimal.ONE) > 0
                ? String.format(" - Nivel %s (%.2fx)", nivelFresco.getNombre(), multiplicador)
                : "";

        PuntosTransaccion tx = new PuntosTransaccion(
                billetera,
                TipoTransaccionPuntos.ACREDITADO_COMPRA,
                puntosGanados,
                billetera.getSaldoPuntos(),
                pedido,
                String.format("Puntos ganados por compra en Pedido #%d (+%d pts%s)", pedido.getId(), puntosGanados, infoNivel),
                null
        );
        puntosTransaccionRepository.save(tx);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void revertirPuntos(Long pedidoId) {
        // Orden determinista por id ascendente (Residual Risk 1)
        List<PuntosTransaccion> transacciones = puntosTransaccionRepository.findByPedidoIdOrderByIdAsc(pedidoId);
        if (transacciones.isEmpty()) {
            return;
        }

        // Bloqueo pesimista antes de evaluar reversión para evitar doble reembolso concurrente (HIGH-03)
        Long usuarioId = transacciones.get(0).getBilletera().getUsuario().getId();
        PuntosBilletera billetera = puntosBilleteraRepository.findByUsuarioIdConLock(usuarioId)
                .orElse(transacciones.get(0).getBilletera());

        // Evitar duplicar la reversión si ya fue revertido
        boolean yaRevertido = transacciones.stream()
                .anyMatch(t -> t.getTipo() == TipoTransaccionPuntos.REVERSION_CANCELACION);
        if (yaRevertido) {
            return;
        }

        for (PuntosTransaccion tx : transacciones) {
            if (tx.getTipo() == TipoTransaccionPuntos.CANJEADO_PEDIDO) {
                // Devolver puntos canjeados al cliente
                int puntosADevolver = Math.abs(tx.getPuntos());
                billetera.setSaldoPuntos(billetera.getSaldoPuntos() + puntosADevolver);
                puntosBilleteraRepository.save(billetera);

                PuntosTransaccion revTx = new PuntosTransaccion(
                        billetera,
                        TipoTransaccionPuntos.REVERSION_CANCELACION,
                        puntosADevolver,
                        billetera.getSaldoPuntos(),
                        tx.getPedido(),
                        String.format("Devolución de puntos canjeados por anulación de Pedido #%d", pedidoId),
                        null
                );
                puntosTransaccionRepository.save(revTx);
            } else if (tx.getTipo() == TipoTransaccionPuntos.ACREDITADO_COMPRA) {
                // Descontar puntos que ya se habían acreditado por compra manteniendo invariante contable (MEDIUM-01)
                int puntosADescontar = tx.getPuntos();
                int puntosEfectivosADescontar = Math.min(billetera.getSaldoPuntos(), puntosADescontar);
                int nuevoSaldo = billetera.getSaldoPuntos() - puntosEfectivosADescontar;
                billetera.setSaldoPuntos(nuevoSaldo);
                billetera.setPuntosHistoricos(Math.max(0, billetera.getPuntosHistoricos() - puntosEfectivosADescontar));
                puntosBilleteraRepository.save(billetera);

                String motivo = puntosEfectivosADescontar < puntosADescontar
                        ? String.format("Deducción de %d pts acreditados por anulación de Pedido #%d (%d pts no recuperables por saldo insuficiente)",
                                puntosEfectivosADescontar, pedidoId, puntosADescontar - puntosEfectivosADescontar)
                        : String.format("Deducción de puntos acreditados por anulación de Pedido #%d", pedidoId);

                PuntosTransaccion revTx = new PuntosTransaccion(
                        billetera,
                        TipoTransaccionPuntos.REVERSION_CANCELACION,
                        -puntosEfectivosADescontar,
                        nuevoSaldo,
                        tx.getPedido(),
                        motivo,
                        null
                );
                puntosTransaccionRepository.save(revTx);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PuntosConfigDto obtenerConfigPuntos() {
        return PuntosConfigDto.desdeEntidad(obtenerOCrearConfig());
    }

    @Override
    @Transactional
    public PuntosConfigDto actualizarConfigPuntos(PuntosConfigDto dto) {
        PuntosConfig cfg = obtenerOCrearConfig();
        cfg.setFactorSolPorPunto(dto.getFactorSolPorPunto());
        cfg.setValorPenPorPunto(dto.getValorPenPorPunto());
        cfg.setMinimoPuntosCanje(dto.getMinimoPuntosCanje());
        cfg.setMaxCoberturaPorcentaje(dto.getMaxCoberturaPorcentaje());
        cfg.setPuntosBienvenida(dto.getPuntosBienvenida());
        if (dto.getActivo() != null) {
            cfg.setActivo(dto.getActivo());
        }
        PuntosConfig guardado = puntosConfigRepository.save(cfg);
        return PuntosConfigDto.desdeEntidad(guardado);
    }

    @Override
    @Transactional
    public BilleteraResponse ajustarPuntosManual(AjustePuntosRequest request, Long adminId) {
        if (request.getPuntos() == null || request.getPuntos() == 0) {
            throw new BadRequestException("La cantidad de puntos a ajustar no puede ser cero");
        }

        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id: " + request.getUsuarioId()));

        Usuario admin = adminId != null ? usuarioRepository.findById(adminId).orElse(null) : null;

        PuntosBilletera billetera = puntosBilleteraRepository.findByUsuarioIdConLock(usuario.getId())
                .orElseGet(() -> obtenerOCrearBilletera(usuario));

        int nuevoSaldo = billetera.getSaldoPuntos() + request.getPuntos();
        if (nuevoSaldo < 0) {
            throw new BadRequestException(
                    String.format("El ajuste dejaría el saldo en negativo. Saldo actual: %d, Ajuste: %d", billetera.getSaldoPuntos(), request.getPuntos())
            );
        }

        billetera.setSaldoPuntos(nuevoSaldo);
        if (request.getPuntos() > 0) {
            billetera.setPuntosHistoricos(billetera.getPuntosHistoricos() + request.getPuntos());
        }
        puntosBilleteraRepository.save(billetera);

        PuntosTransaccion tx = new PuntosTransaccion(
                billetera,
                TipoTransaccionPuntos.AJUSTE_ADMIN,
                request.getPuntos(),
                nuevoSaldo,
                null,
                request.getMotivo().trim(),
                admin
        );
        puntosTransaccionRepository.save(tx);

        return obtenerBilleteraUsuario(usuario.getId());
    }

    // =========================================================================
    // NIVELES DE FIDELIDAD (FASE 3)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<NivelClienteDto> listarNiveles() {
        return nivelClienteRepository.findAllByOrderByOrdenAsc().stream()
                .map(NivelClienteDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public NivelClienteDto actualizarNivel(String codigo, ActualizarNivelRequest request) {
        if (codigo == null || codigo.isBlank()) {
            throw new BadRequestException("El código del nivel no puede estar vacío");
        }
        NivelCliente nivel = nivelClienteRepository.findById(codigo.trim().toUpperCase())
                .orElseThrow(() -> new RecursoNoEncontradoException("Nivel de fidelización no encontrado con código: " + codigo));

        if (request.getNombre() != null && !request.getNombre().isBlank()) {
            nivel.setNombre(request.getNombre().trim());
        }
        if (request.getUmbralPenAnual() != null) {
            if (request.getUmbralPenAnual().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("El umbral anual no puede ser negativo");
            }
            if (nivel.getOrden() == 1 || "BRONCE".equalsIgnoreCase(nivel.getCodigo())) {
                if (request.getUmbralPenAnual().compareTo(BigDecimal.ZERO) != 0) {
                    throw new BadRequestException("El nivel base (Bronce) debe tener un umbral de gasto anual estrictamente igual a 0.00");
                }
            }

            // Validar monotonía estricta de umbrales jerárquicos (HIGH-02)
            List<NivelCliente> todos = nivelClienteRepository.findAllByOrderByOrdenAsc();
            for (int i = 0; i < todos.size(); i++) {
                if (todos.get(i).getCodigo().equalsIgnoreCase(nivel.getCodigo())) {
                    if (i > 0 && request.getUmbralPenAnual().compareTo(todos.get(i - 1).getUmbralPenAnual()) <= 0) {
                        throw new BadRequestException(String.format(
                                "El umbral de gasto (S/ %.2f) debe ser estrictamente mayor al del nivel inferior %s (S/ %.2f)",
                                request.getUmbralPenAnual(), todos.get(i - 1).getNombre(), todos.get(i - 1).getUmbralPenAnual()
                        ));
                    }
                    if (i < todos.size() - 1 && request.getUmbralPenAnual().compareTo(todos.get(i + 1).getUmbralPenAnual()) >= 0) {
                        throw new BadRequestException(String.format(
                                "El umbral de gasto (S/ %.2f) debe ser estrictamente menor al del nivel superior %s (S/ %.2f)",
                                request.getUmbralPenAnual(), todos.get(i + 1).getNombre(), todos.get(i + 1).getUmbralPenAnual()
                        ));
                    }
                }
            }
            nivel.setUmbralPenAnual(request.getUmbralPenAnual());
        }
        if (request.getMultiplicador() != null) {
            if (request.getMultiplicador().compareTo(BigDecimal.ONE) < 0) {
                throw new BadRequestException("El multiplicador de puntos no puede ser menor a 1.00");
            }

            // Validar monotonía de multiplicadores de puntos (HIGH-02)
            List<NivelCliente> todos = nivelClienteRepository.findAllByOrderByOrdenAsc();
            for (int i = 0; i < todos.size(); i++) {
                if (todos.get(i).getCodigo().equalsIgnoreCase(nivel.getCodigo())) {
                    if (i > 0 && request.getMultiplicador().compareTo(todos.get(i - 1).getMultiplicador()) < 0) {
                        throw new BadRequestException(String.format(
                                "El multiplicador (%.2fx) no puede ser inferior al del nivel inferior %s (%.2fx)",
                                request.getMultiplicador(), todos.get(i - 1).getNombre(), todos.get(i - 1).getMultiplicador()
                        ));
                    }
                    if (i < todos.size() - 1 && request.getMultiplicador().compareTo(todos.get(i + 1).getMultiplicador()) > 0) {
                        throw new BadRequestException(String.format(
                                "El multiplicador (%.2fx) no puede ser superior al del nivel superior %s (%.2fx)",
                                request.getMultiplicador(), todos.get(i + 1).getNombre(), todos.get(i + 1).getMultiplicador()
                        ));
                    }
                }
            }
            nivel.setMultiplicador(request.getMultiplicador());
        }
        nivel.setBeneficioEnvio(request.isBeneficioEnvio());

        NivelCliente guardado = nivelClienteRepository.save(nivel);
        return NivelClienteDto.fromEntity(guardado);
    }

    @Override
    @Transactional
    public NivelCliente evaluarNivelUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return null;
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id: " + usuarioId));

        PuntosBilletera billetera = puntosBilleteraRepository.findByUsuarioIdConLock(usuarioId)
                .orElseGet(() -> obtenerOCrearBilletera(usuario));

        BigDecimal gastoAnualPen = pedidoRepository.calcularGastoAnualPen(usuarioId, LocalDateTime.now().minusDays(365));
        if (gastoAnualPen == null) {
            gastoAnualPen = BigDecimal.ZERO;
        }

        List<NivelCliente> aplicables = nivelClienteRepository.findByUmbralPenAnualLessThanEqualOrderByOrdenDesc(gastoAnualPen);
        NivelCliente mejorNivel = aplicables.isEmpty()
                ? obtenerNivelPorDefecto()
                : aplicables.get(0);

        NivelCliente actual = billetera.getNivel() != null ? billetera.getNivel() : obtenerNivelPorDefecto();

        // Política de fidelización: si el nuevo nivel es superior, se promueve. No se desciende en tiempo real.
        if (mejorNivel.getOrden() > actual.getOrden()) {
            billetera.setNivel(mejorNivel);
            puntosBilleteraRepository.save(billetera);
            return mejorNivel;
        }

        return actual;
    }

    // =========================================================================
    // FICHA 360° DEL CONSUMIDOR (FASE 4)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public ClienteResumen360Dto obtenerResumen360(Long usuarioId) {
        if (usuarioId == null) {
            throw new BadRequestException("El ID de usuario es obligatorio");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id: " + usuarioId));

        PuntosBilletera billetera = puntosBilleteraRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> new PuntosBilletera(usuario, 0, obtenerNivelPorDefecto()));

        PuntosConfig cfg = obtenerOCrearConfig();

        // Obtener pedidos no cancelados con pago aprobado (LTV real - AC-4.2)
        List<Pedido> pedidosAprobados = pedidoRepository.findPedidosAprobadosPorUsuario(usuarioId);
        int totalPedidos = pedidosAprobados.size();
        BigDecimal ltvPen = BigDecimal.ZERO;
        for (Pedido p : pedidosAprobados) {
            BigDecimal base = p.getTotalBasePen() != null ? p.getTotalBasePen() : ("PEN".equals(p.getMonedaCodigo()) ? p.getTotal() : BigDecimal.ZERO);
            if (base != null) {
                ltvPen = ltvPen.add(base);
            }
        }
        ltvPen = ltvPen.setScale(2, RoundingMode.HALF_UP);

        BigDecimal ticketPromedioPen = totalPedidos > 0
                ? ltvPen.divide(BigDecimal.valueOf(totalPedidos), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        LocalDateTime fechaUltimaCompra = !pedidosAprobados.isEmpty() ? pedidosAprobados.get(0).getFecha() : null;
        Integer diasDesdeUltimaCompra = fechaUltimaCompra != null
                ? Math.max(0, (int) ChronoUnit.DAYS.between(fechaUltimaCompra.toLocalDate(), LocalDate.now()))
                : null;

        NivelCliente nivel = billetera.getNivel() != null ? billetera.getNivel() : obtenerNivelPorDefecto();
        SegmentoCliente segmento = calcularSegmento(totalPedidos, diasDesdeUltimaCompra, nivel.getCodigo(), ltvPen);

        // Cupones usados
        List<CuponUso> cupones = cuponUsoRepository.findByUsuarioIdAndActivoTrueOrderByFechaUsoDesc(usuarioId);
        int totalCupones = cupones.size();
        BigDecimal totalDescontadoCupones = cupones.stream()
                .map(CuponUso::getDescuentoAplicadoPen)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Puntos
        int saldoPuntos = billetera.getSaldoPuntos();
        BigDecimal valorEstimadoPuntos = BigDecimal.valueOf(saldoPuntos)
                .multiply(cfg.getValorPenPorPunto())
                .setScale(2, RoundingMode.HALF_UP);

        List<PuntosTransaccionDto> ultimasTransacciones = billetera.getId() != null
                ? puntosTransaccionRepository.findByBilleteraIdOrderByFechaDesc(billetera.getId()).stream()
                    .limit(10)
                    .map(PuntosTransaccionDto::desdeEntidad)
                    .toList()
                : List.of();

        List<com.tienda.ecommerce.pedido.dto.PedidoResponse> ultimosPedidos = pedidosAprobados.stream()
                .limit(5)
                .map(this::mapearPedidoADto)
                .toList();

        List<ClienteNotaDto> notas = clienteNotaRepository.findByUsuarioIdOrderByFechaDesc(usuarioId).stream()
                .map(ClienteNotaDto::fromEntity)
                .toList();

        // Finding 3: Obtener teléfono real del contacto desde el último pedido aprobado
        String telefonoContacto = !pedidosAprobados.isEmpty() && pedidosAprobados.get(0).getTelefono() != null
                ? pedidosAprobados.get(0).getTelefono()
                : null;

        ClienteResumen360Dto dto = new ClienteResumen360Dto();
        dto.setUsuarioId(usuario.getId());
        dto.setNombre(usuario.getNombreCompleto());
        dto.setEmail(usuario.getEmail());
        dto.setTelefono(telefonoContacto);
        dto.setTipoDocumento(usuario.getTipoDocumento() != null ? usuario.getTipoDocumento().name() : null);
        dto.setNumeroDocumento(usuario.getNumeroDocumento());
        dto.setFechaRegistro(usuario.getFechaRegistro());

        dto.setNivelActual(nivel.getNombre());
        dto.setMultiplicador(nivel.getMultiplicador());
        dto.setBeneficioEnvioGratis(nivel.isBeneficioEnvio());

        dto.setLtvPen(ltvPen);
        dto.setTotalPedidos(totalPedidos);
        dto.setTicketPromedioPen(ticketPromedioPen);
        dto.setDiasDesdeUltimaCompra(diasDesdeUltimaCompra);
        dto.setFechaUltimaCompra(fechaUltimaCompra);
        dto.setSegmento(segmento);

        dto.setSaldoPuntos(saldoPuntos);
        dto.setValorEstimadoPuntosPen(valorEstimadoPuntos);
        dto.setPuntosHistoricos(billetera.getPuntosHistoricos());
        dto.setCuponesUsados(totalCupones);
        dto.setTotalDescontadoCuponesPen(totalDescontadoCupones);

        dto.setUltimosPedidos(ultimosPedidos);
        dto.setUltimasTransacciones(ultimasTransacciones);
        dto.setNotas(notas);

        return dto;
    }

    private record MetricasRfmAgregadas(BigDecimal ltvPen, int totalPedidos, LocalDateTime fechaUltimaCompra) {}

    @Override
    @Transactional(readOnly = true)
    public List<ClienteFila360Dto> listarClientes360(SegmentoCliente filtroSegmento) {
        List<Usuario> usuarios = usuarioRepository.findAll(Sort.by(Sort.Direction.DESC, "fechaRegistro"));
        List<ClienteFila360Dto> resultado = new ArrayList<>();
        if (usuarios.isEmpty()) {
            return resultado;
        }

        // Finding 2: Optimización O(1) de consultas para prevenir N+1
        // 1. Cargar todas las billeteras en un mapa por usuarioId en una sola consulta
        Map<Long, PuntosBilletera> billeterasPorUsuario = puntosBilleteraRepository.findAll().stream()
                .filter(b -> b.getUsuario() != null && b.getUsuario().getId() != null)
                .collect(Collectors.toMap(b -> b.getUsuario().getId(), b -> b, (b1, b2) -> b1));

        // 2. Cargar métricas RFM agregadas por usuario en una sola consulta SQL agrupada
        List<Object[]> metricasRfmRaw = pedidoRepository.calcularMetricasRfmPorUsuario();
        Map<Long, MetricasRfmAgregadas> mapaRfm = new HashMap<>();
        if (metricasRfmRaw != null) {
            for (Object[] fila : metricasRfmRaw) {
                if (fila != null && fila.length >= 4 && fila[0] instanceof Number uIdNum) {
                    Long uid = uIdNum.longValue();
                    BigDecimal ltv = fila[1] instanceof BigDecimal bd ? bd : (fila[1] != null ? new BigDecimal(fila[1].toString()) : BigDecimal.ZERO);
                    int cant = fila[2] instanceof Number cntNum ? cntNum.intValue() : 0;
                    LocalDateTime maxFecha = null;
                    if (fila[3] instanceof LocalDateTime ldt) {
                        maxFecha = ldt;
                    } else if (fila[3] instanceof java.sql.Timestamp ts) {
                        maxFecha = ts.toLocalDateTime();
                    }
                    mapaRfm.put(uid, new MetricasRfmAgregadas(ltv, cant, maxFecha));
                }
            }
        }

        NivelCliente nivelDefecto = obtenerNivelPorDefecto();

        for (Usuario u : usuarios) {
            if (u.getRol() != null && "ASISTENTE_IA".equals(u.getRol().name())) {
                continue;
            }
            PuntosBilletera billetera = billeterasPorUsuario.get(u.getId());
            if (billetera == null) {
                billetera = new PuntosBilletera(u, 0, nivelDefecto);
            }

            MetricasRfmAgregadas rfm = mapaRfm.get(u.getId());
            int totalPedidos = rfm != null ? rfm.totalPedidos() : 0;
            BigDecimal ltvPen = (rfm != null ? rfm.ltvPen() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            LocalDateTime fechaUltimaCompra = rfm != null ? rfm.fechaUltimaCompra() : null;
            Integer diasDesdeUltimaCompra = fechaUltimaCompra != null
                    ? Math.max(0, (int) ChronoUnit.DAYS.between(fechaUltimaCompra.toLocalDate(), LocalDate.now()))
                    : null;

            NivelCliente nivel = billetera.getNivel() != null ? billetera.getNivel() : nivelDefecto;
            SegmentoCliente segmento = calcularSegmento(totalPedidos, diasDesdeUltimaCompra, nivel.getCodigo(), ltvPen);

            if (filtroSegmento == null || segmento == filtroSegmento) {
                resultado.add(new ClienteFila360Dto(
                        u.getId(),
                        u.getNombreCompleto(),
                        u.getEmail(),
                        nivel.getNombre(),
                        segmento,
                        ltvPen,
                        totalPedidos,
                        diasDesdeUltimaCompra,
                        billetera.getSaldoPuntos(),
                        fechaUltimaCompra
                ));
            }
        }
        return resultado;
    }

    @Override
    @Transactional
    public ClienteNotaDto agregarNota(Long usuarioId, Long adminId, CrearClienteNotaRequest request) {
        if (usuarioId == null) {
            throw new BadRequestException("El ID de cliente es obligatorio");
        }
        if (request == null || request.getNota() == null || request.getNota().isBlank()) {
            throw new BadRequestException("El contenido de la nota no puede estar vacío");
        }
        if (request.getNota().trim().length() > 2000) {
            throw new BadRequestException("La nota no puede superar 2000 caracteres");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id: " + usuarioId));

        Usuario admin = adminId != null
                ? usuarioRepository.findById(adminId).orElseThrow(() -> new RecursoNoEncontradoException("Administrador no encontrado con id: " + adminId))
                : null;

        if (admin == null) {
            throw new BadRequestException("Se requiere un administrador autenticado para registrar la nota");
        }

        ClienteNota nota = new ClienteNota(usuario, admin, request.getNota().trim());
        ClienteNota guardada = clienteNotaRepository.save(nota);
        return ClienteNotaDto.fromEntity(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteNotaDto> listarNotas(Long usuarioId) {
        if (usuarioId == null) {
            throw new BadRequestException("El ID de usuario es obligatorio");
        }
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new RecursoNoEncontradoException("Usuario no encontrado con id: " + usuarioId);
        }
        return clienteNotaRepository.findByUsuarioIdOrderByFechaDesc(usuarioId).stream()
                .map(ClienteNotaDto::fromEntity)
                .toList();
    }

    @Override
    public SegmentoCliente calcularSegmento(int totalPedidos, Integer diasDesdeUltimaCompra, String nivelCodigo, BigDecimal ltvPen) {
        if (totalPedidos == 0 || diasDesdeUltimaCompra == null) {
            return SegmentoCliente.PROSPECTO;
        }
        // VIP: Nivel ORO o LTV >= S/ 3000
        if ("ORO".equalsIgnoreCase(nivelCodigo) || (ltvPen != null && ltvPen.compareTo(new BigDecimal("3000.00")) >= 0)) {
            return SegmentoCliente.VIP;
        }
        // ACTIVO: Último pedido hace <= 30 días
        if (diasDesdeUltimaCompra <= 30) {
            return SegmentoCliente.ACTIVO;
        }
        // EN_RIESGO: Último pedido hace 31–90 días y >= 2 pedidos históricos
        if (diasDesdeUltimaCompra <= 90 && totalPedidos >= 2) {
            return SegmentoCliente.EN_RIESGO;
        }
        // INACTIVO: Último pedido hace > 90 días (o 1 pedido hace > 30 días)
        return SegmentoCliente.INACTIVO;
    }

    private com.tienda.ecommerce.pedido.dto.PedidoResponse mapearPedidoADto(Pedido p) {
        List<com.tienda.ecommerce.pedido.dto.DetallePedidoResponse> detallesDto = new ArrayList<>();
        if (p.getDetalles() != null) {
            for (com.tienda.ecommerce.pedido.DetallePedido d : p.getDetalles()) {
                BigDecimal subtotal = d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
                com.tienda.ecommerce.pedido.dto.DetallePedidoResponse detalleDto = new com.tienda.ecommerce.pedido.dto.DetallePedidoResponse(
                        d.getId(),
                        d.getProducto() != null ? d.getProducto().getId() : null,
                        d.getProductoNombre() != null ? d.getProductoNombre() : (d.getProducto() != null ? d.getProducto().getNombre() : "Producto"),
                        d.getCantidad(),
                        d.getPrecioUnitario(),
                        subtotal
                );
                detalleDto.setMonedaCodigo(d.getMonedaCodigo());
                detallesDto.add(detalleDto);
            }
        }

        com.tienda.ecommerce.pago.Pago pago = null;
        try {
            pago = p.getPago();
        } catch (Exception ignored) {
        }

        com.tienda.ecommerce.pedido.dto.PedidoResponse respuesta = new com.tienda.ecommerce.pedido.dto.PedidoResponse(
                p.getId(),
                p.getUsuario() != null ? p.getUsuario().getId() : null,
                p.getUsuario() != null ? p.getUsuario().getNombre() : null,
                p.getUsuario() != null ? p.getUsuario().getEmail() : null,
                p.getEstado(),
                p.getTotal(),
                p.getDireccionEnvio(),
                p.getTelefono(),
                p.getFecha(),
                detallesDto,
                pago != null ? pago.getMetodoPago() : null,
                pago != null ? pago.getEstadoPago() : null,
                pago != null ? pago.getReferenciaComprobante() : null,
                pago != null ? pago.getCodigoTransaccion() : null,
                pago != null ? pago.getFechaPago() : null
        );
        respuesta.setMonedaCodigo(p.getMonedaCodigo());
        respuesta.setProcesamientoPago(pago != null ? pago.getProcesamientoPago() : null);
        respuesta.setMetodoNombre(pago != null ? pago.getMetodoNombre() : null);
        respuesta.setTotalBasePen(p.getTotalBasePen());
        respuesta.setCuponCodigo(p.getCuponCodigo());
        respuesta.setDescuentoPen(p.getDescuentoPen());
        respuesta.setPuntosCanjeados(p.getPuntosCanjeados());
        respuesta.setDescuentoPuntosPen(p.getDescuentoPuntosPen());
        return respuesta;
    }
}
