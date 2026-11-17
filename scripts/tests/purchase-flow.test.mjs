import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
    retornoSeguro, estadoPedido, estadoPago, PASOS_PEDIDO,
    notaMetodo, cotizacionVigente, fechaPedido, escapar
} from '../../src/main/resources/static/assets/js/purchase-flow.js';

test('el login conserva un regreso local con producto, consulta y sección', () => {
    assert.equal(retornoSeguro('/producto.html?id=200#fotos', 'https://electronix.example'), '/producto.html?id=200#fotos');
    assert.equal(retornoSeguro('/checkout.html', 'http://localhost:8090'), '/checkout.html');
});

test('el login no acepta redirecciones externas, protocolos, controles ni barras invertidas', () => {
    for (const destino of ['https://evil.example', '//evil.example', '/\\evil.example', 'javascript:alert(1)', '\n//evil.example', '/\tevil.example', 'checkout.html', null]) {
        assert.equal(retornoSeguro(destino), null, String(destino));
    }
});

test('el progreso corresponde a los cuatro estados reales y la cancelación no simula una etapa', () => {
    assert.deepEqual(PASOS_PEDIDO.map(p => p[0]), ['PENDIENTE', 'EN_PROCESO', 'ENVIADO', 'ENTREGADO']);
    assert.equal(estadoPedido('ENVIADO').indice, 2);
    assert.equal(estadoPedido('CANCELADO').indice, -1);
    assert.equal(estadoPedido('CANCELADO').cancelado, true);
    assert.equal(estadoPedido('DESCONOCIDO').indice, -1);
    assert.equal(estadoPedido('ENTREGADO').texto, 'Entregado');
});

test('cancelar o entregar un pedido no cambia ni supone el estado del pago', () => {
    assert.equal(estadoPago({ estado: 'CANCELADO', estadoPago: 'APROBADO' }).texto, 'Pago aprobado');
    assert.equal(estadoPago({ estado: 'ENTREGADO', estadoPago: 'PENDIENTE' }).texto, 'Pago pendiente');
    assert.equal(estadoPago({ estado: 'ENVIADO', estadoPago: 'RECHAZADO' }).texto, 'Pago rechazado');
});

test('un método simulado no se anuncia como un cargo real', () => {
    assert.equal(estadoPago({ estadoPago: 'APROBADO', procesamientoPago: 'SIMULADO' }).texto, 'Simulación aprobada');
    assert.match(notaMetodo({ procesamiento: 'SIMULADO' }), /no ingreses datos reales/);
});

test('contraentrega y verificación manual conservan sus condiciones', () => {
    assert.equal(estadoPago({ estadoPago: 'PENDIENTE', procesamientoPago: 'CONTRA_ENTREGA' }).texto, 'Pago al recibir');
    assert.equal(estadoPago({ estadoPago: 'PENDIENTE', procesamientoPago: 'VERIFICACION_MANUAL' }).texto, 'Pago por verificar');
    assert.match(notaMetodo({ procesamiento: 'VERIFICACION_MANUAL' }), /verificación/);
});

test('no se confirma sin token, líneas, total válido y fecha de vencimiento futura', () => {
    const ahora = Date.now();
    const q = { token: 'servidor', venceEn: new Date(ahora + 300000).toISOString(), datos: { total: '118.00', items: [{}] } };
    assert.equal(cotizacionVigente(q, ahora), true);
    assert.equal(cotizacionVigente(q, ahora + 300000), false);
    assert.equal(cotizacionVigente({ ...q, token: '' }, ahora), false);
    assert.equal(cotizacionVigente({ ...q, venceEn: 'invalid' }, ahora), false);
    assert.equal(cotizacionVigente({ ...q, datos: { total: 118, items: [] } }, ahora), false);
    assert.equal(cotizacionVigente({ ...q, datos: { total: 'NaN', items: [{}] } }, ahora), false);
});

test('los mensajes y fechas no insertan HTML ni fechas inventadas', () => {
    assert.equal(escapar('<img onerror="alert(1)">'), '&lt;img onerror=&quot;alert(1)&quot;&gt;');
    assert.equal(fechaPedido('no es una fecha'), 'Fecha no disponible');
    assert.equal(fechaPedido(null), 'Fecha no disponible');
});

test('las cuatro pantallas usan estilos locales y conservan IDs únicos y footer compartido', () => {
    for (const pagina of ['login', 'carrito', 'checkout', 'mis-pedidos']) {
        const html = readFileSync(new URL(`../../src/main/resources/static/${pagina}.html`, import.meta.url), 'utf8');
        const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);
        assert.equal(ids.length, new Set(ids).size, pagina);
        assert.ok(!html.includes('cdn.tailwindcss.com'));
        assert.match(html, /purchase-flow\.css/);
        assert.match(html, /data-site-footer/);
        assert.match(html, /class="store-header"/);
    }
});

test('no se publican Express gratis, dirección de recojo ficticia o un método de entrega sin soporte', () => {
    const html = readFileSync(new URL('../../src/main/resources/static/checkout.html', import.meta.url), 'utf8');
    assert.ok(!html.includes('Despacho Express'));
    assert.ok(!html.includes('Av. Principal 123'));
    assert.ok(!html.includes('name="delivery"'));
    assert.match(html, /id="checkout-currency"/);
    assert.match(html, /id="payment-catalog"/);
});
