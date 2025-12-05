package dev.simplecore.simplix.file.infrastructure.upload;

import dev.simplecore.simplix.file.enums.FileCategory;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for generic file processing operations.
 * <p>
 * Provides domain-agnostic file handling including:
 * <ul>
 *   <li>File validation (size, type)</li>
 *   <li>File category detection</li>
 *   <li>Image processing (resize, WebP optimization)</li>
 *   <li>File storage coordination</li>
 * </ul>
 * <p>
 * This service does NOT depend on any domain entities or repositories.
 * It returns POJOs/records that the application layer can use to create
 * domain entities.
 * <p>
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @Service
 * public class FileUploadService {
 *     private final FileProcessingService fileProcessingService;
 *     private final FileAttachmentRepository repository;
 *
 *     public FileAttachment upload(MultipartFile file, String entityType, String entityId) {
 *         // Process file (validate, optimize, store)
 *         ProcessedFileResult result = fileProcessingService.processAndStore(
 *             FileProcessingRequest.of(file, "files")
 *         );
 *
 *         // Create domain entity from result
 *         FileAttachment attachment = FileAttachment.builder()
 *             .entityType(entityType)
 *             .entityId(entityId)
 *             .storedPath(result.storedInfo().storedPath())
 *             .mimeType(result.mimeType())
 *             .mediaWidth(result.width())
 *             .mediaHeight(result.height())
 *             .build();
 *
 *         return repository.save(attachment);
 *     }
 * }
 * }</pre>
 */
public interface FileProcessingService {

    /**
     * Process and store a file with automatic type detection.
     * <p>
     * Performs the following operations:
     * <ol>
     *   <li>Validate file (size, MIME type)</li>
     *   <li>Detect file category (IMAGE, DOCUMENT, etc.)</li>
     *   <li>For images: extract metadata, apply WebP optimization if eligible, resize if needed</li>
     *   <li>Store file in the configured storage</li>
     * </ol>
     *
     * @param request processing request with file and options
     * @return processed file result with storage info and metadata
     * @throws dev.simplecore.simplix.file.infrastructure.exception.FileUploadException if processing fails
     */
    ProcessedFileResult processAndStore(FileProcessingRequest request);

    /**
     * Process and store a file as an image with specific dimensions.
     * <p>
     * Use this method when you know the file is an image and want to apply
     * specific dimension constraints.
     *
     * @param file      the image file to process
     * @param maxWidth  maximum width (null to use default)
     * @param maxHeight maximum height (null to use default)
     * @return processed file result with image metadata
     * @throws dev.simplecore.simplix.file.infrastructure.exception.FileUploadException if not a valid image
     */
    ProcessedFileResult processAndStoreImage(MultipartFile file, Integer maxWidth, Integer maxHeight);

    /**
     * Process and store a file as a generic (non-image) file.
     * <p>
     * Validates and stores the file without image-specific processing.
     *
     * @param file      the file to process
     * @param directory target storage directory
     * @return processed file result
     */
    ProcessedFileResult processAndStoreGenericFile(MultipartFile file, String directory);

    /**
     * Determine the category of a file based on its MIME type and extension.
     *
     * @param file the file to categorize
     * @return the detected file category
     */
    FileCategory determineCategory(MultipartFile file);

    /**
     * Check if WebP optimization should be applied to the given file.
     * <p>
     * Considers:
     * <ul>
     *   <li>WebP conversion enabled in configuration</li>
     *   <li>WebP writer availability on the platform</li>
     *   <li>MIME type eligibility (jpeg, png, bmp)</li>
     *   <li>Minimum file size threshold</li>
     * </ul>
     *
     * @param file the file to check
     * @return true if WebP optimization should be applied
     */
    boolean shouldOptimizeToWebp(MultipartFile file);

    /**
     * Change file extension to .webp.
     * <p>
     * Utility method for WebP conversion.
     *
     * @param filename original filename
     * @return filename with .webp extension
     */
    String changeExtensionToWebp(String filename);

    /**
     * Extract file extension from filename.
     *
     * @param filename the filename
     * @return lowercase extension without dot, or empty string if none
     */
    String extractExtension(String filename);
}
