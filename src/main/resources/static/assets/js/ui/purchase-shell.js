// El mismo navbar compacto y footer del escaparate para las pantallas de compra.
import './footer.js?v=4';
import { renderNavbar } from './navbar.js';

export async function iniciarShellCompra() {
    const header = document.getElementById('main-navbar');
    header.innerHTML = `
        <div class="fx-topbar"><a href="/index.html" class="fx-brand" title="ELECTRONIX"><img src="/assets/img/logo.svg" alt="Electronix" class="fx-brand-logo"><span>ELECTRONIX</span></a></div>
        <nav class="fx-navbar" aria-label="Navegación principal">
            <div class="fx-container fx-navbar-inner">
                <div class="fx-nav-left"></div>
                <div class="fx-nav-center">
                    <div class="fx-dropdown-wrapper" id="nav-cat-wrap">
                        <button type="button" id="nav-cat-trigger" class="fx-nav-link fx-dropdown-trigger" aria-haspopup="true" aria-expanded="false" aria-label="Explorar categorías">
                            <svg class="store-menu-icon" width="20" height="20" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h10" stroke-width="1.6" stroke-linecap="round"/></svg>
                            <span>Productos</span><svg class="fx-chevron" width="13" height="13" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6" stroke-width="2"/></svg>
                        </button>
                    </div>
                    <a href="/index.html#seleccion" class="fx-nav-link">Colecciones</a>
                    <a href="/productos.html?ofertas=true" class="fx-nav-link">Ofertas</a>
                </div>
                <div class="fx-nav-right">
                    <button type="button" id="nav-search-toggle" class="fx-search-btn" aria-label="Buscar productos"><img src="/assets/img/busquedaicon.svg" alt=""></button>
                    <a href="/carrito.html" class="fx-cart-btn" aria-label="Ver carrito"><img src="/assets/img/carritoicon.svg" alt=""><span id="cart-badge-top" class="fx-cart-count is-hidden">0</span></a>
                    <div class="fx-country" id="nav-country">
                        <button type="button" id="nav-country-btn" aria-haspopup="true" aria-expanded="false" title="Perú"><img id="nav-country-flag" src="https://flagcdn.com/pe.svg" alt="Perú" width="18" height="12"><span id="nav-country-code">PE</span></button>
                        <div id="nav-country-menu" role="menu" aria-label="Seleccionar país"></div>
                    </div>
                    <span id="nav-auth-container"><a href="/login.html" id="nav-login-btn" class="fx-login" aria-label="Ingresar a mi cuenta"><svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="8" r="3.5"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/></svg><span>Mi cuenta</span></a></span>
                </div>
            </div>
            <div id="nav-mega" class="fx-mega" aria-hidden="true">
                <div class="fx-container fx-mega-inner">
                    <div class="fx-mega-col"><p class="fx-mega-kicker">Explorar</p><div id="nav-categories-dropdown" class="fx-mega-biglinks"><a href="/productos.html">Todas las categorías</a><a href="/productos.html?categoria=Laptops">Portátiles</a><a href="/productos.html?categoria=Smartphones">Smartphones</a><a href="/productos.html?categoria=Audio">Audio</a></div><div class="fx-mega-foot"><a href="/productos.html">Explorar el catálogo completo</a></div></div>
                    <div class="fx-mega-col"><p class="fx-mega-kicker">Tu compra</p><div class="fx-mega-smalllinks"><a href="/carrito.html">Mi carrito</a><a href="/mis-pedidos.html">Mis pedidos</a><a href="/informacion.html#despachos">Envíos y despachos</a><a href="/informacion.html#chat">Ayuda</a></div></div>
                </div>
            </div>
            <div id="nav-search-panel" class="fx-search-panel" aria-hidden="true"><div class="fx-container fx-search-panel-inner">
                <form id="nav-search-form" action="/productos.html" method="GET" role="search"><img src="/assets/img/busquedaicon.svg" alt="" aria-hidden="true"><input id="nav-search-input" type="text" name="q" placeholder="Buscar en Electronix" autocomplete="off" aria-label="Buscar en Electronix"><button type="button" id="nav-search-close" aria-label="Cerrar búsqueda">×</button></form>
                <p class="fx-mega-kicker">Enlaces rápidos</p><div class="fx-quick"><a href="/productos.html">El catálogo</a><a href="/productos.html?ofertas=true">Ofertas</a><a href="/productos.html?orden=recientes">Novedades</a></div>
            </div></div>
        </nav><div id="nav-backdrop" class="fx-backdrop"></div>`;
    await import('./fx-header.js?v=3');
    renderNavbar();
}
