# LogiFlow Organization Driver Onboarding Report

> Superseded by [LOGIFLOW_DIRECT_ORGANIZATION_DRIVER_ONBOARDING_REPORT.md](LOGIFLOW_DIRECT_ORGANIZATION_DRIVER_ONBOARDING_REPORT.md), which documents the current direct-registration workflow.

## Summary

Manufacturer and Distributor administrators use the existing driver invitation and public invitation acceptance flow. An invitation creates one tenant-owned `User` with role `DRIVER` and a `RiderProfile`; it does not create a second account or a new activation token type. The invitation link is random, stored as a SHA-256 hash, expires after the configured invitation TTL (7 days by default), and can be accepted once.

On acceptance, the driver sets a password, the invited user becomes `ACTIVE`, and the invitation becomes `ACCEPTED`. For organization drivers, the email link establishes email ownership and no phone OTP is sent or required at login. These drivers are marked approved by their organization administrator on acceptance so they can use the existing approved-and-active assignment gates. Platform pool drivers (no organization) retain their existing phone-verification and Super Admin review behavior.

## Existing Super Admin and Driver App flow

The current Super Admin driver path is `POST /api/v1/drivers` to `DriverService.registerForReview`. It creates an active, organization-less platform pool `User` with role `DRIVER` and `RiderProfile`, pending platform approval, and unverified phone/email. It does **not** create an `Invitation`, send an activation email, or set a password chosen from an activation link. Driver self-registration at `/api/auth/register` is disallowed by the backend. Phone verification checks remain in the regular driver login path for these platform drivers.

The reusable expiring email invitation lifecycle is separate: organization admins invite organization members through `InvitationService.create`; driver invitations use `DriverController` and `DriverService.invite`, which call `InvitationService.createDriver`. That service validates the organization actor, normalizes phone/email, checks uniqueness, scopes the base warehouse, creates an invited driver plus profile, and creates a pending `Invitation`.

For the invitation flow, `SecretTokenService.generate()` creates a random bearer token and only its SHA-256 hash is stored. The raw token is placed in `${app.frontend.base-url}/accept-invitation?token=...`; the create response does not disclose it. `GET /api/invitations/{token}` reads the public invitation, and `POST /api/invitations/{token}/accept` checks pending state and expiry, checks the invited phone, hashes the driver's chosen password, activates the account, verifies email, and marks the invitation accepted. The Driver Mobile App can then use `/api/auth/login` and standard driver endpoints.

Therefore, this change reuses the existing invitation/activation mechanism for organization drivers, but the repository does not currently use that invitation mechanism for Super Admin platform pool onboarding. Modifying that platform flow would change its ownership and approval model, so it was preserved.

The production notification path is event-driven through `OnboardingEmailRequested` and the configured onboarding email listener/provider. Actual delivery depends on the deployment's configured email transport.

## Changes made

- Organization driver invitation acceptance now skips phone OTP issuance and reports that the driver can sign in immediately after activation.
- Login and account-verification checks no longer require phone verification for organization-bound drivers. Customers and platform drivers with no organization retain the existing phone verification requirement.
- Organization drivers become `APPROVED` on invitation acceptance because the owning organization admin is the authority creating that workforce record. Platform pool drivers keep their Super Admin approval flow.
- Updated the invitation service test to assert email activation, active account approval, unverified phone state, and absence of an OTP issue.

No frontend code was changed. This report establishes the backend API shapes for the workspace integration.

## Organization workspace APIs

All endpoints require a valid bearer token. Organization identity is taken from the authenticated actor; caller-supplied organization IDs cannot cross tenant boundaries.

| Method and path | Purpose |
| --- | --- |
| `POST /api/v1/drivers/invitations` | Invite a driver; returns `202 Accepted` and invitation metadata. Body: `fullName`, `email`, `phoneNumber`, `licenseNumber`, optional `nationalId`, `licenseExpiryDate`, `warehouseId`, `notes`. |
| `POST /api/v1/drivers` | Legacy combined registration/invite endpoint. Organization admins are routed to invitation flow; requires `email`. |
| `GET /api/v1/drivers` | Paginated organization driver list, including `INVITED` and active users. Optional `status`, `availability`; an org admin cannot select another tenant. |
| `GET /api/v1/drivers/{driverId}` | Read one driver, tenant-scoped. |
| `PATCH /api/v1/drivers/{driverId}` | Update driver name, phone, and license fields (email is immutable). |
| `PATCH /api/v1/drivers/{driverId}/status` | Suspend/reactivate/deactivate a non-invited driver. |
| `POST /api/v1/drivers/{driverId}/invitation` | Resend an expired/revoked invitation for a still-unaccepted driver. |
| `DELETE /api/v1/drivers/{driverId}/invitation` | Revoke invitation and disable the unaccepted account. |
| `GET /api/v1/organization/invitations` | List tenant invitations, including pending and accepted states. |
| `GET /api/invitations/{token}` | Public activation-link details; token must be pending and unexpired. |
| `POST /api/invitations/{token}/accept` | Public activation and password creation; body includes `phoneNumber` and `password`. |
| `POST /api/auth/login` | Standard email/phone and password login from the Driver App. |

Vehicle assignment is `PATCH /api/v1/vehicles/{vehicleId}/driver`; trip driver assignment is `PATCH /api/v1/trips/{tripId}/assign` (also `/driver`). Existing services enforce same-organization ownership and require the driver to be active and approved. Organization invitation acceptance supplies the approval needed by this gate.

## Data model and access controls

- `User.role` remains `DRIVER`; no driver organization role was added.
- `User.organization` and `baseWarehouse` bind the driver to its owning tenant.
- Driver operational/profile data is stored in `RiderProfile` (`rider_profiles`), with a shared primary key to `User`.
- `Invitation` stores organization, invited user, role, token hash, status, expiry, and acceptance timestamp.
- `DriverService` tenant-scopes list/get/update/resend/revoke. `VehicleService` and `TripService` scope assignments to the same organization and reject inactive drivers.
- The repository contains no `StaffMember` entity or `DRIVER` staff category. Its actual existing driver profile architecture is `RiderProfile`; no parallel entity was added.
- Authentication retains the `DRIVER` role, and web role route rules do not grant drivers Manufacturer or Distributor portal routes. Driver endpoints and assigned-trip checks are role-scoped.

## Verification

Ran focused service tests with a workspace-local Maven repository:

```text
mvn -Dmaven.repo.local=target/m2 -Dtest=InvitationServiceTest,AuthServiceTest test
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

This verifies the invitation activation change and existing auth service tests. No live email provider, database-backed manufacturer/distributor end-to-end onboarding, mobile app, or vehicle/trip API integration environment was available in this run, so those acceptance paths remain unverified end to end.

## Remaining gaps

- The backend has no `StaffMember` entity/category; if that name denotes a separate frontend/domain contract, it needs reconciliation with the repository's existing `RiderProfile` model rather than duplicating driver records.
- Super Admin's existing platform pool creation does not use email invitations or password activation. If acceptance requires literal parity for Super Admin, that is a separate product decision because those accounts are organization-less and use platform approval; this change preserves the current Super Admin behavior.
- Phone number remains required for driver creation and invitation acceptance as a stored contact/login identifier and invitation consistency check; phone *verification* is removed for organization onboarding.
- End-to-end email delivery, mobile activation screens, portal navigation, database tenant isolation, and assignment requests should be exercised in an integrated environment before release.
