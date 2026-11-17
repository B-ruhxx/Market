import { auth } from '/assets/js/auth.js';
import { api } from '/assets/js/api.js';
import { cart } from '/assets/js/cart.js';
import { formatoMoneda } from '/assets/js/money.js';
import { iniciarShellCompra } from '/assets/js/ui/purchase-shell.js';
import { escapar as esc } from '/assets/js/purchase-flow.js';
import { galeriaProducto, pintarImagenProducto } from '/assets/js/product-presentation.js';

await iniciarShellCompra();
const container = document.getElementById('cart-content-container');
const feedback = document.getElementById('cart-feedback');
let ocupado = false;
let cargando = false;

function estado(titulo, descripcion, texto, href) {
    container.innerHTML = `<div class="flow-state"><h2>${esc(titulo)}</h2><p>${esc(descripcion)}</p><a class="store-button" href="${esc(href)}">${esc(texto)}</a></div>`;
    container.setAttribute('aria-busy', 'false');
}

async function cargarCarrito() {
    if (cargando) return;
    if (!auth.isAuthenticated()) {
        estado('Tu selección te espera.', 'Inicia sesión para consultar los productos de tu carrito.', 'Iniciar sesión', '/login.html?redirect=%2Fcarrito.html');
        return;
    }
    cargando = true;
    container.setAttribute('aria-busy', 'true');
    feedback.textContent = '';
    try {
        const res = await api.get('/api/carrito');
        if (!res?.exito || !Array.isArray(res.datos?.items)) throw new Error(res?.mensaje || 'No pudimos consultar tu carrito.');
        pintarCarrito(res.datos);
    } catch {
        container.innerHTML = '<div class="flow-state"><h2>No pudimos abrir tu carrito.</h2><p>Revisa tu conexión y vuelve a intentarlo. No se han eliminado tus productos.</p><button type="button" class="store-button" id="cart-retry">Reintentar</button></div>';
        document.getElementById('cart-retry').addEventListener('click', cargarCarrito);
    } finally {
        cargando = false;
        container.setAttribute('aria-busy', 'false');
    }
}

function pintarCarrito(datos) {
    cart.actualizarBadge(datos.cantidadTotal);
    if (!datos.items.length) {
        estado('Dale espacio a tu próximo equipo.', 'Tu carrito está vacío. Explora el catálogo y encuentra lo que va contigo.', 'Explorar el catálogo', '/productos.html');
        return;
    }
    const formato = valor => formatoMoneda(valor, datos.monedaCodigo || 'PEN');
    container.innerHTML = `
        <section aria-label="Productos seleccionados">
            <div class="flow-cart-list">${datos.items.map(item => `
                <article class="flow-cart-row" id="cart-row-${Number(item.id)}">
                    <a href="/producto.html?id=${Number(item.productoId)}" class="flow-cart-image" tabindex="-1" aria-hidden="true"><img alt="${esc(item.productoNombre)}" decoding="async"></a>
                    <div class="flow-cart-copy">
                        <h2><a href="/producto.html?id=${Number(item.productoId)}">${esc(item.productoNombre)}</a></h2>
                        <p>${formato(item.precioUnitario)} por unidad${Number(item.impuestoPorcentaje) > 0 ? ` · Incluye impuesto de ${esc(item.impuestoPorcentaje)}%` : ''}</p>
                        <div class="flow-cart-controls">
                            <div class="flow-quantity" role="group" aria-label="Cantidad de ${esc(item.productoNombre)}">
                                <button type="button" data-action="minus" data-id="${Number(item.id)}" data-cantidad="${Number(item.cantidad)}" aria-label="Quitar una unidad de ${esc(item.productoNombre)}" ${Number(item.cantidad) <= 1 ? 'disabled data-minimo="true"' : ''}>−</button>
                                <output aria-label="Cantidad">${Number(item.cantidad)}</output>
                                <button type="button" data-action="plus" data-id="${Number(item.id)}" data-cantidad="${Number(item.cantidad)}" aria-label="Agregar una unidad de ${esc(item.productoNombre)}">+</button>
                            </div>
                            <button type="button" class="flow-remove" data-action="remove" data-id="${Number(item.id)}" aria-label="Eliminar ${esc(item.productoNombre)} del carrito">Eliminar</button>
                        </div>
                    </div>
                    <div class="flow-cart-price">${formato(item.subtotal)}</div>
                </article>`).join('')}</div>
            <div class="flow-cart-links"><a href="/productos.html" class="store-text-link">Continuar explorando</a><a href="/informacion.html#chat" class="flow-link">Ayuda con mi compra</a></div>
        </section>
        <aside class="flow-summary" aria-label="Resumen de tu carrito">
            <h2>Tu selección.</h2>
            <p class="flow-note">${Number(datos.cantidadTotal)} ${Number(datos.cantidadTotal) === 1 ? 'unidad' : 'unidades'} en tu carrito.</p>
            <dl class="flow-totals">
                <div><dt>Productos sin impuesto</dt><dd>${formato(datos.subtotal ?? datos.total)}</dd></div>
                <div><dt>Impuestos</dt><dd>${formato(datos.impuestoTotal ?? 0)}</dd></div>
                <div class="flow-total"><dt>Total de productos</dt><dd>${formato(datos.total)}</dd></div>
            </dl>
            ${auth.tienePermiso('COMPRAR') ? '<a id="cart-checkout" href="/checkout.html" class="store-button">Continuar con mi compra</a>' : '<p class="flow-note">Tu cuenta no tiene permiso para realizar compras.</p>'}
            <p class="flow-note">Podrás revisar la moneda y el método de pago en el siguiente paso. Consulta las <a href="/informacion.html#despachos" class="flow-link">condiciones de despacho</a>.</p>
        </aside>`;
    datos.items.forEach(item => {
        const img = document.getElementById(`cart-row-${Number(item.id)}`).querySelector('img');
        pintarImagenProducto(img, galeriaProducto({ nombre: item.productoNombre, imagenUrl: item.imagenUrl })[0], item.productoNombre);
    });
}

function bloquear(valor) {
    container.setAttribute('aria-busy', String(valor));
    container.querySelectorAll('button[data-action]').forEach(button => { button.disabled = valor || button.dataset.minimo === 'true'; });
    const continuar = document.getElementById('cart-checkout');
    if (continuar) {
        continuar.setAttribute('aria-disabled', String(valor));
        continuar.tabIndex = valor ? -1 : 0;
    }
}

container.addEventListener('click', async event => {
    if (event.target.closest('#cart-checkout') && ocupado) { event.preventDefault(); return; }
    const button = event.target.closest('button[data-action]');
    if (!button || ocupado) return;
    ocupado = true;
    const id = button.dataset.id;
    const accion = button.dataset.action;
    const cantidad = Number(button.dataset.cantidad);
    bloquear(true);
    feedback.dataset.tone = '';
    feedback.textContent = 'Actualizando tu selección…';
    try {
        const datos = accion === 'remove' ? await cart.removerItem(id)
            : await cart.actualizarCantidad(id, cantidad + (accion === 'plus' ? 1 : -1));
        pintarCarrito(datos);
        feedback.textContent = accion === 'remove' ? 'Producto eliminado de tu carrito.' : 'Cantidad actualizada.';
        let foco = container.querySelector(`button[data-id="${id}"][data-action="${accion}"]`)
            || container.querySelector('button[data-action="plus"]') || container.querySelector('a');
        bloquear(false);
        if (foco?.disabled) foco = container.querySelector(`button[data-id="${id}"][data-action="plus"]`);
        foco?.focus({ preventScroll: true });
    } catch (err) {
        feedback.dataset.tone = 'error';
        feedback.textContent = err.message || 'No se pudo actualizar tu carrito. Vuelve a intentarlo.';
    } finally {
        ocupado = false;
        bloquear(false);
    }
});

await cargarCarrito();
