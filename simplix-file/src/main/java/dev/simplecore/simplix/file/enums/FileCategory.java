package dev.simplecore.simplix.file.enums;

/**
 * File type classification based on MIME type and extension.
 * <p>
 * Used by file infrastructure layer for validation and processing.
 * Domain layer should use its own FileType enum for entity persistence.
 */
public enum FileCategory {
    IMAGE,      // jpg, jpeg, png, gif, webp, svg, bmp, ico
    VIDEO,      // mp4, webm, mov, avi, mkv
    AUDIO,      // mp3, wav, ogg, flac, aac
    DOCUMENT,   // pdf, doc, docx, xls, xlsx, ppt, pptx, txt, rtf
    ARCHIVE,    // zip, rar, 7z, tar, gz, tar.gz
    OTHER       // Any other file type
}
