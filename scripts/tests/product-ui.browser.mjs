// Prueba opcional: PLAYWRIGHT_MODULE=/ruta/a/playwright node scripts/tests/product-ui.browser.mjs
// Solo lecturas reales. Carrito, usuario y checkout se simulan; no escribe en la base.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.STOREFRONT_URL || 'http://localhost:8090';
const browser = await chromium.launch({ headless: true });
const errores = [];
const ready = (page, selector) => page.waitForFunction(sel =>
    document.querySelector(sel)?.getAttribute('aria-busy') === 'false', selector);
const producto = {
    id: 1, nombre: 'Equipo de prueba <especial>', categoriaId: 1, categoriaNombre: 'Laptops y Computadoras',
    descripcion: 'Descripción de prueba, sin condiciones comerciales inventadas.',
    precio: 100, precioOferta: 80, etiqueta: 'OFERTA', impuestoPorcentaje: 18, monedaCodigo: 'PEN', stock: 2,
    imagenUrl: '/assets/img/promos/portatil.webp',
    imagenes: ['/assets/img/promos/portatil.webp', '/assets/img/promos/audio.webp', '/assets/img/foto-de-prueba-ausente.png']
};
const listado = productos => ({ exito: true, datos: { contenido: productos, totalElementos: productos.length, totalPaginas: 1 } });

try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, ignoreHTTPSErrors: true });
    page.on('pageerror', error => errores.push(error.message));
    for (const [ruta, selector] of [['/', '#home-products'], ['/productos.html?categoriaId=1', '#products-container'], ['/producto.html?id=1', '#product-detail-container']]) {
        await page.goto(base + ruta, { waitUntil: 'domcontentloaded' });
        await ready(page, selector);
        if (ruta.startsWith('/producto.html')) await ready(page, '#similar-track');
        assert.ok(await page.locator('.store-product-card').count() > 0);
        assert.equal(await page.locator('.elx-btn-detail').count(), 0);
        for (const width of [320, 390, 640, 760, 768, 1024, 1440, 1920]) {
            await page.setViewportSize({ width, height: 1000 });
            const estado = await page.evaluate(() => ({
                overflow: document.documentElement.scrollWidth > innerWidth,
                header: document.querySelector('#main-navbar').offsetHeight,
                tiles: [...document.querySelectorAll('.store-product-card')].every(card => {
                    const css = getComputedStyle(card);
                    return css.borderTopWidth === '0px' && css.borderRadius === '0px'
                        && css.backgroundColor === 'rgb(240, 239, 236)' && card.querySelectorAll('button').length === 1;
                })
            }));
            assert.equal(estado.overflow, false, `${ruta} ${width}px`);
            assert.equal(estado.header, width <= 760 ? 48 : 44);
            assert.equal(estado.tiles, true, `${ruta}: tarjetas planas y una acción`);
        }
        console.log(`PASS ${ruta}: tarjetas compartidas, sin Ver detalle, 8 anchos y navbar fino`);
    }
    await page.goto(base + '/productos.html', { waitUntil: 'domcontentloaded' });
    await ready(page, '#products-container');
    await page.locator('#btn-open-filters').click();
    await page.locator('#filter-categoria').selectOption('1');
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => document.querySelector('.elx-product-cat')?.textContent === 'Laptops y Computadoras');
    await page.locator('.elx-product-title').first().click();
    await page.waitForURL('**/producto.html?id=*');
    await ready(page, '#product-detail-container');
    assert.equal(await page.locator('.product-title').count(), 1);
    console.log('PASS filtros del catálogo y apertura de ficha desde el nombre');

    const mock = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    mock.on('pageerror', error => errores.push(error.message));
    let detalle = { ...producto }, fallarDetalle = false, fallarCarrito = false;
    let llamadas = 0, liberarCarrito, entradaCarrito;
    let esperaCarrito = new Promise(resolve => { liberarCarrito = resolve; });
    const inicioCarrito = new Promise(resolve => { entradaCarrito = resolve; });
    await mock.route('**/api/**', async route => {
        const request = route.request(), url = new URL(request.url());
        if (request.method() !== 'GET') {
            assert.equal(url.pathname, '/api/carrito/items', 'No permitir escrituras inesperadas');
            llamadas++;
            assert.deepEqual(request.postDataJSON(), { productoId: 1, cantidad: 2 });
            entradaCarrito();
            if (fallarCarrito) return route.fulfill({ status: 400, json: { exito: false, mensaje: 'Error de prueba' } });
            await esperaCarrito;
            return route.fulfill({ json: { exito: true, datos: { cantidadTotal: 2, items: [], total: 160 } } });
        }
        if (url.pathname === '/api/productos/1') return route.fulfill({
            status: fallarDetalle ? 503 : 200,
            json: fallarDetalle ? { exito: false } : { exito: true, datos: detalle }
        });
        if (url.pathname === '/api/productos') return route.fulfill({ json: listado([
            { ...producto, id: 2, nombre: 'Otra opción', stock: 0 }, { ...producto, id: 3, nombre: 'Disponible' }
        ]) });
        return route.continue();
    });
    await mock.goto(base + '/producto.html?id=1', { waitUntil: 'domcontentloaded' });
    await ready(mock, '#product-detail-container');
    await ready(mock, '#similar-track');
    assert.equal(await mock.locator('.product-title').textContent(), producto.nombre);
    assert.equal(await mock.locator('.product-title especial').count(), 0);
    assert.match(await mock.locator('.product-tax').textContent(), /94\.40/);
    assert.equal(await mock.locator('.product-discount').textContent(), '20% de descuento');
    assert.equal(await mock.locator('.store-product-card').first().locator('.add-btn').isDisabled(), true);
    const thumbs = mock.locator('.product-thumb');
    assert.equal(await thumbs.count(), 3);
    await thumbs.nth(1).tap();
    assert.match(await mock.locator('#elx-main-img').getAttribute('src'), /audio\.webp/);
    await thumbs.nth(1).press('End');
    await mock.waitForFunction(() => document.querySelector('#elx-main-img')?.src.endsWith('/producto-sin-imagen.svg'));
    await thumbs.nth(2).press('Home');
    assert.equal(await thumbs.first().getAttribute('aria-pressed'), 'true');
    assert.equal(await mock.locator('#btn-minus').isDisabled(), true);
    await mock.locator('#btn-plus').tap();
    assert.equal(await mock.locator('#cant-input').inputValue(), '2');
    assert.equal(await mock.locator('#btn-plus').isDisabled(), true);
    await mock.locator('#btn-buy-now').scrollIntoViewIfNeeded();
    await mock.waitForFunction(() => document.body.classList.contains('product-purchase-visible'));
    assert.equal(await mock.locator('#chat-toggle-btn').isVisible(), false);
    console.log('PASS galería táctil/teclado, imagen fallida, oferta/impuesto y límites de cantidad');

    await mock.evaluate(() => localStorage.setItem('ecommerce_token', 'mock-only'));
    await mock.locator('#btn-add-cart').tap();
    await inicioCarrito;
    assert.equal(await mock.locator('#btn-add-cart').isDisabled(), true);
    assert.equal(await mock.locator('#btn-buy-now').isDisabled(), true);
    await mock.locator('#btn-buy-now').evaluate(button => button.click());
    assert.equal(llamadas, 1);
    liberarCarrito();
    await mock.waitForFunction(() => !document.querySelector('#btn-add-cart').disabled);
    assert.equal(await mock.locator('#cart-badge-top').textContent(), '2');
    fallarCarrito = true;
    await mock.locator('#btn-add-cart').tap();
    await mock.getByText('No se pudo agregar el producto. Puedes volver a intentarlo.', { exact: true }).waitFor();
    assert.equal(await mock.locator('#btn-add-cart').isDisabled(), false);
    fallarCarrito = false;
    await mock.route('**/checkout.html', route => route.fulfill({ contentType: 'text/html', body: '<h1>Checkout simulado</h1>' }));
    await mock.locator('#btn-buy-now').tap();
    await mock.waitForURL('**/checkout.html');
    console.log('PASS carrito simulado, bloqueo doble clic, recuperación de error y Comprar ahora');

    await mock.evaluate(() => localStorage.removeItem('ecommerce_token'));
    detalle = { ...producto, stock: 0 };
    await mock.goto(base + '/producto.html?id=1', { waitUntil: 'domcontentloaded' });
    await ready(mock, '#product-detail-container');
    assert.equal(await mock.locator('#btn-buy-now').isDisabled(), true);
    assert.equal(await mock.locator('#btn-add-cart').isDisabled(), true);
    assert.equal(await mock.locator('#cant-input').count(), 0);
    fallarDetalle = true;
    await mock.reload({ waitUntil: 'domcontentloaded' });
    await mock.getByRole('button', { name: 'Reintentar', exact: true }).waitFor();
    fallarDetalle = false;
    await mock.getByRole('button', { name: 'Reintentar', exact: true }).tap();
    await mock.locator('.product-title').waitFor();
    detalle = { ...producto, stock: 2 };
    await mock.reload({ waitUntil: 'domcontentloaded' });
    await ready(mock, '#product-detail-container');
    await mock.locator('#btn-buy-now').tap();
    await mock.waitForURL('**/login.html?redirect=*');
    assert.equal(new URL(mock.url()).pathname, '/login.html');
    assert.equal(new URL(mock.url()).searchParams.get('redirect'), '/producto.html?id=1');
    console.log('PASS agotados, error/reintento y compra anónima dirige a login, no a checkout');
    assert.deepEqual(errores, []);
    console.log('PASS sin excepciones JavaScript');
} finally {
    await browser.close();
}
