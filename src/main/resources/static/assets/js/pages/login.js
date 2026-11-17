import { auth } from '/assets/js/auth.js';
import { iniciarShellCompra } from '/assets/js/ui/purchase-shell.js';
import { retornoSeguro } from '/assets/js/purchase-flow.js';

await iniciarShellCompra();

const form = document.getElementById('login-form');
const email = document.getElementById('login-email');
const password = document.getElementById('login-password');
const remember = document.getElementById('login-remember');
const submit = document.getElementById('btn-submit-login');
const toggle = document.getElementById('login-password-toggle');
const icon = document.getElementById('login-password-icon');
const error = document.getElementById('login-error');
let ocupado = false;
const ojo = '<path d="M2.25 12s3.5-6 9.75-6 9.75 6 9.75 6-3.5 6-9.75 6-9.75-6-9.75-6Z"/><circle cx="12" cy="12" r="2.5"/>';
const ojoCerrado = '<path d="m3 3 18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.2A10.8 10.8 0 0 1 12 5c6.25 0 9.75 7 9.75 7a18.4 18.4 0 0 1-3.2 3.9M6.1 6.1A18.7 18.7 0 0 0 2.25 12S5.75 19 12 19c1.25 0 2.37-.28 3.35-.72"/>';

function mostrarPassword(visible) {
    password.type = visible ? 'text' : 'password';
    toggle.setAttribute('aria-pressed', String(visible));
    toggle.setAttribute('aria-label', visible ? 'Ocultar contraseña' : 'Mostrar contraseña');
    toggle.title = toggle.getAttribute('aria-label');
    icon.innerHTML = visible ? ojoCerrado : ojo;
}
toggle.addEventListener('click', () => mostrarPassword(password.type === 'password'));

// Conservar la limpieza explícita de datos sensibles tras cerrar sesión,
// incluso cuando el navegador restaura el formulario desde su caché.
const desdeLogout = new URLSearchParams(location.search).get('logout') === '1';
function limpiarLogout() {
    if (!desdeLogout) return;
    form.reset();
    email.value = '';
    password.value = '';
    remember.checked = false;
    mostrarPassword(false);
    history.replaceState(null, '', '/login.html');
}
limpiarLogout();
window.addEventListener('pageshow', limpiarLogout);
setTimeout(limpiarLogout, 100);
submit.disabled = false;

form.addEventListener('submit', async event => {
    event.preventDefault();
    if (ocupado || !form.reportValidity()) return;
    ocupado = true;
    submit.disabled = true;
    submit.setAttribute('aria-busy', 'true');
    submit.textContent = 'Ingresando…';
    error.hidden = true;
    try {
        await auth.login(email.value.trim(), password.value, remember.checked);
        const destino = retornoSeguro(new URLSearchParams(location.search).get('redirect'), location.origin);
        location.href = destino || (auth.isAdmin() ? auth.inicioAdmin() : '/index.html');
    } catch (err) {
        error.textContent = err.message || 'No pudimos iniciar sesión. Inténtalo nuevamente.';
        error.hidden = false;
        ocupado = false;
        submit.disabled = false;
        submit.removeAttribute('aria-busy');
        submit.textContent = 'Iniciar sesión';
    }
});
