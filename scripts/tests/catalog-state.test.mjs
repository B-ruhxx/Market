import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
    ORDEN_DEFECTO, PRECIO_SIN_LIMITE, leerEstadoCatalogo,
    consultaCatalogo, urlCatalogo, resolverCategoria
} from '../../src/main/resources/static/assets/js/catalog-state.js';

const base = 'https://electronix.example/productos.html';
const categorias = [
    { id: 1, nombre: 'Laptops y Computadoras' }, { id: 3, nombre: 'Audio y Sonido' },
    { id: 5, nombre: 'Monitores' }, { id: 13, nombre: 'Accesorios Móviles' }
];

test('el catálogo sin filtros mantiene paginación y orden reales, sin límite de precio', () => {
    const estado = leerEstadoCatalogo('');
    assert.equal(estado.orden, ORDEN_DEFECTO);
    assert.equal(estado.precioMax, PRECIO_SIN_LIMITE);
    assert.equal(estado.pagina, 0);
    assert.equal(consultaCatalogo(estado), '/api/productos?page=0&size=12&sortBy=fechaCreacion&direction=desc');
});

test('los enlaces de ofertas y novedades de la portada siguen funcionando', () => {
    const ofertas = leerEstadoCatalogo('?ofertas=true');
    assert.equal(ofertas.ofertas, true);
    assert.equal(ofertas.orden, 'precio:asc');
    assert.match(consultaCatalogo(ofertas), /etiqueta=OFERTA/);
    assert.equal(leerEstadoCatalogo('?orden=recientes').orden, ORDEN_DEFECTO);
    assert.equal(leerEstadoCatalogo('?ofertas=false').ofertas, false);
    assert.equal(leerEstadoCatalogo('?ofertas=false').orden, ORDEN_DEFECTO);
});

test('los filtros se codifican sin inyectar parámetros desde un nombre', () => {
    const estado = { ...leerEstadoCatalogo(''), nombre: 'Audio & nombre=otro', categoriaId: '3', precioMax: 600, orden: 'precio:desc', pagina: 2 };
    const params = new URL(consultaCatalogo(estado), base).searchParams;
    assert.equal(params.get('nombre'), 'Audio & nombre=otro');
    assert.equal(params.getAll('nombre').length, 1);
    assert.equal(params.get('categoriaId'), '3');
    assert.equal(params.get('precioMax'), '600');
    assert.equal(params.get('page'), '2');
    assert.equal(params.get('sortBy'), 'precio');
    assert.equal(params.get('direction'), 'desc');
});

test('cero es un precio máximo válido y no se descarta como filtro vacío', () => {
    const estado = leerEstadoCatalogo('?precioMax=0');
    assert.equal(estado.precioMax, 0);
    assert.match(consultaCatalogo(estado), /precioMax=0/);
    assert.equal(urlCatalogo(estado, base).searchParams.get('precioMax'), '0');
});

test('los valores no válidos del enlace vuelven a opciones seguras', () => {
    for (const orden of ['inexistente', '__proto__', 'constructor', 'toString']) {
        assert.equal(leerEstadoCatalogo(`?orden=${orden}`).orden, ORDEN_DEFECTO);
    }
    for (const precio of ['-100', 'NaN', 'Infinity', '10001', '']) {
        assert.equal(leerEstadoCatalogo(`?precioMax=${precio}`).precioMax, PRECIO_SIN_LIMITE);
    }
    for (const pagina of ['-1', '1.5', 'NaN', 'Infinity']) {
        assert.equal(leerEstadoCatalogo(`?pagina=${pagina}`).pagina, 0);
    }
});

test('los alias anteriores se resuelven a categorías existentes, con acentos', () => {
    for (const [nombre, id] of [['Portátiles', 1], ['Laptops', 1], ['Audio', 3], ['Televisores', 5], ['Accesorios y PC', 13]]) {
        assert.equal(resolverCategoria(categorias, nombre)?.id, id);
    }
    assert.equal(resolverCategoria(categorias, 'Audio y Sonido')?.id, 3);
    assert.equal(resolverCategoria(categorias, ''), null);
    assert.equal(resolverCategoria(categorias, 'Categoría ausente'), null);
});

test('la URL conserva todos los filtros y la página al recargar', () => {
    const estado = leerEstadoCatalogo('?q=Teclado&categoriaId=4&precioMax=800&ofertas=true&orden=nombre&pagina=3');
    const url = urlCatalogo(estado, base);
    assert.deepEqual(leerEstadoCatalogo(url.search), estado);
});

test('un orden explícito de recientes dentro de ofertas no cambia al recargar', () => {
    const estado = leerEstadoCatalogo('?ofertas=true&orden=recientes');
    assert.equal(estado.orden, ORDEN_DEFECTO);
    assert.equal(leerEstadoCatalogo(urlCatalogo(estado, base).search).orden, ORDEN_DEFECTO);
});

test('limpiar filtros también quita ofertas y alias, sin borrar parámetros ajenos', () => {
    const url = urlCatalogo(leerEstadoCatalogo(''), `${base}?ofertas=true&categoria=Laptops&q=Viejo&precioMax=400&pagina=2&utm_source=demo`);
    assert.equal(url.search, '?utm_source=demo');
});

test('la URL canónica reemplaza el alias por categoriaId', () => {
    const estado = leerEstadoCatalogo('?categoria=Audio');
    estado.categoriaId = String(resolverCategoria(categorias, estado.categoria).id);
    const url = urlCatalogo(estado, `${base}?categoria=Audio`);
    assert.equal(url.searchParams.get('categoriaId'), '3');
    assert.equal(url.searchParams.has('categoria'), false);
});

test('el catálogo no depende de Tailwind remoto ni duplica controles accesibles', () => {
    const html = readFileSync(new URL('../../src/main/resources/static/productos.html', import.meta.url), 'utf8');
    assert.ok(!html.includes('cdn.tailwindcss.com'));
    const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
    assert.equal(ids.length, new Set(ids).size);
    assert.match(html, /role="dialog" aria-modal="true"/);
    assert.match(html, /id="filter-drawer"[^>]*inert/);
    assert.match(html, /id="results-count"[^>]*aria-live="polite"/);
});
