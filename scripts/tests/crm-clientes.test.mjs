import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

test('crm-clientes files do not contain emojis', () => {
    const emojiRegex = /\p{Extended_Pictographic}/u;
    const html = fs.readFileSync('src/main/resources/static/admin/crm-clientes.html', 'utf8');
    const css = fs.readFileSync('src/main/resources/static/assets/css/admin/crm-clientes.css', 'utf8');
    const js = fs.readFileSync('src/main/resources/static/assets/js/admin/crm-clientes.js', 'utf8');

    assert.equal(emojiRegex.test(html), false, 'HTML contains emojis');
    assert.equal(emojiRegex.test(css), false, 'CSS contains emojis');
    assert.equal(emojiRegex.test(js), false, 'JS contains emojis');
});

test('crm-clientes HTML contains all IDs referenced in JS', () => {
    const html = fs.readFileSync('src/main/resources/static/admin/crm-clientes.html', 'utf8');
    const js = fs.readFileSync('src/main/resources/static/assets/js/admin/crm-clientes.js', 'utf8');
    const idRegex = /(?:\\\$|getElementById)\(['"]([a-zA-Z0-9_-]+)['"]\)/g;
    let match;
    const missing = [];
    while ((match = idRegex.exec(js)) !== null) {
        const id = match[1];
        if (!html.includes('id="' + id + '"') && !html.includes("id='" + id + "'")) {
            missing.push(id);
        }
    }
    assert.deepEqual(missing, [], 'Missing IDs in HTML: ' + missing.join(', '));
});

test('crm-clientes endpoints contract with live server', async (t) => {
    try {
        const loginRes = await fetch('http://localhost:8090/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: 'Admin@gmail.com', password: 'cambia_esta_clave_por_una_segura' })
        });
        if (loginRes.status !== 200) {
            t.skip('Live server returned non-200 on login');
            return;
        }
        const loginData = await loginRes.json();
        const token = loginData.datos?.token || loginData.token;
        assert.ok(token, 'JWT Token was not returned');

        // Test clientes list endpoint
        const resList = await fetch('http://localhost:8090/api/admin/crm/clientes', {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        assert.equal(resList.status, 200, 'List clientes failed');
        const listData = await resList.json();
        assert.equal(listData.exito, true);
        assert.ok(Array.isArray(listData.datos), 'listData.datos is not an array');
        assert.ok(listData.datos.length > 0, 'No clients returned in seed');

        const firstClient = listData.datos[0];
        assert.ok(firstClient.usuarioId, 'usuarioId missing');
        assert.ok(firstClient.nombre, 'nombre missing');
        assert.ok(firstClient.email, 'email missing');
        assert.ok(firstClient.segmento, 'segmento missing');

        // Test 360 customer profile endpoint
        const res360 = await fetch('http://localhost:8090/api/admin/crm/clientes/' + firstClient.usuarioId + '/360', {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        assert.equal(res360.status, 200, '360 endpoint failed');
        const data360 = await res360.json();
        assert.equal(data360.exito, true);
        assert.equal(data360.datos.usuarioId, firstClient.usuarioId);
        assert.ok(Array.isArray(data360.datos.ultimosPedidos), 'ultimosPedidos must be an array');
        assert.ok(Array.isArray(data360.datos.ultimasTransacciones), 'ultimasTransacciones must be an array');
        assert.ok(Array.isArray(data360.datos.notas), 'notas must be an array');
    } catch (e) {
        if (e.code === 'ECONNREFUSED') {
            t.skip('Live server not reachable at localhost:8090');
        } else {
            throw e;
        }
    }
});
