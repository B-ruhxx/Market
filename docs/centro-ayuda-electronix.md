# Portada, footer y centro de ayuda

## Presentación

- `home-stories.js` comparte las historias de inspiración con Apariencia.
  Solo reemplaza la lista exacta de fábrica; conserva ediciones y listas vacías.
- Las historias separan imagen y texto. Sus fotos se sirven localmente.
- `software-artwork.js` presenta ilustraciones específicas para las cuatro
  licencias de novedades si aún usan fotos genéricas exactas del seed.
  Una imagen personalizada no se reemplaza. No se escriben datos del producto.
- `ui/footer.js` y `storefront.css` comparten el footer claro en inicio,
  catálogo, ficha y ayuda. Los grupos se despliegan en móvil.
- `informacion.html` conserva todos los destinos anteriores del footer, con
  navegación por temas y preguntas desplegables.
- Los enlaces profundos abren privacidad y términos cuando corresponde.
- La atención usa el chat existente; sin sesión el enlace lleva a login.
  No se publican tarifas de envío, plazos de entrega o condiciones no confirmadas.

El hero y la composición del navbar aprobados no se rediseñaron.

## Pruebas

```sh
node --test scripts/tests/*.test.mjs
PLAYWRIGHT_MODULE=/ruta/a/node_modules/playwright node scripts/tests/home-help.browser.mjs
PLAYWRIGHT_MODULE=/ruta/a/node_modules/playwright node scripts/tests/product-ui.browser.mjs
git diff --check
```

Los navegadores comprueban tamaños 320–1920 px, tacto y teclado, footer,
enlaces de ayuda, imágenes coherentes y conservación de Apariencia. Carrito
y chat se simulan cuando corresponde para no crear pedidos o conversaciones.
