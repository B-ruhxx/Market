package com.tienda.ecommerce.auth;

import com.tienda.ecommerce.auth.dto.AuthResponse;
import com.tienda.ecommerce.auth.dto.LoginRequest;
import com.tienda.ecommerce.auth.dto.RegistroRequest;
import com.tienda.ecommerce.common.ApiResponse;
import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.common.CredencialesInvalidasException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(AuthService authService) {
        this(authService, null);
    }

    @Autowired
    public AuthController(AuthService authService, @Autowired(required = false) LoginAttemptService loginAttemptService) {
        this.authService = authService;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/registro")
    public ResponseEntity<ApiResponse<AuthResponse>> registrarse(@Valid @RequestBody RegistroRequest request) {
        AuthResponse response = authService.registro(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.exito("Usuario registrado con éxito", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> iniciarSesion(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : null;
        if (loginAttemptService != null && clientIp != null) {
            loginAttemptService.verificarBloqueoIp(clientIp);
        }
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(ApiResponse.exito("Inicio de sesión exitoso", response));
        } catch (CredencialesInvalidasException | BadRequestException ex) {
            if (loginAttemptService != null && clientIp != null) {
                loginAttemptService.registrarFalloIp(clientIp);
            }
            throw ex;
        }
    }
}
