package com.vibedev.dto.user;

import jakarta.validation.constraints.NotBlank;

public record DeactivateAccountRequest(@NotBlank String password) {}
