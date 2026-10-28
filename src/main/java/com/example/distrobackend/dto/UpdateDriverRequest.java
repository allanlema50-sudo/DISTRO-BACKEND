package com.example.distrobackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateDriverRequest(
        @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String phoneNumber,
        @Size(max = 50) String licenseNumber
) {
}
