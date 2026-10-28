package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.Vehicle;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.VehicleStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CreateVehicleRequest;
import com.example.distrobackend.dto.UpdateVehicleRequest;
import com.example.distrobackend.dto.VehicleResponse;
import com.example.distrobackend.dto.VehicleStatusUpdateRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.VehicleRepository;
import com.example.distrobackend.repository.TripRepository;
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
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final OrganizationRepository organizationRepository;
    private final TripRepository tripRepository;

    @Transactional(readOnly = true)
    public Page<VehicleResponse> list(AuthenticatedUser actor, VehicleStatus status, Pageable pageable) {
        UUID organizationId = requireOrganization(actor);
        Page<Vehicle> vehicles = status == null
                ? vehicleRepository.findByOrganization_Id(organizationId, pageable)
                : vehicleRepository.findByOrganization_IdAndStatus(organizationId, status, pageable);
        return vehicles.map(VehicleResponse::from);
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(AuthenticatedUser actor, UUID vehicleId) {
        UUID organizationId = requireOrganization(actor);
        return vehicleRepository.findByIdAndOrganization(vehicleId, organizationId)
                .map(VehicleResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Vehicle not found"));
    }

    @Transactional
    public VehicleResponse create(AuthenticatedUser actor, CreateVehicleRequest request) {
        UUID organizationId = requireOrganizationAdmin(actor);
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
        String registration = normalizeRegistration(request.registrationNumber());
        if (vehicleRepository.existsByOrganization_IdAndRegistrationNumberIgnoreCase(organizationId, registration)) {
            throw new ApiException(ErrorCode.CONFLICT, "A vehicle with this registration already exists");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setOrganization(organization);
        vehicle.setRegistrationNumber(registration);
        vehicle.setVehicleType(requireText(request.vehicleType(), "Vehicle type"));
        vehicle.setMake(trimToNull(request.make()));
        vehicle.setModel(trimToNull(request.model()));
        vehicle.setCapacityKg(request.capacityKg());
        vehicle.setStatus(VehicleStatus.ACTIVE);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse update(AuthenticatedUser actor, UUID vehicleId, UpdateVehicleRequest request) {
        UUID organizationId = requireOrganizationAdmin(actor);
        Vehicle vehicle = vehicleRepository.findByIdAndOrganizationForUpdate(vehicleId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Vehicle not found"));
        if (request.registrationNumber() != null) {
            String registration = normalizeRegistration(request.registrationNumber());
            if (!registration.equalsIgnoreCase(vehicle.getRegistrationNumber())
                    && vehicleRepository.existsByOrganization_IdAndRegistrationNumberIgnoreCase(
                    organizationId, registration)) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "A vehicle with this registration already exists");
            }
            vehicle.setRegistrationNumber(registration);
        }
        if (request.vehicleType() != null) {
            vehicle.setVehicleType(requireText(request.vehicleType(), "Vehicle type"));
        }
        if (request.make() != null) vehicle.setMake(trimToNull(request.make()));
        if (request.model() != null) vehicle.setModel(trimToNull(request.model()));
        if (request.capacityKg() != null) vehicle.setCapacityKg(request.capacityKg());
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse updateStatus(AuthenticatedUser actor, UUID vehicleId,
                                        VehicleStatusUpdateRequest request) {
        UUID organizationId = requireOrganizationAdmin(actor);
        Vehicle vehicle = vehicleRepository.findByIdAndOrganizationForUpdate(vehicleId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Vehicle not found"));
        if (request.status() != VehicleStatus.ACTIVE
                && tripRepository.existsByVehicle_IdAndStatusIn(vehicleId,
                java.util.List.of(TripStatus.SCHEDULED, TripStatus.ONGOING, TripStatus.DELAYED))) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A vehicle assigned to an active trip cannot be deactivated");
        }
        vehicle.setStatus(request.status());
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    private static UUID requireOrganization(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.MANUFACTURER_STAFF
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (actor.organizationType() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Fleet vehicles are available only to distributor organizations");
        }
        return actor.organizationId();
    }

    private static UUID requireOrganizationAdmin(AuthenticatedUser actor) {
        UUID organizationId = requireOrganization(actor);
        if (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return organizationId;
    }

    private static String normalizeRegistration(String value) {
        return requireText(value, "Registration number").toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, field + " cannot be blank");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
