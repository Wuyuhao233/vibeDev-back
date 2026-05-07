package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.upload.UploadResponse;
import com.vibedev.service.FileStorageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
        String url = "/uploads/images/" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM")) +
                "/" + filename;
        return ApiResponse.ok(new UploadResponse(url, filename, file.getSize()));
    }
}
