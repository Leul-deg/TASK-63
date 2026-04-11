package com.reslife.api.storage;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * Manages physical file storage on the local filesystem.
 *
 * <p>Directory layout:
 * <pre>{upload-dir}/{agreementId}/{storedFilename}</pre>
 *
 * <p>Only server-generated filenames are ever used in path operations —
 * user-supplied filenames are stored as metadata only and never used
 * in filesystem paths.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final Path rootDir;

    public StorageService(@Value("${app.storage.upload-dir}") String uploadDir) {
        this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootDir);
            log.info("File storage root: {}", rootDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory: " + rootDir, e);
        }
    }

    /**
     * Persists a multipart upload to disk.
     *
     * @param agreementId   used as the subdirectory name
     * @param storedFilename server-generated {@code UUID.ext}
     * @param file          the uploaded multipart part
     */
    public void store(UUID agreementId, String storedFilename, MultipartFile file) {
        Path target = safeResolve(agreementId.toString(), storedFilename);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file " + storedFilename, e);
        }
    }

    /**
     * Returns a {@link Resource} for streaming the file to the client.
     *
     * @throws EntityNotFoundException if the file does not exist on disk
     */
    public Resource load(UUID agreementId, String storedFilename) {
        Path file = safeResolve(agreementId.toString(), storedFilename);
        Resource resource = new FileSystemResource(file);
        if (!resource.exists() || !resource.isReadable()) {
            throw new EntityNotFoundException(
                    "File not found on disk: " + storedFilename);
        }
        return resource;
    }

    /**
     * Deletes the stored file.  Logs a warning on failure instead of
     * propagating — the DB record is already deleted at this point.
     */
    public void delete(UUID agreementId, String storedFilename) {
        try {
            Path file = safeResolve(agreementId.toString(), storedFilename);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete stored file {} for agreement {}: {}",
                    storedFilename, agreementId, e.getMessage());
        }
    }

    // ── Message images ────────────────────────────────────────────────────────

    /**
     * Stores a message image under {@code {rootDir}/messages/{storedFilename}}.
     *
     * @param storedFilename server-generated filename (UUID.ext)
     */
    public void storeMessageImage(String storedFilename, MultipartFile file) {
        Path target = safeResolve("messages", storedFilename);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store message image " + storedFilename, e);
        }
    }

    /** Returns a {@link Resource} for a message image. */
    public Resource loadMessageImage(String storedFilename) {
        Path file = safeResolve("messages", storedFilename);
        Resource resource = new FileSystemResource(file);
        if (!resource.exists() || !resource.isReadable()) {
            throw new jakarta.persistence.EntityNotFoundException("Image not found: " + storedFilename);
        }
        return resource;
    }

    /** Deletes a message image silently on failure. */
    public void deleteMessageImage(String storedFilename) {
        try {
            Files.deleteIfExists(safeResolve("messages", storedFilename));
        } catch (IOException e) {
            log.warn("Could not delete message image {}: {}", storedFilename, e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves a path within {@link #rootDir} and verifies the result does not
     * escape the root (defense-in-depth; storedFilenames are always UUID-generated).
     */
    private Path safeResolve(String... parts) {
        Path resolved = rootDir;
        for (String part : parts) {
            resolved = resolved.resolve(part);
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new IllegalArgumentException("Path traversal attempt detected");
        }
        return resolved;
    }
}
