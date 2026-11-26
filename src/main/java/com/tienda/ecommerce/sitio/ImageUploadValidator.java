package com.tienda.ecommerce.sitio;

import com.tienda.ecommerce.common.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Valida archivos de imagen subidos por magic bytes, dimensiones máximas y estructura real,
 * re-codificando PNG y JPEG para eliminar metadatos EXIF y posibles cargas políglotas.
 */
final class ImageUploadValidator {

    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_DIMENSION = 4096;
    static final long MAX_PIXELS = 8_388_608L;
    private static final Set<String> WEBP_ALLOWED_CHUNKS = Set.of("VP8 ", "VP8L", "VP8X", "ALPH");
    private static final Semaphore DECODE_SEMAPHORE = new Semaphore(4);

    record ValidatedImage(byte[] bytes, String contentType, String extension) {
    }

    private ImageUploadValidator() {
    }

    static ValidatedImage validarYSanitizar(MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            throw new BadRequestException("Archivo vacío");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new BadRequestException("El archivo supera los 5MB");
        }
        String tipo = archivo.getContentType();
        String mime = tipo == null ? "" : tipo.toLowerCase(Locale.ROOT).trim();

        byte[] raw = leerAcotado(archivo);
        if (raw.length == 0) {
            throw new BadRequestException("Archivo vacío");
        }

        return switch (mime) {
            case "image/png" -> procesarImageIo(raw, "png", "image/png", ".png");
            case "image/jpeg" -> procesarImageIo(raw, "jpeg", "image/jpeg", ".jpg");
            case "image/webp" -> validarWebp(raw);
            default -> throw new BadRequestException("Solo se permiten PNG, JPG o WEBP");
        };
    }

    private static byte[] leerAcotado(MultipartFile archivo) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(Math.max(archivo.getSize(), 256), MAX_BYTES));
        byte[] buffer = new byte[8192];
        int total = 0;
        try (InputStream in = archivo.getInputStream()) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > MAX_BYTES) {
                    throw new BadRequestException("El archivo supera los 5MB");
                }
                out.write(buffer, 0, n);
            }
        }
        return out.toByteArray();
    }

    private static ValidatedImage procesarImageIo(
            byte[] raw,
            String formatName,
            String mime,
            String extension
    ) throws IOException {
        if ("png".equals(formatName) && !esFirmaPng(raw)) {
            throw new BadRequestException("El contenido no corresponde a una imagen PNG válida");
        }
        if ("jpeg".equals(formatName) && !esFirmaJpeg(raw)) {
            throw new BadRequestException("El contenido no corresponde a una imagen JPEG válida");
        }

        ImageIO.setUseCache(false);
        try {
            DECODE_SEMAPHORE.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Procesamiento de imagen interrumpido");
        }
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            if (iis == null) {
                throw new BadRequestException("No se pudo leer la imagen");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(formatName);
            if (!readers.hasNext()) {
                throw new BadRequestException("Formato de imagen no soportado");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validarDimensiones(width, height);

                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new BadRequestException("Imagen corrupta o inválida");
                }
                BufferedImage outputImg = "jpeg".equals(formatName) ? asegurarSinAlfa(decoded) : decoded;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                String writeFormat = "jpeg".equals(formatName) ? "jpg" : "png";
                if (!ImageIO.write(outputImg, writeFormat, baos)) {
                    throw new BadRequestException("No se pudo procesar la imagen");
                }
                byte[] clean = baos.toByteArray();
                if (clean.length == 0 || clean.length > MAX_BYTES) {
                    throw new BadRequestException("La imagen procesada supera el tamaño permitido");
                }
                return new ValidatedImage(clean, mime, extension);
            } catch (RuntimeException ex) {
                if (ex instanceof BadRequestException bad) {
                    throw bad;
                }
                throw new BadRequestException("Imagen corrupta o inválida");
            } finally {
                reader.dispose();
            }
        } finally {
            DECODE_SEMAPHORE.release();
        }
    }

    private static BufferedImage asegurarSinAlfa(BufferedImage src) {
        if (!src.getColorModel().hasAlpha() && src.getType() != BufferedImage.TYPE_CUSTOM) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static ValidatedImage validarWebp(byte[] raw) {
        if (raw.length < 26
                || raw[0] != 'R' || raw[1] != 'I' || raw[2] != 'F' || raw[3] != 'F'
                || raw[8] != 'W' || raw[9] != 'E' || raw[10] != 'B' || raw[11] != 'P') {
            throw new BadRequestException("El contenido no corresponde a una imagen WEBP válida");
        }
        long riffSize = uint32le(raw, 4);
        if (riffSize < 18 || riffSize + 8L != raw.length) {
            throw new BadRequestException("Estructura WEBP inválida");
        }

        int width = -1;
        int height = -1;
        boolean hasPixelBitstream = false;
        int offset = 12;
        while (offset + 8 <= raw.length) {
            String fourCc = new String(
                    new byte[]{raw[offset], raw[offset + 1], raw[offset + 2], raw[offset + 3]},
                    java.nio.charset.StandardCharsets.US_ASCII
            );
            if (!WEBP_ALLOWED_CHUNKS.contains(fourCc)) {
                throw new BadRequestException("Bloque WEBP no permitido (" + fourCc.trim() + ")");
            }
            long chunkSize = uint32le(raw, offset + 4);
            long paddedSize = chunkSize + (chunkSize & 1L);
            int dataStart = offset + 8;
            if (chunkSize < 0 || dataStart + paddedSize > raw.length) {
                throw new BadRequestException("Estructura de bloques WEBP inválida");
            }

            if ("VP8 ".equals(fourCc)) {
                if (chunkSize < 10
                        || (raw[dataStart] & 1) != 0
                        || raw[dataStart + 3] != (byte) 0x9D
                        || raw[dataStart + 4] != (byte) 0x01
                        || raw[dataStart + 5] != (byte) 0x2A) {
                    throw new BadRequestException("Bloque VP8 WEBP inválido");
                }
                int frameWidth = ((raw[dataStart + 7] & 0x3F) << 8) | (raw[dataStart + 6] & 0xFF);
                int frameHeight = ((raw[dataStart + 9] & 0x3F) << 8) | (raw[dataStart + 8] & 0xFF);
                validarDimensiones(frameWidth, frameHeight);
                if (width >= 0 && (width != frameWidth || height != frameHeight)) {
                    throw new BadRequestException("Dimensiones WEBP inconsistentes");
                }
                width = frameWidth;
                height = frameHeight;
                hasPixelBitstream = true;
            } else if ("VP8L".equals(fourCc)) {
                if (chunkSize < 5 || raw[dataStart] != 0x2F) {
                    throw new BadRequestException("Bloque VP8L WEBP inválido");
                }
                long bits = uint32le(raw, dataStart + 1);
                int frameWidth = (int) (bits & 0x3FFFL) + 1;
                int frameHeight = (int) ((bits >> 14) & 0x3FFFL) + 1;
                validarDimensiones(frameWidth, frameHeight);
                if (width >= 0 && (width != frameWidth || height != frameHeight)) {
                    throw new BadRequestException("Dimensiones WEBP inconsistentes");
                }
                width = frameWidth;
                height = frameHeight;
                hasPixelBitstream = true;
            } else if ("VP8X".equals(fourCc)) {
                if (chunkSize < 10) {
                    throw new BadRequestException("Bloque VP8X WEBP inválido");
                }
                int canvasWidth = 1 + uint24le(raw, dataStart + 4);
                int canvasHeight = 1 + uint24le(raw, dataStart + 7);
                validarDimensiones(canvasWidth, canvasHeight);
                width = canvasWidth;
                height = canvasHeight;
            }

            offset = (int) (dataStart + paddedSize);
        }

        if (offset != raw.length || width <= 0 || height <= 0 || !hasPixelBitstream) {
            throw new BadRequestException("Estructura WEBP incompleta o sin flujo de imagen");
        }
        validarDimensiones(width, height);
        return new ValidatedImage(raw, "image/webp", ".webp");
    }

    private static void validarDimensiones(int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new BadRequestException("Dimensiones de imagen fuera de rango permitido (máx 4096x4096)");
        }
        if ((long) width * (long) height > MAX_PIXELS) {
            throw new BadRequestException("Resolución de imagen excesiva");
        }
    }

    private static boolean esFirmaPng(byte[] raw) {
        return raw.length >= 24
                && raw[0] == (byte) 0x89 && raw[1] == 0x50 && raw[2] == 0x4E && raw[3] == 0x47
                && raw[4] == 0x0D && raw[5] == 0x0A && raw[6] == 0x1A && raw[7] == 0x0A;
    }

    private static boolean esFirmaJpeg(byte[] raw) {
        return raw.length >= 4
                && raw[0] == (byte) 0xFF && raw[1] == (byte) 0xD8 && raw[2] == (byte) 0xFF;
    }

    private static long uint32le(byte[] b, int off) {
        return ((long) (b[off] & 0xFF))
                | ((long) (b[off + 1] & 0xFF) << 8)
                | ((long) (b[off + 2] & 0xFF) << 16)
                | ((long) (b[off + 3] & 0xFF) << 24);
    }

    private static int uint24le(byte[] b, int off) {
        return (b[off] & 0xFF)
                | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16);
    }
}
