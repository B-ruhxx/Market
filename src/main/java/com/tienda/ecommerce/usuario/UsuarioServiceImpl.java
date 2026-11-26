package com.tienda.ecommerce.usuario;

import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.common.RecursoNoEncontradoException;
import com.tienda.ecommerce.usuario.dto.ActualizarUsuarioRequest;
import com.tienda.ecommerce.usuario.dto.CrearUsuarioRequest;
import com.tienda.ecommerce.usuario.dto.EditarUsuarioRequest;
import com.tienda.ecommerce.usuario.dto.UsuarioResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.tienda.ecommerce.configuracion.AccesoService acceso;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, com.tienda.ecommerce.configuracion.AccesoService acceso) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.acceso = acceso;
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id: " + id));
        return mapearADto(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con email: " + email));
        return mapearADto(usuario);
    }

    @Override
    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request) {
        if (request.getRol() != null && "ASISTENTE_IA".equals(request.getRol().name())) {
            throw new BadRequestException("El rol ASISTENTE_IA está reservado para el sistema");
        }
        acceso.validarRol(request.getRol().name());
        String email = request.getEmail().trim().toLowerCase();
        if (com.tienda.ecommerce.chat.ChatServiceImpl.BOT_EMAIL.equalsIgnoreCase(email)) {
            throw new BadRequestException("Ese correo está reservado para el sistema");
        }
        if (usuarioRepository.existsByEmail(email)) {
            throw new BadRequestException("Ya existe un usuario con ese email");
        }
        String doc = request.getNumeroDocumento().trim();
        if ("SYSBOT001".equalsIgnoreCase(doc) || "00000000".equals(doc)) {
            throw new BadRequestException("Ese número de documento está reservado para el sistema");
        }
        if (usuarioRepository.existsByNumeroDocumento(doc)) {
            throw new BadRequestException("Ya existe un usuario con ese documento");
        }
        Usuario usuario = new Usuario(
                null,
                request.getNombres().trim(),
                request.getApellidoPaterno().trim(),
                request.getApellidoMaterno() != null ? request.getApellidoMaterno().trim() : null,
                request.getTipoDocumento(),
                doc,
                email,
                passwordEncoder.encode(request.getPassword().trim()),
                request.getRol(),
                true);
        Usuario guardado = usuarioRepository.save(usuario);
        return mapearADto(guardado);
    }

    @Override
    @Transactional
    public UsuarioResponse actualizar(Long id, ActualizarUsuarioRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id: " + id));

        if (request.getNombres() != null && !request.getNombres().trim().isEmpty()) {
            usuario.setNombres(request.getNombres().trim());
        }
        if (request.getApellidoPaterno() != null && !request.getApellidoPaterno().trim().isEmpty()) {
            usuario.setApellidoPaterno(request.getApellidoPaterno().trim());
        }
        if (request.getApellidoMaterno() != null) {
            String materno = request.getApellidoMaterno().trim();
            usuario.setApellidoMaterno(materno.isEmpty() ? null : materno);
        }
        if (request.getNombres() == null && request.getApellidoPaterno() == null
                && request.getApellidoMaterno() == null) {
            usuario.setNombre(request.getNombre().trim());
        }
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            if (request.getPasswordActual() == null || request.getPasswordActual().trim().isEmpty()) {
                throw new BadRequestException("Para cambiar tu contraseña debes ingresar tu contraseña actual");
            }
            if (!passwordEncoder.matches(request.getPasswordActual().trim(), usuario.getPasswordHash())) {
                throw new BadRequestException("La contraseña actual no es correcta");
            }
            usuario.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
            usuario.setPasswordModificadoEn(java.time.LocalDateTime.now());
        }

        Usuario guardado = usuarioRepository.save(usuario);
        return mapearADto(guardado);
    }

    @Override
    @Transactional
    public UsuarioResponse editar(Long id, EditarUsuarioRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id: " + id));
        if (com.tienda.ecommerce.chat.ChatServiceImpl.BOT_EMAIL.equalsIgnoreCase(usuario.getEmail())
                || (usuario.getRol() != null && "ASISTENTE_IA".equals(usuario.getRol().name()))) {
            throw new BadRequestException("La cuenta de asistente del sistema no es editable");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (com.tienda.ecommerce.chat.ChatServiceImpl.BOT_EMAIL.equalsIgnoreCase(email)) {
            throw new BadRequestException("Ese correo está reservado para el sistema");
        }
        if (!email.equalsIgnoreCase(usuario.getEmail()) && usuarioRepository.existsByEmail(email)) {
            throw new BadRequestException("Ya existe un usuario con ese email");
        }
        String doc = request.getNumeroDocumento().trim();
        if (!doc.equals(usuario.getNumeroDocumento()) && usuarioRepository.existsByNumeroDocumento(doc)) {
            throw new BadRequestException("Ya existe un usuario con ese documento");
        }

        usuario.setNombres(request.getNombres().trim());
        usuario.setApellidoPaterno(request.getApellidoPaterno().trim());
        String materno = request.getApellidoMaterno() != null ? request.getApellidoMaterno().trim() : null;
        usuario.setApellidoMaterno(materno != null && !materno.isEmpty() ? materno : null);
        usuario.setTipoDocumento(request.getTipoDocumento());
        usuario.setNumeroDocumento(doc);
        usuario.setEmail(email);
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
            usuario.setPasswordModificadoEn(java.time.LocalDateTime.now());
        }

        Usuario guardado = usuarioRepository.save(usuario);
        return mapearADto(guardado);
    }

    @Override
    @Transactional
    public UsuarioResponse cambiarRol(Long id, Rol nuevoRol) {
        if (nuevoRol != null && "ASISTENTE_IA".equals(nuevoRol.name())) {
            throw new BadRequestException("El rol ASISTENTE_IA está reservado para el sistema");
        }
        acceso.validarRol(nuevoRol.name());
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id: " + id));
        if (com.tienda.ecommerce.chat.ChatServiceImpl.BOT_EMAIL.equalsIgnoreCase(usuario.getEmail())) {
            throw new BadRequestException("No se puede cambiar el rol de la cuenta del asistente");
        }

        usuario.setRol(nuevoRol);
        Usuario guardado = usuarioRepository.save(usuario);
        return mapearADto(guardado);
    }

    @Override
    @Transactional
    public UsuarioResponse cambiarEstado(Long id, boolean activo) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id: " + id));
        if (com.tienda.ecommerce.chat.ChatServiceImpl.BOT_EMAIL.equalsIgnoreCase(usuario.getEmail())) {
            throw new BadRequestException("No se puede cambiar el estado de la cuenta del asistente");
        }

        usuario.setActivo(activo);
        Usuario guardado = usuarioRepository.save(usuario);
        return mapearADto(guardado);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id: " + id));
        if (com.tienda.ecommerce.chat.ChatServiceImpl.BOT_EMAIL.equalsIgnoreCase(usuario.getEmail())) {
            throw new BadRequestException("No se puede eliminar la cuenta del asistente");
        }
        try {
            usuarioRepository.delete(usuario);
            usuarioRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new BadRequestException("No se puede eliminar: el usuario tiene pedidos o movimientos. Desactívalo en su lugar.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == null || !"ASISTENTE_IA".equals(u.getRol().name()))
                .map(this::mapearADto)
                .toList();
    }

    private UsuarioResponse mapearADto(Usuario u) {
        UsuarioResponse respuesta = new UsuarioResponse(
                u.getId(),
                u.getNombre(),
                u.getNombres(),
                u.getApellidoPaterno(),
                u.getApellidoMaterno(),
                u.getTipoDocumento(),
                u.getNumeroDocumento(),
                u.getEmail(),
                u.getRol(),
                u.isActivo(),
                u.getFechaRegistro()
        );
        respuesta.setPermisos(acceso.permisos(u));
        return respuesta;
    }
}
