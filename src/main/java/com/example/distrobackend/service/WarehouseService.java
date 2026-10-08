package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.ResourceNotFoundException;
import com.example.distrobackend.dto.CreateWarehouseRequest;
import com.example.distrobackend.dto.WarehouseResponse;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.WarehouseRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public WarehouseResponse create(AuthenticatedUser user, CreateWarehouseRequest request) {
        Organization distributor = distributor(user);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (warehouseRepository.existsByOrganization_IdAndCodeIgnoreCase(distributor.getId(), code)) {
            throw new ApiException(ErrorCode.CONFLICT, "A warehouse with this code already exists");
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setOrganization(distributor);
        warehouse.setCode(code);
        warehouse.setName(request.name().trim());
        warehouse.setAddress(trimToNull(request.address()));
        warehouse.setActive(true);
        return WarehouseResponse.from(warehouseRepository.saveAndFlush(warehouse));
    }

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> list(AuthenticatedUser user, Pageable pageable) {
        return warehouseRepository.findByOrganization_IdAndActiveTrue(userOrganizationId(user), pageable)
                .map(WarehouseResponse::from);
    }

    private Organization distributor(AuthenticatedUser user) {
        UUID id = userOrganizationId(user);
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND));
        if (organization.getType() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return organization;
    }

    private static UUID userOrganizationId(AuthenticatedUser user) {
        if (user == null || user.organizationId() == null
                || user.organizationType() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return user.organizationId();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
