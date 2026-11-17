// Presentación compartida de productos. Solo datos de la API, sin promesas comerciales fijas.
import { imagenSoftware } from './software-artwork.js';
export const IMAGEN_PRODUCTO_DEFECTO = '/assets/img/producto-sin-imagen.svg';

export function precioProducto(producto) {
    const base = Number(producto.precio);
    const rebajado = Number(producto.precioOferta);
    const oferta = String(producto.etiqueta || '').trim().toUpperCase() === 'OFERTA'
        && Number.isFinite(rebajado) && rebajado > 0 && rebajado < base;
    const vigente = oferta ? rebajado : base;
    const porcentaje = Math.max(0, Number(producto.impuestoPorcentaje) || 0);
    return {
        base, vigente, oferta, porcentaje,
        descuento: oferta ? Math.round((1 - rebajado / base) * 100) : 0,
        impuesto: vigente * porcentaje / 100,
        total: vigente * (1 + porcentaje / 100)
    };
}

export function galeriaProducto(producto) {
    const fuentes = [producto.imagenUrl, ...(Array.isArray(producto.imagenes) ? producto.imagenes : [])];
    const validas = fuentes.filter(src => {
        if (typeof src !== 'string' || !src.trim()) return false;
        try { return ['http:', 'https:'].includes(new URL(src.trim(), 'https://electronix.local').protocol); }
        catch { return false; }
    }).map(src => imagenSoftware(producto, src.trim()));
    return validas.length ? [...new Set(validas)] : [IMAGEN_PRODUCTO_DEFECTO];
}

export function pintarImagenProducto(img, src, alt) {
    img.alt = alt;
    img.onerror = () => {
        img.onerror = null;
        img.src = IMAGEN_PRODUCTO_DEFECTO;
        img.alt = `Imagen no disponible: ${alt}`;
    };
    img.src = src;
}

export function resumenProducto(descripcion, limite = 170) {
    const texto = String(descripcion || '').trim().split(/\n/)[0];
    if (texto.length <= limite) return texto;
    return `${texto.slice(0, limite).replace(/\s+\S*$/, '').trimEnd()}…`;
}
