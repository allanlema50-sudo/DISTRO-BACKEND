# Canonical reseller listings API status

Updated 2026-11-24. This document describes routes implemented in this repository. Customer payment against reseller listings and mixed-seller checkout are **not implemented**; existing `/api/v1/orders` remains the legacy single-distributor order contract backed by stock items.

## Distributor listing management (new)

All routes require an authenticated `DISTRIBUTOR_ADMIN` or `DISTRIBUTOR_STAFF` principal with an organization. The server derives the distributor organization from that principal.

| Method and URL | Purpose | Request | Response / rules |
|---|---|---|---|
| `GET /api/v1/distributor/reseller-listings` | List own listings | None | `ResellerListingResponse[]`; tenant scoped. |
| `GET /api/v1/distributor/reseller-listings/eligible-products` | Paid purchased products eligible for resale | None | `EligibleResellerProductResponse[]`; accepted quantity comes from accepted receipt records. |
| `POST /api/v1/distributor/reseller-listings` | Create or reuse the canonical listing | `{"manufacturerProductId":"uuid","retailPrice":850.00}` | `201 ResellerListingResponse`; rejects unpublished/noncanonical products and products with no confirmed successful purchase by this distributor. Existing listing is updated, not duplicated. Receipt quantities are initialized from receipt records. |
| `GET /api/v1/distributor/reseller-listings/{id}` | Read one own listing | None | `ResellerListingResponse`; other tenants receive not found. |
| `PATCH /api/v1/distributor/reseller-listings/{id}/price` | Change retail price | `{"retailPrice":875.00}` | `ResellerListingResponse`; positive price required. |
| `POST /api/v1/distributor/reseller-listings/{id}/pause` | Pause customer visibility | None | `ResellerListingResponse`; stock is retained. |
| `POST /api/v1/distributor/reseller-listings/{id}/resume` | Resume visibility | None | `ResellerListingResponse`; Manufacturer product must remain published. |
| `DELETE /api/v1/distributor/reseller-listings/{id}` | Archive listing | None | `204`; inventory is retained. |

Errors use the application's standard `ApiError` envelope: validation or ineligible product `400/403`, unknown listing/product `404`, and disallowed lifecycle transition `409` where applicable.

### Response shape

```json
{
  "id": "reseller-listing-id",
  "distributorOrganizationId": "seller-id",
  "manufacturerProductId": "canonical-product-id",
  "retailPrice": 850.00,
  "currency": "KES",
  "status": "ACTIVE",
  "quantityOnHand": 12,
  "reservedQuantity": 0,
  "availableQuantity": 12,
  "availability": "IN_STOCK",
  "productName": "Canonical product",
  "description": "Manufacturer description",
  "imageUrl": "/api/v1/customer/catalog/reseller-listings/reseller-listing-id/image",
  "brand": "Brand",
  "category": "Building",
  "unit": "bag",
  "manufacturerName": "Manufacturer",
  "distributorName": "Distributor"
}
```

## Customer catalogue (new)

Requires an authenticated `CUSTOMER` principal. Only `ACTIVE` listings whose canonical product is active and published are returned. Out-of-stock rows remain visible by default.

| Method and URL | Purpose | Query/request | Response |
|---|---|---|---|
| `GET /api/v1/customer/catalog/reseller-listings` | Search and paginate customer listings | `search`, `category`, `brand`, optional `distributorOrganizationId`, `availableOnly`, Spring `page`, `size`, `sort` | `Page<ResellerListingResponse>`; never returns bare Manufacturer products. |
| `GET /api/v1/customer/catalog/reseller-listings/{id}` | Read one visible listing | None | `ResellerListingResponse`, including distinct listing, product, and seller IDs. |
| `GET /api/v1/customer/catalog/reseller-listings/{id}/image` | Read the canonical product image through a visible listing | None | Image bytes; listing must be active and product published. |

## Wholesale eligibility and stock provenance

`GET /api/v1/distributor/reseller-listings/eligible-products` reports only Manufacturer products on that distributor's paid, non-rejected, non-cancelled wholesale POs. Payment grants eligibility but does not itself increase quantity. Accepted shipment receipt lines remain the source for listing quantity; duplicate receipt prevention and stock movement behavior remain in the existing purchase-shipment service.

The current receipt integration increments an existing listing, while listing creation reconstructs already-accepted quantity from receipt lines. Both operations serialize on the canonical product row, closing the race where a receipt and first listing creation could otherwise miss each other's update. This supports receipt-before-listing and listing-before-receipt sequences at the service level. It still uses the existing legacy distributor offer as the receiving target and stock-movement subject; reseller listing inventory is therefore a materialization that must be reconciled with that legacy ledger before production rollout.

## Existing routes not extended into listing commerce

- Wholesale: `/api/v1/checkouts`, `/api/v1/checkouts/{id}/payment`, `/api/v1/purchase-order-shipments/{shipmentId}/receive`.
- Existing customer orders: `/api/v1/orders`, `/api/v1/orders/{id}/payments`, `/api/v1/orders/{id}/status`, and order warehouse/trip workflows. These still accept stock item contracts and are not reseller-listing checkout routes.
- Existing payment routes remain per-order and separate from wholesale `CheckoutPayment` records.

## Not implemented

This backend does not yet offer a reseller-listing cart/checkout, one parent customer checkout for mixed sellers, seller-specific child orders created from reseller listing IDs, payment allocation across those child orders, listing-backed atomic reservation/release/consumption, or customer-order fulfillment that debits the reseller listing. Existing order payment, inventory reservation, trip, driver, and delivery OTP support is not wired to this new listing domain. Frontend agents must not represent reseller checkout as available.
