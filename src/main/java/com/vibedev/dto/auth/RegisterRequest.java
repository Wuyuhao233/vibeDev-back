package com.vibedev.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 6, max = 6)
        @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
        String code,

        @NotBlank @Size(min = 2, max = 20)
        @Pattern(regexp = "^[一-龥a-zA-Z0-9]{2,20}$", message = "用户名需2-20字符，中英文数字")
        String username,

        @NotBlank @Size(min = 8)
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,}$", message = "密码至少8位，需含字母和数字")
        String password
) {}
