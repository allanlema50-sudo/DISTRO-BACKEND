package com.example.distrobackend.Domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "purchase_order_shipment_receipt_lines", uniqueConstraints = @UniqueConstraint(
        name = "uk_shipment_receipt_purchase_order_item",
        columnNames = {"shipment_id", "purchase_order_item_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderShipmentReceiptLine {
    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private PurchaseOrderShipment shipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Column(name = "expected_quantity", nullable = false)
    private int expectedQuantity;

    @Column(name = "accepted_quantity", nullable = false)
    private int acceptedQuantity;

    @Column(name = "damaged_quantity", nullable = false)
    private int damagedQuantity;

    @Column(name = "missing_quantity", nullable = false)
    private int missingQuantity;
}
