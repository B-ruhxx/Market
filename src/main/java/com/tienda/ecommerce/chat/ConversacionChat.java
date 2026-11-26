package com.tienda.ecommerce.chat;

import com.tienda.ecommerce.usuario.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Check;

@Entity
@Table(
    name = "conversaciones_chat",
    indexes = {
        @Index(name = "idx_conversacion_cliente", columnList = "cliente_id"),
        @Index(name = "idx_conversacion_admin", columnList = "admin_id"),
        @Index(name = "idx_conversacion_estado", columnList = "estado")
    }
)
@Check(constraints = "estado IN ('ACTIVA', 'CERRADA', 'ARCHIVADA') AND modo_atencion IN ('BOT', 'ESPERANDO_HUMANO', 'HUMANO_ACTIVO')")
public class ConversacionChat {

    public static final String MODO_BOT = "BOT";
    public static final String MODO_ESPERANDO_HUMANO = "ESPERANDO_HUMANO";
    public static final String MODO_HUMANO_ACTIVO = "HUMANO_ACTIVO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Usuario admin;

    @Column(nullable = false, length = 30)
    private String estado = "ACTIVA";

    @Column(name = "modo_atencion", nullable = false, length = 25)
    private String modoAtencion = MODO_BOT;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @OneToMany(mappedBy = "conversacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMensaje> mensajes = new ArrayList<>();

    public ConversacionChat() {
    }

    public ConversacionChat(Long id, Usuario cliente, Usuario admin, String estado) {
        this.id = id;
        this.cliente = cliente;
        this.admin = admin;
        this.estado = estado;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getCliente() {
        return cliente;
    }

    public void setCliente(Usuario cliente) {
        this.cliente = cliente;
    }

    public Usuario getAdmin() {
        return admin;
    }

    public void setAdmin(Usuario admin) {
        this.admin = admin;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getModoAtencion() {
        return modoAtencion;
    }

    public void setModoAtencion(String modoAtencion) {
        this.modoAtencion = modoAtencion;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public List<ChatMensaje> getMensajes() {
        return mensajes;
    }

    public void setMensajes(List<ChatMensaje> mensajes) {
        this.mensajes = mensajes;
    }

    public void agregarMensaje(ChatMensaje mensaje) {
        mensajes.add(mensaje);
        mensaje.setConversacion(this);
    }
}
