// Compartido por la tienda y Apariencia: el diseño inicial también se puede editar.
export const BANNERS_DEFECTO = [
    {
        etiqueta: 'Tu próximo nivel',
        titulo: 'Todo para tu mejor partida.',
        descripcion: 'Teclados, periféricos y potencia para tu setup.',
        boton: 'Explorar gaming',
        linkUrl: '/productos.html?categoria=Perif%C3%A9ricos',
        botonSecundario: 'Ver ofertas',
        linkSecundario: '/productos.html?ofertas=true',
        imagen: '/assets/img/promos/gaming.webp',
        estilo: 'oscuro',
        encuadre: 'producto'
    },
    {
        etiqueta: 'Lleva tus ideas más lejos',
        titulo: 'Ligera. Potente. Muy tuya.',
        descripcion: 'Encuentra una portátil que siga tu ritmo.',
        boton: 'Ver portátiles',
        linkUrl: '/productos.html?categoria=Laptops%20y%20Computadoras',
        botonSecundario: 'Explorar catálogo',
        linkSecundario: '/productos.html',
        imagen: '/assets/img/promos/portatil.webp',
        estilo: 'azul',
        encuadre: 'producto'
    },
    {
        etiqueta: 'Escucha cada detalle',
        titulo: 'Tu mundo. A otro volumen.',
        descripcion: 'Audífonos y sonido para conectar con lo que te mueve.',
        boton: 'Descubrir audio',
        linkUrl: '/productos.html?categoria=Audio%20y%20Sonido',
        botonSecundario: 'Ver ofertas',
        linkSecundario: '/productos.html?ofertas=true',
        imagen: '/assets/img/promos/audio.webp',
        estilo: 'claro',
        encuadre: 'producto'
    },
    {
        etiqueta: 'Siempre contigo',
        titulo: 'Mucho más que un smartphone.',
        descripcion: 'Conecta, crea y disfruta. Todo en tus manos.',
        boton: 'Ver smartphones',
        linkUrl: '/productos.html?categoria=Smartphones',
        botonSecundario: 'Explorar catálogo',
        linkSecundario: '/productos.html',
        imagen: '/assets/img/promos/smartphone.webp',
        estilo: 'oscuro',
        encuadre: 'producto'
    }
];

const BANNERS_LEGACY = [
    {
        etiqueta: '¡COMPRA AHORA!',
        titulo: 'LO MEJOR EN HARDWARE Y GAMING',
        descripcion: 'Encuentra periféricos y componentes con entrega inmediata y garantía oficial.',
        boton: 'Ver más',
        linkUrl: '/productos.html',
        imagen: 'https://images.unsplash.com/photo-1547394765-185e1e68f34e?w=900&auto=format&fit=crop&q=80',
        estilo: 'oscuro'
    },
    {
        etiqueta: 'NUEVA COLECCIÓN',
        titulo: 'PORTÁTILES ULTRALIVIANAS',
        descripcion: '',
        boton: 'Ver productos •',
        linkUrl: '/productos.html',
        imagen: 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=700&auto=format&fit=crop&q=80',
        estilo: 'azul'
    }
];

export function resolverBanners(banners) {
    if (!Array.isArray(banners)) return BANNERS_DEFECTO.map(b => ({ ...b }));
    // Solo actualiza la pareja exacta de fábrica. Nunca modifica bloques personalizados
    // ni repone los que el administrador haya eliminado intencionalmente.
    const esLegacy = banners.length === BANNERS_LEGACY.length && banners.every((banner, i) =>
        banner && Object.keys(banner).length === Object.keys(BANNERS_LEGACY[i]).length &&
        Object.entries(BANNERS_LEGACY[i]).every(([clave, valor]) => banner[clave] === valor)
    );
    return (esLegacy ? BANNERS_DEFECTO : banners).map(b => ({ ...b }));
}
