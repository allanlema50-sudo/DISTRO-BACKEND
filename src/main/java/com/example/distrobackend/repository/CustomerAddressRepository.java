package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
    List<CustomerAddress> findAllByUser_IdOrderByDefaultAddressDescCreatedAtDesc(UUID userId);
    Optional<CustomerAddress> findByIdAndUser_Id(UUID id, UUID userId);
    void deleteAllByUser_Id(UUID userId);
}
