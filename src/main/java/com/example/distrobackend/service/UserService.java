package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByStatus(UserStatus status) {
        return userRepository.findByStatus(status)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse updateUserStatus(UUID id, UserStatus status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus(status);

        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }
}