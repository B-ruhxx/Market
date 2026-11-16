package com.tienda.ecommerce.producto;

import com.tienda.ecommerce.common.BadRequestException;
import com.tienda.ecommerce.common.RecursoNoEncontradoException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventarioService {
    private final ProductoRepository productos;

    public InventarioService(ProductoRepository productos) {
        this.productos = productos;
    }

    @CacheEvict(value = "productos", key = "#id")
    @Transactional
    public void actualizar(Long id, int stock) {
        Producto p = productos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado"));
        if (stock < 0) throw new BadRequestException("Stock no válido");
        p.setStock(stock);
        productos.save(p);
    }
}
