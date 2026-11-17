import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { HERO_DEFECTO, resolverHero, resolverAccesos, resolverEquipos } from '../../src/main/resources/static/assets/js/home-content.js';

const seed = readFileSync(new URL('../../src/main/java/com/tienda/ecommerce/sitio/SitioSeed.java', import.meta.url), 'utf8');
function delSeed(clave) {
    const escapada = clave.replaceAll('.', '\\.');
    return JSON.parse(seed.match(new RegExp(`new SitioConfig\\("${escapada}", """\\s*([\\s\\S]*?)"""\\)`))[1]);
}

test('el hero inicial presenta un ambiente local con una sola acción', () => {
    assert.equal(HERO_DEFECTO.length, 1);
    assert.equal(HERO_DEFECTO[0].imagenModo, 'ambiente');
    assert.equal(HERO_DEFECTO[0].imagen, '/assets/img/promos/hero-espacio.webp');
    assert.ok(HERO_DEFECTO[0].imagenAlt);
    assert.equal(HERO_DEFECTO[0].linkUrl, '/productos.html');
    assert.ok(!HERO_DEFECTO[0].botonSecundario);
    assert.ok(existsSync(new URL(`../../src/main/resources/static${HERO_DEFECTO[0].imagen}`, import.meta.url)));
});

test('el borrador centrado anterior solo se sustituye si no fue personalizado', () => {
    const anterior = [{
        titulo: 'Tecnología para', subtitulo: 'lo que sigue.',
        descripcion: 'Portátiles, audio y más. Encuentra el equipo que va contigo.',
        imagen: '/assets/img/promos/hero-portatil.webp', imagenModo: 'producto', fondoTipo: 'imagen',
        fondoColor1: '#F5F5F7', fondoColor2: '#F5F5F7', mostrarTexto: true,
        textoBoton: 'Explorar productos', linkUrl: '/productos.html'
    }];
    assert.deepEqual(resolverHero(anterior), HERO_DEFECTO);
    anterior[0].linkUrl = '/productos.html?ofertas=true';
    assert.deepEqual(resolverHero(anterior), anterior);
});

test('las campañas de fábrica se presentan con el hero nuevo sin mutar su configuración', () => {
    const anteriores = delSeed('hero.slides');
    const copia = structuredClone(anteriores);
    assert.deepEqual(resolverHero(anteriores), HERO_DEFECTO);
    assert.deepEqual(anteriores, copia);
});

test('el borrador anterior guardado sin cambios también se actualiza', () => {
    const anterior = [{
        titulo: 'Tecnología para', subtitulo: 'lo que sigue.',
        descripcion: 'Para las ideas que creas, las partidas que juegas y todo lo que viene después. Encuentra el equipo que va contigo.',
        imagen: '/assets/img/promos/portatil.webp', imagenModo: 'producto', fondoTipo: 'imagen',
        fondoColor1: '#14191F', fondoColor2: '#14191F', mostrarTexto: true,
        textoBoton: 'Explorar productos', linkUrl: '/productos.html'
    }];
    assert.deepEqual(resolverHero(anterior), HERO_DEFECTO);
    anterior[0].titulo = 'Mi campaña personalizada';
    assert.deepEqual(resolverHero(anterior), anterior);
});

test('se conservan campañas personalizadas, campos adicionales y listas vacías', () => {
    const personalizados = delSeed('hero.slides');
    personalizados[0].titulo = 'Presentación propia';
    personalizados[0].imagenModo = 'fondo';
    assert.deepEqual(resolverHero(personalizados), personalizados);
    assert.deepEqual(resolverHero([]), []);
    assert.deepEqual(resolverHero(null), HERO_DEFECTO);
});

test('las copias del editor son independientes, también para los diseños por capas', () => {
    const original = [{ nombre: 'Mi diseño', capas: [{ id: 'titulo', texto: 'Diseño guardado' }] }];
    const copia = resolverEquipos(original);
    copia[0].capas[0].texto = 'Edición temporal';
    assert.equal(original[0].capas[0].texto, 'Diseño guardado');
});

test('los accesos y equipos de fábrica siguen siendo editables y no se reponen listas vacías', () => {
    assert.equal(resolverAccesos(delSeed('home.cards'))[0].titulo, 'Ofertas');
    assert.equal(resolverEquipos(delSeed('home.equipos'))[0].nombre, 'Portátiles');
    assert.deepEqual(resolverAccesos([]), []);
    assert.deepEqual(resolverEquipos([]), []);
});
