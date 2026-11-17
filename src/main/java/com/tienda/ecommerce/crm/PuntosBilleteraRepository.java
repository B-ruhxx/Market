package com.tienda.ecommerce.crm;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PuntosBilleteraRepository extends JpaRepository<PuntosBilletera, Long> {

    Optional<PuntosBilletera> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioId(Long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PuntosBilletera b WHERE b.usuario.id = :usuarioId")
    Optional<PuntosBilletera> findByUsuarioIdConLock(@Param("usuarioId") Long usuarioId);
}
