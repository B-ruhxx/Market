package com.tienda.ecommerce.crm;

import com.tienda.ecommerce.usuario.Usuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "crm_clientes_notas",
    indexes = {
        @Index(name = "idx_notas_usuario", columnList = "usuario_id, fecha"),
        @Index(name = "idx_notas_admin", columnList = "admin_id")
    }
)
public class ClienteNota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Usuario admin;

    @NotBlank(message = "La nota no puede estar vacía")
    @Size(max = 2000, message = "La nota no puede superar 2000 caracteres")
    @Column(nullable = false, length = 2000)
    private String nota;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public ClienteNota() {
    }

    public ClienteNota(Usuario usuario, Usuario admin, String nota) {
        this.usuario = usuario;
        this.admin = admin;
        this.nota = nota;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Usuario getAdmin() {
        return admin;
    }

    public void setAdmin(Usuario admin) {
        this.admin = admin;
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
