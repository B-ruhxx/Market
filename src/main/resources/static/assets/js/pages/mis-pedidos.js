import { api } from '/assets/js/api.js';
import { auth } from '/assets/js/auth.js';
import { formatoMoneda } from '/assets/js/money.js';
import { iniciarShellCompra } from '/assets/js/ui/purchase-shell.js';
import { escapar as esc, estadoPedido, estadoPago, PASOS_PEDIDO, fechaPedido } from '/assets/js/purchase-flow.js';

await iniciarShellCompra();

const container = document.getElementById('orders-container');
const feedback = document.getElementById('orders-feedback');
const refresh = document.getElementById('orders-refresh');
const filtro = document.getElementById('orders-filter');
let pedidos = [], cargando = false, enlaceAplicado = false;
const abiertos = new Set();
const buscado = new URLSearchParams(location.search).get('pedido');

async function cargarBilletera() {
    try {
        const res = await api.get('/api/crm/puntos/mi-billetera');
        if (!res?.exito || !res?.datos) return;
        const b = res.datos;
        const card = document.getElementById('billetera-crm-card');
        if (!card) return;

        card.hidden = false;
        const saldoEl = document.getElementById('billetera-saldo-puntos');
        if (saldoEl) saldoEl.textContent = `${b.saldoPuntos} pts`;
        const valPen = Number(b.valorEstimadoPen ?? b.equivalentePen ?? 0).toFixed(2);
        const valorEl = document.getElementById('billetera-valor-pen');
        if (valorEl) valorEl.textContent = `S/ ${valPen}`;
        const histEl = document.getElementById('billetera-puntos-historicos');
        if (histEl) histEl.textContent = `Total histórico acumulado: ${b.puntosHistoricos || 0} pts`;

        const nivelCodigo = (b.nivelCodigo || 'BRONCE').toUpperCase();
        const nivelNombre = b.nivelNombre || nivelCodigo;
        const nivelBadge = document.getElementById('billetera-nivel-badge');
        const multBadge = document.getElementById('billetera-multiplicador-badge');
        const envioBadge = document.getElementById('billetera-envio-badge');

        if (nivelBadge) {
            nivelBadge.textContent = nivelNombre;
            if (nivelCodigo === 'ORO') {
                nivelBadge.style.background = 'rgba(234, 179, 8, 0.3)';
                nivelBadge.style.color = '#fef08a';
                nivelBadge.style.borderColor = '#facc15';
            } else if (nivelCodigo === 'PLATA') {
                nivelBadge.style.background = 'rgba(148, 163, 184, 0.3)';
                nivelBadge.style.color = '#f1f5f9';
                nivelBadge.style.borderColor = '#cbd5e1';
            } else {
                nivelBadge.style.background = 'rgba(180, 83, 9, 0.6)';
                nivelBadge.style.color = '#fde68a';
                nivelBadge.style.borderColor = 'rgba(217, 119, 6, 0.5)';
            }
        }

        if (multBadge) {
            const mult = Number(b.multiplicador || 1).toFixed(2);
            multBadge.textContent = `${mult}x Pts`;
        }

        if (envioBadge) {
            envioBadge.style.display = b.beneficioEnvioGratis ? 'inline-block' : 'none';
        }

        const progresoTexto = document.getElementById('billetera-progreso-texto');
        const progresoPct = document.getElementById('billetera-progreso-pct');
        const progresoBarra = document.getElementById('billetera-progreso-barra');

        if (progresoTexto && progresoBarra && progresoPct) {
            const gastoAnual = Number(b.gastoAnualPen || 0).toFixed(2);
            if (b.siguienteNivelNombre) {
                const faltante = Number(b.montoFaltanteSiguienteNivelPen || 0).toFixed(2);
                progresoTexto.innerHTML = `Llevas <strong>S/ ${gastoAnual}</strong> en compras este año. Te faltan <strong>S/ ${faltante}</strong> para alcanzar nivel <strong>${esc(b.siguienteNivelNombre)}</strong>.`;
                const pct = Math.min(100, Math.max(0, Number(b.porcentajeProgresoNivel || 0)));
                progresoPct.textContent = `${pct.toFixed(0)}%`;
                progresoBarra.style.width = `${pct}%`;
            } else {
                progresoTexto.innerHTML = `¡Felicidades! Alcanzaste el <strong>nivel ${esc(nivelNombre)}</strong> (máximo nivel) con <strong>${Number(b.multiplicador || 1.5).toFixed(2)}x puntos</strong> y beneficios VIP exclusivos.`;
                progresoPct.textContent = '100%';
                progresoBarra.style.width = '100%';
            }
        }

        const btnToggle = document.getElementById('btn-toggle-historial-puntos');
        const panel = document.getElementById('historial-puntos-panel');
        const tbody = document.getElementById('historial-puntos-tbody');

        if (btnToggle && panel) {
            btnToggle.onclick = () => {
                const oculto = panel.hidden;
                panel.hidden = !oculto;
                btnToggle.textContent = oculto ? 'Ocultar movimientos ▲' : 'Ver movimientos de puntos ▼';
            };
        }

        if (tbody) {
            const txs = b.transaccionesRecientes ?? b.transacciones ?? [];
            if (txs.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" style="padding: 0.75rem; text-align: center; color: #71717a;">Aún no registras movimientos de puntos</td></tr>';
            } else {
                tbody.innerHTML = txs.map(t => {
                    const esPos = t.puntos > 0;
                    const ptsColor = esPos ? '#34d399' : '#fbbf24';
                    const ptsSign = esPos ? `+${t.puntos}` : `${t.puntos}`;
                    const fecha = t.fecha ? new Date(t.fecha).toLocaleDateString('es-PE', { day:'2-digit', month:'short', hour:'2-digit', minute:'2-digit' }) : '-';
                    return `<tr style="border-bottom: 1px solid #18181b;">
                        <td style="padding: 0.375rem 0.5rem; color: #a1a1aa;">${fecha}</td>
                        <td style="padding: 0.375rem 0.5rem; font-family: monospace; font-size: 0.6875rem; color: #d4d4d8;">${esc(t.tipo)}</td>
                        <td style="padding: 0.375rem 0.5rem; color: #e4e4e7;">${esc(t.descripcion || '-')}</td>
                        <td style="padding: 0.375rem 0.5rem; text-align: right; font-weight: 700; color: ${ptsColor};">${ptsSign} pts</td>
                        <td style="padding: 0.375rem 0.5rem; text-align: right; color: #a1a1aa; font-family: monospace;">${t.saldoResultante} pts</td>
                    </tr>`;
                }).join('');
            }
        }
    } catch (_) {
        // Silencioso si no aplica billetera
    }
}

function timeline(estado) {
    const vista = estadoPedido(estado);
    if (vista.cancelado) return '<p class="flow-message">Este pedido figura como cancelado. El estado del pago se muestra por separado. Consulta con soporte si necesitas ayuda.</p>';
    if (vista.indice < 0) return '<p class="flow-note">No hay un progreso de entrega disponible para este estado.</p>';
    return `<ol class="flow-timeline" aria-label="Progreso del pedido">${PASOS_PEDIDO.map(([, texto], indice) => `
        <li data-done="${indice < vista.indice}" ${indice === vista.indice ? 'aria-current="step"' : ''}>
            ${texto}<span class="flow-note">${indice < vista.indice ? ' · Completado' : indice === vista.indice ? ' · Estado actual' : ''}</span>
        </li>`).join('')}</ol>`;
}

function guardarAbiertos() {
    container.querySelectorAll('.flow-order').forEach(detalle => {
        if (detalle.open) abiertos.add(detalle.dataset.id);
        else abiertos.delete(detalle.dataset.id);
    });
}

function pintarPedidos() {
    guardarAbiertos();
    const lista = pedidos.filter(p => filtro.value === 'activos' ? ['PENDIENTE', 'EN_PROCESO', 'ENVIADO'].includes(p.estado)
        : filtro.value === 'entregados' ? p.estado === 'ENTREGADO'
            : filtro.value === 'cancelados' ? p.estado === 'CANCELADO' : true);
    if (!lista.length) {
        container.innerHTML = pedidos.length
            ? '<div class="flow-state"><h2>No hay pedidos con ese estado.</h2><p>Prueba con otra vista para consultar tus compras.</p><button type="button" id="orders-show-all" class="store-button">Ver todos los pedidos</button></div>'
            : '<div class="flow-state"><h2>Tu próxima experiencia empieza aquí.</h2><p>Aún no tienes pedidos en esta cuenta.</p><a href="/productos.html" class="store-button">Explorar el catálogo</a></div>';
        document.getElementById('orders-show-all')?.addEventListener('click', () => { filtro.value = 'todos'; pintarPedidos(); });
        return;
    }
    container.innerHTML = lista.map(pedido => {
        const estado = estadoPedido(pedido.estado), pago = estadoPago(pedido);
        const formato = valor => formatoMoneda(valor, pedido.monedaCodigo || 'PEN');
        const items = Array.isArray(pedido.detalles) ? pedido.detalles : Array.isArray(pedido.items) ? pedido.items : [];
        const tieneDescuentoCupon = pedido.cuponCodigo && Number(pedido.descuentoPen || 0) > 0;
        const tieneDescuentoPuntos = Number(pedido.puntosCanjeados || 0) > 0;
        return `
            <details class="flow-order" id="pedido-${Number(pedido.id)}" data-id="${Number(pedido.id)}" ${abiertos.has(String(pedido.id)) ? 'open' : ''}>
                <summary>
                    <span><span class="flow-order-id">Pedido #${esc(pedido.id)} <span class="flow-badge" data-tone="${estado.tono}">${esc(estado.texto)}</span></span>
                        <span class="flow-order-meta">${esc(fechaPedido(pedido.fecha || pedido.fechaCreacion))} · ${esc(pago.texto)}</span>
                    </span>
                    <span class="flow-order-cost">${formato(pedido.total)}<small>Consultar progreso</small></span>
                </summary>
                <div class="flow-order-body">
                    ${timeline(pedido.estado)}
                    <p class="flow-note">Estado registrado por la tienda. Usa «Actualizar pedidos» para consultar cambios; no es un seguimiento de transporte en tiempo real.</p>
                    <dl class="flow-facts">
                        <div><dt>Entrega</dt><dd>${esc(pedido.direccionEnvio || 'Dirección no disponible')}</dd></div>
                        <div><dt>Teléfono de contacto</dt><dd>${esc(pedido.telefono || 'No disponible')}</dd></div>
                        <div><dt>Método de pago</dt><dd>${esc(pedido.metodoNombre || pedido.metodoPago || 'No disponible')}</dd></div>
                        <div><dt>Estado del pago</dt><dd><span class="flow-badge" data-tone="${pago.tono}">${esc(pago.texto)}</span><p class="flow-note">${esc(pago.detalle)}</p></dd></div>
                        ${pedido.referenciaComprobante ? `<div><dt>Referencia de pago</dt><dd>${esc(pedido.referenciaComprobante)}</dd></div>` : ''}
                    </dl>
                    <h3>Tu selección.</h3>
                    <div class="flow-order-items">${items.length ? items.map(item => `
                        <div class="flow-order-item"><span>${esc(item.productoNombre || item.nombre)}
                            <small>${Number(item.cantidad)} × ${formato(item.precioUnitario)}${Number(item.impuestoPorcentaje) > 0 ? ` · Impuesto ${esc(item.impuestoPorcentaje)}% incluido` : ''}</small>
                        </span><span>${formato(item.subtotal)}</span></div>`).join('') : '<p class="flow-note">No hay artículos disponibles para mostrar en este pedido.</p>'}</div>
                    ${tieneDescuentoCupon || tieneDescuentoPuntos ? `
                    <div style="margin: 0.75rem 0; padding: 0.75rem; background: var(--flow-surface-soft, #f8fafc); border-radius: 8px; font-size: 0.8125rem;">
                        ${tieneDescuentoCupon ? `
                        <div style="display:flex; justify-content:space-between; color:#047857; margin-bottom: 0.25rem;">
                            <span>Cupón aplicado (<strong>${esc(pedido.cuponCodigo)}</strong>)</span>
                            <strong>-S/ ${Number(pedido.descuentoPen).toFixed(2)}</strong>
                        </div>` : ''}
                        ${tieneDescuentoPuntos ? `
                        <div style="display:flex; justify-content:space-between; color:#b45309;">
                            <span>Puntos canjeados (<strong>${pedido.puntosCanjeados} pts</strong>)</span>
                            <strong>-S/ ${Number(pedido.descuentoPuntosPen || 0).toFixed(2)}</strong>
                        </div>` : ''}
                    </div>` : ''}
                    <div class="flow-actions"><a href="/informacion.html#chat" class="flow-link">Ayuda con este pedido</a><a href="/informacion.html#despachos" class="flow-link">Información de despacho</a></div>
                </div>
            </details>`;
    }).join('');
}

async function cargarPedidos() {
    if (cargando) return;
    cargando = true;
    container.setAttribute('aria-busy', 'true');
    refresh.disabled = true;
    refresh.textContent = 'Actualizando…';
    feedback.dataset.tone = '';
    feedback.textContent = '';
    try {
        const res = await api.get('/api/pedidos/mios');
        if (!res?.exito || !Array.isArray(res.datos)) throw new Error(res?.mensaje || 'No pudimos consultar tus pedidos.');
        pedidos = res.datos;
        if (!enlaceAplicado && buscado && /^[1-9]\d*$/.test(buscado)) {
            if (pedidos.some(p => String(p.id) === buscado)) abiertos.add(buscado);
            else feedback.textContent = 'No encontramos ese pedido en tu cuenta. Puedes consultar las compras disponibles debajo.';
        }
        pintarPedidos();
        const updatedEl = document.getElementById('orders-updated');
        if (updatedEl) updatedEl.textContent = `Última consulta: ${new Date().toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}.`;
        if (!enlaceAplicado && buscado) {
            await document.fonts.ready;
            document.getElementById(`pedido-${Number(buscado)}`)?.scrollIntoView({ block: 'start' });
        }
        enlaceAplicado = true;
    } catch (err) {
        feedback.dataset.tone = 'error';
        feedback.textContent = err.message || 'No se pudieron cargar los pedidos. Vuelve a intentarlo.';
        if (err.status === 401 || err.status === 403) {
            container.innerHTML = '<div class="flow-state"><h2>Vuelve a iniciar sesión.</h2><p>Necesitamos confirmar tu acceso para consultar tus compras.</p><a class="store-button" href="/login.html?redirect=%2Fmis-pedidos.html">Iniciar sesión</a></div>';
        } else if (!pedidos.length) {
            container.innerHTML = '<div class="flow-state"><h2>No pudimos consultar tus pedidos.</h2><p>Revisa tu conexión y pulsa «Actualizar pedidos» para intentarlo otra vez.</p></div>';
        }
    } finally {
        cargando = false;
        container.setAttribute('aria-busy', 'false');
        refresh.disabled = false;
        refresh.textContent = 'Actualizar pedidos';
    }
}

filtro.addEventListener('change', pintarPedidos);
refresh.addEventListener('click', () => {
    cargarPedidos();
    cargarBilletera().catch(() => {});
});

if (auth.protegerRutaAutenticada()) {
    await cargarPedidos();
    cargarBilletera().catch(() => {});
}
