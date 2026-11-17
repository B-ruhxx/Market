import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
    precioProducto, galeriaProducto, pintarImagenProducto,
    resumenProducto, IMAGEN_PRODUCTO_DEFECTO
} from '../../src/main/resources/static/assets/js/product-presentation.js';

test('el precio normal no inventa descuentos ni impuestos', () => {
    assert.deepEqual(precioProducto({ precio: '100.00' }), {
        base: 100, vigente: 100, oferta: false, descuento: 0, porcentaje: 0, impuesto: 0, total: 100
    });
});

test('la oferta requiere etiqueta y un precio rebajado positivo menor al original', () => {
    assert.equal(precioProducto({ precio: 100, precioOferta: 80, etiqueta: 'OFERTA' }).descuento, 20);
    for (const precioOferta of [null, 0, -5, 100, 150, 'texto']) {
        assert.equal(precioProducto({ precio: 100, precioOferta, etiqueta: 'OFERTA' }).oferta, false);
    }
    assert.equal(precioProducto({ precio: 100, precioOferta: 80, etiqueta: 'NUEVO' }).oferta, false);
});

test('el impuesto adicional se calcula sobre el precio vigente, no sobre el tachado', () => {
    const precio = precioProducto({ precio: 100, precioOferta: 80, etiqueta: 'oferta', impuestoPorcentaje: 18 });
    assert.equal(precio.impuesto, 14.4);
    assert.ok(Math.abs(precio.total - 94.4) < 0.000001);
    assert.equal(precioProducto({ precio: 100, impuestoPorcentaje: -5 }).total, 100);
});

test('la galería conserva imágenes del producto, su orden y elimina duplicados', () => {
    const original = { imagenUrl: '/uploads/uno.webp', imagenes: ['/uploads/uno.webp', 'https://example.com/dos.jpg', '/uploads/tres.webp'] };
    const copia = structuredClone(original);
    assert.deepEqual(galeriaProducto(original), ['/uploads/uno.webp', 'https://example.com/dos.jpg', '/uploads/tres.webp']);
    assert.deepEqual(original, copia);
});

test('se descartan fuentes vacías y protocolos no admitidos', () => {
    assert.deepEqual(galeriaProducto({ imagenUrl: 'javascript:alert(1)', imagenes: ['', null, 'data:text/html,test', '/uploads/real.webp'] }), ['/uploads/real.webp']);
    assert.deepEqual(galeriaProducto({}), [IMAGEN_PRODUCTO_DEFECTO]);
    assert.deepEqual(galeriaProducto({ imagenes: 'no es una lista' }), [IMAGEN_PRODUCTO_DEFECTO]);
});

test('una imagen fallida usa el marcador neutro local una sola vez', () => {
    const img = {};
    pintarImagenProducto(img, '/uploads/foto.webp', 'Mi equipo');
    assert.equal(img.src, '/uploads/foto.webp');
    img.onerror();
    assert.equal(img.src, IMAGEN_PRODUCTO_DEFECTO);
    assert.equal(img.onerror, null);
    assert.match(img.alt, /Imagen no disponible/);
    pintarImagenProducto(img, '/uploads/otra.webp', 'Otra imagen');
    assert.equal(typeof img.onerror, 'function');
    assert.equal(img.src, '/uploads/otra.webp');
});

test('el resumen es breve, conserva texto y no simula características', () => {
    assert.equal(resumenProducto(null), '');
    assert.equal(resumenProducto('Descripción propia\nMás detalles'), 'Descripción propia');
    const texto = 'Un equipo para trabajar con documentos y proyectos. '.repeat(10);
    assert.ok(resumenProducto(texto).length <= 171);
    assert.ok(resumenProducto(texto).endsWith('…'));
});

test('la tarjeta compartida tiene una sola acción de compra y no incluye Ver detalle', () => {
    const js = readFileSync(new URL('../../src/main/resources/static/assets/js/ui/productCard.js', import.meta.url), 'utf8');
    assert.ok(!js.includes('elx-btn-detail'));
    assert.equal((js.match(/<button\b/g) || []).length, 1);
    assert.match(js, /href="\/producto\.html\?id=/);
    assert.ok(!js.includes('images.unsplash.com'));
});

test('la ficha no declara cuotas, envío gratuito o garantía como condiciones fijas', () => {
    const js = readFileSync(new URL('../../src/main/resources/static/assets/js/pages/producto.js', import.meta.url), 'utf8');
    for (const promesa of ['6 cuotas', 'sin interés', 'Envío gratis', '12 meses de fábrica', 'Pago 100% seguro']) {
        assert.ok(!js.includes(promesa));
    }
    assert.match(js, /if \(resultado && irAlPago\)/);
});

test('las tres vistas cargan las mismas tarjetas y la ficha ya no depende de Tailwind remoto', () => {
    for (const pagina of ['index', 'productos', 'producto']) {
        const html = readFileSync(new URL(`../../src/main/resources/static/${pagina}.html`, import.meta.url), 'utf8');
        assert.match(html, /\/assets\/css\/ui\/product-tiles\.css/);
        assert.ok(!html.includes('cdn.tailwindcss.com'));
    }
});
