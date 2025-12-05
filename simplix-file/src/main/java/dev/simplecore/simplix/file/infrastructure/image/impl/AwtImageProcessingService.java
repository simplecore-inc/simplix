package dev.simplecore.simplix.file.infrastructure.image.impl;

import dev.simplecore.simplix.file.config.ImageProperties;
import dev.simplecore.simplix.file.infrastructure.exception.ImageProcessingException;
import dev.simplecore.simplix.file.infrastructure.image.ImageMetadata;
import dev.simplecore.simplix.file.infrastructure.image.ImageProcessingService;
import dev.simplecore.simplix.file.infrastructure.image.ProcessedImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;

/**
 * Java AWT-based implementation of ImageProcessingService.
 * <p>
 * Uses Java's built-in ImageIO for image processing without external dependencies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AwtImageProcessingService implements ImageProcessingService {

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/bmp",
        "image/webp"
    );

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/bmp",
        "image/webp",
        "image/svg+xml",
        "image/tiff"
    );

    private final ImageProperties imageProperties;

    @Override
    public ProcessedImage resizeIfExceeds(InputStream input, int maxWidth, int maxHeight) {
        try {
            byte[] imageBytes = input.readAllBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

            if (image == null) {
                throw new ImageProcessingException(
                    ImageProcessingException.ImageErrorCode.INVALID_IMAGE_FORMAT,
                    "Failed to read image"
                );
            }

            int originalWidth = image.getWidth();
            int originalHeight = image.getHeight();

            // Check if resize is needed
            if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
                // Return original
                return new ProcessedImage(imageBytes, "image/jpeg", originalWidth, originalHeight);
            }

            // Calculate new dimensions preserving aspect ratio
            double widthRatio = (double) maxWidth / originalWidth;
            double heightRatio = (double) maxHeight / originalHeight;
            double ratio = Math.min(widthRatio, heightRatio);

            int newWidth = (int) (originalWidth * ratio);
            int newHeight = (int) (originalHeight * ratio);

            BufferedImage resized = resizeImage(image, newWidth, newHeight);
            byte[] resultBytes = writeImage(resized, "jpg", imageProperties.getDefaultQuality());

            log.debug("Resized image from {}x{} to {}x{}", originalWidth, originalHeight, newWidth, newHeight);

            return new ProcessedImage(resultBytes, "image/jpeg", newWidth, newHeight);
        } catch (IOException e) {
            throw new ImageProcessingException(
                ImageProcessingException.ImageErrorCode.RESIZE_FAILED,
                "Failed to resize image",
                e
            );
        }
    }

    @Override
    public ProcessedImage resize(InputStream input, int maxWidth, int maxHeight, boolean preserveAspectRatio) {
        try {
            BufferedImage image = ImageIO.read(input);

            if (image == null) {
                throw new ImageProcessingException(
                    ImageProcessingException.ImageErrorCode.INVALID_IMAGE_FORMAT,
                    "Failed to read image"
                );
            }

            int newWidth;
            int newHeight;

            if (preserveAspectRatio) {
                double widthRatio = (double) maxWidth / image.getWidth();
                double heightRatio = (double) maxHeight / image.getHeight();
                double ratio = Math.min(widthRatio, heightRatio);

                newWidth = (int) (image.getWidth() * ratio);
                newHeight = (int) (image.getHeight() * ratio);
            } else {
                newWidth = maxWidth;
                newHeight = maxHeight;
            }

            BufferedImage resized = resizeImage(image, newWidth, newHeight);
            byte[] resultBytes = writeImage(resized, "jpg", imageProperties.getDefaultQuality());

            return new ProcessedImage(resultBytes, "image/jpeg", newWidth, newHeight);
        } catch (IOException e) {
            throw new ImageProcessingException(
                ImageProcessingException.ImageErrorCode.RESIZE_FAILED,
                "Failed to resize image",
                e
            );
        }
    }

    @Override
    public ProcessedImage generateThumbnail(InputStream input, int width, int height) {
        try {
            BufferedImage image = ImageIO.read(input);

            if (image == null) {
                throw new ImageProcessingException(
                    ImageProcessingException.ImageErrorCode.INVALID_IMAGE_FORMAT,
                    "Failed to read image"
                );
            }

            // Calculate crop dimensions to center-crop to target aspect ratio
            int originalWidth = image.getWidth();
            int originalHeight = image.getHeight();

            double targetRatio = (double) width / height;
            double originalRatio = (double) originalWidth / originalHeight;

            int cropWidth;
            int cropHeight;
            int cropX;
            int cropY;

            if (originalRatio > targetRatio) {
                // Original is wider, crop sides
                cropHeight = originalHeight;
                cropWidth = (int) (originalHeight * targetRatio);
                cropX = (originalWidth - cropWidth) / 2;
                cropY = 0;
            } else {
                // Original is taller, crop top/bottom
                cropWidth = originalWidth;
                cropHeight = (int) (originalWidth / targetRatio);
                cropX = 0;
                cropY = (originalHeight - cropHeight) / 2;
            }

            // Crop to target aspect ratio
            BufferedImage cropped = image.getSubimage(cropX, cropY, cropWidth, cropHeight);

            // Resize to target dimensions
            BufferedImage thumbnail = resizeImage(cropped, width, height);

            int quality = imageProperties.getThumbnail().getDefaultQuality();
            byte[] resultBytes = writeImage(thumbnail, "jpg", quality);

            log.debug("Generated thumbnail {}x{} from {}x{}", width, height, originalWidth, originalHeight);

            return new ProcessedImage(resultBytes, "image/jpeg", width, height);
        } catch (IOException e) {
            throw new ImageProcessingException(
                ImageProcessingException.ImageErrorCode.THUMBNAIL_GENERATION_FAILED,
                "Failed to generate thumbnail",
                e
            );
        }
    }

    @Override
    public ImageMetadata extractMetadata(InputStream input) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (!readers.hasNext()) {
                throw new ImageProcessingException(
                    ImageProcessingException.ImageErrorCode.INVALID_IMAGE_FORMAT,
                    "No image reader found for the input"
                );
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                String format = reader.getFormatName();

                return new ImageMetadata(width, height, format);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ImageProcessingException(
                ImageProcessingException.ImageErrorCode.METADATA_EXTRACTION_FAILED,
                "Failed to extract image metadata",
                e
            );
        }
    }

    @Override
    public boolean isImage(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return IMAGE_MIME_TYPES.contains(mimeType.toLowerCase());
    }

    @Override
    public boolean isSupported(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return SUPPORTED_MIME_TYPES.contains(mimeType.toLowerCase());
    }

    @Override
    public ProcessedImage convertToWebp(InputStream input, int quality, boolean lossless) {
        try {
            BufferedImage image = ImageIO.read(input);

            if (image == null) {
                throw new ImageProcessingException(
                    ImageProcessingException.ImageErrorCode.INVALID_IMAGE_FORMAT,
                    "Failed to read image"
                );
            }

            byte[] webpBytes = writeWebp(image, quality, lossless);

            log.debug("Converted image to WebP: {}x{}, quality={}, lossless={}, size={}",
                image.getWidth(), image.getHeight(), quality, lossless, webpBytes.length);

            return new ProcessedImage(webpBytes, "image/webp", image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new ImageProcessingException(
                ImageProcessingException.ImageErrorCode.CONVERSION_FAILED,
                "Failed to convert image to WebP",
                e
            );
        }
    }

    @Override
    public ProcessedImage optimizeAndConvertToWebp(InputStream input, int maxWidth, int maxHeight, int quality) {
        try {
            byte[] imageBytes = input.readAllBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

            if (image == null) {
                throw new ImageProcessingException(
                    ImageProcessingException.ImageErrorCode.INVALID_IMAGE_FORMAT,
                    "Failed to read image"
                );
            }

            int originalWidth = image.getWidth();
            int originalHeight = image.getHeight();
            BufferedImage processedImage = image;

            // Resize if needed
            if (originalWidth > maxWidth || originalHeight > maxHeight) {
                double widthRatio = (double) maxWidth / originalWidth;
                double heightRatio = (double) maxHeight / originalHeight;
                double ratio = Math.min(widthRatio, heightRatio);

                int newWidth = (int) (originalWidth * ratio);
                int newHeight = (int) (originalHeight * ratio);

                processedImage = resizeImage(image, newWidth, newHeight);

                log.debug("Resized image from {}x{} to {}x{}", originalWidth, originalHeight, newWidth, newHeight);
            }

            // Convert to WebP
            byte[] webpBytes = writeWebp(processedImage, quality, false);

            log.info("Optimized image: {}x{} -> {}x{}, WebP size={} bytes (original={} bytes, {}% reduction)",
                originalWidth, originalHeight,
                processedImage.getWidth(), processedImage.getHeight(),
                webpBytes.length, imageBytes.length,
                Math.round((1 - (double) webpBytes.length / imageBytes.length) * 100));

            return new ProcessedImage(webpBytes, "image/webp", processedImage.getWidth(), processedImage.getHeight());
        } catch (IOException e) {
            throw new ImageProcessingException(
                ImageProcessingException.ImageErrorCode.CONVERSION_FAILED,
                "Failed to optimize and convert image to WebP",
                e
            );
        }
    }

    @Override
    public boolean isWebpSupported() {
        // Check if WebP writer is available
        Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        return writers.hasNext();
    }

    // ==================== Private Methods ====================

    private BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();

        try {
            // Use high quality rendering hints
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Fill with white background (for transparent PNGs)
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, width, height);

            // Draw scaled image
            g2d.drawImage(original, 0, 0, width, height, null);
        } finally {
            g2d.dispose();
        }

        return resized;
    }

    private byte[] writeImage(BufferedImage image, String format, int quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            // For JPEG, we need to use ImageWriter for quality control
            Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (writers.hasNext()) {
                javax.imageio.ImageWriter writer = writers.next();
                try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
                    param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality / 100.0f);
                    writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
                } finally {
                    writer.dispose();
                }
            }
        } else {
            ImageIO.write(image, format, baos);
        }

        return baos.toByteArray();
    }

    private byte[] writeWebp(BufferedImage image, int quality, boolean lossless) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Ensure RGB format for WebP (no alpha for lossy)
        BufferedImage rgbImage = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB && !lossless) {
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = rgbImage.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();
        }

        Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new IOException("WebP writer not available. Ensure webp-imageio dependency is present.");
        }

        javax.imageio.ImageWriter writer = writers.next();
        try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);

            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                if (lossless) {
                    param.setCompressionType("Lossless");
                } else {
                    param.setCompressionType("Lossy");
                    param.setCompressionQuality(quality / 100.0f);
                }
            }

            writer.write(null, new javax.imageio.IIOImage(rgbImage, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }
}
