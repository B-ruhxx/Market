package com.tienda.ecommerce.crm;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PuntosTransaccionRepository extends JpaRepository<PuntosTransaccion, Long> {

    List<PuntosTransaccion> findByBilleteraIdOrderByFechaDesc(Long billeteraId);

    Page<PuntosTransaccion> findByBilleteraIdOrderByFechaDesc(Long billeteraId, Pageable pageable);

    List<PuntosTransaccion> findByPedidoId(Long pedidoId);

    List<PuntosTransaccion> findByPedidoIdOrderByIdAsc(Long pedidoId);

    boolean existsByPedidoIdAndTipo(Long pedidoId, TipoTransaccionPuntos tipo);
}
