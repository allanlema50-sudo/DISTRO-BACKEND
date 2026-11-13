# LogiFlow Manufacturer and Distributor API Integration Guide

This guide describes the backend routes and DTOs present in this repository. Base paths are relative to the deployed API origin. UUIDs are strings. Timestamps are ISO-8601 offset date-time values. Examples omit server-generated fields where noted.

## 1. Architecture, identity, and tenant boundaries

The application is a stateless Spring Boot REST API. Manufacturer master products are `StockItem` rows owned by manufacturer organizations. Distributor offers are separate `StockItem` rows owned by distributors and linked to the master through `sourceStockItem`; do not create a second manufacturer product to represent a distributor offer. Purchase orders, settlements, shipments, trip assignments, stock movements, notifications, and organization records are persisted independently.

Sign in through `/api/auth/**`; send `Authorization: Bearer <JWT>` on protected calls. The JWT principal supplies user ID, role, organization ID, and organization type. Do not send an organization ID to select the caller's tenant. Services scope reads and writes to the authenticated organization and verify the other party's ownership. Role checks use `MANUFACTURER_ADMIN`, `MANUFACTURER_STAFF`, `DISTRIBUTOR_ADMIN`, and `DISTRIBUTOR_STAFF`; decision endpoints that change commercial state are generally admin-only. Platform `SUPER_ADMIN` routes are outside these workspace flows.

Manufacturer product discovery is available to authenticated callers at `/api/v1/products`; distributors should use this route with `search`, `category`, and optional `manufacturerId`. Page responses use Spring Data fields (`content`, `number`, `size`, `totalElements`, `totalPages`, `first`, `last`, `sort`, etc.). `page` is zero-based; `size` and `sort` follow Spring Data conventions. Error JSON is `{ "timestamp": "...Z", "status": 400, "code": "BAD_REQUEST", "message": "...", "path": "/...", "fieldErrors": [{"field":"...","message":"..."}] }`; absent nullable fields are omitted.

## 2. Product discovery and manufacturer publication

### Distributor catalog

`GET /api/v1/products?search=soap&category=Personal%20Care&manufacturerId=<uuid>&page=0&size=20&sort=name,asc` — authenticated; discovery of published, active manufacturer master products. Response is a Spring `Page<ProductResponse>`. The catalog repository filters master products (manufacturer-owned, no source product, active, `PUBLISHED`). `GET /api/v1/products/{productId}` returns an accessible product detail. `GET /api/v1/products/categories` returns category strings. `GET /api/v1/products/manufacturers` and `GET /api/v1/distributor/catalog/manufacturers` return `[{"id":"<uuid>","name":"Acme"}]` to distributor roles.

Manufacturer catalog management: `POST /api/v1/products` (JSON `CreateStockItemRequest`) or multipart `POST /api/v1/products` (form data) creates a product; `PUT /api/v1/products/{productId}` or multipart `PATCH` updates it; `PATCH /api/v1/products/{productId}/status` body `{"active":true}` toggles active state; manufacturer admin `PATCH /api/v1/products/{productId}/publication` body `ProductPublicationRequest` controls publication. Only active and published manufacturer products appear in distributor discovery. Exact create/update field validation is defined in `CreateStockItemRequest`, `UpdateStockItemRequest`, and the multipart DTOs; use OpenAPI at `/swagger-ui/index.html` for the complete product schema.

## 3. Distribution authorization and commercial terms

`POST /api/v1/products/{productId}/authorization-requests` — distributor admin/staff. Example:

```json
{"requestNote":"We can support a launch this quarter","targetMarket":"Independent pharmacies and mini-markets","salesChannels":"Field sales, wholesalers, online B2B","geographicalCoverage":"Nairobi, Kiambu and Machakos","distributionStrategy":"Monthly route visits, launch sampling, and reseller training"}
```

Returns `ProductAuthorizationResponse` with `id`, `manufacturerProductId`, manufacturer/distributor organization IDs, `status`, proposal fields, decision fields, agreed terms, `createdAt`, and `decidedAt`. Status begins `PENDING`. A distributor cannot create a duplicate PENDING or APPROVED authorization for the same product. Requests require an active published manufacturer master product.

`GET /api/v1/products/authorizations` lists the distributor's requests. `GET /api/v1/products/authorizations/incoming` lists a manufacturer's incoming requests. Both are paginated. Manufacturer admin decision: `PATCH /api/v1/products/authorizations/{authorizationId}/decision` (also accepted at `PATCH /api/v1/products/authorizations/{authorizationId}`) with:

```json
{"status":"APPROVED","note":"Approved for the proposed region","agreedWholesalePrice":125.00,"minimumOrderQuantity":12}
```

`agreedWholesalePrice` and `minimumOrderQuantity` are optional but must be supplied together; when absent, PO pricing/MOQ fall back to the manufacturer's master product unit price and no additional minimum. `status` can be `APPROVED` or `REJECTED`; `REJECTED` should include a useful note. `PATCH /api/v1/products/authorizations/{authorizationId}/revocation` revokes an approved relationship (`{"note":"..."}` optional), resulting in `REVOKED`. Existing authorization statuses: `PENDING`, `APPROVED`, `REJECTED`, `REVOKED`. Rejected/revoked requests may be resubmitted. Approval does not imply a PO, payment, shipment, or inventory receipt.

## 4. Purchase orders

Distributor create: `POST /api/v1/purchase-orders` (distributor admin/staff):

```json
{"supplierOrganizationId":"<manufacturer-uuid>","items":[{"stockItemId":"<manufacturer-product-uuid>","quantity":24,"distributorOfferId":"<optional-existing-offer-uuid>"}]}
```

The server checks product ownership/publication, approved authorization, MOQ, manufacturer available stock, unique line products, and (if provided) offer ownership/source validity. It reserves manufacturer stock and snapshots agreed wholesale price into each line; client-supplied price is not accepted. Current request DTO does not accept a destination warehouse; the manufacturer later supplies distributor destination warehouse when creating the shipment. Initial PO status is `SUBMITTED`, with a pending settlement.

Distributor routes: `GET /api/v1/purchase-orders`, `GET /api/v1/purchase-orders/{purchaseOrderId}`, `GET /api/v1/purchase-orders/{purchaseOrderId}/settlements`. Manufacturer routes: `GET /api/v1/manufacturer/purchase-orders`, `GET /api/v1/manufacturer/purchase-orders/{purchaseOrderId}`, `GET /api/v1/manufacturer/purchase-orders/{purchaseOrderId}/settlements`. Both list endpoints are paginated and tenant-scoped. Manufacturer admin reviews using `PATCH /api/v1/manufacturer/purchase-orders/{purchaseOrderId}/decision`:

```json
{"status":"APPROVED","note":""}
```

Only `APPROVED` and `REJECTED` are accepted. Rejection requires a reason and releases reserved manufacturer stock. Approval is the single manufacturer order confirmation required before payment; do not add another confirmation screen/API.

PO status enum: `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `FULFILLED`. `PurchaseOrderResponse` fields are `id`, `purchaseOrderNumber`, `buyerOrganizationId`, `supplierOrganizationId`, `status`, `totalAmount`, `currency`, `rejectionReason`, `submittedAt`, `reviewedAt`, `fulfilledAt`, `items`, `settlements`. Each item has `id`, `stockItemId`, nullable `distributorOfferId`, `sku`, `name`, `quantity`, `unitPrice`, `lineTotal`. Do not PATCH line items after submission; no such endpoint exists.

## 5. M-Pesa payment and reconciliation

Distributor initiates an approved PO settlement with `POST /api/v1/payments/initiate` (legacy alias `/api/payments/initiate`). Send `orderType: "PURCHASE_ORDER"`; the field `orderId` carries the PO UUID:

```json
{"orderId":"<purchase-order-uuid>","orderType":"PURCHASE_ORDER","phoneNumber":"2547XXXXXXXX","provider":"MPESA","idempotencyKey":"po-<purchase-order-uuid>-attempt-1"}
```

`Idempotency-Key` header is also supported and takes precedence. Phone accepts Kenyan formats `0...`, `254...`, or `+254...` for mobile prefixes 7/1. Response `PaymentInitiateResponse` includes `paymentId`, `mpesaCheckoutRequestId`, `status` (`PENDING`), `customerMessage`, `paymentReference`, `provider`. The UI should show the handset prompt and poll `GET /api/v1/payments/{purchaseOrderId}/status` (also `/api/payments/{purchaseOrderId}/status`) with JWT. Settlement history is available through each party's PO `/settlements` route. `GET /api/v1/payments?orderType=PURCHASE_ORDER&orderId=<po-uuid>` also resolves settlements after checking party ownership.

Daraja callback endpoint: `POST /api/v1/payments/mpesa/callback`, public for Safaricom, authenticated with configured `X-Mpesa-Callback-Secret`. The callback is correlated by checkout and merchant request IDs, and verified by querying Daraja; successful receipt and amount are validated against the settlement. Duplicate successful/reversed callbacks are idempotent. Failed result codes mark settlement failed; callback validation/configuration errors are not a payment success. Do not treat the STK initiation response as payment confirmation. Shipment service permits shipment creation only when latest settlement is `SETTLED` and exact amount/currency match.

Payment/settlement statuses: PO settlement `PENDING`, `SETTLED`, `FAILED`, `REVERSED`; general customer payment status is a separate enum. Configure `MPESA_BASE_URL`, `MPESA_CONSUMER_KEY`, `MPESA_CONSUMER_SECRET`, `MPESA_SHORTCODE`, `MPESA_PASSKEY`, `MPESA_CALLBACK_URL`, `MPESA_CALLBACK_SECRET`, and keep callback verification enabled (`MPESA_VERIFY_CALLBACK=true`). Credentials are environment/config values, not frontend values. Live callback delivery requires a reachable HTTPS callback URL and valid Safaricom credentials; sandbox-only tests do not establish live settlement.

## 6. Manufacturer fulfilment, shipments, and tracking

Manufacturer creates shipment: `POST /api/v1/manufacturer/shipments` (manufacturer admin/staff):

```json
{"purchaseOrderId":"<approved-and-paid-po-uuid>","destinationWarehouseId":"<distributor-warehouse-uuid>","trackingNumber":"optional","carrier":"optional"}
```

Server resolves or creates the distributor offer association for each PO line, checks destination warehouse belongs to buyer and is active, and sets `READY_FOR_DISPATCH`. Routes: manufacturer `GET /api/v1/manufacturer/shipments`, `GET /api/v1/manufacturer/shipments/{shipmentId}`, `PATCH /api/v1/manufacturer/shipments/{shipmentId}/status`; distributor `GET /api/v1/purchase-order-shipments`, `GET /api/v1/purchase-order-shipments/{shipmentId}`. Status request example `{"status":"IN_TRANSIT","note":"Departed Nairobi warehouse"}`. Manufacturer transitions: `READY_FOR_DISPATCH -> IN_TRANSIT -> DELIVERED`; manufacturer admin may cancel before completion. Only transition to `IN_TRANSIT` dispatches/decrements reserved manufacturer stock. Distributor confirms receipt separately.

Shipment response includes `id`, PO and organization IDs, `destinationWarehouseId`, `status`, tracking/carrier/notes, dispatch/delivery/receipt timestamps, created/updated timestamps, and receipt lines. Shipment statuses: `READY_FOR_DISPATCH`, `IN_TRANSIT`, `DELIVERED`, `RECEIVED`, `CANCELLED`.

### Driver, vehicle, trip operations

Organization-owned fleet endpoints use authenticated tenant context: `GET/POST /api/v1/drivers`, `GET/PATCH /api/v1/drivers/{driverId}`, `PATCH /api/v1/drivers/{driverId}/availability`, `GET/POST /api/v1/vehicles`, `GET/PATCH /api/v1/vehicles/{vehicleId}`, `PATCH /api/v1/vehicles/{vehicleId}/driver`, `PATCH /api/v1/vehicles/{vehicleId}/status`. Driver invitation via `POST /api/v1/drivers/invitations`; available filtering is `?availability=AVAILABLE` for drivers and `?status=AVAILABLE` for vehicles. Service checks organization ownership, driver approval/availability, and vehicle availability. Use `/api/v1/trips` for persisted logistics trips: POST creates from `TripRequest` (`tripType`, `origin`, ordered `stops` with location and optional PO/order/stock references), PATCH `/{tripId}/assign` body `{"riderId":"<driver-uuid>","vehicleId":"<vehicle-uuid>","clearVehicle":false}`, POST `/{tripId}/dispatch`, PATCH `/{tripId}/status` body `{"status":"...","note":"..."}`, GET `/{tripId}` and `/{tripId}/tracking`. Trip authorization is organization-scoped by service; the driver may see assigned active/history trips and report location through POST `/{tripId}/location`. Trip lifecycle and PO shipment lifecycle are distinct; shipment creation does not itself create a trip or assign a driver. Link them in the frontend using PO/shipment context and trip references supported by the trip DTO.

## 7. Warehouse receipt, inventory, and distributor offer

Distributor confirms only a delivered shipment: `POST /api/v1/purchase-order-shipments/{shipmentId}/receive`:

```json
{"note":"Received; two cartons damaged","lines":[{"purchaseOrderItemId":"<po-item-uuid>","acceptedQuantity":22,"damagedQuantity":2,"missingQuantity":0}]}
```

Include one line per PO item; accepted + damaged + missing must equal ordered quantity. Omitted `lines` defaults each line to fully accepted. Accepted quantity is added to the linked distributor offer's stock in the shipment destination warehouse and an inventory movement is written. The PO becomes `FULFILLED` only at receipt; damaged/missing quantities are persisted as receipt discrepancy lines, not added to saleable stock. Receipt is idempotent once status is `RECEIVED`.

Distributor warehouses: `GET/POST /api/v1/warehouses`, `GET /api/v1/warehouses/{id}`, update/status routes under the same prefix; only use a warehouse belonging to the distributor. Create/edit own offers via `POST /api/stock/offers` body `{"sourceStockItemId":"<manufacturer-product-uuid>","warehouseId":"<own-warehouse-uuid>","sku":"DIST-001","sellingPrice":199.00,"reorderThreshold":5}` and `PATCH /api/stock/offers/{offerId}`. Offers start at zero on-hand and link to master product. Active catalog eligibility requires active/published offer, active warehouse, positive available stock, and active/published manufacturer master product. Thus creating an offer does not make it customer-visible before stock receipt. Do not create a duplicate master product.

Stock routes include `GET /api/stock/items`, `GET /api/stock/items/{id}`, `GET /api/stock/items/{id}/movements`, `GET /api/stock/items/low-stock`, `GET /api/stock/items/{id}/locations`; movement APIs are tenant-scoped. Receipt movements use `RESTOCK_IN` and reference type `PURCHASE_ORDER_SHIPMENT`. Avoid using manual stock adjustments as a substitute for receipt.

## 8. Dashboard, notifications, refresh and UI states

`GET /api/organization/dashboard` returns the caller organization's dashboard. `GET /api/v1/notifications` and notification routes return tenant/user notifications; authorization approval, PO review, shipment ready/delivered/received events generate notifications. There is no separate workflow dashboard endpoint or notification contract beyond these routes.

Suggested UI actions by state:

| State | Workspace display and available action |
|---|---|
| Product `PUBLISHED` + active | Distributor may submit authorization request. |
| Authorization `PENDING` | Distributor sees waiting state; manufacturer reviews proposal. |
| Authorization `APPROVED` | Distributor may create PO; display agreed price/MOQ. |
| PO `SUBMITTED` | Manufacturer admin approves/rejects; distributor waits. |
| PO `APPROVED`, settlement `PENDING` | Distributor may initiate/retry STK push; no fulfilment action yet. |
| Settlement `SETTLED` | Manufacturer may create shipment. |
| Shipment `READY_FOR_DISPATCH` | Manufacturer may dispatch (`IN_TRANSIT`). |
| Shipment `IN_TRANSIT` | Both parties refresh shipment; manufacturer marks `DELIVERED` when appropriate. |
| Shipment `DELIVERED` | Distributor enters accepted/damaged/missing line quantities and confirms receipt. |
| Shipment `RECEIVED`, offer stock available | Distributor offer can surface to customers if offer and master remain active/published. |

After mutations, refetch the specific resource and relevant paginated list. Payment callbacks are server-to-server; frontend polls payment status with backoff and treats `PENDING` as uncertain until terminal state. Do not infer payment from a toast, shipment delivery from trip completion, or stock receipt from shipment status alone.

## 9. Error handling and integration limits

Common HTTP errors include `400` validation/business rule (`BAD_REQUEST`, `INVALID_STATE_TRANSITION`), `401` missing/invalid JWT (`UNAUTHENTICATED`), `403` role or tenant violation (`ACCESS_DENIED`), `404` missing/inaccessible resource, `409` business conflict (`CONFLICT`, `PAYMENT_STATE_CONFLICT`), and `5xx` provider/server failures. Use `code` for behavior and `message` for user detail; field validation may include `fieldErrors`.

All DTO and enum contracts are defined by the records and enums named above; `/v3/api-docs` is the live schema. Dates use offsets, usually UTC from persistence; display in the user's configured timezone. No API accepts frontend-chosen wholesale price or client-supplied payment success. The existing PO create DTO does not take a destination warehouse; shipment creation does. The existing distribution status enum has no `MORE_INFO_REQUESTED` state; manufacturer may reject with a note, and a distributor can resubmit after rejection. Trip and PO shipment are independent logistics records. Shipment assignment to driver/vehicle is not a field on the PO shipment resource; use organization trip assignment APIs where logistics execution requires a driver/vehicle.

## 10. Implementation summary

The backend already contained persistent product publication/discovery, authorization relationships, PO creation/review and stock reservation, Daraja STK initiation and callback reconciliation, payment-gated shipment creation, separate trip/vehicle/driver APIs, discrepancy-aware warehouse receipt, stock movements, and linked distributor offers. This implementation adds proposal fields (target market, sales channels, geography, strategy) and optional manufacturer-approved wholesale price/MOQ to product authorization. POs now apply the approved authorization price and minimum quantity; client prices remain ignored. Migration `V54__distribution_proposal_and_terms.sql` adds the fields. No new endpoint was needed.

Verification: `.\mvnw.cmd -Dtest=!DistroBackendApplicationTests test` passed: 125 tests, zero failures/errors/skips. The application-context test was excluded by the configured test command, so full application startup against the deployment database was not established in this run. Live Safaricom behavior requires the environment configuration above and an externally reachable callback URL; it cannot be claimed verified from this local test run.
