import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { imagenSoftware } from '../../src/main/resources/static/assets/js/software-artwork.js';
import { galeriaProducto } from '../../src/main/resources/static/assets/js/product-presentation.js';
import { INSPIRACION_DEFECTO, resolverInspiracion } from '../../src/main/resources/static/assets/js/home-stories.js';

const foto = 'https://images.unsplash.com/photo-1550439062-609e1531270e?w=600&auto=format&fit=crop&q=80';
const staticPath = '../../src/main/resources/static';
const info = readFileSync(new URL(`${staticPath}/informacion.html`, import.meta.url), 'utf8');
const seed = readFileSync(new URL('../../src/main/java/com/tienda/ecommerce/sitio/SitioSeed.java', import.meta.url), 'utf8');
const originales = JSON.parse(seed.match(/new SitioConfig\("home\.spotlight", """\s*([\s\S]*?)"""\)/)[1]);

test('las cuatro licencias de novedades utilizan ilustraciones específicas locales', () => {
    const casos = [
        ['Antivirus Eset Nod32 3 equipos Pro', 'eset-nod32'],
        ['AutoCAD LT 1 año Pro', 'autocad-lt'],
        ['Adobe Creative Cloud 1 mes Pro', 'creative-cloud'],
        ['Licencia Windows 11 Pro Pro', 'windows-11-pro']
    ];
    for (const [nombre, archivo] of casos) {
        const ruta = imagenSoftware({ nombre }, foto);
        assert.equal(ruta, `/assets/img/software/${archivo}.svg`);
        assert.ok(existsSync(new URL(`${staticPath}${ruta}`, import.meta.url)));
    }
});

test('la foto propia de un producto prevalece, incluso con el mismo nombre', () => {
    const p = { nombre: 'AutoCAD LT 1 año Pro' };
    for (const fuente of ['/uploads/autocad.webp', 'https://example.com/producto.jpg', `${foto}&crop=custom`]) {
        assert.equal(imagenSoftware(p, fuente), fuente);
    }
    assert.equal(imagenSoftware({ nombre: 'Otro software' }, foto), foto);
});

test('la sustitución es compartida por las galerías y no muta productos ni añade fotos genéricas', () => {
    const p = { nombre: 'Adobe Creative Cloud 1 mes', imagenUrl: foto, imagenes: [foto, '/uploads/detalle.webp'], precio: 100, stock: 5 };
    const copia = structuredClone(p);
    assert.deepEqual(galeriaProducto(p), ['/assets/img/software/creative-cloud.svg', '/uploads/detalle.webp']);
    assert.deepEqual(p, copia);
});

test('los seis destacados exactos de fábrica se presentan con las nuevas historias', () => {
    const copia = structuredClone(originales);
    assert.deepEqual(resolverInspiracion(originales), INSPIRACION_DEFECTO);
    assert.deepEqual(originales, copia);
    assert.equal(INSPIRACION_DEFECTO.length, 3);
});

test('se conservan títulos, fotografías y campos personalizados de Apariencia', () => {
    const personalizados = structuredClone(originales);
    personalizados[0].imagen = '/uploads/mi-coleccion.webp';
    assert.deepEqual(resolverInspiracion(personalizados), personalizados);
    const extra = structuredClone(originales);
    extra[0].imagenAlt = 'Mi descripción';
    assert.deepEqual(resolverInspiracion(extra), extra);
});

test('eliminar todos los destacados no vuelve a crearlos y las copias son independientes', () => {
    assert.deepEqual(resolverInspiracion([]), []);
    const copia = resolverInspiracion(null);
    copia[0].titulo = 'Cambio temporal';
    assert.notEqual(INSPIRACION_DEFECTO[0].titulo, copia[0].titulo);
});

test('las fotografías editoriales nuevas son locales y tienen descripción accesible', () => {
    for (const historia of INSPIRACION_DEFECTO) {
        assert.ok(historia.imagenAlt);
        assert.match(historia.imagen, /^\/assets\/img\/promos\/inspiracion-/);
        assert.ok(existsSync(new URL(`${staticPath}${historia.imagen}`, import.meta.url)));
        assert.match(historia.linkUrl, /^\/productos\.html\?categoria=/);
    }
});

test('el centro de ayuda conserva todos los destinos de los enlaces anteriores', () => {
    const ids = [...info.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);
    assert.equal(ids.length, new Set(ids).size);
    for (const id of ['tiendas', 'garantia', 'despachos', 'chat', 'soporte', 'contacto', 'empresas', 'privacidad', 'terminos', 'devoluciones', 'mapa']) {
        assert.ok(ids.includes(id));
    }
    for (const [, hash] of info.matchAll(/href="#([^"]+)"/g)) assert.ok(ids.includes(hash), hash);
});

test('la ayuda usa estilos locales y no inventa tarifas, entregas gratis ni plazos fijos', () => {
    assert.match(info, /pages\/informacion\.css/);
    assert.match(info, /storefront\.css/);
    assert.match(info, /data-site-footer/);
    assert.ok(!info.includes('cdn.tailwindcss.com'));
    assert.ok(!/envío gratis|entrega en 24|entrega en 48|sin costo|respuesta inmediata garantizada/i.test(info));
    assert.match(info, /Usamos tus datos únicamente/);
});
