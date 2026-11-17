package com.tienda.ecommerce.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @Query("select u from Usuario u where lower(u.email) = lower(:email)")
    Optional<Usuario> findByEmail(@Param("email") String email);
    boolean existsByEmail(String email);
    boolean existsByNumeroDocumento(String numeroDocumento);
    List<Usuario> findByRol(Rol rol);
}
