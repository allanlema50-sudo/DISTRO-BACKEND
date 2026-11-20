# LogiFlow Direct Organization Driver Onboarding Report

## Outcome

`POST /api/v1/drivers` now supports direct driver registration for Manufacturer and Distributor administrators as well as the existing Super Admin platform flow. Organization drivers are persisted immediately as `User` + `RiderProfile`, bound to the authenticated organization and optional organization-owned base warehouse. The organization is their approval authority: newly registered organization drivers are `ACTIVE` and `APPROVED`. Their phone remains unverified, and the existing account-verification OTP is issued through `OtpService`.

The organization registration path does not call `InvitationService.createDriver`, create an `Invitation`, generate a link/token, or publish an onboarding email event. Super Admin registration retains its organization-less platform-pool ownership, `PENDING_REVIEW` approval state, and existing behavior.

## Reused Super Admin and mobile flow

The Super Admin endpoint is `POST /api/v1/drivers`; its service method is `DriverService.registerForReview`. It normalizes phone/email, checks phone/email and national-ID uniqueness, creates an active `DRIVER` with a generated initial password hash and unverified phone, and creates the associated `RiderProfile`. Platform drivers remain organization-less and await Super Admin approval before assignment.

The new organization method shares the same internal direct-account/profile creation routine. Its differences are tenant binding, optional base warehouse ownership validation, immediate organization approval, and issuing `OtpPurpose.ACCOUNT_VERIFICATION` to the existing OTP provider. Phone verification behavior in `AuthService` remains required for all drivers. The Driver App uses:

1. `POST /api/auth/verify-account` with identifier and the six-digit account-verification OTP. The existing flow marks the driver's phone verified and returns normal auth tokens.
2. Since registration sets an undisclosed random initial password, the driver uses `POST /api/auth/forgot-password` and `POST /api/auth/reset-password` to receive a password-reset OTP and set a password of their choice.
3. The driver signs in through `POST /api/auth/login` and receives the normal driver-role tokens.

No web activation or invitation-acceptance step is part of this direct flow. The OTP provider may deliver over the configured channel; deployment provider configuration controls its actual destination and delivery.

## Changes

- `DriverController` routes Manufacturer/Distributor admin `POST /api/v1/drivers` requests to direct registration, returning `201 Created` with `DriverResponse`. Super Admin still uses `registerForReview`.
- `DriverService` now shares driver/user/profile creation logic. The organization path validates its own base warehouse, sets the organization and `APPROVED` state, and issues account-verification OTP.
- Removed the driver invitation, resend, and revoke endpoints from `DriverController`; direct Add Driver calls cannot create invite records.
- Restored phone-verification enforcement for every `DRIVER` in `AuthService`, including organization-bound drivers. Customer verification remains unchanged.
- Updated API documentation in the repository README and Manufacturer/Distributor integration guide. No Angular source is present in this backend repository, so no frontend files were changed.

## APIs

| Method and path | Purpose |
| --- | --- |
| `POST /api/v1/drivers` | Authenticated Super Admin or Manufacturer/Distributor admin directly creates a driver. Body follows `DriverRegistrationRequest`: `fullName`, optional `email`, `phoneNumber`, `nationalId`, `licenseNumber`, future `licenseExpiryDate`, optional `notes`, optional `baseWarehouseId`. Response is the new driver record. |
| `GET /api/v1/drivers` | List drivers within the authenticated organization; supports `status`, `availability`, and pagination. Cross-organization IDs are rejected for organization users. |
| `GET /api/v1/drivers/{driverId}` | Read an owned driver record. |
| `PATCH /api/v1/drivers/{driverId}` | Update supported driver/profile fields. |
| `PATCH /api/v1/drivers/{driverId}/status` | Organization admin manages active/suspended/deactivated state. |
| `PATCH /api/v1/drivers/{driverId}/availability` | Driver or owning organization admin updates availability under existing active/approved checks. |
| `POST /api/auth/verify-account` | Driver App submits phone verification OTP. |
| `POST /api/auth/forgot-password` and `POST /api/auth/reset-password` | Driver App requests a reset OTP and sets the driver's password. |
| `POST /api/auth/login` | Driver App authenticates with identifier and chosen password. |
| `PATCH /api/v1/vehicles/{vehicleId}/driver` | Distributor admin assigns an eligible same-organization driver to a vehicle. |
| `PATCH /api/v1/trips/{tripId}/assign` | Organization assigns an eligible same-organization driver to a trip. |

Vehicle and trip services already check active and approved status and enforce organization ownership for organization drivers. Because organization drivers are created active and approved, they satisfy those status gates immediately. Manufacturer admins can manage their organization drivers and assign them to eligible trips; vehicle ownership remains subject to the existing distributor fleet rules.

## Isolation and app access

- Organization ID is taken from the authenticated `AuthenticatedUser`; the request does not select a tenant.
- Base warehouse lookup includes both warehouse ID and the authenticated organization ID.
- Driver list, get, update, status, and availability operations enforce the owning organization.
- Vehicle and trip assignment checks require same-organization ownership and active/approved status.
- The account remains `UserRole.DRIVER`. Existing security route matchers reserve `/api/manufacturer/**` and `/api/distributor/**` for their respective web roles and `/api/driver/**` for drivers. The driver controller also blocks drivers from organization-wide listing and portal operations.

## Tests run

Command:

```text
mvn -Dmaven.repo.local=target/m2 -Dtest=DriverServiceTest,AuthServiceTest,InvitationServiceTest test
```

Result: **18 tests passed, 0 failures, 0 errors**. Added direct registration coverage for Manufacturer and Distributor admins, including tenant ownership, active/approved state, unverified phone, OTP issuance, profile persistence, and no invitation repository interaction. Auth tests cover the phone-verification gate. Existing invitation tests continue to pass.

## Remaining verification limits

- This run used service tests, not a live database or end-to-end HTTP integration environment. Actual SMS/provider delivery, app screens, password setup in the deployed Driver App, cross-tenant request rejection through a running server, and live vehicle/trip assignment requests were not exercised end to end.
- The backend repository does not include Angular or Driver App source, so their wiring to the updated `POST /api/v1/drivers` response and the verify/reset/login sequence must be confirmed in those client repositories.
- Vehicle assignment remains available only under existing Distributor fleet authorization. Manufacturer-owned drivers can be assigned to eligible organization trips, subject to the trip service's existing ownership rules.
