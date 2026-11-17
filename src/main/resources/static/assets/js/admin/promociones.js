import { api } from '../api.js';
import { auth } from '../auth.js';
import { renderAdminShell } from '../ui/adminShell.js';
import { showToast } from '../ui/toast.js';
import { formatoMoneda } from '../money.js';

renderAdminShell('promociones');

const $ = id => document.getElementById(id);
const esc = v => String(v ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');

if (auth.protegerRutaAutenticada()) {
    if (!auth.tienePermiso('CRM_GESTIONAR')) {
        showToast('No tienes permisos para gestionar promociones y cupones CRM', 'error');
        setTimeout(() => location.href = auth.inicioAdmin(), 1500);
    } else {
        iniciar();
    }
}

let cupones = [];

function iniciar() {
    $('btn-nuevo-cupon')?.addEventListener('click', abrirModalCrear);
    $('cupon-modal-close')?.addEventListener('click', cerrarModalCrear);
    $('btn-cupon-cancelar')?.addEventListener('click', cerrarModalCrear);
    $('cupon-form')?.addEventListener('submit', guardarCupon);

    $('usos-modal-close')?.addEventListener('click', cerrarModalUsos);
    $('btn-usos-cerrar')?.addEventListener('click', cerrarModalUsos);

    cargarCupones();
}

async function cargarCupones() {
    try {
        const res = await api.get('/api/admin/crm/cupones');
        cupones = res.datos || [];
        renderizarTabla();
        renderizarEstadisticas();
    } catch (e) {
        showToast(e.message || 'Error al cargar cupones', 'error');
        $('cupones-table-body').innerHTML = `<tr><td colspan="7" class="adm-err">${esc(e.message)}</td></tr>`;
    }
}

function renderizarEstadisticas() {
    const total = cupones.length;
    const activos = cupones.filter(c => c.activo && c.vigente && !c.agotado).length;
    const totalUsos = cupones.reduce((acc, c) => acc + (c.usosActuales || 0), 0);

    if ($('stat-total')) $('stat-total').textContent = total;
    if ($('stat-activos')) $('stat-activos').textContent = activos;
    if ($('stat-usos')) $('stat-usos').textContent = totalUsos;
}

function renderizarTabla() {
    const tbody = $('cupones-table-body');
    if (!tbody) return;

    if (!cupones.length) {
        tbody.innerHTML = `<tr><td colspan="7" class="adm-empty">No hay cupones registrados. Haz clic en "Crear nuevo cupón" para empezar.</td></tr>`;
        return;
    }

    tbody.innerHTML = cupones.map(c => {
        let badgeEstado = '<span class="adm-badge b-ok">Activo</span>';
        if (!c.activo) {
            badgeEstado = '<span class="adm-badge b-off">Pausado</span>';
        } else if (c.agotado) {
            badgeEstado = '<span class="adm-badge b-cancelado">Agotado</span>';
        } else if (!c.vigente) {
            badgeEstado = '<span class="adm-badge b-pendiente">Expirado</span>';
        }

        let tipoHtml = '';
        if (c.tipoDescuento === 'PORCENTAJE') {
            tipoHtml = `<span class="promo-discount-badge">${c.valor}% OFF</span>`;
            if (c.topeDescuentoPen) {
                tipoHtml += `<div class="adm-sub">Tope: ${esc(formatoMoneda(c.topeDescuentoPen, 'PEN'))}</div>`;
            }
        } else if (c.tipoDescuento === 'MONTO_FIJO') {
            tipoHtml = `<span class="promo-discount-badge fijo">${esc(formatoMoneda(c.valor, 'PEN'))} FIJO</span>`;
        } else {
            tipoHtml = `<span class="promo-discount-badge">Envío Gratis</span>`;
        }

        const minCompra = Number(c.montoMinimoPen) > 0 ? `Mínimo: ${esc(formatoMoneda(c.montoMinimoPen, 'PEN'))}` : 'Sin mínimo de compra';
        const limiteUser = `Máx ${c.limitePorUsuario} / cliente`;
        const combinarPts = c.permiteCombinarPuntos ? 'Combinable con puntos' : 'Solo cupón';

        const usosTxt = c.limiteUsosTotal ? `${c.usosActuales} / ${c.limiteUsosTotal}` : `${c.usosActuales} / ∞`;

        const inicio = new Date(c.fechaInicio).toLocaleDateString('es-PE', { day:'2-digit', month:'short', year:'numeric' });
        const fin = new Date(c.fechaFin).toLocaleDateString('es-PE', { day:'2-digit', month:'short', year:'numeric' });

        return `
            <tr>
                <td>
                    <span class="promo-code-badge">
                        <svg width="12" height="12" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z"/></svg>
                        ${esc(c.codigo)}
                    </span>
                    ${c.descripcion ? `<div class="adm-sub">${esc(c.descripcion)}</div>` : ''}
                </td>
                <td>${tipoHtml}</td>
                <td>
                    <div class="promo-rules">
                        <span class="promo-rule-highlight">${esc(minCompra)}</span>
                        <span class="promo-rule-sub">${esc(limiteUser)} · ${esc(combinarPts)}</span>
                    </div>
                </td>
                <td style="text-align: center;">
                    <button type="button" class="promo-canjes-btn btn-ver-usos" data-id="${c.id}" data-codigo="${esc(c.codigo)}">
                        <span>${esc(usosTxt)} canjes</span>
                        <svg width="12" height="12" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/></svg>
                    </button>
                </td>
                <td>
                    <span class="adm-strong" style="font-size: 0.74rem;">${inicio}</span>
                    <div class="adm-sub">hasta ${fin}</div>
                </td>
                <td>${badgeEstado}</td>
                <td class="num adm-celda-num">
                    <div class="promo-actions-wrap">
                        <button type="button" class="adm-btn adm-btn-sm ${c.activo ? 'adm-btn-ghost' : 'adm-btn-primary'} btn-toggle-estado" data-id="${c.id}" data-activo="${c.activo}">
                            ${c.activo ? 'Pausar' : 'Activar'}
                        </button>
                        <button type="button" class="adm-btn adm-btn-sm adm-btn-del btn-eliminar-cupon" data-id="${c.id}" data-codigo="${esc(c.codigo)}" title="Eliminar cupón">
                            Eliminar
                        </button>
                    </div>
                </td>
            </tr>
        `;
    }).join('');

    // Event listeners
    tbody.querySelectorAll('.btn-toggle-estado').forEach(btn => {
        btn.addEventListener('click', () => toggleEstadoCupon(btn.dataset.id, btn.dataset.activo === 'true'));
    });
    tbody.querySelectorAll('.btn-eliminar-cupon').forEach(btn => {
        btn.addEventListener('click', () => eliminarCupon(btn.dataset.id, btn.dataset.codigo));
    });
    tbody.querySelectorAll('.btn-ver-usos').forEach(btn => {
        btn.addEventListener('click', () => verUsosCupon(btn.dataset.id, btn.dataset.codigo));
    });
}

function abrirModalCrear() {
    $('cupon-form').reset();
    $('cupon-codigo').value = '';
    const ahora = new Date();
    const enUnMes = new Date();
    enUnMes.setDate(ahora.getDate() + 30);

    const pad = n => String(n).padStart(2, '0');
    const toInputDate = d => `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;

    $('cupon-fecha-inicio').value = toInputDate(ahora);
    $('cupon-fecha-fin').value = toInputDate(enUnMes);
    $('cupon-modal').classList.remove('hidden');
}

function cerrarModalCrear() {
    $('cupon-modal').classList.add('hidden');
}

async function guardarCupon(e) {
    e.preventDefault();
    const codigo = $('cupon-codigo').value.trim().toUpperCase();
    if (!codigo) {
        showToast('Ingresa un código para el cupón', 'warning');
        return;
    }

    const tipo = $('cupon-tipo').value;
    const valor = parseFloat($('cupon-valor').value);
    if (isNaN(valor) || valor <= 0) {
        showToast('El valor del descuento debe ser mayor a 0', 'warning');
        return;
    }

    const payload = {
        codigo,
        descripcion: $('cupon-descripcion').value.trim() || null,
        tipoDescuento: tipo,
        valor,
        montoMinimoPen: parseFloat($('cupon-minimo').value) || 0,
        topeDescuentoPen: parseFloat($('cupon-tope').value) || null,
        limiteUsosTotal: parseInt($('cupon-limite-total').value, 10) || null,
        limitePorUsuario: parseInt($('cupon-limite-usuario').value, 10) || 1,
        permiteCombinarPuntos: $('cupon-combinar-puntos').checked,
        fechaInicio: $('cupon-fecha-inicio').value,
        fechaFin: $('cupon-fecha-fin').value,
        activo: true
    };

    $('btn-cupon-guardar').disabled = true;
    try {
        await api.post('/api/admin/crm/cupones', payload);
        showToast(`Cupón '${codigo}' creado exitosamente`, 'success');
        cerrarModalCrear();
        await cargarCupones();
    } catch (err) {
        showToast(err.message || 'Error al guardar cupón', 'error');
    } finally {
        $('btn-cupon-guardar').disabled = false;
    }
}

async function toggleEstadoCupon(id, estadoActual) {
    try {
        await api.patch(`/api/admin/crm/cupones/${id}/estado`, { activo: !estadoActual });
        showToast(`Cupón ${!estadoActual ? 'activado' : 'pausado'}`, 'success');
        await cargarCupones();
    } catch (err) {
        showToast(err.message || 'Error al cambiar estado', 'error');
    }
}

async function eliminarCupon(id, codigo) {
    if (!confirm(`¿Estás seguro de eliminar el cupón '${codigo}'?`)) return;
    try {
        await api.delete(`/api/admin/crm/cupones/${id}`);
        showToast(`Cupón '${codigo}' eliminado`, 'success');
        await cargarCupones();
    } catch (err) {
        showToast(err.message || 'Error al eliminar cupón', 'error');
    }
}

async function verUsosCupon(id, codigo) {
    $('usos-modal-title').textContent = `Historial de Usos: ${codigo}`;
    const tbody = $('usos-table-body');
    tbody.innerHTML = `<tr><td colspan="5" class="adm-empty">Cargando usos…</td></tr>`;
    $('usos-modal').classList.remove('hidden');

    try {
        const res = await api.get(`/api/admin/crm/cupones/${id}/usos`);
        const usos = res.datos || [];
        if (!usos.length) {
            tbody.innerHTML = `<tr><td colspan="5" class="adm-empty">Este cupón aún no ha sido utilizado por ningún cliente.</td></tr>`;
            return;
        }

        tbody.innerHTML = usos.map(u => {
            const initial = (u.usuarioNombre || 'U').charAt(0).toUpperCase();
            const fecha = u.fechaUso ? new Date(u.fechaUso).toLocaleString('es-PE', { dateStyle:'short', timeStyle:'short' }) : '—';
            return `<tr>
                <td>
                    <div class="adm-prod">
                        <span class="adm-avatar">${esc(initial)}</span>
                        <div>
                            <div class="adm-strong">${esc(u.usuarioNombre || 'Usuario')}</div>
                            <div class="adm-sub">${esc(u.usuarioEmail || '—')}</div>
                        </div>
                    </div>
                </td>
                <td><span class="adm-id">#${esc(u.pedidoId)}</span></td>
                <td class="num"><strong class="adm-total" style="color: #059669;">${esc(formatoMoneda(u.descuentoAplicadoPen, 'PEN'))}</strong></td>
                <td class="adm-sub">${fecha}</td>
                <td>
                    ${u.activo ? '<span class="adm-badge b-ok">Efectivo</span>' : '<span class="adm-badge b-off">Revertido</span>'}
                </td>
            </tr>`;
        }).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="5" class="adm-err">${esc(err.message)}</td></tr>`;
    }
}

function cerrarModalUsos() {
    $('usos-modal').classList.add('hidden');
}
