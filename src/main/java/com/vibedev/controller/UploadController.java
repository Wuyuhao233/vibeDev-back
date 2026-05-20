package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.upload.UploadResponse;
import com.vibedev.service.FileStorageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class UploadController {

    private final FileStorageService fileStorageService;

    public UploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public ApiResponse<UploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        String storedPath = fileStorageService.storeImage(file);
        String filename = storedPath.substring(storedPath.lastIndexOf('/') + 1);
        return ApiResponse.ok(new UploadResponse(storedPath, filename, file.getSize()));
    }
}
