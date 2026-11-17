import { api } from '/assets/js/api.js';
import { auth } from '/assets/js/auth.js';
import { cart } from '/assets/js/cart.js';
import { formatoMoneda, catalogoMonedas } from '/assets/js/money.js';
import { iniciarShellCompra } from '/assets/js/ui/purchase-shell.js';
import { escapar as esc, cotizacionVigente, notaMetodo, estadoPago, estadoPedido } from '/assets/js/purchase-flow.js';

await iniciarShellCompra();
const $ = id => document.getElementById(id);
let cotizacion = null, metodos = [], metodo = null, secuencia = 0;
let ocupado = false, terminado = false, vencimiento;

function mensaje(texto, tono = '') {
    $('checkout-status').textContent = texto;
    $('checkout-status').dataset.tone = tono;
}
function controles() {
    $('checkout-currency').disabled = ocupado;
    $('quote-refresh').disabled = ocupado;
    $('checkout-delivery').disabled = ocupado;
    $('payment-catalog').disabled = ocupado || !cotizacion;
    $('btn-confirm-order').disabled = ocupado || terminado || !cotizacionVigente(cotizacion) || !metodo;
    $('btn-confirm-order').textContent = ocupado ? 'Registrando tu pedido…' : 'Confirmar mi pedido';
    $('btn-confirm-order').setAttribute('aria-busy', String(ocupado));
}

async function iniciar() {
    if (!auth.tienePermiso('COMPRAR')) throw new Error('Tu cuenta no tiene permiso para realizar compras.');
    const confirmado = new URLSearchParams(location.search).get('pedido');
    if (confirmado && /^[1-9]\d*$/.test(confirmado)) {
        const res = await api.get('/api/pedidos/mios');
        const pedido = res?.exito && Array.isArray(res.datos) ? res.datos.find(p => String(p.id) === confirmado) : null;
        if (!pedido) throw new Error('No encontramos ese pedido en tu cuenta. Puedes consultarlo en Mis pedidos.');
        mostrarConfirmacion(pedido);
        return;
    }
    const res = await api.get('/api/carrito');
    if (!res?.exito || !Array.isArray(res.datos?.items)) throw new Error('No se pudo consultar el carrito.');
    const carrito = res.datos;
    cart.actualizarBadge(carrito.cantidadTotal);
    if (!carrito.items.length) throw new Error('Tu carrito está vacío. Añade un producto para continuar.');
    const monedas = catalogoMonedas();
    monedas.forEach(m => $('checkout-currency').add(new Option(`${m.codigo} · ${m.nombre}`, m.codigo)));
    const codigo = carrito.monedaCodigo || 'PEN';
    if (![...$('checkout-currency').options].some(o => o.value === codigo)) {
        $('checkout-currency').add(new Option(codigo, codigo));
    }
    $('checkout-currency').value = codigo;
    $('checkout-workspace').hidden = false;
    await actualizar();
}

async function actualizar() {
    if (ocupado || terminado) return;
    const revision = ++secuencia;
    clearTimeout(vencimiento);
    const previo = { codigo: metodo?.codigo, version: metodo?.version, valores: new Map(
        [...$('payment-fields').querySelectorAll('input, select, textarea')].map(input => [input.id, input.value])
    ) };
    cotizacion = null;
    controles();
    mensaje('');
    $('quote-note').textContent = 'Calculando los importes en el servidor…';
    $('checkout-summary').setAttribute('aria-busy', 'true');
    $('checkout-items').innerHTML = '<p class="flow-note">Preparando el resumen…</p>';
    for (const id of ['checkout-subtotal', 'checkout-tax', 'checkout-total']) $(id).textContent = '—';
    const moneda = $('checkout-currency').value;
    try {
        const [q, m] = await Promise.all([
            api.post('/api/checkout/cotizacion', { monedaCodigo: moneda }),
            api.get('/api/pagos/metodos?moneda=' + encodeURIComponent(moneda))
        ]);
        if (revision !== secuencia) return;
        if (!q?.exito || !cotizacionVigente(q.datos) || q.datos.datos.monedaCodigo !== moneda) {
            throw new Error(q?.mensaje || 'No se pudo obtener una cotización válida.');
        }
        if (!m?.exito || !Array.isArray(m.datos) || !m.datos.length) {
            throw new Error('No hay métodos de pago habilitados para ' + moneda + '.');
        }
        cotizacion = q.datos;
        metodos = m.datos;
        metodo = metodos.find(item => item.codigo === previo.codigo) || metodos[0];
        const datos = cotizacion.datos;
        const formato = valor => formatoMoneda(valor, datos.monedaCodigo);
        // Subtotales de las líneas firmadas por el servidor; el total nunca se recalcula para cobrar.
        $('checkout-subtotal').textContent = formato(datos.items.reduce((sum, i) => sum + Number(i.precioBaseUnitario) * Number(i.cantidad), 0));
        $('checkout-tax').textContent = formato(datos.items.reduce((sum, i) => sum + Number(i.impuestoMonto || 0), 0));
        $('checkout-total').textContent = formato(datos.total);
        $('checkout-items').innerHTML = datos.items.map(item => `
            <div class="flow-checkout-item"><div><strong>${esc(item.nombre)}</strong>
                <p>${Number(item.cantidad)} × ${formato(item.precioUnitario)}${Number(item.impuestoPorcentaje) > 0 ? ` · Impuesto ${esc(item.impuestoPorcentaje)}% incluido` : ''}${item.monedaOriginal && item.monedaOriginal !== datos.monedaCodigo ? ` · Precio original: ${esc(formatoMoneda(item.precioOriginal, item.monedaOriginal))}` : ''}</p>
            </div><span>${formato(item.subtotal)}</span></div>`).join('');
        $('quote-note').textContent = `Importe vigente hasta ${new Date(cotizacion.venceEn).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}. Revisa el total antes de confirmar.`;
        pintarMetodos();
        if (metodo.codigo === previo.codigo && metodo.version === previo.version) {
            $('payment-fields').querySelectorAll('input, select, textarea').forEach(input => {
                if (previo.valores.has(input.id)) input.value = previo.valores.get(input.id);
            });
        }
        const cotizacionActual = cotizacion;
        vencimiento = setTimeout(() => {
            if (cotizacion !== cotizacionActual || terminado) return;
            cotizacion = null;
            $('quote-note').textContent = 'La cotización venció. Actualiza el importe y revísalo antes de confirmar.';
            controles();
        }, Math.max(0, Date.parse(cotizacion.venceEn) - Date.now()));
    } catch (err) {
        if (revision !== secuencia) return;
        cotizacion = null;
        $('quote-note').textContent = err.message || 'No se pudo calcular el importe.';
        $('checkout-items').innerHTML = '<p class="flow-note">El resumen no está disponible. Actualiza la cotización para continuar.</p>';
        mensaje('No se puede confirmar sin una cotización y un método de pago válidos.', 'error');
    } finally {
        if (revision === secuencia) {
            $('checkout-summary').setAttribute('aria-busy', 'false');
            controles();
        }
    }
}

function pintarMetodos() {
    $('payment-methods').innerHTML = metodos.map((item, indice) => `
        <label class="flow-payment" for="paymethod-${indice}">
            <input id="paymethod-${indice}" type="radio" name="paymethod" value="${esc(item.codigo)}" ${item.codigo === metodo.codigo ? 'checked' : ''}>
            <span><strong>${esc(item.nombre)}</strong><small>${esc(item.descripcion || '')}</small></span>
        </label>`).join('');
    $('payment-methods').querySelectorAll('input').forEach(input => input.addEventListener('change', () => {
        metodo = metodos.find(item => item.codigo === input.value);
        pintarCampos();
        controles();
    }));
    pintarCampos();
}

function pintarCampos() {
    const box = $('payment-fields');
    box.replaceChildren();
    if (!metodo) return;
    if (metodo.instrucciones) {
        const p = document.createElement('p');
        p.className = 'flow-note';
        p.style.whiteSpace = 'pre-line';
        p.textContent = metodo.instrucciones;
        box.appendChild(p);
    }
    function campo(id, labelTexto, tipo, requerido, largo, opciones = []) {
        const wrap = document.createElement('div');
        wrap.className = 'flow-field';
        const label = document.createElement('label');
        label.htmlFor = id;
        label.textContent = labelTexto + (requerido ? ' *' : '');
        const input = document.createElement(tipo === 'select' ? 'select' : tipo === 'textarea' ? 'textarea' : 'input');
        input.id = id;
        input.required = requerido;
        if (tipo === 'select') {
            input.add(new Option('Selecciona una opción', ''));
            opciones.forEach(opcion => input.add(new Option(opcion, opcion)));
        } else {
            if (tipo !== 'textarea') input.type = ['text', 'number', 'email', 'tel', 'password', 'date'].includes(tipo) ? tipo : 'text';
            input.maxLength = largo || 100;
            input.autocomplete = 'off';
        }
        wrap.append(label, input);
        box.appendChild(wrap);
        return input;
    }
    if (metodo.requiereReferencia) {
        const referencia = campo('payment-reference', metodo.etiquetaReferencia || 'Referencia de pago', 'text', true, 100);
        referencia.minLength = 6;
    }
    for (const c of metodo.campos || []) {
        const input = campo('pago-' + c.clave, c.etiqueta, c.tipo, c.obligatorio, c.maxLongitud, c.opciones);
        input.dataset.pago = c.clave;
    }
    const nota = document.createElement('p');
    nota.className = 'flow-message';
    nota.textContent = notaMetodo(metodo);
    box.appendChild(nota);
}

async function confirmar(event) {
    event.preventDefault();
    if (ocupado || terminado) return;
    if (!cotizacionVigente(cotizacion)) {
        await actualizar();
        if (cotizacion) mensaje('La cotización se actualizó. Revisa el importe y vuelve a confirmar.');
        return;
    }
    if (!metodo) return;
    for (const id of ['checkout-address', 'checkout-prov', 'checkout-dist']) {
        $(id).setCustomValidity($(id).value.trim() ? '' : 'Completa este campo.');
    }
    const telefono = $('checkout-phone').value.replace(/\D/g, '');
    $('checkout-phone').setCustomValidity(/^9\d{8}$/.test(telefono) ? '' : 'Ingresa un celular de 9 dígitos que comience con 9.');
    if (!$('checkout-form').reportValidity()) {
        mensaje('Revisa los campos obligatorios antes de confirmar.', 'error');
        return;
    }
    const datosPago = Object.fromEntries([...document.querySelectorAll('[data-pago]')].map(input => [input.dataset.pago, input.value.trim()]));
    let direccion = [$('checkout-address').value.trim(), $('checkout-dist').value.trim(), $('checkout-prov').value.trim(), $('checkout-depto').value].join(', ');
    if ($('checkout-ref').value.trim()) direccion += ` (Ref: ${$('checkout-ref').value.trim()})`;
    ocupado = true;
    controles();
    mensaje('Registrando tu pedido. No cierres esta página.');
    try {
        const res = await api.post('/api/pedidos', {
            direccionEnvio: direccion, telefono, metodoPago: metodo.codigo, versionMetodo: metodo.version,
            cotizacionToken: cotizacion.token, referenciaComprobante: $('payment-reference')?.value.trim() || null, datosPago
        });
        if (!res?.exito || !res.datos?.id) throw new Error(res?.mensaje || 'No recibimos la confirmación del pedido.');
        terminado = true;
        clearTimeout(vencimiento);
        cart.actualizarBadge(0);
        $('payment-fields').replaceChildren();
        mostrarConfirmacion(res.datos);
        const url = new URL(location.href);
        url.searchParams.set('pedido', res.datos.id);
        history.replaceState({}, '', url);
    } catch (err) {
        cotizacion = null;
        clearTimeout(vencimiento);
        mensaje(`${err.message || 'No pudimos confirmar el pedido.'} Si no conoces el resultado, revisa Mis pedidos antes de volver a intentarlo.`, 'error');
        $('quote-note').textContent = 'Actualiza la cotización y revisa el total antes de un nuevo intento.';
    } finally {
        ocupado = false;
        controles();
    }
}

function mostrarConfirmacion(pedido) {
    terminado = true;
    $('checkout-intro').hidden = true;
    $('checkout-workspace').hidden = true;
    mensaje('');
    const pago = estadoPago(pedido), estado = estadoPedido(pedido.estado);
    const box = $('checkout-success');
    box.innerHTML = `
        <p class="store-eyebrow">Pedido registrado · #${esc(pedido.id)}</p>
        <h1 id="checkout-success-title" tabindex="-1">Gracias por elegir Electronix.</h1>
        <p>Tu pedido ya está en tu cuenta. Puedes consultar su progreso y los datos de tu compra en Mis pedidos.</p>
        <div class="flow-actions"><span class="flow-badge" data-tone="${estado.tono}">${esc(estado.texto)}</span><span class="flow-badge" data-tone="${pago.tono}">${esc(pago.texto)}</span></div>
        <p>${esc(pago.detalle)}</p>
        <dl class="flow-facts">
            <div><dt>Número de pedido</dt><dd>#${esc(pedido.id)}</dd></div>
            <div><dt>Total registrado</dt><dd>${formatoMoneda(pedido.total, pedido.monedaCodigo)}</dd></div>
            <div><dt>Dirección de entrega</dt><dd>${esc(pedido.direccionEnvio || 'Consulta los detalles en Mis pedidos.')}</dd></div>
            <div><dt>Método de pago</dt><dd>${esc(pedido.metodoNombre || pedido.metodoPago || 'Consulta los detalles en Mis pedidos.')}</dd></div>
        </dl>
        <div class="flow-actions"><a class="store-button" href="/mis-pedidos.html?pedido=${encodeURIComponent(pedido.id)}">Consultar el progreso</a><a class="store-text-link" href="/productos.html">Seguir explorando</a></div>`;
    box.hidden = false;
    $('checkout-success-title').focus({ preventScroll: true });
    window.scrollTo({ top: 0, behavior: 'instant' });
}

$('checkout-currency').addEventListener('change', actualizar);
$('quote-refresh').addEventListener('click', actualizar);
$('checkout-form').addEventListener('submit', confirmar);
for (const id of ['checkout-address', 'checkout-prov', 'checkout-dist', 'checkout-phone']) {
    $(id).addEventListener('input', () => $(id).setCustomValidity(''));
}
if (auth.protegerRutaAutenticada()) {
    iniciar().catch(err => {
        $('checkout-workspace').hidden = true;
        mensaje(err.message || 'No pudimos preparar tu compra.', 'error');
        $('checkout-status').insertAdjacentHTML('beforeend', '<div class="flow-actions"><a class="flow-link" href="/carrito.html">Revisar mi carrito</a><a class="flow-link" href="/mis-pedidos.html">Mis pedidos</a><button type="button" id="checkout-retry" class="flow-link">Reintentar</button></div>');
        $('checkout-retry').addEventListener('click', () => location.reload());
    });
}
