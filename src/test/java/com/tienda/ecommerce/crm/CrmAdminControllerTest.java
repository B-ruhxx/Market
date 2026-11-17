package com.tienda.ecommerce.crm;

import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.crm.dto.CuponResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrmAdminControllerTest {

    @Mock
    private CrmService crmService;

    @Mock
    private com.tienda.ecommerce.usuario.UsuarioRepository usuarioRepository;

    @InjectMocks
    private CrmAdminController controller;

    @Test
    @DisplayName("Debe lanzar BadRequestException si el body es nulo al cambiar estado (SEC-CRM-08)")
    void cambiarEstado_bodyNull_lanzaBadRequestException() {
        assertThrows(BadRequestException.class, () -> controller.cambiarEstadoCupon(1L, null));
    }

    @Test
    @DisplayName("Debe lanzar BadRequestException si el body no contiene 'activo'")
    void cambiarEstado_bodySinActivo_lanzaBadRequestException() {
        assertThrows(BadRequestException.class, () -> controller.cambiarEstadoCupon(1L, Collections.emptyMap()));
    }

    @Test
    @DisplayName("Debe cambiar estado exitosamente cuando el body es válido")
    void cambiarEstado_bodyValido_exitoso() {
        CuponResponse cupon = new CuponResponse();
        cupon.setId(1L);
        cupon.setActivo(true);
        when(crmService.cambiarEstado(1L, true)).thenReturn(cupon);

        var respuesta = controller.cambiarEstadoCupon(1L, Map.of("activo", true));

        assertNotNull(respuesta);
        assertEquals(200, respuesta.getStatusCode().value());
        assertTrue(respuesta.getBody().getDatos().isActivo());
        verify(crmService).cambiarEstado(1L, true);
    }

    @Test
    @DisplayName("Debe listar niveles de fidelización a través del controlador admin")
    void listarNiveles_exitoso() {
        var dto = new com.tienda.ecommerce.crm.dto.NivelClienteDto("BRONCE", "Bronce", java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, false, 1);
        when(crmService.listarNiveles()).thenReturn(java.util.List.of(dto));

        var respuesta = controller.listarNiveles();

        assertNotNull(respuesta);
        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals(1, respuesta.getBody().getDatos().size());
        assertEquals("BRONCE", respuesta.getBody().getDatos().get(0).getCodigo());
    }

    @Test
    @DisplayName("Debe actualizar nivel de fidelización a través del controlador admin")
    void actualizarNivel_exitoso() {
        var req = new com.tienda.ecommerce.crm.dto.ActualizarNivelRequest("Oro VIP", new java.math.BigDecimal("2000.00"), new java.math.BigDecimal("1.60"), true);
        var dto = new com.tienda.ecommerce.crm.dto.NivelClienteDto("ORO", "Oro VIP", new java.math.BigDecimal("2000.00"), new java.math.BigDecimal("1.60"), true, 3);

        when(crmService.actualizarNivel("ORO", req)).thenReturn(dto);

        var respuesta = controller.actualizarNivel("ORO", req);

        assertNotNull(respuesta);
        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals("Oro VIP", respuesta.getBody().getDatos().getNombre());
        verify(crmService).actualizarNivel("ORO", req);
    }

    @Test
    @DisplayName("Debe obtener ficha 360° del cliente")
    void obtenerResumen360_exitoso() {
        var resumen = new com.tienda.ecommerce.crm.dto.ClienteResumen360Dto();
        resumen.setUsuarioId(10L);
        resumen.setEmail("cliente@test.com");
        when(crmService.obtenerResumen360(10L)).thenReturn(resumen);

        var respuesta = controller.obtenerResumen360(10L);

        assertNotNull(respuesta);
        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals(10L, respuesta.getBody().getDatos().getUsuarioId());
        verify(crmService).obtenerResumen360(10L);
    }

    @Test
    @DisplayName("Debe listar clientes 360° con filtro de segmento")
    void listarClientes_exitoso() {
        var fila = new com.tienda.ecommerce.crm.dto.ClienteFila360Dto(
                10L, "Juan", "juan@test.com", "Bronce", SegmentoCliente.ACTIVO,
                new java.math.BigDecimal("200.00"), 2, 5, 50, java.time.LocalDateTime.now()
        );
        when(crmService.listarClientes360(SegmentoCliente.ACTIVO)).thenReturn(java.util.List.of(fila));

        var respuesta = controller.listarClientes(SegmentoCliente.ACTIVO);

        assertNotNull(respuesta);
        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals(1, respuesta.getBody().getDatos().size());
        assertEquals(SegmentoCliente.ACTIVO, respuesta.getBody().getDatos().get(0).getSegmento());
        verify(crmService).listarClientes360(SegmentoCliente.ACTIVO);
    }

    @Test
    @DisplayName("Debe registrar nota de cliente por admin autenticado")
    void agregarNota_exitoso() {
        var req = new com.tienda.ecommerce.crm.dto.CrearClienteNotaRequest("Nota de prueba");
        var admin = new com.tienda.ecommerce.usuario.Usuario();
        admin.setId(99L);
        admin.setEmail("admin@test.com");

        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.getName()).thenReturn("admin@test.com");
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(java.util.Optional.of(admin));

        var notaDto = new com.tienda.ecommerce.crm.dto.ClienteNotaDto(1L, 10L, 99L, "Admin", "admin@test.com", "Nota de prueba", java.time.LocalDateTime.now());
        when(crmService.agregarNota(10L, 99L, req)).thenReturn(notaDto);

        var respuesta = controller.agregarNota(10L, auth, req);

        assertNotNull(respuesta);
        assertEquals(201, respuesta.getStatusCode().value());
        assertEquals("Nota de prueba", respuesta.getBody().getDatos().getNota());
        verify(crmService).agregarNota(10L, 99L, req);
    }

    @Test
    @DisplayName("Debe listar notas de un cliente")
    void listarNotas_exitoso() {
        var notaDto = new com.tienda.ecommerce.crm.dto.ClienteNotaDto(1L, 10L, 99L, "Admin", "admin@test.com", "Nota 1", java.time.LocalDateTime.now());
        when(crmService.listarNotas(10L)).thenReturn(java.util.List.of(notaDto));

        var respuesta = controller.listarNotas(10L);

        assertNotNull(respuesta);
        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals(1, respuesta.getBody().getDatos().size());
        verify(crmService).listarNotas(10L);
    }
}
