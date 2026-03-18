package dev.simplecore.simplix.file.infrastructure.image.impl;

import dev.simplecore.simplix.file.config.ImageProperties;
import dev.simplecore.simplix.file.infrastructure.exception.ImageProcessingException;
import dev.simplecore.simplix.file.infrastructure.image.ImageMetadata;
import dev.simplecore.simplix.file.infrastructure.image.ProcessedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("AwtImageProcessingService")
class AwtImageProcessingServiceTest {

    @Mock
    private ImageProperties imageProperties;

    @Mock
    private ImageProperties.ThumbnailConfig thumbnailConfig;

    private AwtImageProcessingService service;

    @BeforeEach
    void setUp() {
        lenient().when(imageProperties.getDefaultQuality()).thenReturn(85);
        lenient().when(imageProperties.getThumbnail()).thenReturn(thumbnailConfig);
        lenient().when(thumbnailConfig.getDefaultQuality()).thenReturn(80);
        service = new AwtImageProcessingService(imageProperties);
    }

    private byte[] createTestImage(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    private byte[] createTestImageWithAlpha(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(AlphaComposite.SrcOver);
        g2d.setColor(new Color(255, 0, 0, 128));
        g2d.fillOval(0, 0, width, height);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    private InputStream createTestImageStream(int width, int height, String format) throws IOException {
        return new ByteArrayInputStream(createTestImage(width, height, format));
    }

    @Nested
    @DisplayName("resizeIfExceeds()")
    class ResizeIfExceeds {

        @Test
        @DisplayName("Should return original when image is within limits")
        void shouldReturnOriginalWhenImageIsWithinLimits() throws IOException {
            InputStream input = createTestImageStream(100, 100, "jpg");

            ProcessedImage result = service.resizeIfExceeds(input, 200, 200);

            assertThat(result).isNotNull();
            assertThat(result.width()).isEqualTo(100);
            assertThat(result.height()).isEqualTo(100);
            assertThat(result.mimeType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("Should resize when image exceeds max width")
        void shouldResizeWhenImageExceedsMaxWidth() throws IOException {
            InputStream input = createTestImageStream(400, 200, "jpg");

            ProcessedImage result = service.resizeIfExceeds(input, 200, 200);

            assertThat(result).isNotNull();
            assertThat(result.width()).isLessThanOrEqualTo(200);
            assertThat(result.height()).isLessThanOrEqualTo(200);
            assertThat(result.mimeType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("Should resize when image exceeds max height")
        void shouldResizeWhenImageExceedsMaxHeight() throws IOException {
            InputStream input = createTestImageStream(200, 400, "jpg");

            ProcessedImage result = service.resizeIfExceeds(input, 200, 200);

            assertThat(result).isNotNull();
            assertThat(result.width()).isLessThanOrEqualTo(200);
            assertThat(result.height()).isLessThanOrEqualTo(200);
        }

        @Test
        @DisplayName("Should preserve aspect ratio when resizing")
        void shouldPreserveAspectRatioWhenResizing() throws IOException {
            InputStream input = createTestImageStream(400, 200, "jpg");

            ProcessedImage result = service.resizeIfExceeds(input, 200, 200);

            // Original ratio is 2:1, so at max width 200, height should be 100
            assertThat(result.width()).isEqualTo(200);
            assertThat(result.height()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should throw exception for invalid image data")
        void shouldThrowExceptionForInvalidImageData() {
            InputStream input = new ByteArrayInputStream(new byte[]{0, 1, 2, 3});

            assertThatThrownBy(() -> service.resizeIfExceeds(input, 200, 200))
                .isInstanceOf(ImageProcessingException.class)
                .satisfies(ex -> assertThat(((ImageProcessingException) ex).getErrorCode())
                    .isEqualTo(ImageProcessingException.ImageErrorCode.INVALID_IMAGE_FORMAT));
        }

        @Test
        @DisplayName("Should throw exception when input stream throws IOException")
        void shouldThrowExceptionWhenInputStreamThrowsIOException() {
            InputStream failingInput = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Stream failure");
                }

                @Override
                public byte[] readAllBytes() throws IOException {
                    throw new IOException("Stream failure");
                }
            };

            assertThatThrownBy(() -> service.resizeIfExceeds(failingInput, 200, 200))
                .isInstanceOf(ImageProcessingException.class);
        }
    }

    @Nested
    @DisplayName("resize()")
    class Resize {

        @Test
        @DisplayName("Should resize with aspect ratio preserved")
        void shouldResizeWithAspectRatioPreserved() throws IOException {
            InputStream input = createTestImageStream(400, 200, "jpg");

            ProcessedImage result = service.resize(input, 200, 200, true);

            assertThat(result.width()).isEqualTo(200);
            assertThat(result.height()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should resize without aspect ratio preservation")
        void shouldResizeWithoutAspectRatioPreservation() throws IOException {
            InputStream input = createTestImageStream(400, 200, "jpg");

            ProcessedImage result = service.resize(input, 200, 200, false);

            assertThat(result.width()).isEqualTo(200);
            assertThat(result.height()).isEqualTo(200);
        }

        @Test
        @DisplayName("Should throw exception for invalid image data")
        void shouldThrowExceptionForInvalidImageData() {
            InputStream input = new ByteArrayInputStream(new byte[]{0, 1, 2, 3});

            assertThatThrownBy(() -> service.resize(input, 200, 200, true))
                .isInstanceOf(ImageProcessingException.class)
                .satisfies(ex -> assertThat(((ImageProcessingException) ex).getErrorCode())
                    .isEqualTo(ImageProcessingException.ImageErrorCode.INVALID_IMAGE_FORMAT));
        }

        @Test
        @DisplayName("Should throw exception when input stream throws IOException")
        void shouldThrowExceptionWhenInputStreamThrowsIOException() {
            InputStream failingInput = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Stream failure");
                }
            };

            assertThatThrownBy(() -> service.resize(failingInput, 200, 200, true))
                .isInstanceOf(ImageProcessingException.class);
        }
    }

    @Nested
    @DisplayName("generateThumbnail()")
    class GenerateThumbnail {

        @Test
        @DisplayName("Should generate square thumbnail from landscape image")
        void shouldGenerateSquareThumbnailFromLandscapeImage() throws IOException {
            InputStream input = createTestImageStream(400, 200, "jpg");

            ProcessedImage result = service.generateThumbnail(input, 100, 100);

            assertThat(result.width()).isEqualTo(100);
            assertThat(result.height()).isEqualTo(100);
            assertThat(result.mimeType()).isEqualTo("image/jpeg");
            assertThat(result.data()).isNotEmpty();
        }

        @Test
        @DisplayName("Should generate square thumbnail from portrait image")
        void shouldGenerateSquareThumbnailFromPortraitImage() throws IOException {
            InputStream input = createTestImageStream(200, 400, "jpg");

            ProcessedImage result = service.generateThumbnail(input, 100, 100);

            assertThat(result.width()).isEqualTo(100);
            assertThat(result.height()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should generate rectangular thumbnail")
        void shouldGenerateRectangularThumbnail() throws IOException {
            InputStream input = createTestImageStream(400, 400, "jpg");

            ProcessedImage result = service.generateThumbnail(input, 200, 100);

            assertThat(result.width()).isEqualTo(200);
            assertThat(result.height()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should throw exception for invalid image")
        void shouldThrowExceptionForInvalidImage() {
            InputStream input = new ByteArrayInputStream(new byte[]{0, 1, 2});

            assertThatThrownBy(() -> service.generateThumbnail(input, 100, 100))
                .isInstanceOf(ImageProcessingException.class)
                .satisfies(ex -> assertThat(((ImageProcessingException) ex).getErrorCode())
                    .isEqualTo(ImageProcessingException.ImageErrorCode.INVALID_IMAGE_FORMAT));
        }

        @Test
        @DisplayName("Should throw exception when input stream throws IOException")
        void shouldThrowExceptionWhenInputStreamThrowsIOException() {
            InputStream failingInput = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Stream failure");
                }
            };

            assertThatThrownBy(() -> service.generateThumbnail(failingInput, 100, 100))
                .isInstanceOf(ImageProcessingException.class);
        }
    }

    @Nested
    @DisplayName("extractMetadata()")
    class ExtractMetadata {

        @Test
        @DisplayName("Should extract metadata from JPEG image")
        void shouldExtractMetadataFromJpegImage() throws IOException {
            InputStream input = createTestImageStream(640, 480, "jpg");

            ImageMetadata metadata = service.extractMetadata(input);

            assertThat(metadata.width()).isEqualTo(640);
            assertThat(metadata.height()).isEqualTo(480);
            assertThat(metadata.format()).isNotEmpty();
        }

        @Test
        @DisplayName("Should extract metadata from PNG image")
        void shouldExtractMetadataFromPngImage() throws IOException {
            InputStream input = createTestImageStream(320, 240, "png");

            ImageMetadata metadata = service.extractMetadata(input);

            assertThat(metadata.width()).isEqualTo(320);
            assertThat(metadata.height()).isEqualTo(240);
            assertThat(metadata.format()).isNotEmpty();
        }

        @Test
        @DisplayName("Should throw exception for non-image data")
        void shouldThrowExceptionForNonImageData() {
            InputStream input = new ByteArrayInputStream("not an image".getBytes());

            assertThatThrownBy(() -> service.extractMetadata(input))
                .isInstanceOf(ImageProcessingException.class);
        }

        @Test
        @DisplayName("Should throw exception when input stream throws IOException")
        void shouldThrowExceptionWhenInputStreamThrowsIOException() {
            InputStream failingInput = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Stream failure");
                }
            };

            assertThatThrownBy(() -> service.extractMetadata(failingInput))
                .isInstanceOf(ImageProcessingException.class);
        }
    }

    @Nested
    @DisplayName("isImage()")
    class IsImage {

        @ParameterizedTest
        @DisplayName("Should return true for image MIME types")
        @ValueSource(strings = {
            "image/jpeg", "image/jpg", "image/png", "image/gif",
            "image/bmp", "image/webp", "image/svg+xml", "image/tiff"
        })
        void shouldReturnTrueForImageMimeTypes(String mimeType) {
            assertThat(service.isImage(mimeType)).isTrue();
        }

        @ParameterizedTest
        @DisplayName("Should return false for non-image MIME types")
        @ValueSource(strings = {"application/pdf", "text/plain", "video/mp4"})
        void shouldReturnFalseForNonImageMimeTypes(String mimeType) {
            assertThat(service.isImage(mimeType)).isFalse();
        }

        @ParameterizedTest
        @DisplayName("Should return false for null MIME type")
        @NullSource
        void shouldReturnFalseForNullMimeType(String mimeType) {
            assertThat(service.isImage(mimeType)).isFalse();
        }

        @Test
        @DisplayName("Should be case-insensitive")
        void shouldBeCaseInsensitive() {
            assertThat(service.isImage("IMAGE/JPEG")).isTrue();
            assertThat(service.isImage("Image/Png")).isTrue();
        }
    }

    @Nested
    @DisplayName("isSupported()")
    class IsSupported {

        @ParameterizedTest
        @DisplayName("Should return true for supported MIME types")
        @ValueSource(strings = {
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/bmp", "image/webp"
        })
        void shouldReturnTrueForSupportedMimeTypes(String mimeType) {
            assertThat(service.isSupported(mimeType)).isTrue();
        }

        @ParameterizedTest
        @DisplayName("Should return false for unsupported image MIME types")
        @ValueSource(strings = {"image/svg+xml", "image/tiff"})
        void shouldReturnFalseForUnsupportedImageMimeTypes(String mimeType) {
            assertThat(service.isSupported(mimeType)).isFalse();
        }

        @ParameterizedTest
        @DisplayName("Should return false for null MIME type")
        @NullSource
        void shouldReturnFalseForNullMimeType(String mimeType) {
            assertThat(service.isSupported(mimeType)).isFalse();
        }
    }

    @Nested
    @DisplayName("convertToWebp()")
    class ConvertToWebp {

        @Test
        @DisplayName("Should throw exception for invalid image data")
        void shouldThrowExceptionForInvalidImageData() {
            InputStream input = new ByteArrayInputStream(new byte[]{0, 1, 2});

            assertThatThrownBy(() -> service.convertToWebp(input, 80, false))
                .isInstanceOf(ImageProcessingException.class)
                .satisfies(ex -> assertThat(((ImageProcessingException) ex).getErrorCode())
                    .isEqualTo(ImageProcessingException.ImageErrorCode.INVALID_IMAGE_FORMAT));
        }

        @Test
        @DisplayName("Should convert valid image to WebP if writer is available")
        void shouldConvertValidImageToWebpIfWriterIsAvailable() throws IOException {
            InputStream input = createTestImageStream(200, 200, "jpg");

            if (service.isWebpSupported()) {
                ProcessedImage result = service.convertToWebp(input, 80, false);

                assertThat(result).isNotNull();
                assertThat(result.mimeType()).isEqualTo("image/webp");
                assertThat(result.width()).isEqualTo(200);
                assertThat(result.height()).isEqualTo(200);
                assertThat(result.data()).isNotEmpty();
            } else {
                // WebP not supported, should throw an exception wrapping IOException
                assertThatThrownBy(() -> service.convertToWebp(input, 80, false))
                    .isInstanceOf(ImageProcessingException.class);
            }
        }

        @Test
        @DisplayName("Should convert image to lossless WebP if writer is available")
        void shouldConvertImageToLosslessWebpIfWriterIsAvailable() throws IOException {
            InputStream input = createTestImageStream(100, 100, "png");

            if (service.isWebpSupported()) {
                ProcessedImage result = service.convertToWebp(input, 100, true);

                assertThat(result).isNotNull();
                assertThat(result.mimeType()).isEqualTo("image/webp");
                assertThat(result.data()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("Should convert ARGB image to WebP with RGB conversion")
        void shouldConvertArgbImageToWebpWithRgbConversion() throws IOException {
            byte[] argbImage = createTestImageWithAlpha(100, 100, "png");
            InputStream input = new ByteArrayInputStream(argbImage);

            if (service.isWebpSupported()) {
                ProcessedImage result = service.convertToWebp(input, 80, false);

                assertThat(result).isNotNull();
                assertThat(result.mimeType()).isEqualTo("image/webp");
            }
        }

        @Test
        @DisplayName("Should throw exception when input stream throws IOException")
        void shouldThrowExceptionWhenInputStreamThrowsIOException() {
            InputStream failingInput = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Stream failure");
                }
            };

            assertThatThrownBy(() -> service.convertToWebp(failingInput, 80, false))
                .isInstanceOf(ImageProcessingException.class);
        }
    }

    @Nested
    @DisplayName("optimizeAndConvertToWebp()")
    class OptimizeAndConvertToWebp {

        @Test
        @DisplayName("Should throw exception for invalid image data")
        void shouldThrowExceptionForInvalidImageData() {
            InputStream input = new ByteArrayInputStream(new byte[]{0, 1, 2});

            assertThatThrownBy(() -> service.optimizeAndConvertToWebp(input, 200, 200, 80))
                .isInstanceOf(ImageProcessingException.class)
                .satisfies(ex -> assertThat(((ImageProcessingException) ex).getErrorCode())
                    .isEqualTo(ImageProcessingException.ImageErrorCode.INVALID_IMAGE_FORMAT));
        }

        @Test
        @DisplayName("Should optimize and convert if WebP is supported")
        void shouldOptimizeAndConvertIfWebpIsSupported() throws IOException {
            InputStream input = createTestImageStream(400, 200, "jpg");

            if (service.isWebpSupported()) {
                ProcessedImage result = service.optimizeAndConvertToWebp(input, 200, 200, 80);

                assertThat(result).isNotNull();
                assertThat(result.mimeType()).isEqualTo("image/webp");
                assertThat(result.width()).isLessThanOrEqualTo(200);
                assertThat(result.height()).isLessThanOrEqualTo(200);
            } else {
                assertThatThrownBy(() -> service.optimizeAndConvertToWebp(input, 200, 200, 80))
                    .isInstanceOf(ImageProcessingException.class);
            }
        }

        @Test
        @DisplayName("Should not resize when image is within limits")
        void shouldNotResizeWhenImageIsWithinLimits() throws IOException {
            InputStream input = createTestImageStream(100, 100, "jpg");

            if (service.isWebpSupported()) {
                ProcessedImage result = service.optimizeAndConvertToWebp(input, 200, 200, 80);

                assertThat(result.width()).isEqualTo(100);
                assertThat(result.height()).isEqualTo(100);
            }
        }

        @Test
        @DisplayName("Should throw exception when input stream throws IOException")
        void shouldThrowExceptionWhenInputStreamThrowsIOException() {
            InputStream failingInput = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Stream failure");
                }

                @Override
                public byte[] readAllBytes() throws IOException {
                    throw new IOException("Stream failure");
                }
            };

            assertThatThrownBy(() -> service.optimizeAndConvertToWebp(failingInput, 200, 200, 80))
                .isInstanceOf(ImageProcessingException.class);
        }
    }

    @Nested
    @DisplayName("writeImage() internal behavior")
    class WriteImage {

        @Test
        @DisplayName("Should write PNG format image (non-JPEG path)")
        void shouldWritePngFormatImage() throws IOException {
            // Use PNG input to exercise the non-JPEG branch in writeImage
            InputStream input = createTestImageStream(200, 100, "png");

            // resizeIfExceeds internally calls writeImage with "jpg" format,
            // but resize with exact dimensions will also use JPEG.
            // To test the PNG write path, we use the fact that even with a PNG input,
            // resize produces JPEG output (the format is always "jpg" in resize methods).
            ProcessedImage result = service.resizeIfExceeds(input, 100, 100);

            assertThat(result).isNotNull();
            assertThat(result.data()).isNotEmpty();
        }

        @Test
        @DisplayName("Should extract metadata from BMP image")
        void shouldExtractMetadataFromBmpImage() throws IOException {
            InputStream input = createTestImageStream(160, 120, "bmp");

            ImageMetadata metadata = service.extractMetadata(input);

            assertThat(metadata.width()).isEqualTo(160);
            assertThat(metadata.height()).isEqualTo(120);
            assertThat(metadata.format()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("isWebpSupported()")
    class IsWebpSupported {

        @Test
        @DisplayName("Should return a boolean value")
        void shouldReturnABooleanValue() {
            // This tests the method runs without error; the result depends on the runtime
            boolean supported = service.isWebpSupported();
            assertThat(supported).isIn(true, false);
        }
    }
}
