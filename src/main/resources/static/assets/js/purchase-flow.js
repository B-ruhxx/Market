// Reglas de presentación del recorrido de compra. No calcula cobros ni modifica pedidos.
export function retornoSeguro(destino, origen = 'https://electronix.local') {
    if (typeof destino !== 'string' || !destino.startsWith('/') || destino.startsWith('//')
        || /[\\\u0000-\u001f\u007f]/.test(destino)) return null;
    try {
        const url = new URL(destino, origen);
        return url.origin === new URL(origen).origin ? url.pathname + url.search + url.hash : null;
    } catch { return null; }
}

export const PASOS_PEDIDO = [
    ['PENDIENTE', 'Pedido recibido'],
    ['EN_PROCESO', 'En preparación'],
    ['ENVIADO', 'Enviado'],
    ['ENTREGADO', 'Entregado']
];

export function estadoPedido(estado) {
    const indice = PASOS_PEDIDO.findIndex(([codigo]) => codigo === estado);
    if (estado === 'CANCELADO') return { texto: 'Cancelado', tono: 'neutral', indice: -1, cancelado: true };
    return {
        texto: indice >= 0 ? PASOS_PEDIDO[indice][1] : String(estado || 'Estado no disponible'),
        tono: estado === 'ENTREGADO' ? 'success' : 'neutral', indice, cancelado: false
    };
}

export function estadoPago(pedido) {
    const estado = pedido.estadoPago;
    const tipo = pedido.procesamientoPago;
    if (estado === 'RECHAZADO') return { texto: 'Pago rechazado', tono: 'error', detalle: 'Consulta con soporte si necesitas ayuda con este pago.' };
    if (estado === 'APROBADO') return tipo === 'SIMULADO'
        ? { texto: 'Simulación aprobada', tono: 'neutral', detalle: 'Este método es simulado. No representa un cargo real.' }
        : { texto: 'Pago aprobado', tono: 'success', detalle: 'El sistema registra el pago como aprobado.' };
    if (estado && estado !== 'PENDIENTE') return { texto: `Pago: ${estado}`, tono: 'neutral', detalle: 'Consulta con soporte para conocer más detalles.' };
    if (tipo === 'CONTRA_ENTREGA') return { texto: 'Pago al recibir', tono: 'warning', detalle: 'El pago permanece pendiente hasta su confirmación al entregar.' };
    if (tipo === 'VERIFICACION_MANUAL') return { texto: 'Pago por verificar', tono: 'warning', detalle: 'La referencia de pago está pendiente de verificación por personal autorizado.' };
    return { texto: 'Pago pendiente', tono: 'warning', detalle: 'El sistema todavía no registra este pago como aprobado.' };
}

export function notaMetodo(metodo) {
    if (metodo?.procesamiento === 'SIMULADO') return 'Simulación: no ingreses datos reales de tarjeta. Este método no realiza cargos reales.';
    if (metodo?.procesamiento === 'CONTRA_ENTREGA') return 'El pago quedará pendiente hasta su confirmación al entregar.';
    return 'El pago quedará pendiente de verificación por personal autorizado.';
}

export function cotizacionVigente(cotizacion, ahora = Date.now()) {
    const vence = Date.parse(cotizacion?.venceEn);
    return Boolean(cotizacion?.token && Array.isArray(cotizacion?.datos?.items)
        && cotizacion.datos.items.length && Number.isFinite(Number(cotizacion.datos.total))
        && Number.isFinite(vence) && ahora < vence);
}

export function fechaPedido(valor) {
    const fecha = new Date(valor);
    return valor && Number.isFinite(fecha.getTime())
        ? fecha.toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' })
        : 'Fecha no disponible';
}

export function escapar(valor) {
    return String(valor ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
