# After the demo

These are intentionally not in the demo build.

## Product pictures

The catalog keeps the existing single picture in `product_images` (Flyway V31). Upload and replace go through `POST /api/v1/products/{id}/image`. Customers and LogiFlow load it from `GET /api/v1/products/{id}/image`. JPEG, PNG, and WebP are accepted, up to 5 MB. The file is stored as bytes on that row.

Multiple pictures per product, and storing files in object storage instead of the database row, are post-demo work. That needs a new additive migration (V95 or later). Do not edit V31.
