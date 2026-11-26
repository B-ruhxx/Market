// chat.js: cliente STOMP minimalista del chat en tiempo real (Asistente IA + Soporte humano)

import { api } from './api.js';
import { auth } from './auth.js';
import { showToast } from './ui/toast.js';

// Carga dinámica local (sin CDN externo): inyecta un <script> solo si el global no existe aún
function _loadScript(src) {
    return new Promise((resolve, reject) => {
        if (document.querySelector(`script[src="${src}"]`)) { resolve(); return; }
        const s = document.createElement('script');
        s.src = src;
        s.onload = resolve;
        s.onerror = () => reject(new Error(`No se pudo cargar: ${src}`));
        document.head.appendChild(s);
    });
}

async function _ensureWebSocketLibs() {
    if (typeof SockJS === 'undefined') {
        await _loadScript('/assets/js/vendor/sockjs.min.js');
    }
    if (typeof Stomp === 'undefined') {
        await _loadScript('/assets/js/vendor/stomp.min.js');
    }
}

export class ChatClient {
    constructor() {
        this.stompClient = null;
        this.socket = null;
        this.conversacionId = null;
        this.subscription = null;
        this.estadoSubscription = null;
        this.errorSubscription = null;
        this.adminAlertsSubscription = null;
        this.onMessageReceivedCallback = null;
        this.onEstadoReceivedCallback = null;
        this.onErrorReceivedCallback = null;
        this.onAdminAlertReceivedCallback = null;
        this._manualDisconnect = false;
        this._reconnectAttempts = 0;
        this._reconnectTimer = null;
        this._modoConexion = null; // 'SALA' | 'ALERTS'
    }

    _esErrorAuthDefinitivo(error) {
        const txt = String(error && (error.headers?.message || error.message || error) || '').toLowerCase();
        return txt.includes('sesión') || txt.includes('sesion')
                || txt.includes('permiso') || txt.includes('inactiva')
                || txt.includes('401') || txt.includes('403') || txt.includes('accessdenied');
    }

    _programarReconexion(error) {
        if (this._manualDisconnect || this._esErrorAuthDefinitivo(error)) {
            return;
        }
        if (!api.getToken()) {
            return;
        }
        if (this._reconnectTimer) {
            clearTimeout(this._reconnectTimer);
        }
        this._reconnectAttempts = Math.min(this._reconnectAttempts + 1, 6);
        const baseMs = Math.min(1000 * Math.pow(2, this._reconnectAttempts - 1), 20000);
        const jitterMs = Math.floor(Math.random() * 500);
        this._reconnectTimer = setTimeout(() => {
            this._reconnectTimer = null;
            if (this._manualDisconnect) return;
            if (this._modoConexion === 'SALA' && this.conversacionId) {
                this.conectar(
                    this.conversacionId,
                    this.onMessageReceivedCallback,
                    this.onEstadoReceivedCallback,
                    this.onErrorReceivedCallback
                ).catch(() => {});
            } else if (this._modoConexion === 'ALERTS' && this.onAdminAlertReceivedCallback) {
                this.conectarAlertasAdmin(this.onAdminAlertReceivedCallback).catch(() => {});
            }
        }, baseMs + jitterMs);
    }

    async conectar(conversacionId, onMessageReceived, onEstadoReceived = null, onErrorReceived = null) {
        this._manualDisconnect = false;
        this._modoConexion = 'SALA';
        this.conversacionId = conversacionId;
        this.onMessageReceivedCallback = onMessageReceived;
        this.onEstadoReceivedCallback = onEstadoReceived;
        this.onErrorReceivedCallback = onErrorReceived;

        await _ensureWebSocketLibs();

        return new Promise((resolve, reject) => {
            if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
                reject(new Error('Librerías WebSocket no disponibles'));
                return;
            }

            if (this.socket && this.socket.readyState <= 1) {
                try { this.socket.close(); } catch (_) {}
            }

            const socket = new SockJS('/ws-chat');
            this.socket = socket;
            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = null;

            const headers = {};
            const token = api.getToken();
            if (token) headers['Authorization'] = 'Bearer ' + token;

            let settled = false;
            this.stompClient.connect(headers, (frame) => {
                settled = true;
                this._reconnectAttempts = 0;

                const topic = `/topic/chat/${this.conversacionId}`;
                this.subscription = this.stompClient.subscribe(topic, (mensajeFrame) => {
                    try {
                        const mensaje = JSON.parse(mensajeFrame.body);
                        if (this.onMessageReceivedCallback) {
                            this.onMessageReceivedCallback(mensaje);
                        }
                    } catch (e) {
                        console.error('Error al parsear mensaje de chat:', e);
                    }
                });

                const topicEstado = `/topic/chat/${this.conversacionId}/estado`;
                this.estadoSubscription = this.stompClient.subscribe(topicEstado, (estadoFrame) => {
                    try {
                        const evento = JSON.parse(estadoFrame.body);
                        if (this.onEstadoReceivedCallback) {
                            this.onEstadoReceivedCallback(evento);
                        }
                    } catch (e) {
                        console.error('Error al parsear estado de chat:', e);
                    }
                });

                this.errorSubscription = this.stompClient.subscribe('/user/queue/errors', (errFrame) => {
                    try {
                        const err = JSON.parse(errFrame.body);
                        if (this.onErrorReceivedCallback) {
                            this.onErrorReceivedCallback(err);
                        } else if (err && err.mensaje) {
                            showToast(err.mensaje, 'warning');
                        }
                    } catch (e) {
                        console.error('Error al parsear notificación de error STOMP:', e);
                    }
                });

                resolve(frame);
            }, (error) => {
                console.warn('[WebSocket] Desconexión o error:', error);
                this._programarReconexion(error);
                if (!settled) {
                    settled = true;
                    reject(error);
                }
            });
        });
    }

    async conectarAlertasAdmin(onAlertReceived) {
        this._manualDisconnect = false;
        this._modoConexion = 'ALERTS';
        this.onAdminAlertReceivedCallback = onAlertReceived;

        await _ensureWebSocketLibs();
        return new Promise((resolve, reject) => {
            if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
                reject(new Error('Librerías WebSocket no disponibles'));
                return;
            }
            if (this.socket && this.socket.readyState <= 1) {
                try { this.socket.close(); } catch (_) {}
            }

            const socket = new SockJS('/ws-chat');
            this.socket = socket;
            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = null;
            const headers = {};
            const token = api.getToken();
            if (token) headers['Authorization'] = 'Bearer ' + token;

            let settled = false;
            this.stompClient.connect(headers, (frame) => {
                settled = true;
                this._reconnectAttempts = 0;

                this.adminAlertsSubscription = this.stompClient.subscribe('/topic/admin/chat-alerts', (alertFrame) => {
                    try {
                        const alert = JSON.parse(alertFrame.body);
                        if (onAlertReceived) onAlertReceived(alert);
                    } catch (e) {
                        console.error('Error al parsear alerta admin chat:', e);
                    }
                });
                resolve(frame);
            }, (error) => {
                this._programarReconexion(error);
                if (!settled) {
                    settled = true;
                    reject(error);
                }
            });
        });
    }

    enviarMensaje(contenido) {
        if (!this.stompClient || !this.stompClient.connected) {
            showToast('El chat no está conectado en este momento', 'error');
            return false;
        }

        const usuario = auth.getUser();
        if (!usuario) {
            showToast('Debes iniciar sesión para chatear', 'warning');
            return false;
        }

        const payload = {
            conversacionId: this.conversacionId,
            remitenteId: usuario.id,
            contenido: contenido.trim()
        };

        this.stompClient.send('/app/chat.enviar', {}, JSON.stringify(payload));
        return true;
    }

    desconectar() {
        this._manualDisconnect = true;
        if (this._reconnectTimer) {
            clearTimeout(this._reconnectTimer);
            this._reconnectTimer = null;
        }
        if (this.subscription) {
            try { this.subscription.unsubscribe(); } catch (_) {}
            this.subscription = null;
        }
        if (this.estadoSubscription) {
            try { this.estadoSubscription.unsubscribe(); } catch (_) {}
            this.estadoSubscription = null;
        }
        if (this.errorSubscription) {
            try { this.errorSubscription.unsubscribe(); } catch (_) {}
            this.errorSubscription = null;
        }
        if (this.adminAlertsSubscription) {
            try { this.adminAlertsSubscription.unsubscribe(); } catch (_) {}
            this.adminAlertsSubscription = null;
        }
        if (this.stompClient && this.stompClient.connected) {
            try { this.stompClient.disconnect(); } catch (_) {}
        }
        if (this.socket && this.socket.readyState <= 1) {
            try { this.socket.close(); } catch (_) {}
        }
        this.socket = null;
        this.stompClient = null;
    }
}

// Widget flotante de asistencia y soporte
export function initFloatingChat() {
    if (document.getElementById('floating-chat-container')) return;
    if (window.location.pathname.includes('/admin/') || window.location.pathname === '/login.html') return;

    const widgetHTML = `
        <div id="floating-chat-container" class="elx-chat-float">
            <button id="chat-toggle-btn" type="button" class="elx-chat-toggle" aria-label="Abrir asistente de compras y soporte" aria-expanded="false" aria-controls="chat-window">
                <svg width="24" height="24" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"></path>
                </svg>
            </button>

            <div id="chat-window" class="elx-chat-window elx-hidden" role="dialog" aria-labelledby="chat-widget-title">
                <div class="elx-chat-header">
                    <div class="elx-chat-header-info">
                        <span id="chat-status-dot" class="elx-chat-status-dot" aria-hidden="true"></span>
                        <div class="elx-chat-head-text">
                            <h3 id="chat-widget-title" class="elx-chat-title">Asistente Electronix</h3>
                            <p id="chat-modo-subtitle" class="elx-chat-subtitle">Asistente IA · En línea</p>
                        </div>
                    </div>
                    <div class="elx-chat-header-actions">
                        <button id="chat-escalar-btn" type="button" class="elx-chat-escalar-btn">
                            <svg width="13" height="13" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path>
                            </svg>
                            <span>Asesor</span>
                        </button>
                        <button id="chat-close-btn" type="button" class="elx-chat-close" aria-label="Cerrar chat">
                            <svg width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M6 18L18 6M6 6l12 12"></path></svg>
                        </button>
                    </div>
                </div>

                <div id="chat-messages" class="elx-chat-messages" aria-live="polite" aria-relevant="additions">
                    <div class="elx-chat-hint">Iniciando conversación...</div>
                </div>

                <div id="chat-typing-indicator" class="elx-chat-typing elx-hidden" aria-live="polite">
                    <span class="elx-typing-dots" aria-hidden="true"><span></span><span></span><span></span></span>
                    <span>Electronix AI está escribiendo</span>
                </div>

                <div id="chat-quick-chips" class="elx-chat-chips" aria-label="Sugerencias rápidas">
                    <button type="button" class="elx-chat-chip" data-msg="¿Qué laptops o productos en oferta me recomiendas?">Recomendar equipo</button>
                    <button type="button" class="elx-chat-chip" data-msg="¿Cuál es el estado de mi último pedido?">Mis pedidos</button>
                    <button type="button" class="elx-chat-chip" data-msg="¿Cuántos puntos tengo y qué promociones están disponibles?">Puntos y cupones</button>
                </div>

                <div class="elx-chat-footer">
                    <form id="chat-form" class="elx-chat-form">
                        <label class="elx-visually-hidden" for="chat-input">Mensaje para el asistente</label>
                        <input id="chat-input" type="text" placeholder="Escribe tu consulta..." class="elx-chat-input" autocomplete="off" maxlength="1000" />
                        <button type="submit" class="elx-chat-send" aria-label="Enviar mensaje">
                            <svg width="18" height="18" fill="none" stroke="currentColor" viewBox="-3 3 24 24" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" transform="rotate(45 12 12)" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"></path></svg>
                        </button>
                    </form>
                </div>
            </div>
        </div>
    `;

    document.body.insertAdjacentHTML('beforeend', widgetHTML);

    const toggleBtn = document.getElementById('chat-toggle-btn');
    const closeBtn = document.getElementById('chat-close-btn');
    const escalarBtn = document.getElementById('chat-escalar-btn');
    const chatWindow = document.getElementById('chat-window');
    const chatForm = document.getElementById('chat-form');
    const chatInput = document.getElementById('chat-input');
    const messagesContainer = document.getElementById('chat-messages');
    const typingIndicator = document.getElementById('chat-typing-indicator');
    const modoSubtitle = document.getElementById('chat-modo-subtitle');
    const statusDot = document.getElementById('chat-status-dot');
    const quickChips = document.getElementById('chat-quick-chips');

    let chatClient = null;
    let isConnecting = false;
    let conversacionActiva = null;

    function actualizarEstadoVisualModo(modoAtencion, iaDisponible = true) {
        statusDot.classList.remove('is-waiting', 'is-human');
        if (modoAtencion === 'ESPERANDO_HUMANO') {
            modoSubtitle.textContent = 'Esperando asesor humano';
            statusDot.classList.add('is-waiting');
            escalarBtn.classList.add('elx-hidden');
            if (quickChips) quickChips.classList.add('elx-hidden');
        } else if (modoAtencion === 'HUMANO_ACTIVO') {
            modoSubtitle.textContent = 'Atendido por un asesor';
            statusDot.classList.add('is-human');
            escalarBtn.classList.add('elx-hidden');
            if (quickChips) quickChips.classList.add('elx-hidden');
        } else {
            modoSubtitle.textContent = iaDisponible ? 'Asistente IA · En línea' : 'Soporte en línea';
            escalarBtn.classList.toggle('elx-hidden', !iaDisponible);
            if (quickChips) quickChips.classList.toggle('elx-hidden', !iaDisponible);
        }
    }

    function cerrarChat() {
        chatWindow.classList.add('elx-hidden');
        const container = document.getElementById('floating-chat-container');
        if (container) container.classList.remove('elx-chat-open');
        toggleBtn.setAttribute('aria-expanded', 'false');
        toggleBtn.focus();
    }

    toggleBtn.addEventListener('click', async () => {
        if (!auth.isAuthenticated()) {
            showToast('Inicia sesión para chatear con el asistente', 'warning');
            setTimeout(() => {
                window.location.href = '/login.html?redirect=' + encodeURIComponent(window.location.pathname);
            }, 800);
            return;
        }

        const isHidden = chatWindow.classList.toggle('elx-hidden');
        const container = document.getElementById('floating-chat-container');
        if (container) container.classList.toggle('elx-chat-open', !isHidden);
        toggleBtn.setAttribute('aria-expanded', String(!isHidden));

        if (!isHidden) {
            chatInput.focus();
        }

        if (!isHidden && !chatClient && !isConnecting) {
            isConnecting = true;
            try {
                const res = await api.get('/api/chat/mi-conversacion');
                if (res.exito && res.datos) {
                    conversacionActiva = res.datos;
                    const iaDisp = conversacionActiva.iaDisponible !== false;
                    actualizarEstadoVisualModo(conversacionActiva.modoAtencion, iaDisp);

                    const nuevoClient = new ChatClient();

                    const historialRes = await api.get(`/api/chat/conversaciones/${conversacionActiva.id}/mensajes`);
                    messagesContainer.innerHTML = '';
                    if (historialRes.exito && historialRes.datos && historialRes.datos.length > 0) {
                        historialRes.datos.forEach(m => agregarBurbujaMensaje(m, messagesContainer));
                    } else {
                        const hint = document.createElement('div');
                        hint.className = 'elx-chat-hint';
                        hint.textContent = iaDisp
                            ? 'Hola, soy Electronix AI. Consulta por productos, pedidos o solicita un asesor.'
                            : 'Hola. Escribe tu consulta y nuestro equipo te atenderá en breve.';
                        messagesContainer.appendChild(hint);
                    }

                    await nuevoClient.conectar(
                        conversacionActiva.id,
                        (nuevoMensaje) => {
                            const hint = messagesContainer.querySelector('.elx-chat-hint');
                            if (hint) hint.remove();
                            agregarBurbujaMensaje(nuevoMensaje, messagesContainer);
                        },
                        (eventoEstado) => {
                            if (!eventoEstado) return;
                            if (eventoEstado.tipo === 'TYPING') {
                                typingIndicator.classList.toggle('elx-hidden', !eventoEstado.escribiendo);
                                messagesContainer.scrollTop = messagesContainer.scrollHeight;
                            } else if (eventoEstado.tipo === 'MODO_CAMBIADO') {
                                const disp = conversacionActiva ? (conversacionActiva.iaDisponible !== false) : true;
                                actualizarEstadoVisualModo(eventoEstado.modoAtencion, disp);
                            }
                        },
                        (errPayload) => {
                            if (errPayload && errPayload.mensaje) {
                                showToast(errPayload.mensaje, 'warning');
                            }
                        }
                    );

                    chatClient = nuevoClient;
                }
            } catch (err) {
                console.error('Error al abrir chat flotante:', err);
                chatClient = null;
                messagesContainer.innerHTML = '';
                const errEl = document.createElement('div');
                errEl.className = 'elx-chat-error';
                errEl.textContent = 'No se pudo conectar al chat. Cierra e intenta nuevamente.';
                messagesContainer.appendChild(errEl);
            } finally {
                isConnecting = false;
            }
        }
    });

    escalarBtn.addEventListener('click', async () => {
        if (!conversacionActiva) return;
        try {
            escalarBtn.disabled = true;
            const res = await api.request(`/api/chat/conversaciones/${conversacionActiva.id}/modo`, {
                method: 'PUT',
                body: JSON.stringify({ modoAtencion: 'ESPERANDO_HUMANO' })
            });
            if (res && res.exito) {
                actualizarEstadoVisualModo('ESPERANDO_HUMANO', conversacionActiva.iaDisponible !== false);
                showToast('Solicitaste un asesor humano. Te atenderemos pronto.', 'info');
            }
        } catch (err) {
            showToast('No se pudo solicitar el traspaso', 'error');
        } finally {
            escalarBtn.disabled = false;
        }
    });

    if (quickChips) {
        quickChips.addEventListener('click', (e) => {
            const btn = e.target.closest('.elx-chat-chip');
            if (!btn || !chatClient) return;
            const msg = btn.dataset.msg;
            if (msg) chatClient.enviarMensaje(msg);
        });
    }

    closeBtn.addEventListener('click', cerrarChat);

    chatWindow.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            cerrarChat();
        }
    });

    chatForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const texto = chatInput.value.trim();
        if (!texto || !chatClient) return;

        if (chatClient.enviarMensaje(texto)) {
            chatInput.value = '';
        }
    });
}

// Renderizado seguro por nodos DOM (sin innerHTML sobre contenido externo)
function appendFormattedLine(parent, line) {
    const pattern = /(\*\*[^*]+\*\*|\/producto\.html\?id=\d+)/g;
    let lastIdx = 0;
    let match;

    while ((match = pattern.exec(line)) !== null) {
        if (match.index > lastIdx) {
            parent.appendChild(document.createTextNode(line.slice(lastIdx, match.index)));
        }
        const token = match[0];
        if (token.startsWith('**') && token.endsWith('**')) {
            const strong = document.createElement('strong');
            strong.textContent = token.slice(2, -2);
            parent.appendChild(strong);
        } else if (token.startsWith('/producto.html?id=')) {
            const link = document.createElement('a');
            link.href = token;
            link.className = 'elx-chat-link';
            link.textContent = 'Ver producto';
            parent.appendChild(link);
        }
        lastIdx = pattern.lastIndex;
    }

    if (lastIdx < line.length) {
        parent.appendChild(document.createTextNode(line.slice(lastIdx)));
    }
}

function renderMensajeSeguro(bubbleEl, rawText, esMio) {
    const text = String(rawText ?? '');
    if (esMio) {
        bubbleEl.textContent = text;
        return;
    }
    const lines = text.split('\n');
    lines.forEach((line, index) => {
        if (index > 0) {
            bubbleEl.appendChild(document.createElement('br'));
        }
        appendFormattedLine(bubbleEl, line);
    });
}

function agregarBurbujaMensaje(mensaje, container) {
    const user = auth.getUser();
    const esMio = Boolean(user && mensaje.remitenteId === user.id);
    const esBot = Boolean(mensaje.remitenteRol === 'ASISTENTE_IA');

    const row = document.createElement('div');
    row.className = `elx-chat-row${esMio ? ' elx-mine' : ''}${esBot ? ' elx-bot' : ''}`;

    const sender = document.createElement('div');
    sender.className = 'elx-chat-sender';
    if (esBot) {
        const nameSpan = document.createElement('span');
        nameSpan.textContent = 'Electronix AI';
        const tag = document.createElement('span');
        tag.className = 'elx-chat-ai-tag';
        tag.textContent = 'IA';
        sender.appendChild(nameSpan);
        sender.appendChild(tag);
    } else {
        sender.textContent = mensaje.remitenteNombre || 'Soporte';
    }

    const bubble = document.createElement('div');
    bubble.className = 'elx-chat-bubble';
    renderMensajeSeguro(bubble, mensaje.contenido, esMio);

    const horaTexto = mensaje.fechaEnvio
        ? new Date(mensaje.fechaEnvio).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        : '';
    const time = document.createElement('div');
    time.className = 'elx-chat-time';
    time.textContent = horaTexto;

    row.appendChild(sender);
    row.appendChild(bubble);
    if (horaTexto) {
        row.appendChild(time);
    }

    container.appendChild(row);
    container.scrollTop = container.scrollHeight;
}
