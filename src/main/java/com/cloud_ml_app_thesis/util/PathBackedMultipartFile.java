package com.cloud_ml_app_thesis.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A MultipartFile implementation backed by a temp Path.
 * Allows existing services (DatasetService, MinioService, TrainingHelper) to accept
 * temp files without signature changes.
 */
public class PathBackedMultipartFile implements MultipartFile {

    private final Path path;
    private final String originalFilename;
    private final String contentType;
    private final long size;

    public PathBackedMultipartFile(Path path, String originalFilename, String contentType, long size) {
        this.path = path;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
    }

    @Override
    public String getName() {
        return path.getFileName().toString();
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
