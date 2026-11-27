# Backend codebase structure

## Current layout

The backend currently uses a mostly package-by-layer layout under
`com.example.distrobackend`:

```text
src/main/java/com/example/distrobackend/
  controller/       HTTP controllers; admin controllers are nested under admin/
  service/          83 services and jobs, currently all in one flat package
  repository/       63 Spring Data repositories in one flat package
  dto/              184 DTOs in one flat package
  Domain/entity/    JPA entities
  Domain/enums/     enums
  configuration/    Spring configuration and startup wiring
  integration/      provider/client integration code
  security/         authentication and authorization
  event/            application events
  mapper/           entity/DTO mapping
  Exception/        API exception types and handlers
  Util/             shared utilities

src/main/resources/
  db/migration/     versioned Flyway SQL migrations
  application.properties

src/test/java/com/example/distrobackend/
  configuration/, integration/, security/, service/
```

This is a recognizable layered architecture, but navigation is getting harder:
payment, order, shipment, stock, identity, and organization services sit beside
one another in the same `service` package. Most controllers, repositories,
DTOs, entities, and tests are similarly grouped by technical type rather than
business capability. The `Domain`, `Exception`, and `Util` package names also
break Java's lowercase package convention. `callbackhandling` is a class-name
case/style outlier.

## Recommended destination: feature folders inside each layer

The desired layout is layer-first, with business-feature subpackages inside
each layer. For example, payment controllers remain under `controller`, payment
services under `service`, and so on, but each layer is divided by capability:

```text
com.example.distrobackend/
  DistroBackendApplication.java
  controller/
    payments/       PaymentController, CheckoutController, beneficiary APIs
    orders/         customer and purchase-order APIs
    products/       product/catalog/listing APIs
    inventory/      stock and warehouse APIs
    shipments/      shipment APIs
    trips/          trip and shared-load APIs
    organizations/  organization, staff, and organization settings APIs
    identity/       auth, user, invitation, and access-request APIs
    notifications/  notification APIs
    admin/
      payments/     Super Admin finance/payment APIs
      orders/       Super Admin order APIs
      ...
  service/
    payments/
    orders/
    products/
    inventory/
    shipments/
    trips/
    organizations/
    identity/
    notifications/
    admin/
  repository/
    payments/
    orders/
    products/
    inventory/
    shipments/
    trips/
    organizations/
    identity/
  dto/
    payments/
    orders/
    products/
    inventory/
    shipments/
    trips/
    organizations/
    identity/
  Domain/
    entity/         (feature subpackages can be introduced incrementally)
    enums/
  configuration/
  integration/
  security/
  event/
  mapper/
```

This is commonly described as package-by-layer with feature subpackages. It is
a familiar and reasonable structure for a modular monolith: developers can
find a feature's controllers, services, repositories, and DTOs without
creating a separate application architecture for every feature. Keep the
Spring Boot application class at the existing root so component, entity, and
repository scanning continues to cover the subpackages.

Do not create a package for every entity or endpoint. Place each type in its
primary business capability; use a `common`/`shared` package only for genuinely
reusable cross-feature types. Provider-specific clients can remain under the
existing integration layer, grouped by provider or capability.

## Safe migration order

Package moves change imports and can affect component scanning, test visibility,
and consumers even when business behavior is unchanged. They should not change
HTTP routes, serialized JSON, database names, or migration behavior. Do not
combine them with payment behavior changes or database migrations. Use a
dedicated refactor branch and make one feature move per small commit/PR:

1. Establish the package conventions and verify application, entity, and
   repository scans still include the new module packages.
2. Move one cohesive capability (payments is a natural candidate once current
   payment testing and handoff work is saved), placing its types in the
   corresponding feature subpackage under each existing layer. Keep
   controllers, services, repositories, DTOs, and tests grouped consistently.
3. Compile and run focused tests, then the full suite. Compare generated API
   routes/OpenAPI before and after; routes and JSON contracts must not change.
4. Repeat for orders, inventory/catalog, shipment, organization, identity, and
   notifications. Leave stable unrelated modules untouched until their turn.
5. Rename uppercase/legacy packages and class names in a separate cleanup after
   imports are already localized.

Flyway migrations should remain in `src/main/resources/db/migration` and keep
their immutable, globally ordered version names. They are intentionally not
placed in Java feature packages because Flyway discovers them from the
classpath. Do not rename or edit an already-applied migration to make the
directory look cleaner; use a new forward migration for schema changes.

## Rules for new code

- Add new code to its capability package rather than expanding flat root
  packages when that module has been migrated.
- Keep controllers thin: HTTP validation/authentication in `api`, business
  workflows in `application`, persistence/provider details in
  `infrastructure`.
- Keep API DTOs separate from JPA entities; never serialize entities directly.
- Preserve existing URLs, JSON field names, authorization rules, and migration
  behavior during package-only refactors.
- Tests should mirror the feature/package they exercise and include integration
  tests for database-specific PostgreSQL behavior where relevant.
