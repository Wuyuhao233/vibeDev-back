package com.vibedev.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecoverAccountRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
