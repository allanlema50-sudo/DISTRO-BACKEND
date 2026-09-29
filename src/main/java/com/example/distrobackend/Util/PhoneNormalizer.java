package com.example.distrobackend.Util;


import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;

/**
 * Normalizes phone numbers to E.164 so the same number always maps to one user row.
 * Assumes Kenyan local formats (07xx / 01xx / 254xx) since payments go through M-Pesa;
 * numbers already starting with "+" are accepted as-is.
 */
public final class PhoneNormalizer {
    private PhoneNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Phone number is required");
        }
        String s = raw.trim().replaceAll("[\\s\\-()]", "");

        if (s.startsWith("+")) {
            // keep as-is
        } else if (s.startsWith("254")) {
            s = "+" + s;
        } else if (s.matches("0[17]\\d{8}")) {
            s = "+254" + s.substring(1);
        } else {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid phone number");
        }

        if (!s.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid phone number");
        }
        return s;
    }
}