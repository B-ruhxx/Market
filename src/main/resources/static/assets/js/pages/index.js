import '/assets/js/ui/footer.js?v=4';
import { api } from '/assets/js/api.js';
import { cart } from '/assets/js/cart.js';
import { renderNavbar } from '/assets/js/ui/navbar.js';
import { renderProductCard } from '/assets/js/ui/productCard.js';
import { aplicarSitio } from '/assets/js/sitio.js?v=6';

// Las novedades y la configuración se consultan en paralelo, sin datos simulados.
cargarNovedades();
await aplicarSitio();
renderNavbar();

function normalizar(valor) {
    return String(valor || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim();
}
const ALIAS_CATEGORIA = {
    televisores: 'monitores', laptops: 'laptops y computadoras', portatiles: 'laptops y computadoras',
    audio: 'audio y sonido', 'equipo de sonido': 'audio y sonido',
    accesorios: 'accesorios moviles', 'accesorios y pc': 'accesorios moviles'
};
let categoriasCache = null;
async function obtenerCategorias() {
    if (categoriasCache) return categoriasCache;
    try {
        const res = await api.get('/api/categorias');
        if (res.exito && Array.isArray(res.datos)) categoriasCache = res.datos;
    } catch (error) { console.warn('No se pudieron cargar las categorías:', error); }
    return categoriasCache || [];
}

// Delegación: sigue funcionando si Apariencia vuelve a renderizar las tarjetas.
document.getElementById('categorias-grid')?.addEventListener('click', async event => {
    const tile = event.target.closest('.cat-tile');
    if (!tile) return;
    const nombre = tile.dataset.name || '';
    const cats = await obtenerCategorias();
    const buscado = ALIAS_CATEGORIA[normalizar(nombre)] || normalizar(nombre);
    const categoria = cats.find(cat => normalizar(cat.nombre) === buscado);
    window.location.href = categoria
        ? `/productos.html?categoriaId=${categoria.id}`
        : `/productos.html${nombre ? `?categoria=${encodeURIComponent(nombre)}` : ''}`;
});

function iniciarReveal() {
    const elementos = document.querySelectorAll('.elx-cat-tile, .elx-promo-card');
    if (!('IntersectionObserver' in window) || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const observer = new IntersectionObserver(entradas => {
        entradas.forEach(entrada => {
            if (entrada.isIntersecting) {
                entrada.target.classList.add('elx-visible');
                observer.unobserve(entrada.target);
            }
        });
    }, { threshold: .1 });
    elementos.forEach(el => { el.classList.add('elx-reveal'); observer.observe(el); });
}
iniciarReveal();

function iniciarColecciones() {
    const track = document.getElementById('spot-track');
    const anterior = document.getElementById('spot-prev');
    const siguiente = document.getElementById('spot-next');
    if (!track || !anterior || !siguiente) return;
    function actualizar() {
        track.closest('.elx-spotlight').querySelector('.store-story-controls').hidden = track.scrollWidth <= track.clientWidth + 2;
        anterior.disabled = track.scrollLeft <= 2;
        siguiente.disabled = track.scrollLeft >= track.scrollWidth - track.clientWidth - 2;
    }
    function mover(direccion) {
        track.scrollBy({
            left: direccion * (track.clientWidth * .85),
            behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'
        });
    }
    anterior.addEventListener('click', () => mover(-1));
    siguiente.addEventListener('click', () => mover(1));
    track.addEventListener('scroll', actualizar, { passive: true });
    track.addEventListener('keydown', event => {
        if (event.target !== track || !['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
        event.preventDefault();
        mover(event.key === 'ArrowRight' ? 1 : -1);
    });
    window.addEventListener('resize', actualizar, { passive: true });
    actualizar();
}
iniciarColecciones();

async function cargarNovedades() {
    const contenedor = document.getElementById('home-products');
    if (!contenedor) return;
    try {
        const res = await api.get('/api/productos?page=0&size=4&sortBy=fechaCreacion&direction=desc');
        if (!res.exito || !Array.isArray(res.datos?.contenido)) throw new Error('Catálogo no disponible');
        contenedor.innerHTML = '';
        if (!res.datos.contenido.length) {
            contenedor.innerHTML = '<p class="store-products-status" role="status">Pronto encontrarás novedades aquí. <a href="/productos.html">Explora el catálogo</a>.</p>';
            return;
        }
        res.datos.contenido.forEach(producto => {
            const tarjeta = renderProductCard(producto, item => cart.agregarItem(item.id, 1));
            contenedor.appendChild(tarjeta);
        });
    } catch {
        contenedor.innerHTML = '<p class="store-products-status" role="status">No pudimos cargar las novedades ahora. <a href="/productos.html">Volver a consultar el catálogo</a>.</p>';
    } finally {
        contenedor.setAttribute('aria-busy', 'false');
    }
}
