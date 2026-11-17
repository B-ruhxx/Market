import { api } from '/assets/js/api.js';
import { cart } from '/assets/js/cart.js';
import { renderNavbar } from '/assets/js/ui/navbar.js';
import { renderProductCard } from '/assets/js/ui/productCard.js';
import {
    PRECIO_SIN_LIMITE, ORDEN_DEFECTO, leerEstadoCatalogo,
    consultaCatalogo, urlCatalogo, resolverCategoria
} from '/assets/js/catalog-state.js';

renderNavbar();

const $ = id => document.getElementById(id);
const productsContainer = $('products-container');
const searchInput = $('filter-search');
const categoriaSelect = $('filter-categoria');
const priceInput = $('filter-price');
const orderSelect = $('filter-order');
const offersInput = $('filter-offers');
const pagination = $('pagination-container');
const prevBtn = $('prev-page');
const nextBtn = $('next-page');
const drawer = $('filter-drawer');
const overlay = $('filter-overlay');
const openBtn = $('btn-open-filters');
const closeBtn = $('btn-close-filters');
const applyBtn = $('btn-apply-filters');
const activeFilters = $('active-filters');
const pillsNav = $('cat-pills');
const inicio = leerEstadoCatalogo(window.location.search);
let currentPage = inicio.pagina;
let totalPages = 1;
let solicitud = 0;
let debounceTimer;
let focoAnterior = null;
let overflowAnterior = '';
let fondoInerte = [];
const formatoNumero = valor => Number(valor).toLocaleString('es-PE');

searchInput.value = inicio.nombre;
priceInput.value = inicio.precioMax;
orderSelect.value = inicio.orden;
offersInput.checked = inicio.ofertas;

function estadoActual() {
    return {
        nombre: searchInput.value, categoriaId: categoriaSelect.value,
        precioMax: Number(priceInput.value), ofertas: offersInput.checked,
        orden: orderSelect.value, pagina: currentPage
    };
}

// Panel accesible: el teclado no sale del diálogo y vuelve al control de apertura.
function openDrawer() {
    if (drawer.classList.contains('open')) return;
    focoAnterior = document.activeElement;
    overflowAnterior = document.body.style.overflow;
    fondoInerte = [...document.body.children]
        .filter(el => el !== drawer && el !== overlay && el.tagName !== 'SCRIPT')
        .map(el => [el, el.inert]);
    fondoInerte.forEach(([el]) => { el.inert = true; });
    drawer.inert = false;
    drawer.classList.add('open');
    overlay.classList.add('open');
    drawer.setAttribute('aria-hidden', 'false');
    openBtn.setAttribute('aria-expanded', 'true');
    document.body.style.overflow = 'hidden';
    closeBtn.focus({ preventScroll: true });
}

function closeDrawer() {
    if (!drawer.classList.contains('open')) return;
    fondoInerte.forEach(([el, previo]) => { el.inert = previo; });
    document.body.style.overflow = overflowAnterior;
    (focoAnterior?.isConnected ? focoAnterior : openBtn).focus({ preventScroll: true });
    drawer.inert = true;
    drawer.classList.remove('open');
    overlay.classList.remove('open');
    drawer.setAttribute('aria-hidden', 'true');
    openBtn.setAttribute('aria-expanded', 'false');
}

openBtn.addEventListener('click', openDrawer);
closeBtn.addEventListener('click', closeDrawer);
overlay.addEventListener('click', closeDrawer);
applyBtn.addEventListener('click', closeDrawer);
drawer.addEventListener('keydown', event => {
    if (event.key === 'Escape') { event.preventDefault(); closeDrawer(); }
    if (event.key !== 'Tab') return;
    const controles = [...drawer.querySelectorAll('button:not(:disabled), select, input, a[href]')];
    const primero = controles[0], ultimo = controles.at(-1);
    if (event.shiftKey && document.activeElement === primero) {
        event.preventDefault(); ultimo.focus();
    } else if (!event.shiftKey && document.activeElement === ultimo) {
        event.preventDefault(); primero.focus();
    }
});

function renderPills(categorias) {
    pillsNav.replaceChildren();
    [{ id: '', nombre: 'Todo el catálogo' }, ...categorias].forEach(cat => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'catalog-category';
        button.dataset.categoria = String(cat.id);
        button.textContent = cat.nombre;
        button.addEventListener('click', () => {
            categoriaSelect.value = String(cat.id);
            filtrar();
        });
        pillsNav.appendChild(button);
    });
}

function actualizarControles() {
    const estado = estadoActual();
    pillsNav.querySelectorAll('button').forEach(button =>
        button.setAttribute('aria-pressed', String(button.dataset.categoria === estado.categoriaId)));
    const activa = pillsNav.querySelector('[aria-pressed="true"]');
    if (activa) {
        const limite = pillsNav.getBoundingClientRect();
        const rect = activa.getBoundingClientRect();
        if (rect.right > limite.right) pillsNav.scrollLeft += rect.right - limite.right + 12;
        else if (rect.left < limite.left) pillsNav.scrollLeft += rect.left - limite.left;
    }
    const limitado = estado.precioMax < PRECIO_SIN_LIMITE;
    const precioTexto = limitado ? formatoNumero(estado.precioMax) : 'Sin límite';
    $('price-val').textContent = precioTexto;
    priceInput.setAttribute('aria-valuetext', limitado ? `Hasta ${precioTexto}, en la moneda del producto` : 'Sin límite de precio');
    activeFilters.replaceChildren();
    const filtros = [];
    if (estado.nombre.trim()) filtros.push([`Búsqueda: ${estado.nombre.trim()}`, () => { searchInput.value = ''; }]);
    if (estado.categoriaId) filtros.push([categoriaSelect.selectedOptions[0].textContent, () => { categoriaSelect.value = ''; }]);
    if (limitado) filtros.push([`Precio hasta ${precioTexto}`, () => { priceInput.value = PRECIO_SIN_LIMITE; }]);
    if (estado.ofertas) filtros.push(['En oferta', () => { offersInput.checked = false; }]);
    filtros.forEach(([texto, quitar]) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'catalog-filter-chip';
        button.appendChild(document.createTextNode(texto));
        button.insertAdjacentHTML('beforeend', '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="m6 6 12 12M6 18 18 6"/></svg>');
        button.setAttribute('aria-label', `Quitar filtro: ${texto}`);
        button.addEventListener('click', () => { quitar(); filtrar(); openBtn.focus({ preventScroll: true }); });
        activeFilters.appendChild(button);
    });
    if (filtros.length) {
        const limpiar = document.createElement('button');
        limpiar.type = 'button';
        limpiar.className = 'catalog-clear';
        limpiar.textContent = 'Limpiar todo';
        limpiar.addEventListener('click', () => { limpiarFiltros(); openBtn.focus({ preventScroll: true }); });
        activeFilters.appendChild(limpiar);
    }
    $('filter-active-count').hidden = filtros.length === 0;
    $('filter-active-count').textContent = filtros.length;
}

async function cargarCategorias() {
    try {
        const res = await api.get('/api/categorias');
        if (!res?.exito || !Array.isArray(res.datos)) throw new Error('Categorías no disponibles');
        const categorias = res.datos;
        categorias.forEach(cat => {
            const opt = document.createElement('option');
            opt.value = cat.id;
            opt.textContent = cat.nombre;
            categoriaSelect.appendChild(opt);
        });
        const seleccionada = categorias.find(cat => String(cat.id) === inicio.categoriaId)
            || resolverCategoria(categorias, inicio.categoria);
        categoriaSelect.value = seleccionada ? String(seleccionada.id) : '';
        renderPills(categorias);
        // Una categoría profunda también debe estar a la vista en la tira horizontal.
        actualizarControles();
    } catch (err) {
        console.warn('No se pudieron cargar las categorías del catálogo:', err);
        // Mantener un enlace directo válido incluso si falla la lista de categorías.
        if (inicio.categoriaId) {
            const opt = new Option('Categoría seleccionada', inicio.categoriaId, true, true);
            categoriaSelect.appendChild(opt);
        }
        renderPills([]);
    }
}

function mostrarEstado(titulo, descripcion, accion, callback) {
    const estado = document.createElement('div');
    estado.className = 'catalog-state';
    const h2 = document.createElement('h2');
    h2.textContent = titulo;
    h2.tabIndex = -1;
    const p = document.createElement('p');
    p.textContent = descripcion;
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'store-button';
    button.textContent = accion;
    button.addEventListener('click', callback);
    estado.append(h2, p, button);
    productsContainer.replaceChildren(estado);
}

async function cargarProductos() {
    const pedido = ++solicitud;
    const estado = estadoActual();
    window.history.replaceState({}, '', urlCatalogo(estado, window.location.href));
    actualizarControles();
    productsContainer.setAttribute('aria-busy', 'true');
    $('results-count').textContent = 'Buscando productos…';
    applyBtn.textContent = 'Buscando…';
    prevBtn.disabled = true;
    nextBtn.disabled = true;
    // Sustituir resultados antiguos evita compras sobre una búsqueda ya cambiada.
    productsContainer.innerHTML = '<div class="catalog-skeleton" aria-hidden="true"></div>'.repeat(8);
    try {
        const res = await api.get(consultaCatalogo(estado));
        if (pedido !== solicitud) return;
        if (!res?.exito || !Array.isArray(res.datos?.contenido)) throw new Error(res?.mensaje || 'Respuesta de catálogo no válida');
        const datos = res.datos;
        totalPages = datos.totalPaginas;
        const total = datos.totalElementos ?? datos.contenido.length;
        // Un enlace a una página que ya no existe vuelve a la última disponible.
        if (totalPages > 0 && currentPage >= totalPages) {
            currentPage = totalPages - 1;
            return cargarProductos();
        }
        $('results-count').textContent = `${formatoNumero(total)} producto${total === 1 ? '' : 's'}`;
        applyBtn.textContent = `Ver ${formatoNumero(total)} producto${total === 1 ? '' : 's'}`;
        productsContainer.replaceChildren();
        if (datos.contenido.length) {
            const fragment = document.createDocumentFragment();
            datos.contenido.forEach(producto => {
                const card = renderProductCard(producto, item => cart.agregarItem(item.id, 1));
                fragment.appendChild(card);
            });
            productsContainer.appendChild(fragment);
        } else {
            currentPage = 0;
            window.history.replaceState({}, '', urlCatalogo(estadoActual(), window.location.href));
            mostrarEstado('No encontramos ese equipo.', 'Prueba con otro nombre o amplía los filtros para ver más opciones.', 'Ver todo el catálogo', limpiarFiltros);
        }
        pagination.hidden = totalPages <= 1;
        $('page-info').textContent = `Página ${currentPage + 1} de ${Math.max(totalPages, 1)}`;
        prevBtn.disabled = currentPage === 0;
        nextBtn.disabled = currentPage >= totalPages - 1;
    } catch (err) {
        if (pedido !== solicitud) return;
        pagination.hidden = true;
        $('results-count').textContent = 'Catálogo no disponible';
        applyBtn.textContent = 'Volver al catálogo';
        mostrarEstado('No pudimos cargar el catálogo.', 'Puede ser un problema de conexión. Tus filtros se conservan para volver a intentarlo.', 'Reintentar', cargarProductos);
    } finally {
        if (pedido === solicitud) productsContainer.setAttribute('aria-busy', 'false');
    }
}

function filtrar() {
    clearTimeout(debounceTimer);
    currentPage = 0;
    cargarProductos();
}

function limpiarFiltros() {
    searchInput.value = '';
    categoriaSelect.value = '';
    priceInput.value = PRECIO_SIN_LIMITE;
    orderSelect.value = ORDEN_DEFECTO;
    offersInput.checked = false;
    filtrar();
}

$('catalog-search-form').addEventListener('submit', event => { event.preventDefault(); filtrar(); });
searchInput.addEventListener('input', () => {
    clearTimeout(debounceTimer);
    // Invalida resultados en vuelo desde la primera tecla, no tras el debounce.
    solicitud++;
    debounceTimer = setTimeout(filtrar, 300);
});
categoriaSelect.addEventListener('change', filtrar);
orderSelect.addEventListener('change', filtrar);
offersInput.addEventListener('change', filtrar);
priceInput.addEventListener('input', () => {
    $('price-val').textContent = Number(priceInput.value) < PRECIO_SIN_LIMITE ? formatoNumero(priceInput.value) : 'Sin límite';
});
priceInput.addEventListener('change', filtrar);
$('btn-limpiar-filtros').addEventListener('click', limpiarFiltros);

function cambiarPagina(delta) {
    const siguiente = currentPage + delta;
    if (siguiente < 0 || siguiente >= totalPages) return;
    clearTimeout(debounceTimer);
    currentPage = siguiente;
    cargarProductos();
    $('results-count').scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'start' });
}
prevBtn.addEventListener('click', () => cambiarPagina(-1));
nextBtn.addEventListener('click', () => cambiarPagina(1));

await cargarCategorias();
await cargarProductos();
