# Organization payout beneficiary onboarding

These APIs are for manufacturer and distributor administrators. Paystack keys stay
on the backend; clients must not call Paystack directly.

## Destination options

Load the current Paystack-supported KES destinations when opening the payout
destination form:

```http
GET /api/v1/finance/beneficiaries/banks?currency=KES
GET /api/v1/finance/beneficiaries/mobile-money-providers?currency=KES
Authorization: Bearer <organization-admin-token>
```

The bank list is for bank-account destinations. The mobile-money list includes
M-Pesa when Paystack reports it active for the account, along with any other
supported mobile-money destinations. Use the returned `code` as `bankCode`; do
not hard-code provider codes in the client.

Each option contains `name`, `code`, `currency`, `type`, and `active`.

## Verify a bank account

After the user selects a bank and enters an account number, resolve it through
the backend:

```http
POST /api/v1/finance/beneficiaries/resolve-bank-account
Content-Type: application/json
Authorization: Bearer <organization-admin-token>

{
  "accountNumber": "0123456789",
  "bankCode": "68",
  "currency": "KES"
}
```

The response contains Paystack's `accountName`, selected `bankCode` and
`bankName`, `currency`, and a masked account number. Show the returned name as
read-only and require the user to confirm that it is correct before continuing.
The backend resolves the account again during beneficiary creation and rejects
the request if the submitted confirmed name no longer matches.
This verifies the name Paystack associates with that bank account; it does not
prove that the organization is the legal owner or replace business KYC.

## Create a beneficiary

```http
POST /api/v1/finance/beneficiaries
Content-Type: application/json
Authorization: Bearer <organization-admin-token>

{
  "method": "BANK_ACCOUNT",
  "accountName": "Jane Doe",
  "currency": "KES",
  "accountNumber": "0123456789",
  "bankCode": "68",
  "detailsConfirmed": true
}
```

For mobile money, select a provider from the mobile-money options list and send
its code, the Kenyan phone number, and a user-entered recipient name:

```json
{
  "method": "MOBILE_MONEY",
  "accountName": "Jane Doe",
  "currency": "KES",
  "mobileNumber": "+254700000001",
  "bankCode": "MPESA",
  "detailsConfirmed": true
}
```

Paystack does not return a resolved account-holder name for its Kenyan mobile
money recipient example. Therefore the UI must ask the user to confirm the
phone number and recipient name, but must not label the name as provider-
verified.

The response status is `ACTIVE` when Super Admin review is disabled and Paystack
accepts the destination. When review is enabled it is `PENDING_REVIEW`; show that
state and explain that payout is unavailable until approval. Existing
beneficiaries are not automatically re-reviewed when the setting changes.

Configure:

```dotenv
PAYSTACK_BENEFICIARY_REVIEW_REQUIRED=false
```

Set to `true` to require the existing Super Admin beneficiary review action.
The setting defaults to `false`. `PAYSTACK_TRANSFERS_ENABLED` must also be true
for the destination-list, verification, and recipient-creation calls.
