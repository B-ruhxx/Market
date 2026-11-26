package com.tienda.ecommerce.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMensajeRepository extends JpaRepository<ChatMensaje, Long> {

    @Query("SELECT m FROM ChatMensaje m JOIN FETCH m.remitente WHERE m.conversacion.id = :conversacionId ORDER BY m.fechaEnvio ASC, m.id ASC")
    List<ChatMensaje> findByConversacionIdOrderByFechaEnvioAsc(@Param("conversacionId") Long conversacionId);

    @Query("SELECT m FROM ChatMensaje m JOIN FETCH m.remitente WHERE m.conversacion.id = :conversacionId ORDER BY m.fechaEnvio DESC, m.id DESC")
    List<ChatMensaje> findRecientesPorConversacion(@Param("conversacionId") Long conversacionId, Pageable pageable);

    @Query("SELECT m FROM ChatMensaje m WHERE m.conversacion.id = :conversacionId ORDER BY m.fechaEnvio DESC, m.id DESC LIMIT 1")
    Optional<ChatMensaje> findUltimoMensaje(@Param("conversacionId") Long conversacionId);

    @Query("SELECT m FROM ChatMensaje m WHERE m.id IN (" +
           "SELECT MAX(m2.id) FROM ChatMensaje m2 WHERE m2.conversacion.id IN :conversacionIds GROUP BY m2.conversacion.id)")
    List<ChatMensaje> findUltimosMensajesPorConversaciones(@Param("conversacionIds") List<Long> conversacionIds);
}
