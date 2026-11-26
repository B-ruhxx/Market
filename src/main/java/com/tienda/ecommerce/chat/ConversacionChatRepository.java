package com.tienda.ecommerce.chat;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversacionChatRepository extends JpaRepository<ConversacionChat, Long> {

    @Query("SELECT c FROM ConversacionChat c JOIN FETCH c.cliente LEFT JOIN FETCH c.admin WHERE c.cliente.id = :clienteId AND c.estado = 'ACTIVA'")
    Optional<ConversacionChat> findActivaByClienteId(@Param("clienteId") Long clienteId);

    @Query("SELECT c FROM ConversacionChat c JOIN FETCH c.cliente LEFT JOIN FETCH c.admin WHERE c.id = :id")
    Optional<ConversacionChat> findByIdConUsuarios(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ConversacionChat c JOIN FETCH c.cliente LEFT JOIN FETCH c.admin WHERE c.id = :id")
    Optional<ConversacionChat> findByIdConLock(@Param("id") Long id);

    @Query("SELECT c FROM ConversacionChat c JOIN FETCH c.cliente LEFT JOIN FETCH c.admin ORDER BY c.fechaCreacion DESC")
    List<ConversacionChat> findAllConUsuarios();

    @Query("SELECT c FROM ConversacionChat c JOIN FETCH c.cliente LEFT JOIN FETCH c.admin ORDER BY c.fechaCreacion DESC")
    List<ConversacionChat> findAllConUsuarios(Pageable pageable);
}
