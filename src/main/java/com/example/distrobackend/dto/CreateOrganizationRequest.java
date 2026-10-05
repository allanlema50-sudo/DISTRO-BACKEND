package com.example.distrobackend.dto.organization;

import com.example.distrobackend.Domain.enums.Organizationtype;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @NotNull
        Organizationtype type

) {
}