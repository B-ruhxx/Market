// puntos.js — Lógica interactiva para la página Electronix Club (CMR Puntos)
import { auth } from '/assets/js/auth.js';
import { api } from '/assets/js/api.js';
import { renderNavbar } from '/assets/js/ui/navbar.js';
import { formatoMoneda } from '/assets/js/money.js';

// Inicializar la barra de navegación compartida
renderNavbar();

// Estado reactivo de la página
let billetera = null;
let tasaValorPen = 0.05; // 1 punto = S/ 0.05 (20 pts = S/ 1.00)
let factorSolPorPunto = 5.0; // S/ 5 gastados = 1 punto base
let multiplicadorActual = 1.00;
const maxCoberturaPct = 50; // Hasta 50% de la orden

const esc = v => String(v ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// Inicialización garantizada para ES Modules (sin esperar DOMContentLoaded si ya cargó)
async function iniciar() {
    const user = auth.getUser();
    if (user) {
        optimizarVistaAutenticado(user);
    }

    inicializarSimulador();
    inicializarDrawer();

    await Promise.allSettled([
        cargarDatosUsuario(),
        cargarNiveles()
    ]);
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', iniciar);
} else {
    iniciar();
}

// Actualización optimista inmediata si hay sesión activa en storage
function optimizarVistaAutenticado(user) {
    document.documentElement.classList.add('user-is-authenticated');

    const passHolder = document.getElementById('pass-holder');
    if (passHolder) {
        const nombreCompleto = [user.nombre, user.apellidoPaterno].filter(Boolean).join(' ') || 'SOCIO CLUB';
        passHolder.textContent = nombreCompleto.toUpperCase();
    }

    const heroEyebrow = document.getElementById('hero-eyebrow');
    if (heroEyebrow) {
        const primerNombre = (user.nombre || 'SOCIO').split(' ')[0];
        heroEyebrow.innerHTML = `
            <span class="club-eyebrow-icon" aria-hidden="true">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            </span>
            <span id="hero-eyebrow-text">BIENVENIDO DE VUELTA, ${esc(primerNombre.toUpperCase())}</span>
        `;
    }

    const heroTitle = document.getElementById('hero-title');
    if (heroTitle) {
        heroTitle.textContent = 'Tu Billetera Electronix Club está lista.';
    }

    const heroDesc = document.getElementById('hero-desc');
    if (heroDesc) {
        heroDesc.innerHTML = 'Tienes puntos acumulados y listos para canjear en el checkout. Sigue sumando con cada pedido que realices en la tienda.';
    }

    const passRate = document.getElementById('pass-rate');
    if (passRate) {
        passRate.textContent = 'SOCIO ACTIVO · CLUB';
    }
}

// Carga de datos de fidelización del usuario desde el servidor
async function cargarDatosUsuario() {
    const user = auth.getUser();
    if (!user) {
        return;
    }

    optimizarVistaAutenticado(user);

    try {
        const res = await api.get('/api/crm/puntos/mi-billetera');
        if (res && res.exito && res.datos) {
            billetera = res.datos;
            aplicarEstadoAutenticado(user, billetera);
        }
    } catch (err) {
        console.warn('No se pudo sincronizar la billetera del usuario:', err.message);
    }
}

// Conmutar interfaz a modo Autenticado (Hero bifurcado & Club Pass dinámico)
function aplicarEstadoAutenticado(user, b) {
    document.documentElement.classList.add('user-is-authenticated');

    // 1. Textos y títulos del Hero
    const primerNombre = (user.nombre || 'SOCIO').split(' ')[0];
    const heroEyebrow = document.getElementById('hero-eyebrow');
    if (heroEyebrow) {
        heroEyebrow.innerHTML = `
            <span class="club-eyebrow-icon" aria-hidden="true">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            </span>
            <span id="hero-eyebrow-text">BIENVENIDO DE VUELTA, ${esc(primerNombre.toUpperCase())}</span>
        `;
    }

    const heroTitle = document.getElementById('hero-title');
    if (heroTitle) {
        heroTitle.textContent = 'Tu Billetera Electronix Club está lista.';
    }

    // 2. Actualizar Tarjeta Virtual Club Pass
    const nombreCompleto = [user.nombre, user.apellidoPaterno].filter(Boolean).join(' ') || 'SOCIO CLUB';
    const passHolder = document.getElementById('pass-holder');
    if (passHolder) passHolder.textContent = nombreCompleto.toUpperCase();

    const passTierBadge = document.getElementById('pass-tier-badge');
    const nivelCodigo = (b.nivelCodigo || 'BRONCE').toUpperCase();
    const mult = Number(b.multiplicador || 1).toFixed(2);
    if (passTierBadge) {
        passTierBadge.textContent = `${b.nivelNombre || nivelCodigo} · ${mult}x`;
        passTierBadge.setAttribute('data-tier', nivelCodigo);
    }

    const passPoints = document.getElementById('pass-points');
    if (passPoints) passPoints.textContent = b.saldoPuntos ?? 0;

    const equivPen = Number(b.valorEstimadoPen ?? ((b.saldoPuntos || 0) * tasaValorPen)).toFixed(2);
    const passEquiv = document.getElementById('pass-equiv');
    if (passEquiv) passEquiv.textContent = `(≈ S/ ${equivPen})`;

    const passRate = document.getElementById('pass-rate');
    if (passRate) {
        passRate.textContent = b.beneficioEnvioGratis ? 'SOCIO VIP · ENVÍO GRATIS' : `ACUMULACIÓN ${mult}X`;
    }

    // Barra de progreso hacia el siguiente nivel
    const progressWrap = document.getElementById('pass-progress-wrap');
    const progressLabel = document.getElementById('pass-progress-label');
    const progressPct = document.getElementById('pass-progress-pct');
    const progressBar = document.getElementById('pass-progress-bar');

    if (progressWrap) {
        progressWrap.style.display = 'block';
        if (b.siguienteNivelNombre) {
            const faltante = Number(b.montoFaltanteSiguienteNivelPen || 0).toFixed(2);
            if (progressLabel) progressLabel.textContent = `Faltan S/ ${faltante} para ${esc(b.siguienteNivelNombre)}`;
            const pct = Math.min(100, Math.max(0, Number(b.porcentajeProgresoNivel || 0)));
            if (progressPct) progressPct.textContent = `${pct.toFixed(0)}%`;
            if (progressBar) progressBar.style.width = `${pct}%`;
        } else {
            if (progressLabel) progressLabel.textContent = `¡Nivel Máximo (${esc(b.nivelNombre)}) alcanzado!`;
            if (progressPct) progressPct.textContent = '100%';
            if (progressBar) progressBar.style.width = '100%';
        }
    }

    // 3. Actualizar configuraciones del simulador
    multiplicadorActual = Number(b.multiplicador || 1.0);
    const btnUseBalance = document.getElementById('btn-sim-use-balance');
    const simUserPts = document.getElementById('sim-user-pts');
    if (btnUseBalance && simUserPts) {
        simUserPts.textContent = b.saldoPuntos || 0;
        btnUseBalance.style.display = (b.saldoPuntos > 0) ? 'inline-flex' : 'none';
        btnUseBalance.onclick = () => {
            const sliderPuntos = document.getElementById('sim-puntos-slider');
            const sliderMonto = document.getElementById('sim-monto-slider');
            if (sliderPuntos && sliderMonto) {
                const monto = Number(sliderMonto.value);
                const maxDescuento = monto * (maxCoberturaPct / 100);
                const maxPuntosPorTope = Math.floor(maxDescuento / tasaValorPen);
                const puntosACanjear = Math.min(b.saldoPuntos, maxPuntosPorTope);
                sliderPuntos.value = puntosACanjear;
                recalcularSimulador();
            }
        };
    }

    // 4. Llenar el drawer de historial de puntos
    poblarHistorialDrawer(b);
}

// Carga de niveles dinámicos desde el backend
async function cargarNiveles() {
    try {
        const res = await api.get('/api/crm/niveles');
        if (res && res.exito && Array.isArray(res.datos)) {
            resaltarNivelActual(res.datos);
        }
    } catch (_) {
        // En caso de fallo de red, los niveles estáticos permanecen en pantalla
    }
}

// Resalta la tarjeta de nivel si el usuario está autenticado
function resaltarNivelActual(nivelesList) {
    if (!billetera) return;
    const miNivelCodigo = (billetera.nivelCodigo || 'BRONCE').toUpperCase();

    document.querySelectorAll('[data-tier-card]').forEach(card => {
        const tier = card.getAttribute('data-tier-card');
        if (tier === miNivelCodigo) {
            card.classList.add('is-current-tier');
            if (!card.querySelector('.club-tier-current-tag')) {
                const tag = document.createElement('div');
                tag.className = 'club-tier-current-tag';
                tag.innerHTML = `
                    <svg viewBox="0 0 24 24" fill="currentColor"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                    <span>Tu Nivel Actual</span>
                `;
                card.prepend(tag);
            }
        } else {
            card.classList.remove('is-current-tier');
        }
    });
}

// Lógica del simulador interactivo de canje
function inicializarSimulador() {
    const sliderMonto = document.getElementById('sim-monto-slider');
    const sliderPuntos = document.getElementById('sim-puntos-slider');

    if (sliderMonto && sliderPuntos) {
        sliderMonto.addEventListener('input', recalcularSimulador);
        sliderPuntos.addEventListener('input', recalcularSimulador);
    }

    // Presets rápidos de compra
    document.querySelectorAll('.club-preset-chip').forEach(btn => {
        btn.addEventListener('click', () => {
            const monto = btn.getAttribute('data-monto');
            if (sliderMonto && monto) {
                sliderMonto.value = monto;
                recalcularSimulador();
            }
        });
    });

    recalcularSimulador();
}

function recalcularSimulador() {
    const sliderMonto = document.getElementById('sim-monto-slider');
    const sliderPuntos = document.getElementById('sim-puntos-slider');
    if (!sliderMonto || !sliderPuntos) return;

    const monto = Number(sliderMonto.value);
    let puntos = Number(sliderPuntos.value);

    // Regla de negocio: Cobertura máxima del 50% de la orden
    const maxDescuentoPermitido = monto * (maxCoberturaPct / 100);
    const maxPuntosPermitidos = Math.floor(maxDescuentoPermitido / tasaValorPen);

    const warningEl = document.getElementById('sim-warning');
    if (puntos > maxPuntosPermitidos) {
        puntos = maxPuntosPermitidos;
        sliderPuntos.value = puntos;
        if (warningEl) warningEl.style.display = 'inline-flex';
    } else {
        if (warningEl) warningEl.style.display = 'none';
    }

    const descuento = puntos * tasaValorPen;
    const totalFinal = Math.max(0, monto - descuento);
    // Puntos ganados por compra: gasto neto / factor * multiplicador
    const puntosGanados = Math.round((totalFinal / factorSolPorPunto) * multiplicadorActual);

    // Actualizar etiquetas en vivo
    const montoTxt = document.getElementById('sim-monto-txt');
    if (montoTxt) montoTxt.textContent = `S/ ${monto.toFixed(2)}`;

    const puntosTxt = document.getElementById('sim-puntos-txt');
    if (puntosTxt) puntosTxt.textContent = `${puntos} pts`;

    const equivTxt = document.getElementById('sim-pts-equiv');
    if (equivTxt) equivTxt.textContent = `S/ ${descuento.toFixed(2)}`;

    const resSubtotal = document.getElementById('res-subtotal');
    if (resSubtotal) resSubtotal.textContent = `S/ ${monto.toFixed(2)}`;

    const resPtsCount = document.getElementById('res-pts-count');
    if (resPtsCount) resPtsCount.textContent = puntos;

    const resDescuento = document.getElementById('res-descuento');
    if (resDescuento) resDescuento.textContent = `- S/ ${descuento.toFixed(2)}`;

    const resTotal = document.getElementById('res-total');
    if (resTotal) resTotal.textContent = `S/ ${totalFinal.toFixed(2)}`;

    const resGanados = document.getElementById('res-ganados');
    if (resGanados) resGanados.textContent = `+ ${puntosGanados} pts`;
}

// Drawer de historial de puntos
function inicializarDrawer() {
    const overlay = document.getElementById('drawer-overlay');
    const btnOpen = document.getElementById('btn-open-drawer');
    const btnClose = document.getElementById('btn-close-drawer');
    const passCard = document.getElementById('club-pass');

    function abrirDrawer() {
        if (overlay) {
            overlay.classList.add('is-open');
            overlay.setAttribute('aria-hidden', 'false');
            document.body.style.overflow = 'hidden';
            if (btnClose) btnClose.focus();

            // Si aún no se han cargado datos, refrescar inmediatamente
            if (!billetera && auth.getUser()) {
                cargarDatosUsuario().catch(() => {});
            }
        }
    }

    function cerrarDrawer() {
        if (overlay) {
            overlay.classList.remove('is-open');
            overlay.setAttribute('aria-hidden', 'true');
            document.body.style.overflow = '';
            if (btnOpen) btnOpen.focus();
        }
    }

    // Exponer globalmente en window para garantizar que nunca sea un botón engañoso
    window.abrirDrawerHistorialClub = abrirDrawer;
    window.cerrarDrawerHistorialClub = cerrarDrawer;

    if (btnOpen) {
        btnOpen.onclick = (e) => {
            e.preventDefault();
            abrirDrawer();
        };
    }

    if (passCard && auth.getUser()) {
        passCard.style.cursor = 'pointer';
        passCard.title = 'Haz clic para consultar tu historial de puntos';
        passCard.onclick = abrirDrawer;
    }

    if (btnClose) btnClose.onclick = cerrarDrawer;
    if (overlay) {
        overlay.onclick = (e) => {
            if (e.target === overlay) cerrarDrawer();
        };
    }

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && overlay && overlay.classList.contains('is-open')) {
            cerrarDrawer();
        }
    });
}

function poblarHistorialDrawer(billeteraObj) {
    const loading = document.getElementById('drawer-loading');
    const content = document.getElementById('drawer-content');
    const empty = document.getElementById('drawer-empty');
    const tbody = document.getElementById('drawer-tbody');
    const drawerPts = document.getElementById('drawer-user-points');
    const drawerName = document.getElementById('drawer-user-name');

    if (loading) loading.style.display = 'none';

    const user = auth.getUser();
    if (drawerName && user) {
        drawerName.textContent = user.nombre || 'Miembro Club';
    }
    if (drawerPts && billeteraObj) {
        const equiv = Number(billeteraObj.valorEstimadoPen ?? ((billeteraObj.saldoPuntos || 0) * tasaValorPen)).toFixed(2);
        drawerPts.textContent = `${billeteraObj.saldoPuntos || 0} pts (≈ S/ ${equiv})`;
    }

    const transacciones = billeteraObj?.transaccionesRecientes;
    if (!Array.isArray(transacciones) || transacciones.length === 0) {
        if (empty) empty.style.display = 'block';
        if (content) content.style.display = 'none';
        return;
    }

    if (content) content.style.display = 'block';
    if (empty) empty.style.display = 'none';

    if (tbody) {
        tbody.innerHTML = transacciones.map(tx => {
            const esIngreso = Number(tx.puntos || 0) >= 0;
            const signo = esIngreso ? '+' : '';
            const claseColor = esIngreso ? 'club-tx-pts-gain' : 'club-tx-pts-spent';
            const fechaStr = tx.fecha ? new Date(tx.fecha).toLocaleDateString('es-PE', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';
            const descripcion = tx.descripcion || tx.tipo || 'Movimiento de puntos';

            return `
                <tr>
                    <td style="white-space: nowrap; color: #64748b;">${esc(fechaStr)}</td>
                    <td>
                        <div style="font-weight: 700; color: #1e293b;">${esc(descripcion)}</div>
                        ${tx.referenciaPedidoId ? `<div style="font-size: 11px; color: #94a3b8;">Pedido #${esc(tx.referenciaPedidoId)}</div>` : ''}
                    </td>
                    <td style="text-align: right;" class="${claseColor}">
                        ${signo}${tx.puntos} pts
                    </td>
                    <td style="text-align: right; font-weight: 700; color: #475569;">
                        ${tx.saldoResultante ?? '—'}
                    </td>
                </tr>
            `;
        }).join('');
    }
}
