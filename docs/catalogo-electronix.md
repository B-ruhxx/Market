# Catálogo Electronix

El catálogo, las novedades del inicio y los relacionados comparten tarjetas
planas de fondo gris cálido, definidas en `assets/css/ui/product-tiles.css`.
Mantienen precio y un botón «Agregar». La imagen y el nombre abren la ficha;
ya no hay un botón «Ver detalle».

## Comportamiento

- Categorías reales, búsqueda con espera breve y orden visible.
- Panel de categoría, precio máximo y ofertas. Los filtros son inmediatos.
- Filtros eliminables y URL que conserva búsqueda, categoría, precio, ofertas,
  orden y página al recargar. Se mantienen los alias anteriores de categorías.
- Paginación de 12 productos y cuadrícula de 2, 3 o 4 columnas.
- Cierre del panel con Escape, foco contenido y restaurado al salir.
- Estados de carga, sin resultados y fallo de conexión con reintento.
- Las búsquedas antiguas no sobrescriben resultados nuevos.
- Carrito existente con botón bloqueado mientras se agrega. No cambia sus API.
- Imagen local neutra cuando falta una foto; no se sustituye por otro producto.

Los productos, imágenes, monedas, stock y etiquetas provienen de la API
existente. El rediseño no crea ofertas ni modifica registros. Los precios
máximos se comparan en la moneda de cada producto, sin conversión.

## Ficha del producto

`producto.html`, `assets/css/pages/producto-premium.css` y
`assets/js/pages/producto.js` presentan galería e información en dos columnas,
sin contenedor redondeado ni caja adicional de compra. En móvil pasan a una
columna. Hay miniaturas navegables por teclado cuando el producto tiene varias
imágenes, límites de cantidad según stock y botones bloqueados durante la compra.

Las ofertas e impuestos adicionales usan los campos reales de la API. Se
retiraron las cuotas sin interés, envío gratis y garantías que antes se
anunciaban con valores fijos. La descripción del administrador se conserva.
«Comprar ahora» solo abre checkout si el agregado al carrito fue exitoso; sin
sesión se vuelve al producto exacto después de iniciar sesión, conservando su ID.

Las fotografías genéricas del inventario se conservan: no se reemplazan por
recortes de modelos diferentes ni se presentan como fotografías verificadas
del modelo. Para completar ese acabado se deben cargar fotos adecuadas desde
Admin → Productos. El cambio visual no escribe en la base de datos.

## Verificación

```sh
node --test scripts/tests/*.test.mjs
git diff --check
```

Prueba opcional de navegador, con Playwright instalado fuera del proyecto:

```sh
PLAYWRIGHT_MODULE=/ruta/a/node_modules/playwright node scripts/tests/product-ui.browser.mjs
```

Además se comprobó en Chromium: tamaños 320–1920 px, interacción táctil,
teclado, filtros, orden, paginación, recarga de enlaces y recuperación de
errores. Las pruebas de agregado al carrito usaron respuestas simuladas
para no escribir en la base de datos.
