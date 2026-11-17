package com.tienda.ecommerce.crm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface NivelClienteRepository extends JpaRepository<NivelCliente, String> {

    List<NivelCliente> findAllByOrderByOrdenAsc();

    Optional<NivelCliente> findByCodigo(String codigo);

    List<NivelCliente> findByUmbralPenAnualLessThanEqualOrderByOrdenDesc(BigDecimal gastoAnual);
}
