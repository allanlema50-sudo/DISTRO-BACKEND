package com.example.distrobackend.service;

import com.example.distrobackend.dto.mpesa.MpesaStkPushResponse;

/** Starts a customer or purchase-order collection. Daraja is the pilot implementation. */
public interface CustomerCollectionProvider {

    MpesaStkPushResponse push(String accountReference, String description, String phone,
                              int amountKes, String callbackUrl);
}
