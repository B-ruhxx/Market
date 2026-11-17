// Sustituye únicamente las fotos genéricas de fábrica de estas cuatro licencias.
// Una foto propia subida en Productos siempre prevalece. No escribe en la base.
const FOTOS_DEMO = new Set([
    'https://images.unsplash.com/photo-1550439062-609e1531270e?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1517180102446-f3ece451e9d8?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1461749280684-dccba630e2f6?w=600&auto=format&fit=crop&q=80'
]);
const ARTES = [
    [/^antivirus eset nod32 3 equipos(?: pro)?$/, 'eset-nod32'],
    [/^autocad lt 1 ano(?: pro)?$/, 'autocad-lt'],
    [/^adobe creative cloud 1 mes(?: pro)?$/, 'creative-cloud'],
    [/^licencia windows 11 pro(?: pro)?$/, 'windows-11-pro']
];

export function imagenSoftware(producto, fuente) {
    if (!FOTOS_DEMO.has(fuente)) return fuente;
    const nombre = String(producto.nombre || '').toLowerCase().normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '').trim();
    const arte = ARTES.find(([patron]) => patron.test(nombre));
    return arte ? `/assets/img/software/${arte[1]}.svg` : fuente;
}
