import { api } from '/assets/js/api.js';
import { auth } from '/assets/js/auth.js';
import { ChatClient } from '/assets/js/chat.js';
import { renderAdminShell } from '/assets/js/ui/adminShell.js?v=2';
import { showToast } from '/assets/js/ui/toast.js';

const convsLista = document.getElementById('conversaciones-lista');
const btnRefrescar = document.getElementById('btn-refrescar-convs');
const messagesContainer = document.getElementById('admin-chat-messages');
const chatForm = document.getElementById('admin-chat-form');
const chatInput = document.getElementById('admin-chat-input');
const chatSend = document.getElementById('admin-chat-send');

const clienteNombre = document.getElementById('chat-cliente-nombre');
const clienteAvatar = document.getElementById('chat-cliente-avatar');
const clienteEstado = document.getElementById('chat-cliente-estado');
const headActions = document.getElementById('chat-head-actions');
const modoBadge = document.getElementById('chat-modo-badge');
const btnTomarChat = document.getElementById('btn-tomar-chat');
const btnDevolverBot = document.getElementById('btn-devolver-bot');

let chatClient = null;
let alertsClient = null;
let conversacionSeleccionada = null;
let seleccionSeq = 0;
let historyAbortController = null;
let autoRefreshTimer = null;

renderAdminShell('chat');

if (!auth.protegerRutaAdmin()) {
    // Redirige al login
} else {
    initChatAdmin();
}

function esc(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function etiquetaModo(modo) {
    if (modo === 'ESPERANDO_HUMANO') return 'En espera';
    if (modo === 'HUMANO_ACTIVO') return 'Asesor';
    return 'Asistente IA';
}

function claseModo(modo) {
    if (modo === 'ESPERANDO_HUMANO') return 'adm-modo-urgente';
    if (modo === 'HUMANO_ACTIVO') return 'adm-modo-humano';
    return 'adm-modo-bot';
}

function actualizarCabeceraModo(modo) {
    if (!conversacionSeleccionada) return;
    conversacionSeleccionada.modoAtencion = modo;
    headActions.hidden = false;
    modoBadge.textContent = etiquetaModo(modo);
    modoBadge.className = `adm-modo-badge ${claseModo(modo)}`;

    btnTomarChat.hidden = modo === 'HUMANO_ACTIVO';
    btnDevolverBot.hidden = modo === 'BOT';

    clienteEstado.textContent = `Sala #${conversacionSeleccionada.id} · ${conversacionSeleccionada.estado || 'Activa'} · ${etiquetaModo(modo)}`;
}

async function initChatAdmin() {
    cargarConversaciones();

    try {
        alertsClient = new ChatClient();
        await alertsClient.conectarAlertasAdmin((alerta) => {
            if (alerta && alerta.tipo === 'ESCALADO_HUMANO') {
                showToast(`${alerta.clienteNombre || 'Un cliente'} solicita atención de un asesor`, 'warning');
            }
            cargarConversaciones(true);
        });
    } catch (e) {
        console.warn('[admin-chat] no se pudo suscribir a alertas:', e);
    }

    btnRefrescar.addEventListener('click', () => {
        cargarConversaciones();
    });

    btnTomarChat.addEventListener('click', () => cambiarModoSeleccionada('HUMANO_ACTIVO'));
    btnDevolverBot.addEventListener('click', () => cambiarModoSeleccionada('BOT'));

    chatForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const texto = chatInput.value.trim();
        if (!texto || !chatClient || !conversacionSeleccionada) return;

        if (chatClient.enviarMensaje(texto)) {
            chatInput.value = '';
        }
    });

    autoRefreshTimer = setInterval(() => {
        if (document.hidden) return;
        cargarConversaciones(true);
    }, 25000);
}

async function cambiarModoSeleccionada(nuevoModo) {
    if (!conversacionSeleccionada) return;
    try {
        btnTomarChat.disabled = true;
        btnDevolverBot.disabled = true;
        const res = await api.request(`/api/chat/conversaciones/${conversacionSeleccionada.id}/modo`, {
            method: 'PUT',
            body: JSON.stringify({ modoAtencion: nuevoModo })
        });
        if (res && res.exito) {
            actualizarCabeceraModo(nuevoModo);
            cargarConversaciones(true);
            showToast(nuevoModo === 'BOT' ? 'Conversación devuelta al Asistente IA' : 'Control humano activado', 'success');
        }
    } catch (err) {
        showToast('No se pudo cambiar el modo de atención', 'error');
    } finally {
        btnTomarChat.disabled = false;
        btnDevolverBot.disabled = false;
    }
}

function pintarCargando() {
    convsLista.innerHTML = '<div class="adm-msg-placeholder">Cargando conversaciones…</div>';
}

function pintarError(mensaje) {
    convsLista.innerHTML = '';
    const wrapper = document.createElement('div');
    wrapper.className = 'adm-msg-placeholder';

    const titulo = document.createElement('div');
    titulo.className = 'adm-chat-error-titulo';
    titulo.textContent = 'No se pudo cargar el chat';

    const detalle = document.createElement('div');
    detalle.className = 'adm-chat-error-detalle';
    detalle.textContent = mensaje;

    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'adm-btn adm-btn-primary adm-btn-sm';
    btn.textContent = 'Reintentar';
    btn.addEventListener('click', () => cargarConversaciones());

    wrapper.appendChild(titulo);
    wrapper.appendChild(detalle);
    wrapper.appendChild(btn);
    convsLista.appendChild(wrapper);
}

async function cargarConversaciones(silencioso = false) {
    if (!silencioso) pintarCargando();

    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 12000);

    try {
        const res = await api.request('/api/chat/conversaciones', { method: 'GET', signal: ctrl.signal });

        if (!res || !res.exito) {
            throw new Error((res && res.mensaje) || 'Respuesta inválida del servidor');
        }

        const lista = Array.isArray(res.datos) ? res.datos : [];
        convsLista.innerHTML = '';

        if (lista.length === 0) {
            convsLista.innerHTML = '<div class="adm-msg-placeholder">Aún no hay conversaciones.<br/>Aparecerán aquí cuando un cliente abra el chat.</div>';
            return;
        }

        lista.forEach(conv => {
            const item = document.createElement('button');
            item.type = 'button';
            const sel = conversacionSeleccionada && conversacionSeleccionada.id === conv.id;
            const urgente = conv.modoAtencion === 'ESPERANDO_HUMANO';
            item.className = 'adm-conv' + (sel ? ' adm-sel' : '') + (urgente ? ' adm-conv-urgente' : '');
            item.dataset.convId = conv.id;

            const avatar = document.createElement('span');
            avatar.className = 'adm-conv-avatar';
            avatar.setAttribute('aria-hidden', 'true');
            avatar.textContent = conv.clienteNombre ? conv.clienteNombre.charAt(0).toUpperCase() : 'C';

            const body = document.createElement('span');
            body.className = 'adm-conv-body';

            const nameRow = document.createElement('span');
            nameRow.className = 'adm-conv-name-row';

            const nameSpan = document.createElement('span');
            nameSpan.className = 'adm-conv-name';
            nameSpan.textContent = conv.clienteNombre || 'Cliente';

            const pill = document.createElement('span');
            pill.className = `adm-modo-pill ${claseModo(conv.modoAtencion)}`;
            pill.textContent = etiquetaModo(conv.modoAtencion);

            nameRow.appendChild(nameSpan);
            nameRow.appendChild(pill);

            const prev = document.createElement('span');
            prev.className = 'adm-conv-prev';
            prev.textContent = conv.ultimoMensaje || 'Sin mensajes';

            body.appendChild(nameRow);
            body.appendChild(prev);

            item.appendChild(avatar);
            item.appendChild(body);
            item.addEventListener('click', () => seleccionarConversacion(conv));
            convsLista.appendChild(item);

            if (sel) {
                actualizarCabeceraModo(conv.modoAtencion || 'BOT');
            }
        });
    } catch (err) {
        console.error('[admin-chat] error cargando conversaciones:', err);
        if (silencioso) return;
        const msg = err && err.name === 'AbortError'
            ? 'La petición tardó demasiado (timeout 12 s). Revisa tu conexión.'
            : (err.message || 'Error desconocido');
        pintarError(msg);
    } finally {
        clearTimeout(timer);
    }
}

async function seleccionarConversacion(conv) {
    const seq = ++seleccionSeq;

    if (historyAbortController) {
        historyAbortController.abort();
    }
    historyAbortController = new AbortController();
    const signal = historyAbortController.signal;

    conversacionSeleccionada = conv;
    clienteNombre.textContent = conv.clienteNombre || 'Cliente';
    clienteAvatar.textContent = conv.clienteNombre ? conv.clienteNombre.charAt(0).toUpperCase() : 'C';
    actualizarCabeceraModo(conv.modoAtencion || 'BOT');

    document.querySelectorAll('#conversaciones-lista .adm-conv').forEach(el => {
        el.classList.toggle('adm-sel', String(el.dataset.convId) === String(conv.id));
    });

    chatInput.disabled = true;
    chatSend.disabled = true;

    if (chatClient) {
        chatClient.desconectar();
        chatClient = null;
    }

    messagesContainer.innerHTML = '<div class="adm-msg-placeholder">Cargando historial…</div>';

    try {
        const resHistorial = await api.request(`/api/chat/conversaciones/${conv.id}/mensajes`, {
            method: 'GET',
            signal
        });
        if (seq !== seleccionSeq) return;

        messagesContainer.innerHTML = '';
        const historial = resHistorial && resHistorial.datos ? resHistorial.datos : [];
        if (historial.length === 0) {
            messagesContainer.innerHTML = '<div class="adm-msg-placeholder">Sin mensajes todavía. Saluda al cliente para iniciar atención humana.</div>';
        } else {
            historial.forEach(m => agregarMensajeUI(m));
        }

        const nuevoClient = new ChatClient();
        await nuevoClient.conectar(
            conv.id,
            (nuevoMensaje) => {
                if (seq !== seleccionSeq) return;
                agregarMensajeUI(nuevoMensaje);
            },
            (eventoEstado) => {
                if (seq !== seleccionSeq) return;
                if (eventoEstado && eventoEstado.tipo === 'MODO_CAMBIADO') {
                    actualizarCabeceraModo(eventoEstado.modoAtencion);
                    cargarConversaciones(true);
                }
            },
            (errPayload) => {
                if (errPayload && errPayload.mensaje) {
                    showToast(errPayload.mensaje, 'warning');
                }
            }
        );

        if (seq !== seleccionSeq) {
            nuevoClient.desconectar();
            return;
        }

        chatClient = nuevoClient;
        chatInput.disabled = false;
        chatSend.disabled = false;
        chatInput.focus();
        cargarConversaciones(true);
    } catch (err) {
        if (err && err.name === 'AbortError') return;
        if (seq !== seleccionSeq) return;
        console.error('[admin-chat] error abriendo conversación:', err);
        messagesContainer.innerHTML = '';
        const errDiv = document.createElement('div');
        errDiv.className = 'adm-msg-placeholder adm-chat-error-linea';
        errDiv.textContent = `Error al abrir la conversación: ${err.message}`;
        messagesContainer.appendChild(errDiv);
        showToast('Error al conectar con la conversación', 'error');
    }
}

function agregarMensajeUI(m) {
    const user = auth.getUser();
    const esMio = Boolean(user && m.remitenteId === user.id);
    const esBot = Boolean(m.remitenteRol === 'ASISTENTE_IA');

    const row = document.createElement('div');
    row.className = `adm-row ${esMio ? 'mine' : 'theirs'}${esBot ? ' bot' : ''}`;

    const hora = m.fechaEnvio ? new Date(m.fechaEnvio).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
    const quien = esBot
        ? 'Electronix AI · Asistente'
        : (m.remitenteNombre ? `${m.remitenteNombre}${m.remitenteRol ? ' · ' + m.remitenteRol : ''}` : '');

    if (quien) {
        const meta = document.createElement('div');
        meta.className = 'adm-bubble-meta';
        meta.textContent = quien;
        row.appendChild(meta);
    }

    const bubble = document.createElement('div');
    bubble.className = 'adm-bubble';
    const lines = String(m.contenido ?? '').split('\n');
    lines.forEach((line, idx) => {
        if (idx > 0) bubble.appendChild(document.createElement('br'));
        bubble.appendChild(document.createTextNode(line));
    });
    row.appendChild(bubble);

    if (hora) {
        const timeEl = document.createElement('div');
        timeEl.className = 'adm-bubble-time';
        timeEl.textContent = hora;
        row.appendChild(timeEl);
    }

    const ph = messagesContainer.querySelector('.adm-msg-placeholder');
    if (ph) ph.remove();

    messagesContainer.appendChild(row);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}
