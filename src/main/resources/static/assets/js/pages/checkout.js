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
let cuponAplicado = null, billetera = null, puntosCanjeados = 0, descuentoPuntosPen = 0;

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

    $('btn-aplicar-cupon')?.addEventListener('click', manejarCupon);
    $('input-cupon')?.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); manejarCupon(); } });
    $('btn-aplicar-puntos')?.addEventListener('click', manejarPuntos);
    $('input-puntos')?.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); manejarPuntos(); } });

    cargarBilletera().catch(() => {});
    await actualizar();
}

async function cargarBilletera() {
    try {
        const res = await api.get('/api/crm/puntos/mi-billetera');
        if (res.datos) {
            billetera = res.datos;
            const seccion = $('seccion-puntos-crm');
            if (seccion) {
                seccion.hidden = false;
                if ($('puntos-disponibles-badge')) {
                    $('puntos-disponibles-badge').textContent = `${billetera.saldoPuntos} pts`;
                }
                if ($('puntos-equivalencia-txt')) {
                    const equiv = Number(billetera.valorEstimadoPen ?? billetera.equivalentePen ?? 0).toFixed(2);
                    const nivel = billetera.nivelNombre || 'Bronce';
                    const mult = Number(billetera.multiplicador || 1).toFixed(2);
                    $('puntos-equivalencia-txt').textContent = `Tienes ${billetera.saldoPuntos} puntos (≈ S/ ${equiv}) · Nivel ${nivel} (${mult}x puntos).`;
                }
                const input = $('input-puntos');
                if (input) {
                    input.max = billetera.saldoPuntos;
                }
            }
        }
    } catch (_) {
        // Usuario sin billetera o no habilitado
    }
}

function recalcularTotales() {
    if (!cotizacion?.datos) return;
    const datos = cotizacion.datos;
    const formato = valor => formatoMoneda(valor, datos.monedaCodigo);
    const tasa = Number(datos.tasaPen || 1);
    let totalFinal = Number(datos.total);

    // Cupón
    if (cuponAplicado) {
        const descCuponMoneda = tasa > 0 ? (Number(cuponAplicado.descuentoCalculadoPen || 0) / tasa) : Number(cuponAplicado.descuentoCalculadoPen || 0);
        totalFinal = Math.max(0, totalFinal - descCuponMoneda);
        if ($('row-descuento-cupon')) {
            $('row-descuento-cupon').hidden = false;
            $('lbl-descuento-cupon').textContent = `Descuento Cupón (${esc(cuponAplicado.codigo)})`;
            $('checkout-discount').textContent = `-${formato(descCuponMoneda)}`;
        }
    } else if ($('row-descuento-cupon')) {
        $('row-descuento-cupon').hidden = true;
    }

    // Puntos
    if (puntosCanjeados > 0 && descuentoPuntosPen > 0) {
        const descPuntosMoneda = tasa > 0 ? (descuentoPuntosPen / tasa) : descuentoPuntosPen;
        totalFinal = Math.max(0, totalFinal - descPuntosMoneda);
        if ($('row-descuento-puntos')) {
            $('row-descuento-puntos').hidden = false;
            if ($('txt-puntos-canjeados')) $('txt-puntos-canjeados').textContent = puntosCanjeados;
            $('checkout-discount-points').textContent = `-${formato(descPuntosMoneda)}`;
        }
    } else if ($('row-descuento-puntos')) {
        $('row-descuento-puntos').hidden = true;
    }

    $('checkout-total').textContent = formato(totalFinal);
}

async function manejarCupon() {
    const input = $('input-cupon');
    const feedback = $('cupon-feedback');
    const codigo = input?.value?.trim().toUpperCase();

    if (!codigo) {
        cuponAplicado = null;
        if (feedback) { feedback.hidden = true; feedback.textContent = ''; }
        recalcularTotales();
        return;
    }

    if (!cotizacion?.datos) return;

    try {
        const subtotalPen = Number(cotizacion.datos.totalBasePen || cotizacion.datos.total || 0);
        const res = await api.post('/api/crm/cupones/validar', {
            codigo,
            montoSubtotalPen: subtotalPen,
            deseaCombinarConPuntos: puntosCanjeados > 0
        });

        if (res.exito && res.datos?.valido) {
            cuponAplicado = {
                codigo: res.datos.codigo,
                descuentoCalculadoPen: res.datos.descuentoCalculadoPen,
                permiteCombinarPuntos: res.datos.permiteCombinarPuntos
            };
            if (feedback) {
                feedback.hidden = false;
                feedback.style.color = '#059669';
                feedback.textContent = `✓ Cupón '${codigo}' aplicado: S/ ${Number(res.datos.descuentoCalculadoPen).toFixed(2)} de descuento.`;
            }
        } else {
            cuponAplicado = null;
            if (feedback) {
                feedback.hidden = false;
                feedback.style.color = '#e11d48';
                feedback.textContent = res.datos?.mensaje || 'El cupón no es válido para esta compra.';
            }
        }
    } catch (e) {
        cuponAplicado = null;
        if (feedback) {
            feedback.hidden = false;
            feedback.style.color = '#e11d48';
            feedback.textContent = e.message || 'Error al validar cupón';
        }
    }
    recalcularTotales();
}

async function manejarPuntos() {
    const input = $('input-puntos');
    const feedback = $('puntos-feedback');
    const pts = parseInt(input?.value || '0', 10);

    if (isNaN(pts) || pts <= 0) {
        puntosCanjeados = 0;
        descuentoPuntosPen = 0;
        if (feedback) { feedback.hidden = true; feedback.textContent = ''; }
        recalcularTotales();
        return;
    }

    if (!cotizacion?.datos) return;

    try {
        const subtotalPen = Number(cotizacion.datos.totalBasePen || cotizacion.datos.total || 0);
        const res = await api.post('/api/crm/puntos/simular-canje', {
            puntosACanjear: pts,
            montoSubtotalPen: subtotalPen
        });

        if (res.exito && res.datos?.puedeCanjear) {
            puntosCanjeados = res.datos.puntosCanjeados;
            descuentoPuntosPen = res.datos.descuentoPen;
            if (feedback) {
                feedback.hidden = false;
                feedback.style.color = '#d97706';
                feedback.textContent = `✓ ${puntosCanjeados} puntos canjeados: S/ ${Number(descuentoPuntosPen).toFixed(2)} de descuento directo.`;
            }
        } else {
            puntosCanjeados = 0;
            descuentoPuntosPen = 0;
            if (feedback) {
                feedback.hidden = false;
                feedback.style.color = '#e11d48';
                feedback.textContent = res.datos?.mensaje || 'No fue posible canjear los puntos.';
            }
        }
    } catch (e) {
        puntosCanjeados = 0;
        descuentoPuntosPen = 0;
        if (feedback) {
            feedback.hidden = false;
            feedback.style.color = '#e11d48';
            feedback.textContent = e.message || 'Error al canjear puntos';
        }
    }
    recalcularTotales();
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
        $('checkout-subtotal').textContent = formato(datos.items.reduce((sum, i) => sum + Number(i.precioBaseUnitario) * Number(i.cantidad), 0));
        $('checkout-tax').textContent = formato(datos.items.reduce((sum, i) => sum + Number(i.impuestoMonto || 0), 0));
        $('checkout-items').innerHTML = datos.items.map(item => `
            <div class="flow-checkout-item"><div><strong>${esc(item.nombre)}</strong>
                <p>${Number(item.cantidad)} × ${formato(item.precioUnitario)}${Number(item.impuestoPorcentaje) > 0 ? ` · Impuesto ${esc(item.impuestoPorcentaje)}% incluido` : ''}${item.monedaOriginal && item.monedaOriginal !== datos.monedaCodigo ? ` · Precio original: ${esc(formatoMoneda(item.precioOriginal, item.monedaOriginal))}` : ''}</p>
            </div><span>${formato(item.subtotal)}</span></div>`).join('');
        $('quote-note').textContent = `Importe vigente hasta ${new Date(cotizacion.venceEn).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}. Revisa el total antes de confirmar.`;

        recalcularTotales();
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
    const list = $('payment-methods');
    list.replaceChildren();
    for (const item of metodos) {
        const option = document.createElement('label');
        option.className = 'flow-method-card';
        option.innerHTML = `
            <input type="radio" name="metodo-seleccionado" value="${esc(item.codigo)}" ${item.codigo === metodo.codigo ? 'checked' : ''}>
            <div>
                <strong>${esc(item.nombre)}</strong>
                <p>${esc(item.descripcion || notaMetodo(item.codigo))}</p>
                <small>${esc(item.procesamientoPago)}</small>
            </div>`;
        option.querySelector('input').addEventListener('change', () => {
            metodo = item;
            pintarCampos();
            controles();
        });
        list.appendChild(option);
    }
    pintarCampos();
}

function pintarCampos() {
    const box = $('payment-fields');
    box.replaceChildren();
    if (!metodo) return;
    const fields = Array.isArray(metodo.formulario?.campos) ? metodo.formulario.campos : [];
    if (!fields.length) return;
    const fieldset = document.createElement('fieldset');
    fieldset.className = 'flow-form-section';
    fieldset.innerHTML = `<h3>Datos para ${esc(metodo.nombre)}.</h3>`;
    const grid = document.createElement('div');
    grid.className = 'flow-fields';
    for (const field of fields) {
        const wrapper = document.createElement('div');
        wrapper.className = `flow-field ${field.tipo === 'TEXTAREA' ? 'full' : 'mobile-full'}`;
        const label = document.createElement('label');
        label.htmlFor = `pay-${field.campo}`;
        label.textContent = `${field.etiqueta}${field.obligatorio ? ' *' : ''}`;
        wrapper.appendChild(label);
        let input;
        if (field.tipo === 'SELECT') {
            input = document.createElement('select');
            for (const option of field.opciones || []) {
                input.add(new Option(option.label || option.valor, option.valor));
            }
        } else if (field.tipo === 'TEXTAREA') {
            input = document.createElement('textarea');
            input.rows = 2;
        } else {
            input = document.createElement('input');
            input.type = field.tipo === 'NUMBER' ? 'number' : field.tipo === 'EMAIL' ? 'email' : 'text';
        }
        input.id = `pay-${field.campo}`;
        input.name = field.campo;
        input.dataset.pago = field.campo;
        input.required = Boolean(field.obligatorio);
        if (field.placeholder) input.placeholder = field.placeholder;
        wrapper.appendChild(input);
        grid.appendChild(wrapper);
    }
    fieldset.appendChild(grid);
    box.appendChild(fieldset);
}

async function confirmar(event) {
    event.preventDefault();
    if (ocupado || terminado || !cotizacionVigente(cotizacion) || !metodo) return;
    for (const id of ['checkout-address', 'checkout-prov', 'checkout-dist', 'checkout-phone']) {
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
            direccionEnvio: direccion,
            telefono,
            metodoPago: metodo.codigo,
            versionMetodo: metodo.version,
            cotizacionToken: cotizacion.token,
            referenciaComprobante: $('payment-reference')?.value.trim() || null,
            datosPago,
            cuponCodigoAplicado: cuponAplicado ? cuponAplicado.codigo : null,
            puntosACanjear: puntosCanjeados > 0 ? puntosCanjeados : null
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
