package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse createTrip(
            @AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody TripRequest request) {

        if (me.role() == UserRole.CUSTOMER || me.role() == UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Role not authorized to create trips");
        }

        return tripService.createTrip(me, request);
    }

    @GetMapping("/active")
    public List<TripResponse> getActiveTrips(@AuthenticationPrincipal AuthenticatedUser me) {
        if (me.role() != UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only drivers can view active trips via this endpoint");
        }
        return tripService.getActiveTripsForRider(me.userId());
    }

    @GetMapping("/history")
    public List<TripResponse> getTripHistory(@AuthenticationPrincipal AuthenticatedUser me) {
        if (me.role() != UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only drivers can view trip history via this endpoint");
        }
        return tripService.getTripHistoryForRider(me.userId());
    }

    @PostMapping("/{tripId}/stops/{stopId}/confirm")
    public TripResponse confirmStopDelivery(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable UUID tripId,
            @PathVariable UUID stopId,
            @Valid @RequestBody ConfirmStopRequest request) {

        if (me.role() != UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only drivers can confirm deliveries");
        }

        return tripService.confirmStopDelivery(me, tripId, stopId, request);
    }

    @PostMapping("/{tripId}/location")
    public LocationPingResponse recordLocation(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable UUID tripId,
            @Valid @RequestBody LocationPingRequest request) {
        return tripService.recordLocation(me, tripId, request);
    }

    @GetMapping("/{tripId}/location")
    public List<LocationPingResponse> locationHistory(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable UUID tripId) {
        return tripService.locationHistory(me, tripId);
    }

    @PatchMapping("/{tripId}/assign")
    public TripResponse assignRider(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable UUID tripId,
            @Valid @RequestBody TripAssignRequest request) {

        if (me.role() == UserRole.CUSTOMER || me.role() == UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Role not authorized to assign drivers");
        }

        return tripService.assignRider(me, tripId, request.riderId());
    }
}
