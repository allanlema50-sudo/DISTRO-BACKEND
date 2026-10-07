package com.example.distrobackend.Exception;

public class InsufficientStockException extends ApiException {
    public InsufficientStockException(String message) {
        super(ErrorCode.INSUFFICIENT_STOCK, message);
    }
}
