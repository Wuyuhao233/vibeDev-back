package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadPath;
    private final Set<String> allowedImageTypes;
    private final long maxFileSize;

    public FileStorageService(
            @Value("${app.upload.path}") String uploadPath,
            @Value("${app.upload.max-file-size}") long maxFileSize,
            @Value("${app.upload.allowed-image-types}") List<String> allowedImageTypes) {
        this.uploadPath = Paths.get(uploadPath).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
        this.allowedImageTypes = Set.copyOf(allowedImageTypes);
        try {
            Files.createDirectories(this.uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create upload directory: " + this.uploadPath, e);
        }
    }

    public String storeImage(MultipartFile file) {
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(ErrorCode.VALIDATION_FILE_SIZE, "File size exceeds " + maxFileSize + " bytes");
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedImageTypes.contains(contentType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FILE_FORMAT, "File type not allowed: " + contentType);
        }

        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.'));
        }
        String storedName = UUID.randomUUID() + extension;

        try {
            Path targetPath = uploadPath.resolve(storedName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + storedName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }
}
