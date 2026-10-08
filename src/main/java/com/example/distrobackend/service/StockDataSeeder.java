package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@Profile({"local", "dev", "test"})
@RequiredArgsConstructor
public class StockDataSeeder implements CommandLineRunner {

    private static final UUID DEMO_MANUFACTURER_ID =
            UUID.fromString("cc1b2e2f-8b6e-4d3f-ae4a-2f7c9d5b3a81");
    private static final UUID DEMO_DISTRIBUTOR_ID =
            UUID.fromString("b2f9f0e1-7a5d-4c2f-9d3a-1e6b8c4a2f70");
    private static final UUID DEMO_WAREHOUSE_ID =
            UUID.fromString("d3a2c1b0-9f8e-4d7c-b6a5-3e2f1a0b9c82");

    private final StockItemRepository stockItemRepository;

    @Override
    public void run(String... args) {
        log.info("Checking if mock StockItems exist...");
        ensureDemoOrganizationsAndWarehouse();

        seedStockItem("4a11ac56-e282-4797-901c-19cfd347c5ce", "SKU-BAM-NGU", "Bamburi Nguvu", "cat-cement", new BigDecimal("750.00"));
        seedStockItem("58cd0c9e-cbca-402b-ad6d-87d19b4d124a", "SKU-BAM-TEM", "Bamburi Tembo", "cat-cement", new BigDecimal("770.00"));
        seedStockItem("09aa428d-dab9-4214-95a4-316d708539fc", "SKU-BAM-FUN", "Bamburi Fundi", "cat-cement", new BigDecimal("700.00"));
        seedStockItem("ddad51d9-27c3-43d1-b20a-1708038b8b6f", "SKU-BAM-POW", "Bamburi Powerplus", "cat-cement", new BigDecimal("850.00"));
        seedStockItem("5da2ead0-d3d7-451c-882f-636c726745e0", "SKU-BAM-PMAX", "Bamburi Powermax", "cat-cement", new BigDecimal("900.00"));
        seedStockItem("e9383398-aa95-485c-9769-be0ce33bab6e", "SKU-SIM-CEM", "Simba Cement", "cat-cement", new BigDecimal("740.00"));

        seedStockItem("ed648cf6-9505-4b11-8fcd-49b92d987977", "SKU-DEV-D12", "Steel Rods D12", "cat-steel", new BigDecimal("1250.00"));
        seedStockItem("0e44d42f-729a-4d85-9c58-bf65ec71bf66", "SKU-MAB-G30", "Mabati Gauge 30", "cat-steel", new BigDecimal("720.00"));

        seedStockItem("ea7839a6-28d7-4d41-883b-48ac34e42691", "SKU-CRO-SILK", "Crown Silk Vinyl Emulsion", "cat-paint", new BigDecimal("4800.00"));
        seedStockItem("1cdaadf8-f438-4aee-9d30-13eca7deb818", "SKU-BAS-GLO", "Basco Super Gloss", "cat-paint", new BigDecimal("5200.00"));

        seedStockItem("d793cd02-ca0d-4453-8aeb-e6159e1f8282", "SKU-MAI-DRY", "Dry Maize", "cat-maize", new BigDecimal("3600.00"));
        seedStockItem("9330b5cf-0058-4b38-a7f5-4b0f62eeac09", "SKU-RIC-PIS", "Pishori Rice", "cat-rice", new BigDecimal("3900.00"));

        seedStockItem("6bcd5c24-fee7-4227-b2f3-e291f9289439", "SKU-EXE-ALL", "EXE All Purpose Wheat Flour", "cat-flour", new BigDecimal("2150.00"));

        seedStockItem("6bacae7f-63b9-45b6-a9c7-ffca7faf241d", "SKU-FRE-20L", "Fresh Fri Cooking Oil 20L", "cat-veg-oil", new BigDecimal("4200.00"));
        seedStockItem("1e35b7a5-33ba-4609-bfbb-e76a5f2c9a45", "SKU-FRE-5L", "Fresh Fri Cooking Oil 5L", "cat-veg-oil", new BigDecimal("1250.00"));
        seedStockItem("807d0b14-341a-4e3a-82cb-a1e59ce6a135", "SKU-KIM-2KG", "Kimbo Cooking Fat", "cat-palm-oil", new BigDecimal("520.00"));

        seedStockItem("c963f35a-c6da-47e8-abf5-1f64bf4062b1", "SKU-OMO-1KG", "Omo Washing Powder", "cat-laundry", new BigDecimal("340.00"));
        seedStockItem("1af00c2f-91cd-40ad-9716-67ab482e9461", "SKU-SUN-800G", "Sunlight Bar Soap", "cat-laundry", new BigDecimal("180.00"));
        seedStockItem("ac6f907d-a0a9-450b-845a-493482eb4539", "SKU-GEI-175G", "Geisha Bath Soap", "cat-bath", new BigDecimal("95.00"));
        seedStockItem("c0fafba2-f149-48bc-8f72-bde35d698934", "SKU-VIM-500G", "Vim Dishwashing Paste", "cat-dish", new BigDecimal("160.00"));

        log.info("Mock manufacturer products and distributor offers initialized!");
    }

    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private void ensureDemoOrganizationsAndWarehouse() {
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (id) DO NOTHING",
                DEMO_MANUFACTURER_ID, "Local Demo Manufacturer", "MANUFACTURER");
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (id) DO NOTHING",
                DEMO_DISTRIBUTOR_ID, "Local Demo Distributor", "DISTRIBUTOR");
        jdbcTemplate.update(
                "INSERT INTO warehouses (id, organization_id, code, name, active, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (id) DO NOTHING",
                DEMO_WAREHOUSE_ID, DEMO_DISTRIBUTOR_ID, "MAIN", "Main Demo Warehouse");
    }

    private void seedStockItem(String idStr, String sku, String name, String category, BigDecimal price) {
        UUID id = UUID.fromString(idStr);
        boolean created = !stockItemRepository.existsById(id);
        if (created) {
            jdbcTemplate.update(
                "INSERT INTO stock_items (id, organization_id, sku, name, category, unit_price, quantity_on_hand, reorder_threshold, is_active, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, DEMO_MANUFACTURER_ID, sku, name, category, price, 100, 10, true
            );
        }
        if (created || sourceBelongsToManufacturer(id)) {
            seedDistributorOffer(id.toString(), sku, name, category, price);
        }
    }

    private boolean sourceBelongsToManufacturer(UUID sourceId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_items si "
                        + "JOIN organizations o ON o.id = si.organization_id "
                        + "WHERE si.id = ? AND o.type = 'MANUFACTURER'",
                Long.class, sourceId);
        return count != null && count == 1L;
    }

    private void seedDistributorOffer(String sourceId, String sourceSku, String name,
                                      String category, BigDecimal price) {
        UUID sourceIdValue = UUID.fromString(sourceId);
        UUID offerId = UUID.nameUUIDFromBytes((sourceId + ":demo-offer").getBytes(StandardCharsets.UTF_8));
        String offerSku = ("DEMO-" + sourceSku);
        if (offerSku.length() > 50) {
            offerSku = offerSku.substring(0, 50);
        }
        jdbcTemplate.update(
                "INSERT INTO stock_items (id, organization_id, source_stock_item_id, warehouse_id, sku, name, category, unit_price, quantity_on_hand, reserved_quantity, reorder_threshold, is_active, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                        "ON CONFLICT (id) DO NOTHING",
                offerId, DEMO_DISTRIBUTOR_ID, sourceIdValue, DEMO_WAREHOUSE_ID,
                offerSku, name, category, price, 100, 0, 10, true);
    }
}
