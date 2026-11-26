package com.tienda.ecommerce.auth;

import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.common.TooManyRequestsException;
import com.tienda.ecommerce.configuracion.AccesoService;
import com.tienda.ecommerce.usuario.Rol;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import com.tienda.ecommerce.usuario.UsuarioServiceImpl;
import com.tienda.ecommerce.usuario.dto.ActualizarUsuarioRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityFixesTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccesoService accesoService;

    @Test
    @DisplayName("Rate limiter bloquea el sexto intento consecutivo")
    void loginAttemptService_bloqueaTrasMultiplesIntentos() {
        LoginAttemptService rateLimiter = new LoginAttemptService();
        String ip = "192.168.1.100";

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> rateLimiter.verificarBloqueo(ip));
            rateLimiter.registrarFallo(ip);
        }

        assertThrows(TooManyRequestsException.class, () -> rateLimiter.verificarBloqueo(ip));
    }

    @Test
    @DisplayName("Mapa local de LoginAttemptService permanece acotado ante ataques spray")
    void loginAttemptService_mapaLocalAcotado() {
        LoginAttemptService rateLimiter = new LoginAttemptService();
        for (int i = 0; i < 10_500; i++) {
            rateLimiter.registrarFallo("spray-" + i + "@test.com");
        }
        assertTrue(rateLimiter.localTrackedSize() <= 10_000);
    }

    @Test
    @DisplayName("Actualizar password exige la contraseña actual")
    void actualizarPassword_exigePasswordActual() {
        UsuarioServiceImpl usuarioService = new UsuarioServiceImpl(usuarioRepository, passwordEncoder, accesoService);

        Usuario usuario = new Usuario(1L, "Juan", "juan@test.com", "hash_actual", Rol.CLIENTE, true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        ActualizarUsuarioRequest requestSinActual = new ActualizarUsuarioRequest("Juan", "nuevaClave123");
        assertThrows(BadRequestException.class, () -> usuarioService.actualizar(1L, requestSinActual));

        ActualizarUsuarioRequest requestConActualInvalida = new ActualizarUsuarioRequest("Juan", "nuevaClave123");
        requestConActualInvalida.setPasswordActual("claveEquivocada");
        when(passwordEncoder.matches("claveEquivocada", "hash_actual")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> usuarioService.actualizar(1L, requestConActualInvalida));
    }

    @Test
    @DisplayName("Actualizar password con clave actual correcta registra fecha de modificación")
    void actualizarPassword_conClaveCorrecta_actualizaTimestamp() {
        UsuarioServiceImpl usuarioService = new UsuarioServiceImpl(usuarioRepository, passwordEncoder, accesoService);

        Usuario usuario = new Usuario(1L, "Juan", "juan@test.com", "hash_actual", Rol.CLIENTE, true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("claveCorrecta", "hash_actual")).thenReturn(true);
        when(passwordEncoder.encode("nuevaClave123")).thenReturn("nuevo_hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarUsuarioRequest request = new ActualizarUsuarioRequest("Juan", "nuevaClave123");
        request.setPasswordActual("claveCorrecta");

        usuarioService.actualizar(1L, request);

        assertEquals("nuevo_hash", usuario.getPasswordHash());
        assertNotNull(usuario.getPasswordModificadoEn());
    }
}
