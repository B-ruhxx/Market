import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { BANNERS_DEFECTO, resolverBanners } from '../../src/main/resources/static/assets/js/home-banners.js';

test('el escaparate inicial tiene cuatro productos con imágenes locales', () => {
    assert.equal(BANNERS_DEFECTO.length, 4);
    BANNERS_DEFECTO.forEach(banner => {
        assert.equal(banner.encuadre, 'producto');
        assert.ok(banner.botonSecundario && banner.linkSecundario);
        assert.ok(existsSync(new URL(`../../src/main/resources/static${banner.imagen}`, import.meta.url)));
    });
});

test('una configuración ausente usa los valores iniciales', () => {
    assert.deepEqual(resolverBanners(null), BANNERS_DEFECTO);
    assert.deepEqual(resolverBanners(undefined), BANNERS_DEFECTO);
});

test('la pareja antigua de fábrica se actualiza sin escribir en la base', () => {
    const codigo = readFileSync(new URL('../../src/main/resources/static/assets/js/home-banners.js', import.meta.url), 'utf8');
    const literal = codigo.match(/const BANNERS_LEGACY = (\[[\s\S]*?\n\]);/)[1];
    const legacy = Function(`return (${literal})`)();
    const antes = JSON.stringify(legacy);
    assert.deepEqual(resolverBanners(legacy), BANNERS_DEFECTO);
    assert.equal(JSON.stringify(legacy), antes);

    legacy[0].titulo = 'Mi campaña personalizada';
    assert.deepEqual(resolverBanners(legacy), legacy);
    assert.equal(resolverBanners(legacy).length, 2);
});

test('no se reponen banners eliminados ni se limitan los personalizados a dos', () => {
    assert.deepEqual(resolverBanners([]), []);
    const banners = Array.from({ length: 6 }, (_, i) => ({ titulo: `Campaña ${i}` }));
    assert.deepEqual(resolverBanners(banners), banners);
});

test('los valores iniciales se entregan como copias independientes', () => {
    const banners = resolverBanners(null);
    banners[0].titulo = 'Edición local';
    assert.notEqual(banners[0].titulo, BANNERS_DEFECTO[0].titulo);
});

test('el seed del servidor y los valores de la tienda coinciden', () => {
    const seed = readFileSync(new URL('../../src/main/java/com/tienda/ecommerce/sitio/SitioSeed.java', import.meta.url), 'utf8');
    const json = seed.match(/new SitioConfig\("home\.banners", """\s*([\s\S]*?)"""\)/)[1];
    assert.deepEqual(JSON.parse(json), BANNERS_DEFECTO);
});
