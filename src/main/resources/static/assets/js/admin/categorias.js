import { api } from '/assets/js/api.js';
        import { auth } from '/assets/js/auth.js';
        import { renderAdminShell } from '/assets/js/ui/adminShell.js?v=2';
        import { showToast } from '/assets/js/ui/toast.js';
        import { renderPager, swapSuave } from '/assets/js/ui/pager.js?v=2';

        // NOTA: referencias DOM primero (evita TDZ si init las toca antes del await).
        const tbody = document.getElementById('categorias-table-body');
        const modal = document.getElementById('cat-modal');
        const modalClose = document.getElementById('cat-modal-close');
        const btnCancelar = document.getElementById('btn-cat-cancelar');
        const btnNueva = document.getElementById('btn-nueva-cat');
        const form = document.getElementById('cat-form');
        const modalTitle = document.getElementById('cat-modal-title');

        // paginacion local: 10 por pagina
        const pager = document.getElementById('categorias-pager');
        const tableWrap = tbody.closest('.adm-table-wrap');
        let TAMANO_PAGINA = 10;
        let paginaActual = 0;
        let todasCategorias = [];
        let temporizadorResize;

        renderAdminShell('categorias');

        if (!auth.protegerRutaAdmin()) {
            // Guard
        } else {
            initCategoriasAdmin();
        }

        function esc(value) {
            return String(value ?? '')
                .replace(/&/g, '&amp;').replace(/</g, '&lt;')
                .replace(/>/g, '&gt;').replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;');
        }

        // modal scroll: bloquea el scroll del fondo mientras el formulario esta abierto
        function bloquearFondo(bloquear) {
            document.body.style.overflow = bloquear ? 'hidden' : '';
        }

        function initCategoriasAdmin() {
            cargarCategorias();

            btnNueva.addEventListener('click', () => {
                modalTitle.textContent = 'Nueva categoría';
                document.getElementById('cat-id').value = '';
                document.getElementById('cat-nombre').value = '';
                document.getElementById('cat-descripcion').value = '';
                modal.classList.remove('hidden');
                bloquearFondo(true);
            });

            modalClose.addEventListener('click', () => { modal.classList.add('hidden'); bloquearFondo(false); });
            btnCancelar.addEventListener('click', () => { modal.classList.add('hidden'); bloquearFondo(false); });

            form.addEventListener('submit', async (e) => {
                e.preventDefault();
                await guardarCategoria();
            });

            window.addEventListener('resize', () => {
                clearTimeout(temporizadorResize);
                temporizadorResize = setTimeout(() => ajustarFilasAlViewport(), 150);
            });
        }

        function filasPorViewport() {
            if (!window.matchMedia('(min-width: 901px)').matches || !tableWrap) return 10;
            const fila = tbody.querySelector('tr');
            const cabecera = tableWrap.querySelector('thead');
            if (!fila || !cabecera) return TAMANO_PAGINA;
            const altoDisponible = tableWrap.clientHeight - cabecera.getBoundingClientRect().height;
            const altoFila = Math.max(1, fila.getBoundingClientRect().height);
            return Math.max(1, Math.min(50, Math.floor(altoDisponible / altoFila)));
        }

        function ajustarFilasAlViewport() {
            const siguienteTamano = filasPorViewport();
            if (siguienteTamano === TAMANO_PAGINA) return;
            const primerRegistro = paginaActual * TAMANO_PAGINA;
            TAMANO_PAGINA = siguienteTamano;
            paginaActual = Math.floor(primerRegistro / TAMANO_PAGINA);
            cargarCategorias();
        }

        async function cargarCategorias() {
            try {
                const res = await api.get('/api/categorias');
                todasCategorias = (res.exito && res.datos) ? res.datos : [];
                const totalPaginas = Math.ceil(todasCategorias.length / TAMANO_PAGINA);
                if (paginaActual > totalPaginas - 1) paginaActual = Math.max(0, totalPaginas - 1);
                const inicio = paginaActual * TAMANO_PAGINA;
                const lista = todasCategorias.slice(inicio, inicio + TAMANO_PAGINA);

                await swapSuave(tbody, () => pintarFilas(lista));
                renderPager(pager, {
                    pagina: paginaActual,
                    totalPaginas,
                    onPage: p => { paginaActual = p; cargarCategorias(); }
                });
                ajustarFilasAlViewport();
            } catch (err) {
                const msg = esc(err.message);
                await swapSuave(tbody, () => {
                    tbody.innerHTML = `<tr><td colspan="4" class="adm-err">Error: ${msg}</td></tr>`;
                });
            }
        }

        function pintarFilas(lista) {
            tbody.innerHTML = '';

            if (lista.length > 0) {
                lista.forEach(c => {
                        const tr = document.createElement('tr');
                        tr.innerHTML = `
                            <td><span class="adm-id">#${c.id}</span></td>
                            <td class="adm-strong">${esc(c.nombre)}</td>
                            <td class="adm-texto-suave">${esc(c.descripcion) || '—'}</td>
                            <td class="num adm-celda-num">
                                <button class="adm-btn adm-btn-edit adm-btn-sm btn-editar">Editar</button>
                                <button class="adm-btn adm-btn-del adm-btn-sm btn-eliminar">Eliminar</button>
                            </td>
                        `;

                        tr.querySelector('.btn-editar').addEventListener('click', () => {
                            modalTitle.textContent = 'Editar categoría #' + c.id;
                            document.getElementById('cat-id').value = c.id;
                            document.getElementById('cat-nombre').value = c.nombre;
                            document.getElementById('cat-descripcion').value = c.descripcion || '';
                            modal.classList.remove('hidden');
                            bloquearFondo(true);
                        });

                        tr.querySelector('.btn-eliminar').addEventListener('click', () => eliminarCategoria(c.id, c.nombre));

                        tbody.appendChild(tr);
                    });
                } else {
                    tbody.innerHTML = `<tr><td colspan="4" class="adm-empty">No hay categorías creadas.</td></tr>`;
                }
        }

        async function guardarCategoria() {
            const id = document.getElementById('cat-id').value;
            const payload = {
                nombre: document.getElementById('cat-nombre').value.trim(),
                descripcion: document.getElementById('cat-descripcion').value.trim()
            };

            try {
                if (id) {
                    await api.put(`/api/categorias/${id}`, payload);
                    showToast('Categoría actualizada', 'success');
                } else {
                    await api.post('/api/categorias', payload);
                    showToast('Categoría creada', 'success');
                }
                modal.classList.add('hidden');
                bloquearFondo(false);
                // creada: salta a la ultima pagina para verla
                if (!id) paginaActual = Number.MAX_SAFE_INTEGER;
                cargarCategorias();
            } catch (err) {
                showToast(err.message, 'error');
            }
        }

        async function eliminarCategoria(id, nombre) {
            if (!confirm(`¿Eliminar la categoría "${nombre}"?`)) return;
            try {
                await api.delete(`/api/categorias/${id}`);
                showToast('Categoría eliminada', 'info');
                // si era la ultima de la pagina, retrocede una
                const inicio = paginaActual * TAMANO_PAGINA;
                if (todasCategorias.slice(inicio, inicio + TAMANO_PAGINA).length <= 1 && paginaActual > 0) {
                    paginaActual -= 1;
                }
                cargarCategorias();
            } catch (err) {
                showToast(err.message, 'error');
            }
        }
