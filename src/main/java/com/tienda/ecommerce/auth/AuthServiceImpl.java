package com.tienda.ecommerce.auth;

import com.tienda.ecommerce.auth.dto.AuthResponse;
import com.tienda.ecommerce.auth.dto.LoginRequest;
import com.tienda.ecommerce.auth.dto.RegistroRequest;
import com.tienda.ecommerce.carrito.CarritoService;
import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.common.CredencialesInvalidasException;
import com.tienda.ecommerce.usuario.Rol;
import com.tienda.ecommerce.usuario.TipoDocumento;
import com.tienda.ecommerce.usuario.Usuario;
import com.tienda.ecommerce.usuario.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final String DUMMY_BCRYPT_HASH =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("dummy-timing-guard-2026");

    private final UsuarioRepository usuarioRepository;
    private final CarritoService carritoService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.tienda.ecommerce.configuracion.AccesoService acceso;
    private final LoginAttemptService loginAttemptService;
    private final com.tienda.ecommerce.crm.CrmService crmService;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthServiceImpl(
            UsuarioRepository usuarioRepository,
            CarritoService carritoService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            com.tienda.ecommerce.configuracion.AccesoService acceso,
            LoginAttemptService loginAttemptService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.tienda.ecommerce.crm.CrmService crmService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.carritoService = carritoService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.acceso = acceso;
        this.loginAttemptService = loginAttemptService != null ? loginAttemptService : new LoginAttemptService();
        this.crmService = crmService;
    }

    public AuthServiceImpl(
            UsuarioRepository usuarioRepository,
            CarritoService carritoService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            com.tienda.ecommerce.configuracion.AccesoService acceso
    ) {
        this(usuarioRepository, carritoService, passwordEncoder, jwtService, acceso, new LoginAttemptService(), null);
    }

    @Override
    @Transactional
    public AuthResponse registro(RegistroRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (usuarioRepository.existsByEmail(email)) {
            throw new BadRequestException("Ya existe una cuenta registrada con el email: " + email);
        }

        String nombres = valor(request.getNombres());
        String paterno = valor(request.getApellidoPaterno());
        String materno = valor(request.getApellidoMaterno());
        if (nombres == null || paterno == null) {
            String legado = valor(request.getNombre());
            if (legado == null) {
                throw new BadRequestException("El nombre es obligatorio");
            }
            String[] partes = legado.split("\\s+");
            if (nombres == null) {
                nombres = partes[0];
            }
            if (paterno == null) {
                paterno = partes.length > 1 ? partes[1] : null;
            }
            if (materno == null && partes.length > 2) {
                materno = String.join(" ", java.util.Arrays.copyOfRange(partes, 2, partes.length));
            }
            if (paterno == null) {
                throw new BadRequestException("Indica nombres y apellido paterno");
            }
        }

        if (email.equalsIgnoreCase(com.tienda.ecommerce.chat.ChatServiceImpl.BOT_EMAIL)) {
            throw new BadRequestException("Ese correo está reservado para el sistema");
        }
        TipoDocumento tipo = request.getTipoDocumento() != null ? request.getTipoDocumento() : TipoDocumento.DNI;
        String numero = valor(request.getNumeroDocumento());
        validarDocumento(tipo, numero);
        if ("00000000".equals(numero) || "SYSBOT001".equalsIgnoreCase(numero)) {
            throw new BadRequestException("Ese número de documento está reservado");
        }
        if (usuarioRepository.existsByNumeroDocumento(numero)) {
            throw new BadRequestException("Ya existe una cuenta registrada con ese documento");
        }

        Usuario usuario = new Usuario(null, nombres, paterno, materno, tipo, numero,
                email, passwordEncoder.encode(request.getPassword()), Rol.CLIENTE, true);

        Usuario guardado = usuarioRepository.save(usuario);

        // Inicializar carrito para el nuevo usuario
        carritoService.obtenerOCrearCarritoEntity(guardado.getId());

        // Inicializar billetera con bono de bienvenida CRM
        if (crmService != null) {
            try {
                crmService.inicializarBilleteraBienvenida(guardado);
            } catch (Exception e) {
                log.error("Fallo al inicializar billetera de bienvenida para el usuario #{}: {}", guardado.getId(), e.getMessage(), e);
            }
        }

        String token = jwtService.generarToken(guardado);
        AuthResponse respuesta = new AuthResponse(token, guardado.getId(), guardado.getNombre(), guardado.getEmail(), guardado.getRol());
        respuesta.setPermisos(acceso.permisos(guardado));
        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Serializa verificacion + BCrypt + conteo de fallos por cuenta para evitar carreras de concurrencia
        synchronized (loginAttemptService.lockParaClave(email)) {
            loginAttemptService.verificarBloqueo(email);

            Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
            if (usuario == null) {
                passwordEncoder.matches(request.getPassword(), DUMMY_BCRYPT_HASH);
                loginAttemptService.registrarFallo(email);
                throw new CredencialesInvalidasException("Credenciales incorrectas: email o contraseña no válidos");
            }

            if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
                loginAttemptService.registrarFallo(email);
                throw new CredencialesInvalidasException("Credenciales incorrectas: email o contraseña no válidos");
            }

            if (!usuario.isActivo() || (usuario.getRol() != null && "ASISTENTE_IA".equals(usuario.getRol().name()))) {
                throw new BadRequestException("Tu cuenta se encuentra inactiva. Contacta al administrador.");
            }
            if (!acceso.rolActivo(usuario)) throw new BadRequestException("El rol de tu cuenta se encuentra inactivo");

            // Login exitoso: reiniciar contador de fallos
            loginAttemptService.registrarExito(email);

            String token = jwtService.generarToken(usuario);
            AuthResponse respuesta = new AuthResponse(token, usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol());
            respuesta.setPermisos(acceso.permisos(usuario));
            return respuesta;
        }
    }

    private static String valor(String s) {
        return s != null && !s.trim().isEmpty() ? s.trim() : null;
    }

    private static void validarDocumento(TipoDocumento tipo, String numero) {
        if (numero == null) {
            throw new BadRequestException("El número de documento es obligatorio");
        }
        boolean valido = tipo == TipoDocumento.CE
                ? numero.matches("^[A-Za-z0-9]{9}$")
                : numero.matches("^[0-9]{8}$");
        if (!valido) {
            throw new BadRequestException(tipo == TipoDocumento.CE
                    ? "CE inválido: debe tener 9 caracteres alfanuméricos"
                    : "DNI inválido: debe tener 8 dígitos");
        }
    }
}
