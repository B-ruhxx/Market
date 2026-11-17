// Doble de API para verificar la compra sin usuarios, carritos, pagos ni pedidos reales.
export const usuarioPrueba = { id: 17, nombre: 'Cliente de prueba', email: 'cliente@example.test', permisos: ['COMPRAR'], rol: 'CLIENTE' };
export const metodosPrueba = [
    { codigo: 'YAPE', nombre: 'Yape de prueba', descripcion: 'Referencia con verificación manual.', instrucciones: 'Utiliza únicamente referencias de prueba.', procesamiento: 'VERIFICACION_MANUAL', requiereReferencia: true, etiquetaReferencia: 'Referencia de prueba', campos: [], version: 'manual-v1' },
    { codigo: 'PAGO_CONTRAENTREGA', nombre: 'Pago al recibir', descripcion: 'Pendiente hasta la entrega.', procesamiento: 'CONTRA_ENTREGA', requiereReferencia: false, campos: [], version: 'entrega-v1' },
    { codigo: 'TARJETA_PRUEBA', nombre: 'Tarjeta simulada', descripcion: 'Sin cargos reales.', procesamiento: 'SIMULADO', requiereReferencia: false, campos: [
        { clave: 'titular', etiqueta: 'Nombre de prueba', tipo: 'text', obligatorio: true, maxLongitud: 60 },
        { clave: 'resultado', etiqueta: 'Resultado de prueba', tipo: 'select', obligatorio: true, opciones: ['Aprobar', 'Rechazar'] }
    ], version: 'simulado-v1' }
];
const initialItems = [
    { id: 11, productoId: 200, productoNombre: 'Antivirus Eset Nod32 3 equipos Pro', precioUnitario: 118, cantidad: 2, subtotal: 236, subtotalSinImpuesto: 200, impuestoMonto: 36, impuestoPorcentaje: 18, monedaCodigo: 'PEN', imagenUrl: 'https://images.unsplash.com/photo-1550439062-609e1531270e?w=600&auto=format&fit=crop&q=80' },
    { id: 12, productoId: 1, productoNombre: 'Portátil de prueba <especial>', precioUnitario: 300, cantidad: 1, subtotal: 300, subtotalSinImpuesto: 300, impuestoMonto: 0, impuestoPorcentaje: 0, monedaCodigo: 'PEN', imagenUrl: '/assets/img/promos/portatil.webp' }
];
export function carritoPrueba(items = initialItems) {
    return { id: 1, items: structuredClone(items), monedaCodigo: 'PEN', total: items.reduce((s, i) => s + i.subtotal, 0), subtotal: items.reduce((s, i) => s + i.subtotalSinImpuesto, 0), impuestoTotal: items.reduce((s, i) => s + i.impuestoMonto, 0), cantidadTotal: items.reduce((s, i) => s + i.cantidad, 0) };
}
export const pedidoPrueba = {
    id: 704, estado: 'PENDIENTE', total: 536, monedaCodigo: 'PEN',
    direccionEnvio: 'Calle de prueba 123, Distrito, Provincia, Lima', telefono: '912345678',
    fecha: '2026-11-17T10:00:00', metodoPago: 'YAPE', metodoNombre: 'Yape de prueba',
    estadoPago: 'PENDIENTE', procesamientoPago: 'VERIFICACION_MANUAL', referenciaComprobante: 'OP-PRUEBA',
    detalles: initialItems.map(i => ({ productoId: i.productoId, productoNombre: i.productoNombre, precioUnitario: i.precioUnitario, cantidad: i.cantidad, subtotal: i.subtotal, impuestoPorcentaje: i.impuestoPorcentaje }))
};

export async function simularCompras(context, { autenticado = true } = {}) {
    const state = {
        user: structuredClone(usuarioPrueba), cart: carritoPrueba(), methods: structuredClone(metodosPrueba),
        orders: [
            structuredClone(pedidoPrueba),
            { ...structuredClone(pedidoPrueba), id: 703, estado: 'ENVIADO', estadoPago: 'APROBADO' },
            { ...structuredClone(pedidoPrueba), id: 702, estado: 'ENTREGADO', estadoPago: 'APROBADO', procesamientoPago: 'SIMULADO' },
            { ...structuredClone(pedidoPrueba), id: 701, estado: 'CANCELADO', estadoPago: 'APROBADO' }
        ],
        requests: [], quotes: new Map(), quoteNumber: 0,
        failCart: false, failOrders: false, failQuote: false, failCreate: false,
        beforeQuote: null, beforeMutation: null, beforeCreate: null, quoteTotal: null,
        expiresIn: 300000
    };
    if (autenticado) await context.addInitScript(user => {
        // Playwright también ejecuta este script en documentos iniciales y frames sin almacenamiento.
        if (window !== window.top || !/^https?:$/.test(location.protocol)) return;
        if (!localStorage.getItem('ecommerce_token') && !sessionStorage.getItem('ecommerce_token')) {
            sessionStorage.setItem('ecommerce_token', 'mock-only');
            sessionStorage.setItem('ecommerce_user', JSON.stringify(user));
        }
    }, state.user);
    await context.route('**/api/**', async route => {
        const req = route.request(), url = new URL(req.url()), method = req.method();
        const body = method !== 'GET' && method !== 'DELETE' ? req.postDataJSON() : null;
        state.requests.push({ path: url.pathname, search: url.search, method, body });
        const ok = datos => route.fulfill({ json: { exito: true, datos } });
        const fail = (mensaje, status = 503) => route.fulfill({ status, json: { exito: false, mensaje } });
        if (url.pathname === '/api/monedas') return ok([
            { codigo: 'PEN', nombre: 'Sol', locale: 'es-PE', decimales: 2 }, { codigo: 'USD', nombre: 'Dólar', locale: 'en-US', decimales: 2 }
        ]);
        if (url.pathname === '/api/sitio/publico') return ok({ 'site.nombre': 'ELECTRONIX' });
        if (url.pathname === '/api/categorias') return ok([{ id: 1, nombre: 'Laptops y Computadoras' }, { id: 15, nombre: 'Software' }]);
        if (url.pathname === '/api/usuarios/me') return ok(state.user);
        if (url.pathname.startsWith('/api/chat/')) return ok(null);
        if (url.pathname === '/api/auth/login') {
            if (body.email === 'error@example.test') return fail('Credenciales incorrectas <img src=x>', 401);
            return ok({ ...state.user, token: 'mock-login' });
        }
        if (url.pathname === '/api/carrito' && method === 'GET') return state.failCart ? fail('Error de conexión de prueba') : ok(state.cart);
        if (url.pathname.startsWith('/api/carrito/items/')) {
            if (state.beforeMutation) await state.beforeMutation();
            const id = Number(url.pathname.split('/').at(-1));
            const item = state.cart.items.find(i => i.id === id);
            if (method === 'PUT') {
                if (body.cantidad > 5) return fail('Stock máximo de prueba: 5 unidades.', 400);
                if (!item || body.cantidad < 1) return fail('Cantidad no válida.', 400);
                const base = item.precioUnitario / (1 + item.impuestoPorcentaje / 100);
                item.cantidad = body.cantidad;
                item.subtotal = item.precioUnitario * item.cantidad;
                item.subtotalSinImpuesto = base * item.cantidad;
                item.impuestoMonto = item.subtotal - item.subtotalSinImpuesto;
            } else if (method === 'DELETE') state.cart.items = state.cart.items.filter(i => i.id !== id);
            else return fail('Método inesperado bloqueado por la prueba.', 500);
            state.cart = carritoPrueba(state.cart.items);
            return ok(state.cart);
        }
        if (url.pathname === '/api/pagos/metodos') return ok(state.methods);
        if (url.pathname === '/api/checkout/cotizacion' && method === 'POST') {
            if (state.beforeQuote) await state.beforeQuote(body);
            if (state.failQuote) return fail('No se pudo cotizar en la prueba.');
            const factor = body.monedaCodigo === 'USD' ? 0.25 : 1;
            const items = state.cart.items.map(i => ({
                productoId: i.productoId, nombre: i.productoNombre, cantidad: i.cantidad,
                precioOriginal: i.precioUnitario / (1 + i.impuestoPorcentaje / 100), monedaOriginal: 'PEN',
                precioBaseUnitario: i.precioUnitario / (1 + i.impuestoPorcentaje / 100) * factor,
                precioUnitario: i.precioUnitario * factor, subtotal: i.subtotal * factor,
                impuestoPorcentaje: i.impuestoPorcentaje, impuestoMonto: i.impuestoMonto * factor
            }));
            const q = { token: 'quote-' + ++state.quoteNumber, venceEn: new Date(Date.now() + state.expiresIn).toISOString(),
                datos: { monedaCodigo: body.monedaCodigo, total: state.quoteTotal ?? state.cart.total * factor, items } };
            state.quotes.set(q.token, q);
            return ok(q);
        }
        if (url.pathname === '/api/pedidos' && method === 'POST') {
            if (state.beforeCreate) await state.beforeCreate();
            if (state.failCreate) return fail('No se pudo registrar el pedido de prueba.', 400);
            const q = state.quotes.get(body.cotizacionToken);
            const pago = state.methods.find(m => m.codigo === body.metodoPago);
            if (!q || Date.now() >= Date.parse(q.venceEn) || !pago || body.versionMetodo !== pago.version) return fail('Cotización o método no válido.', 400);
            const pedido = {
                ...structuredClone(pedidoPrueba), id: 705, total: q.datos.total, monedaCodigo: q.datos.monedaCodigo,
                direccionEnvio: body.direccionEnvio, telefono: body.telefono, metodoPago: pago.codigo, metodoNombre: pago.nombre,
                procesamientoPago: pago.procesamiento, estadoPago: pago.procesamiento === 'SIMULADO' ? 'APROBADO' : 'PENDIENTE',
                referenciaComprobante: body.referenciaComprobante,
                detalles: q.datos.items.map(i => ({ ...i, productoNombre: i.nombre }))
            };
            state.orders.unshift(pedido);
            state.cart = carritoPrueba([]);
            state.quotes.delete(body.cotizacionToken);
            return ok(pedido);
        }
        if (url.pathname === '/api/pedidos/mios') return state.failOrders ? fail('No se pudieron actualizar los pedidos de prueba.') : ok(state.orders);
        // Nada que no esté definido arriba llega al backend, especialmente escrituras.
        return fail('Petición bloqueada por el doble de API: ' + method + ' ' + url.pathname, 500);
    });
    return state;
}
