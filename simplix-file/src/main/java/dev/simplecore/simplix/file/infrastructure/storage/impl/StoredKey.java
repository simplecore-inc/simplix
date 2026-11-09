package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.infrastructure.storage.StoredFileInfo;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The name and path a new object is stored under: {@code {directory}/{yyyy/MM/dd}/{uuid}.{ext}}.
 * <p>
 * Every storage implementation and the direct upload ticket build the key here, so an object
 * uploaded by either route lands in the same place.
 *
 * @param extension  extension of the original name in lower case, without dot
 * @param storedName generated file name
 * @param storedPath path relative to the storage root
 */
record StoredKey(String extension, String storedName, String storedPath) {

    static StoredKey create(String directory, String originalName) {
        String extension = StoredFileInfo.extractExtension(originalName);
        String storedName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        LocalDate now = LocalDate.now();
        String datePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String fullDirectory = directory != null ? directory + "/" + datePath : datePath;
        return new StoredKey(extension, storedName, fullDirectory + "/" + storedName);
    }
}
