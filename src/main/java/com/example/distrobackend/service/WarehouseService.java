package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.ResourceNotFoundException;
import com.example.distrobackend.dto.CreateWarehouseRequest;
import com.example.distrobackend.dto.UpdateWarehouseRequest;
import com.example.distrobackend.dto.UpdateWarehouseStatusRequest;
import com.example.distrobackend.dto.WarehouseResponse;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.WarehouseRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final StockItemRepository stockItemRepository;

    @Transactional
    public WarehouseResponse create(AuthenticatedUser user, CreateWarehouseRequest request) {
        Organization organization = organization(user);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (warehouseRepository.existsByOrganization_IdAndCodeIgnoreCase(organization.getId(), code)) {
            throw new ApiException(ErrorCode.CONFLICT, "A warehouse with this code already exists");
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setOrganization(organization);
        warehouse.setCode(code);
        warehouse.setName(request.name().trim());
        warehouse.setAddress(trimToNull(request.address()));
        warehouse.setActive(true);
        try {
            return WarehouseResponse.from(warehouseRepository.saveAndFlush(warehouse));
        } catch (DataIntegrityViolationException ex) {
            String detail = ex.getMostSpecificCause().getMessage();
            if (detail != null && detail.contains("uk_warehouses_organization_code")) {
                throw new ApiException(ErrorCode.CONFLICT, "A warehouse with this code already exists");
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> list(AuthenticatedUser user, Pageable pageable) {
        return warehouseRepository.findByOrganization_Id(userOrganizationId(user), pageable)
                .map(WarehouseResponse::from);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(AuthenticatedUser user, UUID warehouseId) {
        return WarehouseResponse.from(findOwnedWarehouse(user, warehouseId));
    }

    @Transactional
    public WarehouseResponse update(AuthenticatedUser user, UUID warehouseId, UpdateWarehouseRequest request) {
        Warehouse warehouse = findOwnedWarehouse(user, warehouseId);
        checkVersion(warehouse, request.version());
        if (request.name() == null && request.address() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "At least one warehouse field is required");
        }
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Warehouse name cannot be blank");
            }
            warehouse.setName(name);
        }
        if (request.address() != null) {
            warehouse.setAddress(trimToNull(request.address()));
        }
        return WarehouseResponse.from(warehouseRepository.saveAndFlush(warehouse));
    }

    @Transactional
    public WarehouseResponse updateStatus(AuthenticatedUser user, UUID warehouseId,
                                         UpdateWarehouseStatusRequest request) {
        Warehouse warehouse = findOwnedWarehouse(user, warehouseId);
        checkVersion(warehouse, request.version());
        if (!request.active() && warehouse.isActive()
                && stockItemRepository.existsByWarehouse_IdAndActiveTrue(warehouseId)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A warehouse with active stock cannot be deactivated");
        }
        warehouse.setActive(request.active());
        return WarehouseResponse.from(warehouseRepository.saveAndFlush(warehouse));
    }

    private Warehouse findOwnedWarehouse(AuthenticatedUser user, UUID warehouseId) {
        UUID organizationId = userOrganizationId(user);
        return warehouseRepository.findByIdAndOrganization_Id(warehouseId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND));
    }

    private Organization organization(AuthenticatedUser user) {
        UUID id = userOrganizationId(user);
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND));
        if (user.organizationType() == null || organization.getType() != user.organizationType()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return organization;
    }

    private static UUID userOrganizationId(AuthenticatedUser user) {
        if (user == null || user.organizationId() == null
                || user.organizationType() == null
                || (user.role() != com.example.distrobackend.Domain.enums.UserRole.MANUFACTURER_ADMIN
                && user.role() != com.example.distrobackend.Domain.enums.UserRole.MANUFACTURER_STAFF
                && user.role() != com.example.distrobackend.Domain.enums.UserRole.DISTRIBUTOR_ADMIN
                && user.role() != com.example.distrobackend.Domain.enums.UserRole.DISTRIBUTOR_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return user.organizationId();
    }

    private static void checkVersion(Warehouse warehouse, Long expectedVersion) {
        if (expectedVersion == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "version is required for warehouse updates");
        }
        if (expectedVersion != warehouse.getVersion()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The warehouse was changed by another request. Refresh and retry");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
