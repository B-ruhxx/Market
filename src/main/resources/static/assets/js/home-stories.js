// Colecciones editoriales compartidas con Apariencia. Las ediciones propias se conservan.
export const INSPIRACION_DEFECTO = [
    {
        kicker: 'Tiempo para ti', titulo: 'Una pausa. Otra partida.',
        descripcion: 'Consolas y mandos para disfrutar a tu manera.',
        boton: 'Explorar gaming', linkUrl: '/productos.html?categoria=Gaming%20y%20Consolas',
        imagen: '/assets/img/promos/inspiracion-juego.webp',
        imagenAlt: 'Mando de videojuegos sobre una superficie clara.'
    },
    {
        kicker: 'Tu música', titulo: 'Dale espacio a lo que escuchas.',
        descripcion: 'Encuentra el audio que acompaña tus momentos.',
        boton: 'Descubrir audio', linkUrl: '/productos.html?categoria=Audio%20y%20Sonido',
        imagen: '/assets/img/promos/inspiracion-audio.webp',
        imagenAlt: 'Audífonos negros junto a un teclado sobre un escritorio claro.'
    },
    {
        kicker: 'Siempre contigo', titulo: 'Conecta con tu día.',
        descripcion: 'Tecnología para lo que haces, donde estés.',
        boton: 'Ver smartphones', linkUrl: '/productos.html?categoria=Smartphones',
        imagen: '/assets/img/promos/inspiracion-movil.webp',
        imagenAlt: 'Persona utilizando un teléfono móvil.'
    }
];

const foto = id => `https://images.unsplash.com/photo-${id}?w=900&auto=format&fit=crop&q=80`;
const ANTERIORES = [
    { kicker: 'Gaming', titulo: 'Noches gamer sin lag', descripcion: 'Arma tu setup con monitores 144Hz y periféricos pro.', boton: 'Ver colección', linkUrl: '/productos.html?categoria=Monitores', imagen: foto('1550745165-9bc0b252726f') },
    { kicker: 'Audio', titulo: 'Sonido que te envuelve', descripcion: 'Audífonos y parlantes con cancelación de ruido.', boton: 'Escuchar ahora', linkUrl: '/productos.html?categoria=Audio%20y%20Sonido', imagen: foto('1505740420928-5e560c06d30e') },
    { kicker: 'Móviles', titulo: 'Smartphones destacados', descripcion: 'Lo último en fotografía móvil y 5G.', boton: 'Descubrir', linkUrl: '/productos.html?categoria=Smartphones', imagen: foto('1511707171634-5f897ff02aa9') },
    { kicker: 'Smart Home', titulo: 'Tu casa inteligente', descripcion: 'Asistentes, focos y cámaras WiFi en un solo lugar.', boton: 'Explorar', linkUrl: '/productos.html', imagen: foto('1550009158-9ebf69173e03') },
    { kicker: 'Accesorios', titulo: 'Potencia tu PC', descripcion: 'Teclados, ratones y componentes al mejor precio.', boton: 'Ver más', linkUrl: '/productos.html?categoria=Accesorios%20M%C3%B3viles', imagen: foto('1527864550417-7fd91fc51a46') },
    { kicker: 'Cómputo', titulo: 'Portátiles para todo', descripcion: 'Ultralivianas, gamers y estaciones de trabajo.', boton: 'Comprar ahora', linkUrl: '/productos.html?categoria=Laptops%20y%20Computadoras', imagen: foto('1496181133206-80ce9b88a853') }
];

export function resolverInspiracion(bloques) {
    const fabrica = Array.isArray(bloques) && bloques.length === ANTERIORES.length && bloques.every((b, i) =>
        b && Object.keys(b).length === Object.keys(ANTERIORES[i]).length
        && Object.entries(ANTERIORES[i]).every(([clave, valor]) => b[clave] === valor));
    return structuredClone(!Array.isArray(bloques) || fabrica ? INSPIRACION_DEFECTO : bloques);
}
