package com.example.distrobackend.Domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Organizationtype {

    MANUFACTURER,
    DISTRIBUTOR,
    LOGISTICS;

    @JsonCreator
    public static Organizationtype fromJson(String value) {
        if (value == null) {
            return null;
        }
        return Organizationtype.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }

}
