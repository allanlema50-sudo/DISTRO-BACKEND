package com.example.distrobackend.dto;

import java.util.List;

public record ProviderTransactionMatchPage(
        int page,
        int perPage,
        long providerTotal,
        List<ProviderTransactionMatch> transactions
) {
    public ProviderTransactionMatchPage {
        transactions = List.copyOf(transactions);
    }
}
