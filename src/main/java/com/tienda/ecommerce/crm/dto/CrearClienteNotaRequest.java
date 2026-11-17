package com.tienda.ecommerce.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CrearClienteNotaRequest {

    @NotBlank(message = "El contenido de la nota no puede estar vacío")
    @Size(max = 2000, message = "La nota no puede superar los 2000 caracteres")
    private String nota;

    public CrearClienteNotaRequest() {
    }

    public CrearClienteNotaRequest(String nota) {
        this.nota = nota;
    }

    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota;
    }
}
