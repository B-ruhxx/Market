import { api } from '/assets/js/api.js';
import { auth } from '/assets/js/auth.js';
import { renderAdminShell } from '/assets/js/ui/adminShell.js';
import { showToast } from '/assets/js/ui/toast.js';
import { renderPager } from '/assets/js/ui/pager.js?v=2';

const $ = id => document.getElementById(id);
const esc = v => String(v ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');

let clientesData = [];
let listaFiltradaActual = [];
let filtroSegmentoActual = '';
let clienteActivoId = null;
let paginaActual = 1;
const TAMANO_PAGINA = 15;

renderAdminShell('crm-clientes');

if (!auth.protegerRutaAdmin()) {
    // Redirección por protección
} else if (!auth.tienePermiso('CRM_GESTIONAR')) {
    showToast('No tienes permiso para acceder al módulo CRM 360°', 'error');
    setTimeout(() => { window.location.href = '/admin/dashboard.html'; }, 1000);
} else {
    iniciar();
}

async function iniciar() {
    $('btn-refrescar-clientes')?.addEventListener('click', () => cargarClientes(filtroSegmentoActual));
    $('btn-exportar-csv')?.addEventListener('click', exportarCSV);

    $('input-buscar-cliente')?.addEventListener('input', e => {
        filtrarTablaLocal(e.target.value.trim().toLowerCase());
    });

    // Filtros por botón de segmento (Chips)
    document.querySelectorAll('.btn-filtro-seg').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.btn-filtro-seg').forEach(b => b.classList.remove('is-active'));
            btn.classList.add('is-active');

            filtroSegmentoActual = btn.dataset.segmento || '';
            cargarClientes(filtroSegmentoActual);
        });
    });

    // Pestañas del Modal 360°
    document.querySelectorAll('.crm-tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.crm-tab-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.crm-tab-content').forEach(c => c.classList.add('hidden'));

            btn.classList.add('active');
            const targetId = btn.dataset.tab;
            $(targetId)?.classList.remove('hidden');
        });
    });

    $('btn-cerrar-modal-360')?.addEventListener('click', cerrarModal360);
    $('form-crear-nota')?.addEventListener('submit', guardarNota);

    // Cargar si la URL trae ?id=X
    const params = new URLSearchParams(window.location.search);
    const idParam = params.get('id');

    await cargarClientes('');

    if (idParam) {
        const idNum = parseInt(idParam, 10);
        if (idNum > 0) abrirFicha360(idNum);
    }
}

async function cargarClientes(segmento) {
    const tbody = $('tbody-clientes');
    if (!tbody) return;

    try {
        const url = segmento ? `/api/admin/crm/clientes?segmento=${encodeURIComponent(segmento)}` : '/api/admin/crm/clientes';
        const res = await api.get(url);
        if (!res.exito || !Array.isArray(res.datos)) return;

        paginaActual = 1;
        clientesData = res.datos;
        renderizarTablaClientes(clientesData);
    } catch (e) {
        showToast('Error al cargar clientes: ' + e.message, 'error');
    }
}

function obtenerBadgeNivel(nivel) {
    const n = String(nivel || 'Bronce').trim();
    if (n.toLowerCase() === 'oro') {
        return '<span class="badge-tier badge-tier--oro">Oro</span>';
    } else if (n.toLowerCase() === 'plata') {
        return '<span class="badge-tier badge-tier--plata">Plata</span>';
    }
    return '<span class="badge-tier badge-tier--bronce">Bronce</span>';
}

function obtenerBadgeSegmento(segmento, label) {
    const s = String(segmento || '').toUpperCase();
    const txt = esc(label || segmento);
    if (s === 'VIP') {
        return `<span class="adm-badge b-vip">👑 ${txt}</span>`;
    } else if (s === 'ACTIVO') {
        return `<span class="adm-badge b-ok">⚡ ${txt}</span>`;
    } else if (s === 'EN_RIESGO') {
        return `<span class="adm-badge b-pendiente">⚠️ ${txt}</span>`;
    } else if (s === 'PROSPECTO') {
        return `<span class="adm-badge b-proceso">🌱 ${txt}</span>`;
    } else if (s === 'INACTIVO') {
        return `<span class="adm-badge b-off">💤 ${txt}</span>`;
    }
    return `<span class="adm-badge b-off">${txt}</span>`;
}

function renderizarTablaClientes(lista) {
    listaFiltradaActual = lista;
    const tbody = $('tbody-clientes');
    const pager = $('crm-pager');
    if (!tbody) return;

    if (!lista || lista.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="adm-empty">No se encontraron clientes para este criterio.</td></tr>';
        if (pager) pager.innerHTML = '';
        return;
    }

    const totalPaginas = Math.ceil(lista.length / TAMANO_PAGINA);
    if (paginaActual > totalPaginas) paginaActual = totalPaginas;
    if (paginaActual < 1) paginaActual = 1;

    const inicio = (paginaActual - 1) * TAMANO_PAGINA;
    const fin = inicio + TAMANO_PAGINA;
    const paginaItems = lista.slice(inicio, fin);

    tbody.innerHTML = paginaItems.map(c => {
        const ltv = Number(c.ltvPen || 0).toFixed(2);
        const dias = c.diasDesdeUltimaCompra != null ? `${c.diasDesdeUltimaCompra} d` : 'Nunca';
        const fechaUltima = c.fechaUltimaCompra
            ? new Date(c.fechaUltimaCompra).toLocaleDateString('es-PE', { day:'2-digit', month:'short', year:'numeric' })
            : '—';

        const initial = (c.nombre || c.email || 'U').charAt(0).toUpperCase();

        return `<tr>
            <td>
                <div class="adm-prod">
                    <span class="adm-avatar">${esc(initial)}</span>
                    <div>
                        <div class="adm-strong">${esc(c.nombre)}</div>
                        <div class="adm-sub">${esc(c.email)}</div>
                    </div>
                </div>
            </td>
            <td>${obtenerBadgeNivel(c.nivel)}</td>
            <td>${obtenerBadgeSegmento(c.segmento, c.segmentoLabel)}</td>
            <td class="num">
                <span class="adm-strong adm-total" style="font-size: 0.88rem;">S/ ${ltv}</span>
            </td>
            <td style="text-align: center;">
                <span class="adm-strong">${c.totalPedidos || 0}</span>
            </td>
            <td style="text-align: center;">
                <span class="adm-strong">${dias}</span>
                <div class="adm-sub">${fechaUltima}</div>
            </td>
            <td class="num">
                <span class="crm-pts-cell">${c.saldoPuntos || 0} <small>pts</small></span>
            </td>
            <td class="num adm-celda-num">
                <button type="button" class="btn-ver-360 adm-btn adm-btn-edit adm-btn-sm" data-id="${c.usuarioId}">
                    Ver 360° →
                </button>
            </td>
        </tr>`;
    }).join('');

    tbody.querySelectorAll('.btn-ver-360').forEach(btn => {
        btn.addEventListener('click', () => {
            const id = parseInt(btn.dataset.id, 10);
            abrirFicha360(id);
        });
    });

    if (pager) {
        renderPager(pager, {
            pagina: paginaActual,
            totalPaginas,
            onPage: p => {
                paginaActual = p;
                renderizarTablaClientes(listaFiltradaActual);
            }
        });
    }
}

function filtrarTablaLocal(query) {
    paginaActual = 1;
    if (!query) {
        renderizarTablaClientes(clientesData);
        return;
    }
    const filtrados = clientesData.filter(c =>
        (c.nombre && c.nombre.toLowerCase().includes(query)) ||
        (c.email && c.email.toLowerCase().includes(query))
    );
    renderizarTablaClientes(filtrados);
}

async function abrirFicha360(usuarioId) {
    clienteActivoId = usuarioId;

    try {
        const res = await api.get(`/api/admin/crm/clientes/${usuarioId}/360`);
        if (!res.exito || !res.datos) {
            showToast('No fue posible cargar la ficha 360°', 'error');
            return;
        }

        const d = res.datos;

        // Header info
        $('m360-nombre').textContent = d.nombre || 'Cliente #' + d.usuarioId;
        $('m360-avatar').textContent = (d.nombre ? d.nombre.charAt(0) : 'U').toUpperCase();
        $('m360-meta').textContent = `${d.email || ''} ${d.numeroDocumento ? '· ' + (d.tipoDocumento || 'DOC') + ' ' + d.numeroDocumento : ''} ${d.telefono ? '· Tel: ' + d.telefono : ''}`;

        // Badges
        const badgeNivel = $('m360-badge-nivel');
        const nivelStr = String(d.nivelActual || 'Bronce').trim();
        badgeNivel.textContent = nivelStr.toUpperCase();
        if (nivelStr.toLowerCase() === 'oro') {
            badgeNivel.className = 'badge-tier badge-tier--oro';
        } else if (nivelStr.toLowerCase() === 'plata') {
            badgeNivel.className = 'badge-tier badge-tier--plata';
        } else {
            badgeNivel.className = 'badge-tier badge-tier--bronce';
        }

        const badgeSeg = $('m360-badge-segmento');
        const seg = String(d.segmento || '').toUpperCase();
        badgeSeg.textContent = d.segmentoLabel || d.segmento || 'ACTIVO';
        if (seg === 'VIP') {
            badgeSeg.className = 'adm-badge b-vip';
        } else if (seg === 'ACTIVO') {
            badgeSeg.className = 'adm-badge b-ok';
        } else if (seg === 'EN_RIESGO') {
            badgeSeg.className = 'adm-badge b-pendiente';
        } else if (seg === 'PROSPECTO') {
            badgeSeg.className = 'adm-badge b-proceso';
        } else {
            badgeSeg.className = 'adm-badge b-off';
        }

        $('m360-badge-num-pedidos').textContent = d.totalPedidos || 0;
        $('m360-badge-num-notas').textContent = d.notas ? d.notas.length : 0;

        // Pestaña Resumen RFM
        $('rfm-ltv').textContent = `S/ ${Number(d.ltvPen || 0).toFixed(2)}`;
        $('rfm-ticket').textContent = `S/ ${Number(d.ticketPromedioPen || 0).toFixed(2)}`;
        $('rfm-recencia').textContent = d.diasDesdeUltimaCompra != null ? `${d.diasDesdeUltimaCompra} días` : 'Sin compras';
        $('rfm-saldo-puntos').textContent = `${d.saldoPuntos || 0} pts`;
        $('rfm-valor-puntos').textContent = `≈ S/ ${Number(d.valorEstimadoPuntosPen || 0).toFixed(2)}`;

        let diag = '';
        if (d.segmento === 'VIP') diag = 'Cliente de alto valor comercial. Se sugiere trato preferencial y atención prioritaria con beneficios exclusivos.';
        else if (d.segmento === 'ACTIVO') diag = 'Comprador habitual con actividad reciente en los últimos 30 días. Mantener fidelización constante.';
        else if (d.segmento === 'EN_RIESGO') diag = 'Cliente histórico con 2 o más compras pero sin pedidos en 31-90 días. Se recomienda enviar promoción o cupón de reactivación.';
        else if (d.segmento === 'PROSPECTO') diag = 'Usuario registrado que aún no concreta su primera compra. Ideal para cupón promocional de bienvenida.';
        else diag = 'Cliente inactivo por más de 90 días. Requiere campaña de reenganche comercial.';
        $('rfm-diagnostico').textContent = diag;

        // Pestaña Pedidos
        const tbodyPedidos = $('tbody-m360-pedidos');
        if (d.ultimosPedidos && d.ultimosPedidos.length > 0) {
            tbodyPedidos.innerHTML = d.ultimosPedidos.map(p => {
                const fecha = p.fecha ? new Date(p.fecha).toLocaleDateString('es-PE', { day:'2-digit', month:'short', year:'numeric' }) : '—';
                const total = Number(p.total || 0).toFixed(2);
                let badgeEst = '<span class="adm-badge b-ok">Entregado</span>';
                if (p.estado === 'PENDIENTE') badgeEst = '<span class="adm-badge b-pendiente">Pendiente</span>';
                else if (p.estado === 'EN_PROCESO') badgeEst = '<span class="adm-badge b-proceso">En Proceso</span>';
                else if (p.estado === 'ENVIADO') badgeEst = '<span class="adm-badge b-proceso">Enviado</span>';
                else if (p.estado === 'CANCELADO') badgeEst = '<span class="adm-badge b-cancelado">Cancelado</span>';

                return `<tr>
                    <td><span class="adm-id">#${p.id}</span></td>
                    <td class="adm-sub">${fecha}</td>
                    <td>${badgeEst}</td>
                    <td><span class="adm-sub">${esc(p.estadoPago || 'PENDIENTE')}</span></td>
                    <td class="num"><strong class="adm-total">S/ ${total}</strong></td>
                </tr>`;
            }).join('');
        } else {
            tbodyPedidos.innerHTML = '<tr><td colspan="5" class="adm-empty">Sin pedidos registrados para este cliente.</td></tr>';
        }

        // Pestaña Recompensas
        $('m360-cupones-usados').textContent = d.cuponesUsados || 0;
        $('m360-cupones-ahorro').textContent = `S/ ${Number(d.totalDescontadoCuponesPen || 0).toFixed(2)}`;
        $('m360-puntos-historicos').textContent = `${d.puntosHistoricos || 0} pts`;

        const tbodyPuntos = $('tbody-m360-puntos');
        if (d.ultimasTransacciones && d.ultimasTransacciones.length > 0) {
            tbodyPuntos.innerHTML = d.ultimasTransacciones.map(t => {
                const esPos = t.puntos > 0;
                const ptsCls = esPos ? 'color: #059669; font-weight: 800;' : 'color: #d97706; font-weight: 800;';
                const fecha = t.fecha ? new Date(t.fecha).toLocaleDateString('es-PE', { day:'2-digit', month:'short' }) : '—';
                return `<tr>
                    <td class="adm-sub">${fecha}</td>
                    <td><span class="adm-id" style="font-size: 0.72rem;">${esc(t.tipo)}</span></td>
                    <td><span class="adm-strong">${esc(t.descripcion || '—')}</span></td>
                    <td class="num" style="${ptsCls}">${esPos ? '+' : ''}${t.puntos} pts</td>
                    <td class="num"><strong class="adm-strong">${t.saldoResultante} pts</strong></td>
                </tr>`;
            }).join('');
        } else {
            tbodyPuntos.innerHTML = '<tr><td colspan="5" class="adm-empty">Sin movimientos de puntos registrados.</td></tr>';
        }

        // Pestaña Notas
        renderizarNotas(d.notas || []);

        // Abrir modal
        $('modal-360')?.classList.remove('hidden');
    } catch (e) {
        showToast('Error al abrir ficha 360°: ' + e.message, 'error');
    }
}

function renderizarNotas(notas) {
    const timeline = $('timeline-notas');
    if (!timeline) return;

    if (notas.length === 0) {
        timeline.innerHTML = '<p class="adm-empty">Sin notas registradas para este cliente.</p>';
        return;
    }

    timeline.innerHTML = notas.map(n => {
        const fecha = n.fecha ? new Date(n.fecha).toLocaleString('es-PE', { dateStyle:'short', timeStyle:'short' }) : '—';
        return `<div class="crm-note-card">
            <div class="crm-note-top">
                <span class="crm-note-author">
                    <svg width="13" height="13" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/></svg>
                    ${esc(n.adminNombre || 'Administrador')}
                </span>
                <span class="crm-note-date">${fecha}</span>
            </div>
            <p class="crm-note-text">${esc(n.nota)}</p>
        </div>`;
    }).join('');
}

async function guardarNota(e) {
    e.preventDefault();
    if (!clienteActivoId) return;

    const targetId = clienteActivoId;
    const textarea = $('input-nota-texto');
    const notaTexto = textarea?.value?.trim();
    if (!notaTexto) {
        showToast('Escribe el contenido de la nota', 'warning');
        return;
    }

    const btn = $('btn-guardar-nota');
    btn.disabled = true;

    try {
        const res = await api.post(`/api/admin/crm/clientes/${targetId}/notas`, { nota: notaTexto });
        if (res.exito) {
            showToast('Nota registrada correctamente', 'success');
            textarea.value = '';
            if (clienteActivoId === targetId) {
                const resNotas = await api.get(`/api/admin/crm/clientes/${targetId}/notas`);
                if (resNotas.exito && Array.isArray(resNotas.datos) && clienteActivoId === targetId) {
                    renderizarNotas(resNotas.datos);
                    $('m360-badge-num-notas').textContent = resNotas.datos.length;
                }
            }
        }
    } catch (e) {
        showToast('Error al registrar nota: ' + e.message, 'error');
    } finally {
        btn.disabled = false;
    }
}

function cerrarModal360() {
    $('modal-360')?.classList.add('hidden');
    clienteActivoId = null;
}

function sanitizarCeldaCsv(v) {
    let str = String(v ?? '');
    if (/^[=+\-@\t\r]/.test(str)) {
        str = "'" + str;
    }
    return `"${str.replace(/"/g, '""')}"`;
}

function exportarCSV() {
    if (!clientesData || clientesData.length === 0) {
        showToast('No hay datos de clientes para exportar', 'warning');
        return;
    }

    const encabezados = ['ID', 'Nombre', 'Email', 'Nivel VIP', 'Segmento RFM', 'LTV (S/)', 'Total Pedidos', 'Dias sin Compra', 'Puntos Disponibles', 'Ultima Compra'];
    const filas = clientesData.map(c => [
        c.usuarioId,
        sanitizarCeldaCsv(c.nombre),
        sanitizarCeldaCsv(c.email),
        sanitizarCeldaCsv(c.nivel || 'Bronce'),
        sanitizarCeldaCsv(c.segmentoLabel || c.segmento),
        Number(c.ltvPen || 0).toFixed(2),
        c.totalPedidos || 0,
        c.diasDesdeUltimaCompra != null ? c.diasDesdeUltimaCompra : '',
        c.saldoPuntos || 0,
        sanitizarCeldaCsv(c.fechaUltimaCompra ? c.fechaUltimaCompra.substring(0, 10) : '')
    ]);

    const csvContent = '\uFEFF' + [encabezados.join(','), ...filas.map(f => f.join(','))].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `crm_clientes_360_${filtroSegmentoActual || 'todos'}_${new Date().toISOString().substring(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    showToast('Archivo CSV generado y descargado exitosamente', 'success');
}
