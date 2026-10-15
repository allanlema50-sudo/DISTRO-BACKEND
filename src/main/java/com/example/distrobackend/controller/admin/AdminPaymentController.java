package com.example.distrobackend.controller.admin;

import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.dto.PaymentResponse;
import com.example.distrobackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentRepository paymentRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        List<Payment> payments = paymentRepository.findAll();
        return payments.stream()
                .map(PaymentResponse::from)
                .collect(Collectors.toList());
    }
}
