package com.tienda.ecommerce.crm;

import com.tienda.ecommerce.common.ApiResponse;
import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.crm.dto.*;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/crm")
@PreAuthorize("hasAuthority('CRM_GESTIONAR')")
public class CrmAdminController {

    private final CrmService crmService;
    private final UsuarioRepository usuarioRepository;

    public CrmAdminController(CrmService crmService, UsuarioRepository usuarioRepository) {
        this.crmService = crmService;
        this.usuarioRepository = usuarioRepository;
    }

    // --- Cupones ---

    @GetMapping("/cupones")
    public ResponseEntity<ApiResponse<List<CuponResponse>>> listarCupones() {
        List<CuponResponse> cupones = crmService.listarCupones();
        return ResponseEntity.ok(ApiResponse.exito("Listado de cupones obtenido", cupones));
    }

    @PostMapping("/cupones")
    public ResponseEntity<ApiResponse<CuponResponse>> crearCupon(@Valid @RequestBody CrearCuponRequest request) {
        CuponResponse creado = crmService.crearCupon(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.exito("Cupón creado exitosamente", creado));
    }

    @PatchMapping("/cupones/{id}/estado")
    public ResponseEntity<ApiResponse<CuponResponse>> cambiarEstadoCupon(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Boolean> body
    ) {
        if (body == null || !body.containsKey("activo") || body.get("activo") == null) {
            throw new BadRequestException("El campo 'activo' es obligatorio");
        }
        boolean activo = body.get("activo");
        CuponResponse actualizado = crmService.cambiarEstado(id, activo);
        return ResponseEntity.ok(ApiResponse.exito(
                activo ? "Cupón activado exitosamente" : "Cupón pausado exitosamente",
                actualizado
        ));
    }

    @DeleteMapping("/cupones/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarCupon(@PathVariable Long id) {
        crmService.eliminarCupon(id);
        return ResponseEntity.ok(ApiResponse.exito("Cupón eliminado o desactivado correctamente", null));
    }

    @GetMapping("/cupones/{id}/usos")
    public ResponseEntity<ApiResponse<List<CuponUsoResponse>>> listarUsosCupon(@PathVariable Long id) {
        List<CuponUsoResponse> usos = crmService.listarUsos(id);
        return ResponseEntity.ok(ApiResponse.exito("Historial de usos obtenido", usos));
    }

    // --- Puntos y Fidelización ---

    @GetMapping("/puntos/config")
    public ResponseEntity<ApiResponse<PuntosConfigDto>> obtenerConfigPuntos() {
        PuntosConfigDto config = crmService.obtenerConfigPuntos();
        return ResponseEntity.ok(ApiResponse.exito("Configuración de puntos obtenida", config));
    }

    @PutMapping("/puntos/config")
    public ResponseEntity<ApiResponse<PuntosConfigDto>> actualizarConfigPuntos(@Valid @RequestBody PuntosConfigDto dto) {
        PuntosConfigDto actualizada = crmService.actualizarConfigPuntos(dto);
        return ResponseEntity.ok(ApiResponse.exito("Configuración de puntos actualizada exitosamente", actualizada));
    }

    @PostMapping("/puntos/ajuste")
    public ResponseEntity<ApiResponse<BilleteraResponse>> ajustarPuntos(
            Authentication authentication,
            @Valid @RequestBody AjustePuntosRequest request
    ) {
        Long adminId = null;
        if (authentication != null && authentication.getName() != null) {
            adminId = usuarioRepository.findByEmail(authentication.getName())
                    .map(Usuario::getId)
                    .orElse(null);
        }
        BilleteraResponse actualizada = crmService.ajustarPuntosManual(request, adminId);
        return ResponseEntity.ok(ApiResponse.exito("Ajuste de puntos realizado exitosamente", actualizada));
    }

    @GetMapping("/puntos/usuarios/{usuarioId}")
    public ResponseEntity<ApiResponse<BilleteraResponse>> verBilleteraUsuario(@PathVariable Long usuarioId) {
        BilleteraResponse billetera = crmService.obtenerBilleteraUsuario(usuarioId);
        return ResponseEntity.ok(ApiResponse.exito("Billetera del usuario obtenida", billetera));
    }

    // --- Niveles de Fidelidad (Tiers VIP - Fase 3) ---

    @GetMapping("/niveles")
    public ResponseEntity<ApiResponse<List<NivelClienteDto>>> listarNiveles() {
        List<NivelClienteDto> niveles = crmService.listarNiveles();
        return ResponseEntity.ok(ApiResponse.exito("Niveles de fidelización obtenidos", niveles));
    }

    @PutMapping("/niveles/{codigo}")
    public ResponseEntity<ApiResponse<NivelClienteDto>> actualizarNivel(
            @PathVariable String codigo,
            @Valid @RequestBody ActualizarNivelRequest request
    ) {
        NivelClienteDto actualizado = crmService.actualizarNivel(codigo, request);
        return ResponseEntity.ok(ApiResponse.exito("Nivel actualizado exitosamente", actualizado));
    }

    @PostMapping("/niveles/evaluar/{usuarioId}")
    public ResponseEntity<ApiResponse<NivelClienteDto>> evaluarNivelUsuario(@PathVariable Long usuarioId) {
        NivelCliente nivel = crmService.evaluarNivelUsuario(usuarioId);
        return ResponseEntity.ok(ApiResponse.exito("Nivel del usuario evaluado exitosamente", NivelClienteDto.fromEntity(nivel)));
    }

    // --- Ficha 360° del Consumidor & CRM (Fase 4) ---

    @GetMapping("/clientes/{id}/360")
    public ResponseEntity<ApiResponse<ClienteResumen360Dto>> obtenerResumen360(@PathVariable Long id) {
        ClienteResumen360Dto resumen = crmService.obtenerResumen360(id);
        return ResponseEntity.ok(ApiResponse.exito("Ficha 360° del cliente obtenida", resumen));
    }

    @GetMapping("/clientes")
    public ResponseEntity<ApiResponse<List<ClienteFila360Dto>>> listarClientes(
            @RequestParam(required = false) SegmentoCliente segmento
    ) {
        List<ClienteFila360Dto> clientes = crmService.listarClientes360(segmento);
        return ResponseEntity.ok(ApiResponse.exito("Listado de clientes obtenido", clientes));
    }

    @PostMapping("/clientes/{id}/notas")
    public ResponseEntity<ApiResponse<ClienteNotaDto>> agregarNota(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody CrearClienteNotaRequest request
    ) {
        Long adminId = null;
        if (authentication != null && authentication.getName() != null) {
            adminId = usuarioRepository.findByEmail(authentication.getName())
                    .map(Usuario::getId)
                    .orElse(null);
        }
        ClienteNotaDto creada = crmService.agregarNota(id, adminId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.exito("Nota registrada exitosamente", creada));
    }

    @GetMapping("/clientes/{id}/notas")
    public ResponseEntity<ApiResponse<List<ClienteNotaDto>>> listarNotas(@PathVariable Long id) {
        List<ClienteNotaDto> notas = crmService.listarNotas(id);
        return ResponseEntity.ok(ApiResponse.exito("Notas del cliente obtenidas", notas));
    }
}
