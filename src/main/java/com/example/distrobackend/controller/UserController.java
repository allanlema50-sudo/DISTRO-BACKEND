package com.example.distrobackend.controller;




import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.dto.UpdateMyProfileRequest;
import com.example.distrobackend.dto.ChangePasswordRequest;
import com.example.distrobackend.dto.MessageResponse;
import com.example.distrobackend.dto.CustomerAddressRequest;
import com.example.distrobackend.dto.CustomerAddressResponse;
import com.example.distrobackend.dto.PushDeviceTokenRequest;
import com.example.distrobackend.dto.PushDeviceTokenDeleteRequest;
import com.example.distrobackend.dto.PushDeviceTokenResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AuthService;
import com.example.distrobackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    /** Any authenticated user. */
    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return authService.getProfile(me.userId());
    }

    @PatchMapping
    public UserResponse updateMyProfile(@AuthenticationPrincipal AuthenticatedUser me,
                                        @Valid @RequestBody UpdateMyProfileRequest request) {
        return authService.updateMyProfile(me.userId(), request);
    }

    @PostMapping("/password")
    public MessageResponse changePassword(@AuthenticationPrincipal AuthenticatedUser me,
                                          @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(me.userId(), request);
    }

    @GetMapping("/addresses")
    public List<CustomerAddressResponse> listAddresses(@AuthenticationPrincipal AuthenticatedUser me) {
        return userService.listMyAddresses(me.userId());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAddressResponse createAddress(@AuthenticationPrincipal AuthenticatedUser me,
                                                 @Valid @RequestBody CustomerAddressRequest request) {
        return userService.createMyAddress(me.userId(), request);
    }

    @PutMapping("/addresses/{addressId}")
    public CustomerAddressResponse updateAddress(@AuthenticationPrincipal AuthenticatedUser me,
                                                 @PathVariable UUID addressId,
                                                 @Valid @RequestBody CustomerAddressRequest request) {
        return userService.updateMyAddress(me.userId(), addressId, request);
    }

    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID addressId) {
        userService.deleteMyAddress(me.userId(), addressId);
    }

    @PostMapping("/push-tokens")
    public PushDeviceTokenResponse registerPushToken(@AuthenticationPrincipal AuthenticatedUser me,
                                                     @Valid @RequestBody PushDeviceTokenRequest request) {
        return userService.registerPushToken(me.userId(), request);
    }

    @DeleteMapping("/push-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregisterPushToken(@AuthenticationPrincipal AuthenticatedUser me,
                                    @Valid @RequestBody PushDeviceTokenDeleteRequest request) {
        userService.unregisterPushToken(me.userId(), request.token());
    }

    @DeleteMapping
    public void deleteMyAccount(@AuthenticationPrincipal AuthenticatedUser me) {
        userService.deleteUserAccount(me.userId());
    }
}
