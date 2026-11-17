package com.tienda.ecommerce.crm;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromocionCuponRepository extends JpaRepository<PromocionCupon, Long> {

    Optional<PromocionCupon> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PromocionCupon c WHERE UPPER(c.codigo) = UPPER(:codigo)")
    Optional<PromocionCupon> findByCodigoConLock(@Param("codigo") String codigo);
}
