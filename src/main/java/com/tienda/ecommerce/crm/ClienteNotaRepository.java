package com.tienda.ecommerce.crm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClienteNotaRepository extends JpaRepository<ClienteNota, Long> {

    @Query("SELECT n FROM ClienteNota n JOIN FETCH n.admin WHERE n.usuario.id = :usuarioId ORDER BY n.fecha DESC")
    List<ClienteNota> findByUsuarioIdOrderByFechaDesc(@Param("usuarioId") Long usuarioId);

    long countByUsuarioId(Long usuarioId);
}
