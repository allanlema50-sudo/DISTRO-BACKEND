package com.example.distrobackend.mapper;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.dto.UserResponse;

public class UserMapper {

    private UserMapper() { }

    /** Map while the user's lazy organization and warehouse associations are available. */
    public static UserResponse toResponse(User user) {
        if (user == null) return null;
        Organization org = user.getOrganization();
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhoneNumber(),
                user.getRole(), org == null ? null : org.getId(), org == null ? null : org.getName(),
                org == null ? null : org.getType(), user.isPhoneVerified(), user.isEmailVerified(), user.getStatus(),
                user.getJobTitle(), user.getBaseWarehouse() == null ? null : user.getBaseWarehouse().getId());
    }
}
