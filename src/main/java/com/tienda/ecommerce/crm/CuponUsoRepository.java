package com.tienda.ecommerce.crm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CuponUsoRepository extends JpaRepository<CuponUso, Long> {

    long countByCuponIdAndUsuarioIdAndActivoTrue(Long cuponId, Long usuarioId);

    List<CuponUso> findByCuponIdOrderByFechaUsoDesc(Long cuponId);

    Optional<CuponUso> findByPedidoId(Long pedidoId);

    boolean existsByCuponId(Long cuponId);

    List<CuponUso> findByUsuarioIdAndActivoTrueOrderByFechaUsoDesc(Long usuarioId);

    long countByUsuarioIdAndActivoTrue(Long usuarioId);
}
