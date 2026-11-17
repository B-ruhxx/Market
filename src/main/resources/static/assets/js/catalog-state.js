// Estado y consultas del catálogo. Funciones puras, sin acceso al DOM ni escrituras.
export const PRECIO_SIN_LIMITE = 10000;
export const ORDEN_DEFECTO = 'fechaCreacion:desc';
export const ORDENES = {
    recientes: ORDEN_DEFECTO,
    'precio-asc': 'precio:asc',
    'precio-desc': 'precio:desc',
    nombre: 'nombre:asc'
};

export function leerEstadoCatalogo(search) {
    const params = new URLSearchParams(search);
    const precio = params.get('precioMax');
    const numero = precio === null || precio === '' ? NaN : Number(precio);
    const pagina = Number(params.get('pagina'));
    const ofertas = params.has('ofertas') && params.get('ofertas') !== 'false';
    const orden = params.get('orden');
    return {
        nombre: params.get('q') || '',
        categoriaId: params.get('categoriaId') || '',
        categoria: params.get('categoria') || '',
        precioMax: Number.isFinite(numero) && numero >= 0 && numero < PRECIO_SIN_LIMITE
            ? Math.round(numero / 100) * 100 : PRECIO_SIN_LIMITE,
        ofertas,
        orden: Object.hasOwn(ORDENES, orden) ? ORDENES[orden] : (ofertas ? 'precio:asc' : ORDEN_DEFECTO),
        pagina: Number.isSafeInteger(pagina) && pagina > 0 ? pagina - 1 : 0
    };
}

export function consultaCatalogo(estado, tamano = 12) {
    const orden = Object.values(ORDENES).includes(estado.orden) ? estado.orden : ORDEN_DEFECTO;
    const [sortBy, direction] = orden.split(':');
    const params = new URLSearchParams({ page: estado.pagina, size: tamano, sortBy, direction });
    if (estado.nombre.trim()) params.set('nombre', estado.nombre.trim());
    if (estado.categoriaId) params.set('categoriaId', estado.categoriaId);
    if (estado.precioMax < PRECIO_SIN_LIMITE) params.set('precioMax', estado.precioMax);
    if (estado.ofertas) params.set('etiqueta', 'OFERTA');
    return `/api/productos?${params}`;
}

export function urlCatalogo(estado, href) {
    const url = new URL(href);
    ['q', 'categoria', 'categoriaId', 'precioMax', 'ofertas', 'orden', 'pagina'].forEach(key => url.searchParams.delete(key));
    if (estado.nombre.trim()) url.searchParams.set('q', estado.nombre.trim());
    if (estado.categoriaId) url.searchParams.set('categoriaId', estado.categoriaId);
    if (estado.precioMax < PRECIO_SIN_LIMITE) url.searchParams.set('precioMax', estado.precioMax);
    if (estado.ofertas) url.searchParams.set('ofertas', 'true');
    // Explicitar el orden evita que ofertas vuelva a elegir otro al recargar.
    if (estado.orden !== ORDEN_DEFECTO || estado.ofertas) {
        const codigo = Object.keys(ORDENES).find(key => ORDENES[key] === estado.orden);
        if (codigo) url.searchParams.set('orden', codigo);
    }
    if (estado.pagina > 0) url.searchParams.set('pagina', estado.pagina + 1);
    return url;
}

const normalizar = valor => String(valor || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim();
const ALIAS = {
    televisores: 'monitores', laptops: 'laptops y computadoras', portatiles: 'laptops y computadoras',
    audio: 'audio y sonido', 'equipo de sonido': 'audio y sonido',
    accesorios: 'accesorios moviles', 'accesorios y pc': 'accesorios moviles'
};

export function resolverCategoria(categorias, nombre) {
    const buscado = normalizar(nombre);
    if (!buscado) return null;
    return categorias.find(c => normalizar(c.nombre) === buscado)
        || categorias.find(c => normalizar(c.nombre) === ALIAS[buscado])
        || categorias.find(c => normalizar(c.nombre).includes(buscado))
        || null;
}
