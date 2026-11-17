// Una sola tarjeta para catálogo, novedades y relacionados.
import { formatoMoneda } from '../money.js';
import { precioProducto, galeriaProducto, pintarImagenProducto } from '../product-presentation.js';

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

export function renderProductCard(producto, onAddToCart) {
    const card = document.createElement('article');
    card.className = 'elx-product-card store-product-card';

    const precioFormateado = formatoMoneda(producto.precio, producto.monedaCodigo);
    const tieneStock = producto.stock > 0;
    const etiquetaUp = String(producto.etiqueta || '').trim().toUpperCase();
    const precio = precioProducto(producto);

    const ETIQUETAS = {
        NUEVO: ['Nuevo', 'elx-ribbon-nuevo'],
        OFERTA: [precio.oferta ? `-${precio.descuento}%` : 'Oferta', 'elx-ribbon-oferta'],
        TENDENCIA: ['Tendencia', 'elx-ribbon-tendencia']
    };
    const etiqueta = Object.hasOwn(ETIQUETAS, etiquetaUp) ? ETIQUETAS[etiquetaUp] : null;
    const bloquePrecio = precio.oferta
        ? `<div class="elx-product-price"><s class="elx-old-price">${precioFormateado}</s> <span class="elx-offer-price">${formatoMoneda(precio.vigente, producto.monedaCodigo)}</span></div>`
        : `<div class="elx-product-price">${precioFormateado}</div>`;

    card.innerHTML = `
        <div class="elx-product-img-wrap">
            ${etiqueta ? `<span class="elx-ribbon ${etiqueta[1]}">${etiqueta[0]}</span>` : ''}
            <a href="/producto.html?id=${Number(producto.id) || ''}" tabindex="-1" aria-hidden="true">
                <img
                    alt="${escapeHtml(producto.nombre)}"
                    class="elx-product-img"
                    loading="lazy"
                    decoding="async"
                />
            </a>
        </div>

        <div class="elx-product-info">
            <div>
                <div class="elx-product-cat">${escapeHtml(producto.categoriaNombre) || 'Tecnología'}</div>
                <a href="/producto.html?id=${Number(producto.id) || ''}">
                    <h3 class="elx-product-title">${escapeHtml(producto.nombre)}</h3>
                </a>

            </div>
            <div class="store-product-bottom">
                ${bloquePrecio}
                ${tieneStock ? '' : '<p class="store-product-stock">Agotado por ahora</p>'}
            <div class="elx-product-actions">
                <button
                    type="button"
                    class="add-btn elx-btn-add ${tieneStock ? '' : 'elx-btn-deshabilitado'}"
                    ${tieneStock ? '' : 'disabled'}
                >
                    <span>${tieneStock ? 'Agregar' : 'Sin stock'}</span>
                </button>
            </div>
            </div>
        </div>
    `;

    pintarImagenProducto(card.querySelector('img'), galeriaProducto(producto)[0], producto.nombre || 'Producto');
    const addBtn = card.querySelector('.add-btn');
    addBtn.setAttribute('aria-label', tieneStock
        ? `Agregar ${producto.nombre} al carrito` : `Sin stock: ${producto.nombre}`);
    if (addBtn && tieneStock && onAddToCart) {
        addBtn.addEventListener('click', async () => {
            if (addBtn.disabled) return;
            addBtn.disabled = true;
            addBtn.setAttribute('aria-busy', 'true');
            addBtn.textContent = 'Agregando…';
            try { await onAddToCart(producto); }
            catch { /* El carrito informa del error con su propio aviso. */ }
            finally {
                addBtn.disabled = false;
                addBtn.removeAttribute('aria-busy');
                addBtn.textContent = 'Agregar';
            }
        });
    }

    return card;
}
