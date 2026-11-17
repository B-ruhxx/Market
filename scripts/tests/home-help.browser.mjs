// PLAYWRIGHT_MODULE=/ruta/a/playwright node scripts/tests/home-help.browser.mjs
// API pública de solo lectura; el chat se simula para no crear conversaciones.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.STOREFRONT_URL || 'http://localhost:8090';
const browser = await chromium.launch();
const errores = [];
const ready = page => page.waitForFunction(() => document.querySelector('#home-products')?.getAttribute('aria-busy') === 'false' && document.querySelectorAll('.store-story-image').length > 0);
try {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, ignoreHTTPSErrors: true });
    ctx.on('page', page => page.on('pageerror', error => errores.push(error.message)));
    await ctx.route('**/api/**', route => {
        assert.equal(route.request().method(), 'GET', 'La prueba no debe escribir datos');
        if (new URL(route.request().url()).pathname.startsWith('/api/chat/')) return route.fulfill({ json: { exito: false } });
        return route.continue();
    });
    const p = await ctx.newPage();
    await p.goto(base + '/', { waitUntil: 'domcontentloaded' });
    await ready(p);
    const imagenes = await p.locator('#home-products img').evaluateAll(imgs => imgs.map(i => i.getAttribute('src')));
    assert.deepEqual(imagenes, ['eset-nod32', 'autocad-lt', 'creative-cloud', 'windows-11-pro'].map(nombre => `/assets/img/software/${nombre}.svg`));
    assert.equal(await p.locator('.elx-spot-card').count(), 3);
    const producto = await p.locator('#home-products .elx-product-img-wrap a').first().getAttribute('href');
    for (const ancho of [320, 390, 640, 760, 768, 1024, 1440, 1920]) {
        await p.setViewportSize({ width: ancho, height: 1000 });
        assert.equal(await p.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `Inicio ${ancho}px`);
        assert.equal(await p.locator('#main-navbar').evaluate(el => el.offsetHeight), ancho <= 760 ? 48 : 44);
        assert.equal(await p.locator('.store-hero-actions a').count(), 1);
    }
    await p.setViewportSize({ width: 1440, height: 1000 });
    assert.equal(await p.locator('.store-story-controls').isVisible(), false);
    assert.equal(await p.locator('.fx-footer').evaluate(el => getComputedStyle(el).backgroundColor), 'rgb(239, 238, 234)');
    await p.goto(base + producto, { waitUntil: 'domcontentloaded' });
    await p.waitForFunction(() => document.querySelector('#product-detail-container')?.getAttribute('aria-busy') === 'false');
    assert.equal(await p.locator('#elx-main-img').getAttribute('src'), imagenes[0]);
    await p.goto(base + '/productos.html?categoriaId=15', { waitUntil: 'domcontentloaded' });
    await p.waitForFunction(() => document.querySelector('#products-container')?.getAttribute('aria-busy') === 'false');
    assert.equal(await p.locator('.elx-product-img').first().getAttribute('src'), imagenes[0]);
    console.log('PASS imágenes coherentes en inicio/catálogo/ficha, inspiración y footer claro; 8 anchos');

    await p.goto(base + '/informacion.html#despachos', { waitUntil: 'domcontentloaded' });
    await p.evaluate(() => document.fonts.ready);
    await p.waitForFunction(() => document.querySelector('#despachos').getBoundingClientRect().top >= 44 && document.querySelector('#despachos').getBoundingClientRect().top < 130);
    const ids = await p.locator('.help-menu a').evaluateAll(enlaces => enlaces.map(a => a.hash.slice(1)));
    for (const id of ids) {
        await p.locator(`.help-menu a[href="#${id}"]`).click();
        assert.equal(new URL(p.url()).hash, '#' + id);
        await p.waitForFunction(tema => document.querySelector(`.help-menu a[href="#${tema}"]`)?.getAttribute('aria-current') === 'location', id);
        assert.equal(await p.locator(`.help-menu a[href="#${id}"]`).getAttribute('aria-current'), 'location');
        if (['privacidad', 'terminos'].includes(id)) assert.equal(await p.locator('#' + id).evaluate(el => el.open), true);
    }
    const footerHashes = await p.locator('.fx-footer a[href*="informacion.html#"]').evaluateAll(enlaces => enlaces.map(a => new URL(a.href).hash.slice(1)));
    for (const id of footerHashes) assert.equal(await p.locator(`[id="${id}"]`).count(), 1);
    await p.locator('.help-menu a[href="#despachos"]').click();
    const pregunta = p.locator('.help-questions details').first();
    await pregunta.locator('summary').focus();
    await p.keyboard.press('Enter');
    assert.equal(await pregunta.evaluate(el => el.open), true);
    await p.keyboard.press('Enter');
    assert.equal(await pregunta.evaluate(el => el.open), false);
    await p.locator('.help-menu a[href="#chat"]').click();
    assert.equal(await p.locator('#help-open-chat').getAttribute('href'), '/login.html?redirect=%2Finformacion.html%23chat');
    await p.evaluate(() => localStorage.setItem('ecommerce_token', 'mock-only'));
    await p.locator('#help-open-chat').click();
    assert.equal(await p.locator('#floating-chat-container').evaluate(el => el.classList.contains('elx-chat-open')), true);
    await p.locator('#chat-close-btn').click();
    await p.evaluate(() => localStorage.removeItem('ecommerce_token'));
    console.log('PASS destinos del footer, temas de ayuda, preguntas con teclado y apertura del chat simulado');

    for (const ancho of [320, 390, 640, 760, 768, 1024, 1440, 1920]) {
        await p.setViewportSize({ width: ancho, height: 1000 });
        assert.equal(await p.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `Ayuda ${ancho}px`);
    }
    const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, ignoreHTTPSErrors: true });
    mobile.on('pageerror', error => errores.push(error.message));
    await mobile.goto(base + '/', { waitUntil: 'domcontentloaded' });
    await ready(mobile);
    await mobile.locator('.elx-spotlight').scrollIntoViewIfNeeded();
    const antes = await mobile.locator('#spot-track').evaluate(el => el.scrollLeft);
    await mobile.locator('#spot-next').tap();
    await mobile.waitForFunction(inicio => document.querySelector('#spot-track').scrollLeft > inicio + 10, antes);
    const grupo = mobile.locator('.fx-dir-group').first();
    await grupo.locator('summary').tap();
    assert.equal(await grupo.evaluate(el => el.open), true);
    await grupo.locator('summary').tap();
    assert.equal(await grupo.evaluate(el => el.open), false);
    await mobile.goto(base + '/informacion.html#despachos', { waitUntil: 'domcontentloaded' });
    await mobile.locator('.help-questions summary').first().tap();
    assert.equal(await mobile.locator('.help-questions details').first().evaluate(el => el.open), true);
    await mobile.locator('#nav-cat-trigger').tap();
    assert.equal(await mobile.locator('#nav-mega').getAttribute('aria-hidden'), 'false');
    await mobile.locator('#nav-mega a[href="#garantia"]').tap();
    assert.equal(await mobile.locator('#nav-mega').getAttribute('aria-hidden'), 'true');
    await mobile.locator('#nav-search-toggle').tap();
    await mobile.waitForFunction(() => document.activeElement.id === 'nav-search-inline-input');
    await mobile.locator('#nav-search-inline-close').tap();
    console.log('PASS ayuda responsive, carrusel táctil, acordeones, navegación y búsqueda móvil');

    for (const historias of [[], [{ kicker: 'Propio', titulo: 'Mi colección', descripcion: 'Texto propio', boton: 'Ver', linkUrl: '/productos.html', imagen: '/assets/img/promos/inspiracion-audio.webp' }]]) {
        const propia = await ctx.newPage();
        await propia.route('**/api/sitio/publico', route => route.fulfill({ json: { exito: true, datos: { 'home.spotlight': JSON.stringify(historias) } } }));
        await propia.goto(base + '/', { waitUntil: 'domcontentloaded' });
        await propia.waitForFunction(() => document.querySelector('.fx-brand > span')?.textContent === 'ELECTRONIX' && document.querySelector('#home-products')?.getAttribute('aria-busy') === 'false');
        assert.equal(await propia.locator('.elx-spot-card').count(), historias.length);
        if (!historias.length) assert.equal(await propia.locator('.elx-spotlight').isVisible(), false);
        else assert.equal(await propia.locator('.elx-spot-bottom h3').textContent(), 'Mi colección');
        await propia.close();
    }
    console.log('PASS Apariencia conserva historias personalizadas y secciones eliminadas, sin escrituras');
    assert.deepEqual(errores, []);
    console.log('PASS sin excepciones JavaScript');
} finally {
    await browser.close();
}
