package com.example.distrobackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(
        @Size(min = 1, max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String phoneNumber
) { }
