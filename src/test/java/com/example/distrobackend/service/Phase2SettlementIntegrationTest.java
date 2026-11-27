package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.DistributorProductAuthorization;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlementPayment;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockLocation;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.CreditTermStatus;
import com.example.distrobackend.Domain.enums.FeePayer;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PaymentTerms;
import com.example.distrobackend.Domain.enums.PlatformFeeType;
import com.example.distrobackend.Domain.enums.ProductAuthorizationStatus;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.SettlementMethod;
import com.example.distrobackend.Domain.enums.SettlementPaymentStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.ConfirmBankPaymentRequest;
import com.example.distrobackend.dto.CreatePurchaseOrderRequest;
import com.example.distrobackend.dto.CreditStatementResponse;
import com.example.distrobackend.dto.PickPurchaseOrderItemRequest;
import com.example.distrobackend.dto.PlatformFeeReportResponse;
import com.example.distrobackend.dto.PurchaseOrderItemRequest;
import com.example.distrobackend.dto.PurchaseOrderResponse;
import com.example.distrobackend.dto.PurchaseOrderSettlementResponse;
import com.example.distrobackend.dto.UpdateCreditDefaultRequest;
import com.example.distrobackend.dto.UpdateCreditTermsRequest;
import com.example.distrobackend.dto.UpdatePlatformFeeRequest;
import com.example.distrobackend.repository.DistributorProductAuthorizationRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.PlatformCreditDefaultRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementPaymentRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockLocationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.repository.WarehouseRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 bank-transfer settlement, credit terms, platform fees and overdue rules
 * on a real (embedded) Postgres. Each test builds its own organizations so tests
 * do not depend on each other's data.
 */
class Phase2SettlementIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String FEE_SOURCE = "PURCHASE_ORDER_FEE";
    private static final String SETTLEMENT_SOURCE = "PURCHASE_ORDER_SETTLEMENT";

    @Autowired PurchaseOrderService purchaseOrderService;
    @Autowired BankTransferSettlementService bank;
    @Autowired CreditTermsService creditTerms;
    @Autowired PlatformFeeService feeService;
    @Autowired CreditOverdueService overdueService;
    @Autowired SettlementProofDocumentService proofService;
    @Autowired PaymentService paymentService;
    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WarehouseRepository warehouses;
    @Autowired StockItemRepository stockItems;
    @Autowired StockLocationRepository stockLocations;
    @Autowired DistributorProductAuthorizationRepository authorizations;
    @Autowired PurchaseOrderSettlementRepository settlements;
    @Autowired PurchaseOrderSettlementPaymentRepository paymentRepository;
    @Autowired PlatformCreditDefaultRepository creditDefaults;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    // ---------------------------------------------------------------- fixtures

    private static final class World {
        Organization manufacturer;
        Organization distributor;
        StockItem stock;
        AuthenticatedUser mfrAdmin;
        AuthenticatedUser mfrStaff;
        AuthenticatedUser distAdmin;
        AuthenticatedUser superAdmin;
    }

    private World world() {
        World w = new World();
        w.manufacturer = organization(Organizationtype.MANUFACTURER);
        w.distributor = organization(Organizationtype.DISTRIBUTOR);
        User mfrAdmin = user(w.manufacturer, UserRole.MANUFACTURER_ADMIN);
        User mfrStaff = user(w.manufacturer, UserRole.MANUFACTURER_STAFF);
        User distAdmin = user(w.distributor, UserRole.DISTRIBUTOR_ADMIN);
        User superAdmin = user(null, UserRole.SUPER_ADMIN);
        w.mfrAdmin = actor(mfrAdmin);
        w.mfrStaff = actor(mfrStaff);
        w.distAdmin = actor(distAdmin);
        w.superAdmin = actor(superAdmin);

        Warehouse warehouse = new Warehouse();
        warehouse.setOrganization(w.manufacturer);
        warehouse.setCode("W" + shortId());
        warehouse.setName("Main warehouse");
        warehouse = warehouses.saveAndFlush(warehouse);

        StockItem stock = new StockItem();
        stock.setOrganization(w.manufacturer);
        stock.setWarehouse(warehouse);
        stock.setSku("SKU-" + shortId());
        stock.setName("Test product");
        stock.setUnitPrice(new BigDecimal("100.00"));
        stock.setQuantityOnHand(1000);
        w.stock = stockItems.saveAndFlush(stock);

        StockLocation location = new StockLocation();
        location.setStockItem(w.stock);
        location.setLocationCode("A-01");
        location.setQuantity(1000);
        stockLocations.saveAndFlush(location);

        DistributorProductAuthorization authorization = new DistributorProductAuthorization();
        authorization.setProduct(w.stock);
        authorization.setManufacturerOrganization(w.manufacturer);
        authorization.setDistributorOrganization(w.distributor);
        authorization.setStatus(ProductAuthorizationStatus.APPROVED);
        authorization.setRequestedBy(distAdmin);
        authorizations.saveAndFlush(authorization);
        return w;
    }

    private Organization organization(Organizationtype type) {
        Organization organization = new Organization();
        organization.setName(type + " " + shortId());
        organization.setType(type);
        return organizations.saveAndFlush(organization);
    }

    private User user(Organization organization, UserRole role) {
        User user = new User();
        user.setFullName(role + " " + shortId());
        user.setPhoneNumber("+254" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L)));
        user.setPasswordHash("not-a-real-hash");
        user.setRole(role);
        user.setOrganization(organization);
        return users.saveAndFlush(user);
    }

    private static AuthenticatedUser actor(User user) {
        return new AuthenticatedUser(user.getId(), user.getRole(),
                user.getOrganization() == null ? null : user.getOrganization().getId(),
                user.getRole().organizationtype(), Instant.now().plusSeconds(3600));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private PurchaseOrderResponse placeOrder(World w, PaymentTerms terms, int quantity) {
        return purchaseOrderService.create(w.distAdmin, new CreatePurchaseOrderRequest(
                w.manufacturer.getId(), List.of(new PurchaseOrderItemRequest(w.stock.getId(), quantity)), terms));
    }

    private void setCredit(World w, String limit, int days, CreditTermStatus status) {
        creditTerms.putCredit(w.mfrAdmin, w.distributor.getId(),
                new UpdateCreditTermsRequest(new BigDecimal(limit), days, status, null, null));
    }

    private void submit(World w, PurchaseOrderResponse order, String reference) {
        bank.submit(w.distAdmin, order.id(), reference, pdf(), UUID.randomUUID().toString());
    }

    private void confirm(World w, PurchaseOrderResponse order, String reference, String amount) {
        bank.confirm(w.mfrAdmin, order.id(), new ConfirmBankPaymentRequest(reference, new BigDecimal(amount)));
    }

    private PurchaseOrderResponse reload(World w, PurchaseOrderResponse order) {
        return purchaseOrderService.getForManufacturer(w.mfrAdmin, order.id());
    }

    private static PurchaseOrderSettlementResponse bankSettlement(PurchaseOrderResponse order) {
        return order.settlements().stream().filter(s -> s.method() == SettlementMethod.BANK_TRANSFER)
                .findFirst().orElseThrow();
    }

    private static MultipartFile pdf() {
        byte[] content = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        return new MockMultipartFile("file", "slip.pdf", "application/pdf", content);
    }

    private static byte[] image(String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    private static void assertCode(ErrorCode expected, Executable action) {
        assertEquals(expected, assertThrows(ApiException.class, action).getCode());
    }

    private int reservedQuantity(World w) {
        return stockItems.findById(w.stock.getId()).orElseThrow().getReservedQuantity();
    }

    private int journalCount(String sourceType, UUID sourceId) {
        return jdbc.queryForObject("select count(*) from ledger_journals where source_type = ? and source_id = ?",
                Integer.class, sourceType, sourceId);
    }

    private List<Map<String, Object>> entries(String sourceType, UUID sourceId) {
        return jdbc.queryForList("select a.account_type, a.organization_id, e.direction, e.amount "
                + "from ledger_entries e join ledger_accounts a on a.id = e.account_id "
                + "join ledger_journals j on j.id = e.journal_id where j.source_type = ? and j.source_id = ?",
                sourceType, sourceId);
    }

    private void setFee(World w, String percent) {
        feeService.update(w.superAdmin, new UpdatePlatformFeeRequest(
                PlatformFeeType.PERCENT, new BigDecimal(percent), "KES", FeePayer.SUPPLIER, null));
    }

    // ------------------------------------------------------- bank-transfer flow

    @Test
    void distributorUploadDoesNotSettleTheOrder() {
        World w = world();
        PurchaseOrderResponse order = placeOrder(w, PaymentTerms.PAY_NOW, 5);

        submit(w, order, "BANK-REF-1");

        PurchaseOrderResponse after = reload(w, order);
        PurchaseOrderSettlementResponse settlement = bankSettlement(after);
        assertEquals(SettlementStatus.PENDING, settlement.status());
        assertEquals(0, settlement.amountPaid().compareTo(BigDecimal.ZERO));
        assertEquals(1, settlement.payments().size());
        assertEquals(SettlementPaymentStatus.SUBMITTED, settlement.payments().get(0).status());
        assertNull(settlement.payments().get(0).amount());
        assertNotNull(settlement.payments().get(0).proofDocumentId());
        assertTrue(after.settlements().stream().noneMatch(s -> s.status() == SettlementStatus.SETTLED));
        assertEquals("PAYMENT_PENDING", after.fulfilmentStatus());
        assertFalse(after.dispatchEligible());
        assertEquals(0, reservedQuantity(w));
    }

    @Test
    void supplierConfirmationSettlesOnceAndWritesNoProviderClearingJournal() {
        World w = world();
        PurchaseOrderResponse order = placeOrder(w, PaymentTerms.PAY_NOW, 5);
        submit(w, order, "BANK-REF-2");

        confirm(w, order, "BANK-REF-2", "500.00");

        PurchaseOrderResponse settled = reload(w, order);
        PurchaseOrderSettlementResponse settlement = bankSettlement(settled);
        assertEquals(SettlementStatus.SETTLED, settlement.status());
        assertEquals(0, new BigDecimal("500.00").compareTo(settlement.amountPaid()));
        assertEquals(0, settlement.amountOutstanding().compareTo(BigDecimal.ZERO));
        assertEquals(SettlementPaymentStatus.CONFIRMED, settlement.payments().get(0).status());
        assertEquals(0, new BigDecimal("500.00").compareTo(settlement.payments().get(0).amount()));
        // Stock is reserved once the payment commits, exactly as for an M-Pesa settlement.
        assertEquals("READY_FOR_PICKING", settled.fulfilmentStatus());
        assertEquals(5, reservedQuantity(w));
        // The money never touched the platform, so nothing is posted to the ledger at all.
        assertEquals(0, journalCount(SETTLEMENT_SOURCE, settlement.id()));
        Integer clearing = jdbc.queryForObject("select count(*) from ledger_entries e "
                + "join ledger_accounts a on a.id = e.account_id join ledger_journals j on j.id = e.journal_id "
                + "where a.account_type = 'PROVIDER_CLEARING' and j.source_id = ?", Integer.class, settlement.id());
        assertEquals(0, clearing);

        // Confirming the same reference again changes nothing.
        java.time.OffsetDateTime settledAt = settlement.settledAt();
        confirm(w, order, "BANK-REF-2", "500.00");
        PurchaseOrderSettlementResponse again = bankSettlement(reload(w, order));
        assertEquals(settledAt, again.settledAt());
        assertEquals(1, again.payments().size());
        assertEquals(5, reservedQuantity(w));
    }

    @Test
    void sameReferenceTwiceIsANoOpAndAReferenceCannotSettleAnotherOrder() {
        World w = world();
        PurchaseOrderResponse first = placeOrder(w, PaymentTerms.PAY_NOW, 2);
        PurchaseOrderResponse second = placeOrder(w, PaymentTerms.PAY_NOW, 3);

        submit(w, first, "dup-ref-77");
        submit(w, first, "DUP-REF-77");   // same reference again, new idempotency key, different case
        String key = UUID.randomUUID().toString();
        bank.submit(w.distAdmin, first.id(), "DUP-REF-77", pdf(), key);
        bank.submit(w.distAdmin, first.id(), "DUP-REF-77", pdf(), key);   // same key again
        assertEquals(1, bankSettlement(reload(w, first)).payments().size());

        assertCode(ErrorCode.PAYMENT_REFERENCE_IN_USE,
                () -> submit(w, second, "DUP-REF-77"));
        assertTrue(second.settlements().stream().noneMatch(s -> s.method() == SettlementMethod.BANK_TRANSFER));
        // The reference belongs to the first order, so it cannot be confirmed against the second.
        assertCode(ErrorCode.NOT_FOUND, () -> confirm(w, second, "DUP-REF-77", "300.00"));

        // A different manufacturer's order may use the same bank reference text.
        World other = world();
        PurchaseOrderResponse otherOrder = placeOrder(other, PaymentTerms.PAY_NOW, 1);
        submit(other, otherOrder, "DUP-REF-77");
    }

    @Test
    void onlyTheSuppliersAdministratorCanConfirm() {
        World w = world();
        World stranger = world();
        PurchaseOrderResponse order = placeOrder(w, PaymentTerms.PAY_NOW, 2);
        submit(w, order, "ACCESS-REF-1");
        ConfirmBankPaymentRequest request = new ConfirmBankPaymentRequest("ACCESS-REF-1", new BigDecimal("200.00"));

        assertCode(ErrorCode.ACCESS_DENIED, () -> bank.confirm(stranger.mfrAdmin, order.id(), request));
        assertCode(ErrorCode.ACCESS_DENIED, () -> bank.confirm(w.distAdmin, order.id(), request));
        assertCode(ErrorCode.ACCESS_DENIED, () -> bank.confirm(w.mfrStaff, order.id(), request));
        assertCode(ErrorCode.ACCESS_DENIED, () -> bank.confirm(w.superAdmin, order.id(), request));
        // A distributor that is not the buyer cannot submit either.
        assertCode(ErrorCode.ACCESS_DENIED, () -> bank.submit(stranger.distAdmin, order.id(),
                "ACCESS-REF-2", pdf(), UUID.randomUUID().toString()));
        assertCode(ErrorCode.ACCESS_DENIED, () -> bank.submit(w.mfrAdmin, order.id(),
                "ACCESS-REF-3", pdf(), UUID.randomUUID().toString()));

        PurchaseOrderSettlementResponse settlement = bankSettlement(reload(w, order));
        assertEquals(SettlementStatus.PENDING, settlement.status());
        assertEquals(SettlementPaymentStatus.SUBMITTED, settlement.payments().get(0).status());
        assertEquals(1, settlement.payments().size());
    }

    @Test
    void aPayerCannotConfirmTheirOwnPaymentEvenAsSupplierAdmin() {
        World w = world();
        // Same person both buys (as distributor admin) and tries to confirm (as manufacturer admin).
        // Role/org checks alone would allow a compromised dual-membership case; submittedBy blocks it.
        PurchaseOrderResponse order = placeOrder(w, PaymentTerms.PAY_NOW, 2);
        bank.submit(w.distAdmin, order.id(), "SELF-CONFIRM-REF", pdf(), UUID.randomUUID().toString());
        // Force submittedBy to the manufacturer admin who will attempt confirm.
        PurchaseOrderSettlementPayment payment = paymentRepository
                .findBySupplierOrganization_IdAndPayerReference(w.manufacturer.getId(), "SELF-CONFIRM-REF")
                .orElseThrow();
        payment.setSubmittedBy(users.getReferenceById(w.mfrAdmin.userId()));
        paymentRepository.saveAndFlush(payment);

        assertCode(ErrorCode.ACCESS_DENIED, () -> bank.confirm(w.mfrAdmin, order.id(),
                new ConfirmBankPaymentRequest("SELF-CONFIRM-REF", new BigDecimal("200.00"))));
        assertEquals(SettlementPaymentStatus.SUBMITTED,
                bankSettlement(reload(w, order)).payments().get(0).status());
    }

    @Test
    void payNowRequiresTheExactTotal() {
        World w = world();
        PurchaseOrderResponse order = placeOrder(w, PaymentTerms.PAY_NOW, 5);
        submit(w, order, "EXACT-REF");

        assertCode(ErrorCode.PAYMENT_AMOUNT_MISMATCH, () -> confirm(w, order, "EXACT-REF", "499.99"));
        assertCode(ErrorCode.PAYMENT_AMOUNT_MISMATCH, () -> confirm(w, order, "EXACT-REF", "500.01"));
        assertCode(ErrorCode.PAYMENT_AMOUNT_MISMATCH, () -> confirm(w, order, "EXACT-REF", "250.00"));

        PurchaseOrderSettlementResponse settlement = bankSettlement(reload(w, order));
        assertEquals(SettlementStatus.PENDING, settlement.status());
        assertEquals(SettlementPaymentStatus.SUBMITTED, settlement.payments().get(0).status());
    }

    @Test
    void creditOrdersAcceptPartialPaymentsAndTrackTheRemainder() {
        World w = world();
        setCredit(w, "1000.00", 30, null);
        PurchaseOrderResponse order = placeOrder(w, PaymentTerms.CREDIT, 6);   // 600.00

        submit(w, order, "PART-A");
        submit(w, order, "PART-B");
        submit(w, order, "PART-C");
        confirm(w, order, "PART-A", "200.00");
        confirm(w, order, "PART-B", "200.00");

        PurchaseOrderResponse partial = reload(w, order);
        assertEquals(0, new BigDecimal("400.00").compareTo(partial.amountPaid()));
        assertEquals(0, new BigDecimal("200.00").compareTo(partial.amountOutstanding()));
        PurchaseOrderSettlementResponse settlement = bankSettlement(partial);
        assertEquals(SettlementStatus.PENDING, settlement.status());   // partial is derived, never a new status
        assertEquals(0, new BigDecimal("200.00").compareTo(settlement.amountOutstanding()));

        assertCode(ErrorCode.PAYMENT_AMOUNT_MISMATCH, () -> confirm(w, order, "PART-C", "300.00"));
        confirm(w, order, "PART-C", "200.00");

        PurchaseOrderResponse paid = reload(w, order);
        assertEquals(SettlementStatus.SETTLED, bankSettlement(paid).status());
        assertEquals(0, new BigDecimal("600.00").compareTo(paid.amountPaid()));
        assertEquals(0, paid.amountOutstanding().compareTo(BigDecimal.ZERO));
        // A settled order takes no further payments.
        assertCode(ErrorCode.PAYMENT_STATE_CONFLICT, () -> submit(w, order, "PART-D"));
    }

    // ------------------------------------------------------------ credit terms

    @Test
    void creditOrderOverTheLimitIsRejectedButPayNowIsNot() {
        World w = world();
        setCredit(w, "1000.00", 30, null);

        PurchaseOrderResponse first = placeOrder(w, PaymentTerms.CREDIT, 6);        // 600
        assertEquals(PaymentTerms.CREDIT, first.paymentTerms());
        assertNotNull(first.dueAt());
        assertEquals(6, reservedQuantity(w));                                       // reserved at creation

        assertCode(ErrorCode.CREDIT_LIMIT_EXCEEDED, () -> placeOrder(w, PaymentTerms.CREDIT, 5));   // 600 + 500 > 1000
        assertEquals(6, reservedQuantity(w));                                       // rejected order holds nothing

        PurchaseOrderResponse payNow = placeOrder(w, PaymentTerms.PAY_NOW, 30);     // 3000, same limit ignored
        assertEquals(PaymentTerms.PAY_NOW, payNow.paymentTerms());
        assertNull(payNow.dueAt());
        assertEquals(6, reservedQuantity(w));                                       // PAY_NOW reserves only after payment

        PurchaseOrderResponse second = placeOrder(w, PaymentTerms.CREDIT, 4);       // 600 + 400 = 1000, at the limit
        assertEquals(10, reservedQuantity(w));
        assertNotNull(second.id());
    }

    @Test
    void creditIsRefusedWithoutUsableTerms() {
        World w = world();
        creditDefaults.deleteAll();
        assertCode(ErrorCode.CREDIT_NOT_AVAILABLE, () -> placeOrder(w, PaymentTerms.CREDIT, 1));   // no row, no default

        setCredit(w, "1000.00", 30, CreditTermStatus.SUSPENDED);
        assertCode(ErrorCode.CREDIT_NOT_AVAILABLE, () -> placeOrder(w, PaymentTerms.CREDIT, 1));   // suspended

        // PAY_NOW is never affected.
        placeOrder(w, PaymentTerms.PAY_NOW, 1);
        assertEquals(0, reservedQuantity(w));
    }

    @Test
    void platformDefaultAppliesOnlyWhenConfiguredWithALimit() {
        World w = world();
        try {
            creditTerms.putDefault(w.superAdmin, new UpdateCreditDefaultRequest("KES", new BigDecimal("0.00"), 14, null));
            assertCode(ErrorCode.CREDIT_NOT_AVAILABLE, () -> placeOrder(w, PaymentTerms.CREDIT, 1));   // zero limit = off

            creditTerms.putDefault(w.superAdmin, new UpdateCreditDefaultRequest("KES", new BigDecimal("500.00"), 14, 0L));
            PurchaseOrderResponse order = placeOrder(w, PaymentTerms.CREDIT, 4);
            assertNotNull(order.dueAt());
            assertCode(ErrorCode.CREDIT_LIMIT_EXCEEDED, () -> placeOrder(w, PaymentTerms.CREDIT, 2));

            // A relationship row overrides the default.
            setCredit(w, "5000.00", 60, null);
            placeOrder(w, PaymentTerms.CREDIT, 20);
        } finally {
            creditDefaults.deleteAll();
        }
    }

    @Test
    void creditOrdersCanBePickedBeforePaymentButPayNowOrdersCannotAndPaymentIsNeverBlockedByFulfilment() {
        World w = world();
        setCredit(w, "5000.00", 30, null);
        PurchaseOrderResponse credit = placeOrder(w, PaymentTerms.CREDIT, 3);
        PurchaseOrderResponse payNow = placeOrder(w, PaymentTerms.PAY_NOW, 3);

        PurchaseOrderResponse picked = purchaseOrderService.pickManufacturerItem(w.mfrStaff, credit.id(),
                new PickPurchaseOrderItemRequest(credit.items().get(0).id(), 3));
        assertEquals("READY_FOR_PACKING", picked.fulfilmentStatus());

        // An unpaid PAY_NOW order has no reserved stock yet, so it cannot be picked.
        assertCode(ErrorCode.INVALID_STATE_TRANSITION, () -> purchaseOrderService.pickManufacturerItem(w.mfrStaff,
                payNow.id(), new PickPurchaseOrderItemRequest(payNow.items().get(0).id(), 3)));

        // Fulfilment is under way on the credit order and a bank payment is still recorded.
        submit(w, credit, "LATE-PAY-1");
        confirm(w, credit, "LATE-PAY-1", "300.00");
        PurchaseOrderResponse paid = reload(w, credit);
        assertEquals(SettlementStatus.SETTLED, bankSettlement(paid).status());
        assertEquals("READY_FOR_PACKING", paid.fulfilmentStatus());   // settling did not reset fulfilment
    }

    @Test
    void overdueCreditBlocksNewCreditOrdersUntilPaid() {
        World w = world();
        setCredit(w, "5000.00", 30, null);
        PurchaseOrderResponse overdue = placeOrder(w, PaymentTerms.CREDIT, 2);
        jdbc.update("update purchase_orders set due_at = now() - interval '2 days' where id = ?", overdue.id());

        assertCode(ErrorCode.CREDIT_OVERDUE, () -> placeOrder(w, PaymentTerms.CREDIT, 1));
        placeOrder(w, PaymentTerms.PAY_NOW, 1);   // PAY_NOW still works

        assertTrue(overdueService.flagOverdue(java.time.OffsetDateTime.now(), 100) >= 1);
        assertNotNull(jdbc.queryForObject("select overdue_at from purchase_orders where id = ?",
                java.sql.Timestamp.class, overdue.id()));
        PurchaseOrderResponse flagged = reload(w, overdue);
        assertTrue(flagged.overdue());
        CreditStatementResponse statement = creditTerms.statement(w.mfrAdmin, w.distributor.getId());
        assertTrue(statement.orders().stream().anyMatch(l -> l.purchaseOrderId().equals(overdue.id()) && l.overdue()));
        assertEquals(1, statement.credit().overdueOrders());

        submit(w, overdue, "OVERDUE-PAY");
        confirm(w, overdue, "OVERDUE-PAY", "200.00");
        placeOrder(w, PaymentTerms.CREDIT, 1);   // paid, so credit reopens
    }

    @Test
    void supplierAdministratorCanCancelAnUntouchedOverdueCreditOrderAndStockIsReleased() {
        World w = world();
        setCredit(w, "5000.00", 30, null);
        PurchaseOrderResponse order = placeOrder(w, PaymentTerms.CREDIT, 4);
        assertEquals(4, reservedQuantity(w));

        assertCode(ErrorCode.CONFLICT, () -> purchaseOrderService.cancelOverdue(w.mfrAdmin, order.id(), null));   // not due yet
        jdbc.update("update purchase_orders set due_at = now() - interval '1 day' where id = ?", order.id());
        assertCode(ErrorCode.ACCESS_DENIED, () -> purchaseOrderService.cancelOverdue(w.distAdmin, order.id(), null));
        assertCode(ErrorCode.ACCESS_DENIED, () -> purchaseOrderService.cancelOverdue(w.mfrStaff, order.id(), null));
        assertCode(ErrorCode.ACCESS_DENIED, () -> purchaseOrderService.cancelOverdue(world().mfrAdmin, order.id(), null));

        PurchaseOrderResponse cancelled = purchaseOrderService.cancelOverdue(w.mfrAdmin, order.id(), "No payment");
        assertEquals(PurchaseOrderStatus.CANCELLED, cancelled.status());
        assertEquals(0, reservedQuantity(w));
        // Cancelled credit no longer counts against the limit.
        assertEquals(0, creditTerms.getCredit(w.mfrAdmin, w.distributor.getId()).outstanding().signum());
    }

    @Test
    void anOverdueCreditOrderWithPaymentsCannotBeCancelled() {
        World w = world();
        setCredit(w, "5000.00", 30, null);
        PurchaseOrderResponse order = placeOrder(w, PaymentTerms.CREDIT, 4);
        submit(w, order, "HALF-PAY");
        confirm(w, order, "HALF-PAY", "100.00");
        jdbc.update("update purchase_orders set due_at = now() - interval '1 day' where id = ?", order.id());

        assertCode(ErrorCode.CONFLICT, () -> purchaseOrderService.cancelOverdue(w.mfrAdmin, order.id(), null));
        assertEquals(4, reservedQuantity(w));
    }

    @Test
    void concurrentCreditOrdersCannotExceedTheLimit() throws Exception {
        World w = world();
        setCredit(w, "1000.00", 30, null);
        int threads = 6;                       // each order is 400, so at most two fit under 1000
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<ErrorCode>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<ErrorCode> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        placeOrder(w, PaymentTerms.CREDIT, 4);
                        return null;
                    } catch (ApiException ex) {
                        return ex.getCode();
                    }
                };
                results.add(pool.submit(task));
            }
            ready.await();
            go.countDown();
            int created = 0;
            for (Future<ErrorCode> result : results) {
                ErrorCode code = result.get();
                if (code == null) {
                    created++;
                } else {
                    assertEquals(ErrorCode.CREDIT_LIMIT_EXCEEDED, code);
                }
            }
            assertEquals(2, created);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, new BigDecimal("800.00").compareTo(
                creditTerms.getCredit(w.mfrAdmin, w.distributor.getId()).outstanding()));
        assertEquals(8, reservedQuantity(w));
    }

    // ---------------------------------------------------------------- platform fee

    @Test
    void feeIsZeroByDefaultAndAccruesOnBankSettlementOnceConfigured() {
        World w = world();
        PurchaseOrderResponse free = placeOrder(w, PaymentTerms.PAY_NOW, 5);
        submit(w, free, "FEE-FREE");
        confirm(w, free, "FEE-FREE", "500.00");
        UUID freeSettlement = bankSettlement(reload(w, free)).id();
        PurchaseOrderSettlement stored = settlements.findById(freeSettlement).orElseThrow();
        assertEquals(0, stored.getPlatformFeeAmount().signum());
        assertEquals(0, journalCount(FEE_SOURCE, freeSettlement));

        try {
            setFee(w, "5.00");
            PurchaseOrderResponse charged = placeOrder(w, PaymentTerms.PAY_NOW, 5);
            submit(w, charged, "FEE-CHARGED");
            confirm(w, charged, "FEE-CHARGED", "500.00");
            confirm(w, charged, "FEE-CHARGED", "500.00");   // idempotent, accrues once
            UUID settlementId = bankSettlement(reload(w, charged)).id();

            PurchaseOrderSettlement settlement = settlements.findById(settlementId).orElseThrow();
            assertEquals(0, new BigDecimal("25.00").compareTo(settlement.getPlatformFeeAmount()));
            assertEquals(FeePayer.SUPPLIER, settlement.getPlatformFeePayer());
            assertEquals(1, journalCount(FEE_SOURCE, settlementId));
            List<Map<String, Object>> entries = entries(FEE_SOURCE, settlementId);
            assertEquals(2, entries.size());
            assertTrue(entries.stream().anyMatch(e -> "PLATFORM_FEE_RECEIVABLE".equals(e.get("account_type"))
                    && "DEBIT".equals(e.get("direction"))
                    && w.manufacturer.getId().equals(e.get("organization_id"))));
            assertTrue(entries.stream().anyMatch(e -> "PLATFORM_REVENUE".equals(e.get("account_type"))
                    && "CREDIT".equals(e.get("direction"))));
            // The manufacturer's payable is not reduced by the fee.
            assertTrue(entries.stream().noneMatch(e -> "ORGANIZATION_PAYABLE".equals(e.get("account_type"))));

            LocalDate today = LocalDate.now();
            PlatformFeeReportResponse report = feeService.report(w.superAdmin, today.minusDays(1), today.plusDays(1), "KES");
            PlatformFeeReportResponse.Row row = report.rows().stream()
                    .filter(r -> r.organizationId().equals(w.manufacturer.getId())).findFirst().orElseThrow();
            assertEquals(1, row.feeCount());
            assertEquals(0, new BigDecimal("25.00").compareTo(row.totalFees()));
            assertCode(ErrorCode.ACCESS_DENIED, () -> feeService.report(w.mfrAdmin, today, today, "KES"));
        } finally {
            setFee(w, "0.00");
        }
    }

    @Test
    void mpesaStkSettlementPostsTheFullAmountToTheManufacturerAndAccruesTheFee() {
        World w = world();
        PurchaseOrderResponse order = placeOrder(w, PaymentTerms.PAY_NOW, 5);
        UUID settlementId = settlements.findByPurchaseOrderIdOrderByCreatedAtDesc(order.id()).get(0).getId();
        try {
            setFee(w, "2.00");
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> {
                PurchaseOrderSettlement settlement = settlements.findByIdForUpdate(settlementId).orElseThrow();
                settlement.setStatus(SettlementStatus.SETTLED);
                settlement.setProviderReference("QWE" + shortId().toUpperCase());
                settlement.setSettledAt(java.time.OffsetDateTime.now());
                // The callback handler calls this when Daraja confirms an STK payment.
                Object target = AopTestUtils.getUltimateTargetObject(paymentService);
                ReflectionTestUtils.invokeMethod(target, "finalizePurchaseOrderSettlement", settlement);
                settlements.save(settlement);
            });

            PurchaseOrderSettlement settlement = settlements.findById(settlementId).orElseThrow();
            assertEquals(SettlementMethod.MPESA, settlement.getMethod());
            assertEquals(0, new BigDecimal("500.00").compareTo(settlement.getAmountPaid()));
            assertEquals(0, new BigDecimal("10.00").compareTo(settlement.getPlatformFeeAmount()));

            List<Map<String, Object>> capture = entries(SETTLEMENT_SOURCE, settlementId);
            assertEquals(2, capture.size());
            assertTrue(capture.stream().anyMatch(e -> "PROVIDER_CLEARING".equals(e.get("account_type"))
                    && "DEBIT".equals(e.get("direction"))
                    && new BigDecimal("500.00").compareTo((BigDecimal) e.get("amount")) == 0));
            assertTrue(capture.stream().anyMatch(e -> "ORGANIZATION_PAYABLE".equals(e.get("account_type"))
                    && "CREDIT".equals(e.get("direction"))
                    && w.manufacturer.getId().equals(e.get("organization_id"))
                    && new BigDecimal("500.00").compareTo((BigDecimal) e.get("amount")) == 0));
            assertEquals(1, journalCount(FEE_SOURCE, settlementId));

            PurchaseOrderSettlementResponse response = reload(w, order).settlements().stream()
                    .filter(s -> s.id().equals(settlementId)).findFirst().orElseThrow();
            assertEquals(1, response.payments().size());
            assertEquals(SettlementPaymentStatus.CONFIRMED, response.payments().get(0).status());
            assertEquals(SettlementMethod.MPESA, response.payments().get(0).method());
        } finally {
            setFee(w, "0.00");
        }
    }

    // ----------------------------------------------------------------- proof files

    @Test
    void invalidAndOversizedProofsAreRejectedAndStoreNothing() throws IOException {
        World w = world();
        PurchaseOrderResponse order = placeOrder(w, PaymentTerms.PAY_NOW, 1);
        byte[] pdfBytes = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(pdfBytes, 0, oversized, 0, pdfBytes.length);
        List<MultipartFile> rejected = List.of(
                new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("file", "slip.pdf", "application/pdf", "not really a pdf".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("file", "slip.png", "image/png", pdfBytes),
                new MockMultipartFile("file", "script.html", "text/html", "<script>1</script>".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("file", "big.pdf", "application/pdf", oversized),
                new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]));
        int index = 0;
        for (MultipartFile file : rejected) {
            String reference = "BAD-FILE-" + index++;
            assertCode(ErrorCode.BAD_REQUEST,
                    () -> bank.submit(w.distAdmin, order.id(), reference, file, UUID.randomUUID().toString()));
        }
        assertTrue(reload(w, order).settlements().stream().noneMatch(s -> s.method() == SettlementMethod.BANK_TRANSFER
                && !s.payments().isEmpty()));
        assertEquals(0, jdbc.queryForObject("select count(*) from settlement_proof_documents where purchase_order_id = ?",
                Integer.class, order.id()));

        // Real PNG and JPEG files are accepted.
        bank.submit(w.distAdmin, order.id(), "GOOD-PNG",
                new MockMultipartFile("file", "slip.png", "image/png", image("png")), UUID.randomUUID().toString());
        bank.submit(w.distAdmin, order.id(), "GOOD-JPG",
                new MockMultipartFile("file", "slip.jpg", "image/jpeg", image("jpg")), UUID.randomUUID().toString());
        assertEquals(2, bankSettlement(reload(w, order)).payments().size());
    }

    @Test
    void proofsAreOnlyDownloadableBySupplierAdministratorsAndSuperAdmins() {
        World w = world();
        World stranger = world();
        PurchaseOrderResponse order = placeOrder(w, PaymentTerms.PAY_NOW, 1);
        submit(w, order, "PROOF-REF");
        UUID documentId = bankSettlement(reload(w, order)).payments().get(0).proofDocumentId();

        SettlementProofDocumentService.ProofDownload download = proofService.download(w.mfrAdmin, order.id(), documentId);
        assertEquals("application/pdf", download.contentType());
        assertTrue(new String(download.content(), StandardCharsets.US_ASCII).startsWith("%PDF-"));
        assertNotNull(proofService.download(w.superAdmin, order.id(), documentId));

        assertCode(ErrorCode.ACCESS_DENIED, () -> proofService.download(stranger.mfrAdmin, order.id(), documentId));
        assertCode(ErrorCode.ACCESS_DENIED, () -> proofService.download(w.distAdmin, order.id(), documentId));
        assertCode(ErrorCode.ACCESS_DENIED, () -> proofService.download(stranger.distAdmin, order.id(), documentId));
        // A document id only resolves under the order it was uploaded for.
        PurchaseOrderResponse other = placeOrder(w, PaymentTerms.PAY_NOW, 1);
        assertCode(ErrorCode.NOT_FOUND, () -> proofService.download(w.mfrAdmin, other.id(), documentId));
    }
}
