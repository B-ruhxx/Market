package com.tienda.ecommerce.crm.dto;

import com.tienda.ecommerce.crm.ClienteNota;
import java.time.LocalDateTime;

public class ClienteNotaDto {

    private Long id;
    private Long usuarioId;
    private Long adminId;
    private String adminNombre;
    private String adminEmail;
    private String nota;
    private LocalDateTime fecha;

    public ClienteNotaDto() {
    }

    public ClienteNotaDto(Long id, Long usuarioId, Long adminId, String adminNombre, String adminEmail, String nota, LocalDateTime fecha) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.adminId = adminId;
        this.adminNombre = adminNombre;
        this.adminEmail = adminEmail;
        this.nota = nota;
        this.fecha = fecha;
    }

    public static ClienteNotaDto fromEntity(ClienteNota entidad) {
        if (entidad == null) {
            return null;
        }
        return new ClienteNotaDto(
                entidad.getId(),
                entidad.getUsuario() != null ? entidad.getUsuario().getId() : null,
                entidad.getAdmin() != null ? entidad.getAdmin().getId() : null,
                entidad.getAdmin() != null ? entidad.getAdmin().getNombreCompleto() : "Admin",
                entidad.getAdmin() != null ? entidad.getAdmin().getEmail() : "",
                entidad.getNota(),
                entidad.getFecha()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public String getAdminNombre() {
        return adminNombre;
    }

    public void setAdminNombre(String adminNombre) {
        this.adminNombre = adminNombre;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
