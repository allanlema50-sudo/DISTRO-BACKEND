package com.example.distrobackend.Exception;

public class InsufficientStockException extends ApiException {

    public InsufficientStockException() {
        super(ErrorCode.INSUFFICIENT_STOCK);
    }
}
