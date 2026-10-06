package com.example.distrobackend.Domain.enums;

public enum UserRole {

    PLATFORM_ADMIN,

    MANUFACTURER_ADMIN,
    MANUFACTURER_STAFF,

    DISTRIBUTOR_ADMIN,
    DISTRIBUTOR_STAFF,

    DRIVER,
    CUSTOMER,
    SUPER_ADMIN;

    /**
     * Organization type implied by the role,
     * or null for PLATFORM_ADMIN / DRIVER / CUSTOMER.
     */
    public Organizationtype organizationtype() {

        return switch (this) {

            case MANUFACTURER_ADMIN,
                 MANUFACTURER_STAFF ->
                    Organizationtype.MANUFACTURER;

            case DISTRIBUTOR_ADMIN,
                 DISTRIBUTOR_STAFF ->
                    Organizationtype.DISTRIBUTOR;

            default ->
                    null;
        };
    }

    /**
     * Web workspace the user is routed to after login.
     * null for mobile-only roles.
     */
    public String webHomePath() {

        if (this == PLATFORM_ADMIN) {
            return "/dashboard";
        }

        Organizationtype type = organizationtype();

        if (type == null) {
            return null;
        }

        return type == Organizationtype.MANUFACTURER
                ? "/manufacturer/dashboard"
                : "/distributor/dashboard";
    }
}