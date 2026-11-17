import { api } from '/assets/js/api.js';
import { formatoMoneda } from '/assets/js/money.js';
import { cart } from '/assets/js/cart.js';
import { renderNavbar } from '/assets/js/ui/navbar.js';
import { renderProductCard } from '/assets/js/ui/productCard.js';
import { precioProducto, galeriaProducto, pintarImagenProducto, resumenProducto } from '/assets/js/product-presentation.js';

renderNavbar();

const productId = new URLSearchParams(window.location.search).get('id') || '';
const container = document.getElementById('product-detail-container');
const breadcrumbTitle = document.getElementById('breadcrumb-title');
let solicitud = 0;
let observadorCompra;

function esc(value) {
    return String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function mostrarError(titulo, descripcion, reintentar = false) {
    container.innerHTML = `<div class="product-state" role="status">
        <h1>${esc(titulo)}</h1><p>${esc(descripcion)}</p>
        ${reintentar ? '<button type="button" id="product-retry" class="store-button">Reintentar</button>' : ''}
        <a href="/productos.html" class="store-text-link">Volver al catálogo</a>
    </div>`;
    document.getElementById('product-retry')?.addEventListener('click', cargarDetalle);
    container.setAttribute('aria-busy', 'false');
}

if (!/^[1-9]\d*$/.test(productId)) {
    mostrarError('Elige tu próximo equipo.', 'Abre un producto desde el catálogo para consultar sus detalles.');
} else {
    cargarDetalle();
}

async function cargarDetalle() {
    const pedido = ++solicitud;
    observadorCompra?.disconnect();
    document.body.classList.remove('product-purchase-visible');
    container.setAttribute('aria-busy', 'true');
    container.innerHTML = '<p class="product-state" role="status">Estamos preparando los detalles de tu equipo…</p>';
    try {
        const res = await api.get(`/api/productos/${encodeURIComponent(productId)}`);
        if (pedido !== solicitud) return;
        if (!res?.exito || !res.datos) throw new Error('Producto no disponible');
        const p = res.datos;
        const precio = precioProducto(p);
        const moneda = valor => formatoMoneda(valor, p.monedaCodigo);
        const tieneStock = p.stock > 0;
        const categoriaUrl = p.categoriaId
            ? `/productos.html?categoriaId=${encodeURIComponent(p.categoriaId)}` : '/productos.html';
        const resumen = String(p.descripcion || '').trim().length > 170 ? resumenProducto(p.descripcion) : '';
        breadcrumbTitle.textContent = p.nombre;
        document.title = `${p.nombre} — ${document.querySelector('.fx-brand > span')?.textContent || 'ELECTRONIX'}`;

        container.innerHTML = `
            <div class="product-overview">
                <section class="product-gallery" aria-label="Imágenes del producto">
                    <div class="product-stage"><img id="elx-main-img" alt="${esc(p.nombre)}" decoding="async" fetchpriority="high"></div>
                    <div id="product-gallery-bottom" class="product-gallery-bottom" hidden>
                        <div id="product-thumbs" class="product-thumbs" role="group" aria-label="Elegir imagen"></div>
                        <p id="product-gallery-status" class="product-gallery-status" aria-live="polite"></p>
                    </div>
                </section>
                <section class="product-summary" aria-labelledby="product-title">
                    <a class="product-category" href="${esc(categoriaUrl)}">${esc(p.categoriaNombre || 'Tecnología')}</a>
                    <h1 id="product-title" class="product-title">${esc(p.nombre)}</h1>
                    ${resumen ? `<p class="product-summary-text">${esc(resumen)}</p>` : ''}
                    <div class="product-pricing">
                        <div class="product-price">
                            <span>${moneda(precio.vigente)}</span>
                            ${precio.oferta ? `<s>${moneda(precio.base)}</s>` : ''}
                        </div>
                        ${precio.oferta ? `<p class="product-discount">${precio.descuento}% de descuento</p>` : ''}
                        ${precio.porcentaje > 0 ? `<p class="product-tax">Impuesto adicional: ${precio.porcentaje}%.<br>Total por unidad con impuesto: <strong>${moneda(precio.total)}</strong>.</p>` : ''}
                    </div>
                    <p class="product-stock${tieneStock ? '' : ' is-unavailable'}">${tieneStock ? `Disponible · ${esc(p.stock)} ${p.stock === 1 ? 'unidad' : 'unidades'}` : 'Agotado por ahora'}</p>
                    <div class="product-purchase">
                        ${tieneStock ? `<div class="product-quantity-row">
                            <label for="cant-input">Cantidad</label>
                            <div class="product-quantity">
                                <button id="btn-minus" type="button" aria-label="Quitar una unidad">−</button>
                                <input id="cant-input" type="number" value="1" min="1" max="${Number(p.stock)}" readonly>
                                <button id="btn-plus" type="button" aria-label="Agregar una unidad">+</button>
                            </div>
                        </div>` : ''}
                        <div class="product-purchase-actions">
                            <button type="button" id="btn-buy-now" class="product-buy" ${tieneStock ? '' : 'disabled'}>Comprar ahora</button>
                            <button type="button" id="btn-add-cart" class="product-add" ${tieneStock ? '' : 'disabled'}>Agregar al carrito</button>
                        </div>
                        <p id="product-feedback" class="product-feedback" role="status" aria-live="polite"></p>
                        <a class="product-advice" href="/informacion.html#chat">${tieneStock ? '¿Necesitas ayuda para elegir? Conversemos.' : 'Consultar disponibilidad con el equipo'}</a>
                    </div>
                </section>
            </div>
            <section class="product-information" aria-labelledby="product-information-title">
                <h2 id="product-information-title">Conoce los detalles.</h2>
                <div>
                    <p class="product-description">${esc(p.descripcion || 'Aún no hay una descripción disponible. Puedes consultar los detalles con nuestro equipo.')}</p>
                    <dl class="product-facts">
                        <div><dt>Categoría</dt><dd>${esc(p.categoriaNombre || 'Tecnología')}</dd></div>
                        <div><dt>Referencia</dt><dd>#${esc(p.id)}</dd></div>
                        <div><dt>Disponibilidad</dt><dd>${tieneStock ? `${esc(p.stock)} ${p.stock === 1 ? 'unidad' : 'unidades'}` : 'Sin stock'}</dd></div>
                        <div><dt>Moneda</dt><dd>${esc(p.monedaCodigo || 'PEN')}</dd></div>
                    </dl>
                </div>
            </section>
            <section class="product-related" aria-labelledby="product-related-title">
                <div class="product-related-head">
                    <h2 id="product-related-title">Sigue explorando.</h2>
                    <a href="${esc(categoriaUrl)}" class="store-text-link">Ver la categoría</a>
                </div>
                <div id="similar-track" class="product-related-grid" aria-busy="true">
                    <p class="product-related-status" role="status">Buscando más opciones…</p>
                </div>
            </section>`;
        iniciarGaleria(p);
        if (tieneStock) iniciarCompra(p);
        // En móvil el acceso flotante no debe tapar cantidad ni botones de compra.
        if ('IntersectionObserver' in window) {
            observadorCompra = new IntersectionObserver(([entrada]) => {
                document.body.classList.toggle('product-purchase-visible', entrada.isIntersecting);
            });
            observadorCompra.observe(container.querySelector('.product-purchase'));
        }
        container.setAttribute('aria-busy', 'false');
        cargarSimilares(p, pedido);
    } catch (err) {
        if (pedido !== solicitud) return;
        mostrarError(err.status === 404 ? 'Este equipo no está disponible.' : 'No pudimos abrir el producto.',
            err.status === 404 ? 'Puedes explorar otras opciones en nuestro catálogo.' : 'Revisa tu conexión y vuelve a intentarlo.',
            err.status !== 404);
    }
}

function iniciarGaleria(producto) {
    const imagenes = galeriaProducto(producto);
    const main = document.getElementById('elx-main-img');
    const thumbs = document.getElementById('product-thumbs');
    const status = document.getElementById('product-gallery-status');
    const botones = [];
    function seleccionar(indice) {
        pintarImagenProducto(main, imagenes[indice], `${producto.nombre} · imagen ${indice + 1}`);
        botones.forEach((boton, i) => boton.setAttribute('aria-pressed', String(i === indice)));
        status.textContent = `${indice + 1} de ${imagenes.length}`;
    }
    if (imagenes.length > 1) {
        document.getElementById('product-gallery-bottom').hidden = false;
        imagenes.forEach((src, indice) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'product-thumb';
            button.setAttribute('aria-label', `Ver imagen ${indice + 1} de ${imagenes.length}`);
            const img = document.createElement('img');
            img.loading = 'lazy';
            pintarImagenProducto(img, src, '');
            img.alt = '';
            button.appendChild(img);
            button.addEventListener('click', () => seleccionar(indice));
            button.addEventListener('keydown', event => {
                if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
                event.preventDefault();
                const siguiente = event.key === 'Home' ? 0 : event.key === 'End' ? imagenes.length - 1
                    : (indice + (event.key === 'ArrowRight' ? 1 : -1) + imagenes.length) % imagenes.length;
                seleccionar(siguiente);
                botones[siguiente].focus({ preventScroll: true });
                thumbs.scrollLeft += botones[siguiente].getBoundingClientRect().left - thumbs.getBoundingClientRect().left;
            });
            botones.push(button);
            thumbs.appendChild(button);
        });
    }
    seleccionar(0);
}

function iniciarCompra(producto) {
    const input = document.getElementById('cant-input');
    const minus = document.getElementById('btn-minus');
    const plus = document.getElementById('btn-plus');
    const buy = document.getElementById('btn-buy-now');
    const add = document.getElementById('btn-add-cart');
    const feedback = document.getElementById('product-feedback');
    let comprando = false;
    function actualizarCantidad() {
        minus.disabled = comprando || Number(input.value) <= 1;
        plus.disabled = comprando || Number(input.value) >= Number(producto.stock);
    }
    minus.addEventListener('click', () => {
        input.value = Math.max(1, Number(input.value) - 1);
        actualizarCantidad();
    });
    plus.addEventListener('click', () => {
        input.value = Math.min(Number(producto.stock), Number(input.value) + 1);
        actualizarCantidad();
    });
    async function comprar(irAlPago) {
        if (comprando) return;
        comprando = true;
        buy.disabled = true;
        add.disabled = true;
        actualizarCantidad();
        const boton = irAlPago ? buy : add;
        boton.setAttribute('aria-busy', 'true');
        boton.textContent = 'Agregando…';
        feedback.textContent = '';
        try {
            const resultado = await cart.agregarItem(producto.id, Number(input.value));
            if (resultado && irAlPago) window.location.href = '/checkout.html';
            else feedback.textContent = resultado ? 'Producto agregado a tu carrito.' : 'Inicia sesión para continuar con tu compra.';
        } catch {
            feedback.textContent = 'No se pudo agregar el producto. Puedes volver a intentarlo.';
        } finally {
            comprando = false;
            buy.disabled = false;
            add.disabled = false;
            buy.textContent = 'Comprar ahora';
            add.textContent = 'Agregar al carrito';
            boton.removeAttribute('aria-busy');
            actualizarCantidad();
        }
    }
    add.addEventListener('click', () => comprar(false));
    buy.addEventListener('click', () => comprar(true));
    actualizarCantidad();
}

async function cargarSimilares(producto, pedido) {
    const track = document.getElementById('similar-track');
    try {
        const query = new URLSearchParams({ size: 5 });
        if (producto.categoriaId) query.set('categoriaId', producto.categoriaId);
        let res = await api.get(`/api/productos?${query}`);
        let lista = res?.exito && Array.isArray(res.datos?.contenido) ? res.datos.contenido : [];
        lista = lista.filter(p => String(p.id) !== String(producto.id)).slice(0, 4);
        if (!lista.length && producto.categoriaId) {
            res = await api.get('/api/productos?size=5');
            lista = (res?.exito && Array.isArray(res.datos?.contenido) ? res.datos.contenido : [])
                .filter(p => String(p.id) !== String(producto.id)).slice(0, 4);
        }
        if (pedido !== solicitud) return;
        track.replaceChildren();
        if (lista.length) {
            lista.forEach(p => track.appendChild(renderProductCard(p, item => cart.agregarItem(item.id, 1))));
        } else {
            track.innerHTML = '<p class="product-related-status">No hay más opciones disponibles por ahora.</p>';
        }
    } catch {
        if (pedido === solicitud) track.innerHTML = '<p class="product-related-status">Puedes encontrar más opciones en el catálogo.</p>';
    } finally {
        if (pedido === solicitud) track.setAttribute('aria-busy', 'false');
    }
}
