# Imágenes del escaparate Electronix

Las imágenes de `src/main/resources/static/assets/img/promos/` se sirven
localmente: las promociones no dependen de cargar sus imágenes desde otra web.
Son fotografías de referencia de categorías, no fichas de un modelo específico.

## Fuentes

### Inspiración editorial

Las nuevas imágenes se sirven localmente en WEBP de 1400 × 1050 px, bajo la
[licencia de Unsplash](https://unsplash.com/license):

| Archivo | Fuente |
| --- | --- |
| `inspiracion-juego.webp` | https://images.unsplash.com/photo-1592840496694-26d035b52b48 |
| `inspiracion-audio.webp` | https://images.unsplash.com/photo-1546435770-a3e426bf472b |
| `inspiracion-movil.webp` | https://images.unsplash.com/photo-1512428559087-560fa5ceab42 |

Son fotografías editoriales de categorías, no fichas de modelos específicos.
Las tres imágenes suman aproximadamente 91 KiB. Los textos y fotografías se
pueden cambiar desde **Admin → Apariencia → Tarjetas destacadas**. Solo se
sustituye automáticamente la configuración exacta de fábrica; se conservan
listas vacías, imágenes propias y cualquier otro cambio.

### Ilustraciones de licencias

Los cuatro SVG de `assets/img/software/` son composiciones vectoriales propias
que identifican ESET NOD32, AutoCAD LT, Adobe Creative Cloud y Windows 11 Pro.
No son fotografías, empaques oficiales ni certificados de distribución.
Los nombres y marcas identifican sus respectivos productos y propietarios.

`software-artwork.js` sustituye únicamente las tres fotografías genéricas
exactas del seed cuando corresponden a esas licencias. La presentación se
comparte entre inicio, catálogo y ficha, sin modificar registros, precios,
stock ni la selección de novedades. Una imagen propia cargada en Admin →
Productos prevalece y no se reemplaza. Otras familias de software no cambian.

Fotografías de Unsplash, utilizadas bajo su [licencia](https://unsplash.com/license):

| Archivo | Fotografía original |
| --- | --- |
| `gaming.webp` | https://images.unsplash.com/photo-1618384887929-16ec33fab9ef |
| `portatil.webp` | https://images.unsplash.com/photo-1496181133206-80ce9b88a853 |
| `audio.webp` | https://images.unsplash.com/photo-1583394838336-acd977736f90 |
| `smartphone.webp` | https://images.unsplash.com/photo-1603891128711-11b4b03bb138 |

Se recortaron y se retiró el fondo de las fotografías para integrarlas en el
escaparate. La pantalla de la portátil incorpora un degradado abstracto propio.
Los archivos se optimizaron a WEBP con transparencia.

### Fotografía del hero

La apertura utiliza `hero-espacio.webp`, una fotografía de ambiente de
[Unsplash](https://images.unsplash.com/photo-1593062096033-9a26b09da705),
bajo su [licencia](https://unsplash.com/license). Se sirve localmente en WEBP
de 2400 × 1600 px (aproximadamente 126 KiB). El escritorio, el teléfono y el
equipo son referenciales; no representan un producto ni un conjunto a la venta.
El degradado de contraste se aplica con CSS, sin modificar la fotografía.

Los archivos de los borradores anteriores se conservan para no romper enlaces
guardados en diseños personalizados:

`hero-portatil.webp` utiliza la fotografía
[MacBook Air (3rd generation, space gray), de NiloGlock](https://commons.wikimedia.org/wiki/File:MacBook_Air_(3rd_generation,_space_gray).png),
publicada bajo [CC0](https://creativecommons.org/publicdomain/zero/1.0/).
Se recortó el espacio transparente, se optimizó a WEBP de 1800 px y se añadió
una pantalla abstracta propia. Es una imagen referencial de la categoría, no una
promesa de disponibilidad de ese modelo. No se utiliza la miniatura del bloque
promocional como imagen ampliada del hero.

La cabecera y el hero usan Inter, servida localmente como `Electronix UI`.
Su licencia SIL Open Font License se incluye en `assets/fonts/OFL-Inter.txt`.

El modo **Ambiente / texto integrado a la derecha** del editor permite cambiar
la apertura sin perder el diseño. Se conservan también los modos de fotografía
centrada, producto y fondos de color. Solo se actualizan automáticamente las
configuraciones exactas de fábrica o de los borradores anteriores; cualquier
personalización se conserva y no se escribe en la base al visitar la página.

## Edición y compatibilidad

En **Admin → Apariencia → Banners promocionales** se pueden cambiar textos,
enlaces, imágenes, fondo, encuadre y un segundo botón opcional. El modo
**Producto completo** conserva la silueta sin recortarla; **Fotografía a todo
el ancho** mantiene el uso de fotografías convencionales.

Los dos banners originales, únicamente si siguen exactamente como venían de
fábrica, se presentan con el nuevo diseño de cuatro bloques. La actualización es
visual y no escribe en la base de datos. Los banners personalizados y las listas
vacías se conservan. Al guardar la sección se publica lo que muestra el editor.

El footer se comparte entre inicio, catálogo y detalle. En pantallas de hasta
640 px el directorio utiliza desplegables nativos, accesibles por teclado.

Pruebas de configuración y compatibilidad (Node.js 22 o superior):

```bash
node --test scripts/tests/home-banners.test.mjs
node --test scripts/tests/home-hero.test.mjs
```
