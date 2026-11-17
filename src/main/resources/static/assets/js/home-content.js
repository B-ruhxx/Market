// Presentación inicial compartida con Apariencia. No escribe en la base de datos.
export const HERO_DEFECTO = [{
    titulo: 'Tu espacio.',
    subtitulo: 'Tus posibilidades.',
    descripcion: 'Para trabajar, jugar y hacer más de lo que te gusta. Encuentra la tecnología que encaja contigo.',
    imagen: '/assets/img/promos/hero-espacio.webp',
    imagenAlt: 'Escritorio de madera con una portátil, un teléfono y un altavoz.',
    imagenModo: 'ambiente',
    fondoTipo: 'imagen',
    fondoColor1: '#202629',
    fondoColor2: '#202629',
    mostrarTexto: true,
    textoBoton: 'Encuentra tu equipo',
    linkUrl: '/productos.html'
}];

export const ACCESOS_DEFECTO = [
    { titulo: 'Ofertas', descripcion: 'Una buena oportunidad.', icono: '', boton: 'Explorar', linkUrl: '/productos.html?ofertas=true' },
    { titulo: 'Novedades', descripcion: 'Lo último en llegar.', icono: '', boton: 'Descubrir', linkUrl: '/productos.html?orden=recientes' },
    { titulo: 'El catálogo', descripcion: 'Encuentra tu próximo equipo.', icono: '', boton: 'Ver productos', linkUrl: '/productos.html' },
    { titulo: 'Asesoría', descripcion: 'Elige con más confianza.', icono: '', boton: 'Consultar', linkUrl: '/informacion.html#chat' },
    { titulo: 'Tu compra', descripcion: 'Te acompañamos en cada paso.', icono: '', boton: 'Conocer más', linkUrl: '/informacion.html#despachos' }
];

export const EQUIPOS_INICIALES = [
    { nombre: 'Portátiles', descripcion: 'Ideas en movimiento.', producto: '/assets/img/promos/portatil.webp', categoria: 'Laptops y Computadoras' },
    { nombre: 'Smartphones', descripcion: 'Tu mundo, conectado.', producto: '/assets/img/promos/smartphone.webp', categoria: 'Smartphones' },
    { nombre: 'Audio', descripcion: 'Escucha algo diferente.', producto: '/assets/img/promos/audio.webp', categoria: 'Audio y Sonido' },
    { nombre: 'Periféricos', descripcion: 'Cada detalle cuenta.', producto: '/assets/img/promos/gaming.webp', categoria: 'Periféricos' },
    { nombre: 'Monitores', descripcion: 'Amplía tus posibilidades.', producto: '/assets/img/promos/monitor.svg', categoria: 'Monitores' }
].map(equipo => ({
    ...equipo, fondo: '', fondoColor: '#F0EFEC', imagen: '', icono: '',
    cta: 'Explorar', presentacion: 'compacta'
}));

const HERO_ANTERIOR = [
    { titulo: 'SUPER OFERTAS', subtitulo: 'ESPECIALES', imagen: 'https://images.unsplash.com/photo-1547394765-185e1e68f34e?w=1400&auto=format&fit=crop&q=80', fondoTipo: 'imagen', fondoColor1: '#000000', fondoColor2: '#000000', mostrarTexto: true, textoBoton: '', linkUrl: '/productos.html?ofertas=true' },
    { titulo: 'TECNOLOGÍA GAMER', subtitulo: 'HASTA 40% OFF', imagen: 'https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=1400&auto=format&fit=crop&q=80', fondoTipo: 'imagen', fondoColor1: '#000000', fondoColor2: '#000000', mostrarTexto: true, textoBoton: 'Ver ofertas', linkUrl: '/productos.html?categoria=Monitores' },
    { titulo: 'AUDIO Y VIDEO', subtitulo: 'ENVÍO GRATIS', imagen: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=1400&auto=format&fit=crop&q=80', fondoTipo: 'imagen', fondoColor1: '#000000', fondoColor2: '#000000', mostrarTexto: true, textoBoton: '', linkUrl: '/productos.html' }
];
const HERO_PRIMERA_PROPUESTA = [{
    titulo: 'Tecnología para', subtitulo: 'lo que sigue.',
    descripcion: 'Para las ideas que creas, las partidas que juegas y todo lo que viene después. Encuentra el equipo que va contigo.',
    imagen: '/assets/img/promos/portatil.webp', imagenModo: 'producto', fondoTipo: 'imagen',
    fondoColor1: '#14191F', fondoColor2: '#14191F', mostrarTexto: true,
    textoBoton: 'Explorar productos', linkUrl: '/productos.html'
}];
const HERO_SEGUNDA_PROPUESTA = [{
    titulo: 'Tecnología para', subtitulo: 'lo que sigue.',
    descripcion: 'Portátiles, audio y más. Encuentra el equipo que va contigo.',
    imagen: '/assets/img/promos/hero-portatil.webp', imagenModo: 'producto', fondoTipo: 'imagen',
    fondoColor1: '#F5F5F7', fondoColor2: '#F5F5F7', mostrarTexto: true,
    textoBoton: 'Explorar productos', linkUrl: '/productos.html'
}];
const ACCESOS_ANTERIORES = [
    { titulo: 'OFERTAS', descripcion: '¡OFERTAS EN MILES DE PRODUCTOS PARA TI!', icono: '', boton: 'MAS INFORMACIÓN', linkUrl: '/productos.html?ofertas=true' },
    { titulo: '+ VENDIDOS', descripcion: 'PRODUCTOS MAS VENDIDOS EN ESTE MES', icono: '', boton: 'MAS INFORMACIÓN', linkUrl: '/productos.html?orden=recientes' },
    { titulo: 'RECOMENDACIÓN', descripcion: 'MEJORES PRODUCTOS RECOMENDADOS', icono: '', boton: 'VER', linkUrl: '/productos.html' },
    { titulo: 'SOPORTE', descripcion: '¿NECESITAS AYUDA?', icono: '', boton: 'AYUDA', linkUrl: '/productos.html' },
    { titulo: 'TIENDAS', descripcion: 'NUESTRAS TIENDAS FÍSICAS', icono: '', boton: 'VISITAR', linkUrl: '/productos.html' }
];
const foto = (id, ancho = 600) => `https://images.unsplash.com/photo-${id}?w=${ancho}&auto=format&fit=crop&q=80`;
const EQUIPOS_ANTERIORES = [
    ['TELEVISORES', 'Pantallas y monitores gamer', '1527443224154-c4a3942d3acf', '1593359677879-a4bb92f829d1', 'Televisores'],
    ['SMARTPHONES', 'Última generación y accesorios', '1511707171634-5f897ff02aa9', '1510557880182-3d4d3cba35a5', 'Smartphones'],
    ['PORTATILES', 'Ultralivianas, gamers y trabajo', '1496181133206-80ce9b88a853', '1496181133206-80ce9b88a853', 'Laptops'],
    ['EQUIPO DE SONIDO', 'Audífonos, parlantes y más', '1505740420928-5e560c06d30e', '1583394838336-acd977736f90', 'Audio'],
    ['ACCESORIOS Y PC', 'Teclados, ratones y componentes', '1587829741301-dc798b83add3', '1527864550417-7fd91fc51a46', 'Accesorios']
].map(([nombre, descripcion, fondo, producto, categoria]) => ({
    nombre, descripcion, fondo: foto(fondo), fondoColor: '#111827', producto: foto(producto, 500),
    imagen: foto(fondo), icono: '', cta: 'Ver productos', categoria
}));

function iguales(a, b) {
    if (a === b) return true;
    if (!a || !b || typeof a !== 'object' || typeof b !== 'object') return false;
    const claves = Object.keys(a);
    return claves.length === Object.keys(b).length && claves.every(clave =>
        Object.hasOwn(b, clave) && iguales(a[clave], b[clave])
    );
}

function resolver(bloques, anteriores, nuevos, alternativas = []) {
    // Una edición mínima (incluidas capas del Estudio o campos nuevos) se conserva.
    const resultado = !Array.isArray(bloques) || iguales(bloques, anteriores)
        || alternativas.some(alternativa => iguales(bloques, alternativa)) ? nuevos : bloques;
    return structuredClone(resultado);
}

export const resolverHero = bloques => resolver(bloques, HERO_ANTERIOR, HERO_DEFECTO, [HERO_PRIMERA_PROPUESTA, HERO_SEGUNDA_PROPUESTA]);
export const resolverAccesos = bloques => resolver(bloques, ACCESOS_ANTERIORES, ACCESOS_DEFECTO);
export const resolverEquipos = bloques => resolver(bloques, EQUIPOS_ANTERIORES, EQUIPOS_INICIALES);
