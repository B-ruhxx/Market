import '/assets/js/ui/footer.js?v=4';
import { renderNavbar } from '/assets/js/ui/navbar.js';
import { auth } from '/assets/js/auth.js';

renderNavbar();

const enlaces = document.querySelectorAll('.help-menu a');
function seleccionarTema() {
    const hash = window.location.hash || '#despachos';
    enlaces.forEach(enlace => {
        if (enlace.hash === hash) enlace.setAttribute('aria-current', 'location');
        else enlace.removeAttribute('aria-current');
    });
    const destino = document.getElementById(hash.slice(1));
    if (destino?.tagName === 'DETAILS') destino.open = true;
}
seleccionarTema();
window.addEventListener('hashchange', seleccionarTema);
document.addEventListener('click', event => {
    const enlace = event.target.closest('a[href]');
    if (!enlace) return;
    const url = new URL(enlace.href);
    if (url.origin !== location.origin || url.pathname !== location.pathname || !url.hash) return;
    const destino = document.getElementById(url.hash.slice(1));
    if (destino?.tagName === 'DETAILS') destino.open = true;
});

const chat = document.getElementById('help-open-chat');
if (auth.isAuthenticated()) chat.textContent = 'Abrir chat de soporte';
chat.addEventListener('click', event => {
    if (!auth.isAuthenticated()) return;
    const boton = document.getElementById('chat-toggle-btn');
    if (!boton) return;
    event.preventDefault();
    const widget = document.getElementById('floating-chat-container');
    if (!widget?.classList.contains('elx-chat-open')) boton.click();
    document.getElementById('chat-input')?.focus({ preventScroll: true });
});

// Mantener los enlaces profundos alineados después de cargar la fuente local.
document.fonts.ready.then(() => {
    if (window.location.hash) document.getElementById(window.location.hash.slice(1))?.scrollIntoView({ block: 'start' });
});
