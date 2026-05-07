package com.vibedev.dto.upload;

public record UploadResponse(
        String url,
        String filename,
        long size
) {}
