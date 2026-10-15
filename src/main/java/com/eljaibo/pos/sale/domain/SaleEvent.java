package com.eljaibo.pos.sale.domain;

import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sale_event")
public class SaleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_id")
    private SaleItem saleItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private SaleEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by_id", nullable = false)
    private AppUser performedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private Terminal terminal;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SaleEvent() {
        // Requerido por JPA.
    }

    public static SaleEvent opened(Sale sale, AppUser user, Terminal terminal, Instant now) {
        SaleEvent event = new SaleEvent();
        event.sale = Objects.requireNonNull(sale);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);
        event.eventType = SaleEventType.SALE_OPENED;
        event.details = Map.of("saleNumber", sale.getSaleNumber());
        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;
        return event;
    }

    public static SaleEvent itemAdded(
            Sale sale,
            SaleItem saleItem,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.saleItem = Objects.requireNonNull(saleItem);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType = SaleEventType.SALE_ITEM_ADDED;

        event.details = Map.of(
                "productName", saleItem.getProductName(),
                "presentationName", saleItem.getPresentationName(),
                "quantity", saleItem.getQuantity(),
                "unitPrice", saleItem.getUnitPrice(),
                "lineTotal", saleItem.getLineTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent itemQuantityChanged(
            Sale sale,
            SaleItem saleItem,
            BigDecimal previousQuantity,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.saleItem = Objects.requireNonNull(saleItem);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType =
                SaleEventType.SALE_ITEM_QUANTITY_CHANGED;

        event.details = Map.of(
                "previousQuantity", previousQuantity,
                "newQuantity", saleItem.getQuantity(),
                "newLineTotal", saleItem.getLineTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent itemRemoved(
            Sale sale,
            SaleItem saleItem,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.saleItem = Objects.requireNonNull(saleItem);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType = SaleEventType.SALE_ITEM_REMOVED;

        event.details = Map.of(
                "productName", saleItem.getProductName(),
                "presentationName", saleItem.getPresentationName(),
                "quantity", saleItem.getQuantity(),
                "lineTotal", saleItem.getLineTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent itemPriceChanged(
            Sale sale,
            SaleItem saleItem,
            BigDecimal previousPrice,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.saleItem = Objects.requireNonNull(saleItem);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType =
                SaleEventType.SALE_ITEM_PRICE_CHANGED;

        event.details = Map.of(
                "previousPrice", previousPrice,
                "newPrice", saleItem.getUnitPrice(),
                "newLineTotal", saleItem.getLineTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent itemDiscountApplied(
            Sale sale,
            SaleItem saleItem,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.saleItem = Objects.requireNonNull(saleItem);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType =
                SaleEventType.SALE_ITEM_DISCOUNT_APPLIED;

        event.details = Map.of(
                "discountType",
                saleItem.getDiscountType().name(),
                "discountValue",
                saleItem.getDiscountValue(),
                "discountAmount",
                saleItem.getDiscountAmount(),
                "lineTotal",
                saleItem.getLineTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent itemDiscountRemoved(
            Sale sale,
            SaleItem saleItem,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.saleItem = Objects.requireNonNull(saleItem);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType =
                SaleEventType.SALE_ITEM_DISCOUNT_REMOVED;

        event.details = Map.of(
                "lineTotal",
                saleItem.getLineTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent itemCourtesyApplied(
            Sale sale,
            SaleItem saleItem,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.saleItem = Objects.requireNonNull(saleItem);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType =
                SaleEventType.SALE_ITEM_COURTESY_APPLIED;

        event.details = Map.of(
                "grossAmount",
                saleItem.getGrossAmount()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent itemCourtesyRemoved(
            Sale sale,
            SaleItem saleItem,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.saleItem = Objects.requireNonNull(saleItem);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType =
                SaleEventType.SALE_ITEM_COURTESY_REMOVED;

        event.details = Map.of(
                "lineTotal",
                saleItem.getLineTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent saleDiscountApplied(
            Sale sale,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType = SaleEventType.SALE_DISCOUNT_APPLIED;

        event.details = Map.of(
                "discountType", sale.getSaleDiscountType().name(),
                "discountValue", sale.getSaleDiscountValue(),
                "discountAmount", sale.getSaleDiscountAmount(),
                "total", sale.getTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent saleDiscountRemoved(
            Sale sale,
            DiscountType previousDiscountType,
            BigDecimal previousDiscountValue,
            BigDecimal previousDiscountAmount,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType =
                SaleEventType.SALE_DISCOUNT_REMOVED;

        event.details = Map.of(
                "discountType",
                previousDiscountType.name(),
                "discountValue",
                previousDiscountValue,
                "discountAmount",
                previousDiscountAmount,
                "totalAfterRemoval",
                sale.getTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent discountApplied(
            Sale sale,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType = SaleEventType.SALE_DISCOUNT_APPLIED;

        event.details = Map.of(
                "discountType",
                sale.getSaleDiscountType().name(),
                "discountValue",
                sale.getSaleDiscountValue(),
                "discountAmount",
                sale.getSaleDiscountAmount(),
                "total",
                sale.getTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent discountRemoved(
            Sale sale,
            DiscountType previousDiscountType,
            BigDecimal previousDiscountValue,
            BigDecimal previousDiscountAmount,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType =
                SaleEventType.SALE_DISCOUNT_REMOVED;

        event.details = Map.of(
                "discountType",
                previousDiscountType.name(),
                "discountValue",
                previousDiscountValue,
                "discountAmount",
                previousDiscountAmount,
                "totalAfterRemoval",
                sale.getTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent paid(
            Sale sale,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);
        event.eventType = SaleEventType.SALE_PAID;

        event.details = Map.of(
                "total",
                sale.getTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent credit(
            Sale sale,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);
        event.eventType = SaleEventType.SALE_CREDIT;

        event.details = Map.of(
                "customerId",
                sale.getCustomer().getId().toString(),
                "total",
                sale.getTotal()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public static SaleEvent itemMoved(
            Sale eventSale,
            SaleItem saleItem,
            Sale sourceSale,
            Sale targetSale,
            String direction,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(eventSale);
        event.saleItem = Objects.requireNonNull(saleItem);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);

        event.eventType =
                SaleEventType.SALE_ITEM_MOVED;

        event.details = Map.of(
                "direction", direction,
                "sourceSaleId",
                sourceSale.getId().toString(),
                "sourceSaleNumber",
                sourceSale.getSaleNumber(),
                "targetSaleId",
                targetSale.getId().toString(),
                "targetSaleNumber",
                targetSale.getSaleNumber(),
                "productName",
                saleItem.getProductName(),
                "presentationName",
                saleItem.getPresentationName(),
                "quantity",
                saleItem.getQuantity(),
                "lineTotal",
                saleItem.getLineTotal()
        );

        event.occurredAt =
                Objects.requireNonNull(now);

        event.createdAt = now;

        return event;
    }

    public static SaleEvent itemPartiallyMoved(
            Sale eventSale,
            SaleItem eventItem,
            SaleItem sourceItem,
            SaleItem targetItem,
            Sale sourceSale,
            Sale targetSale,
            String direction,
            BigDecimal movedQuantity,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale =
                Objects.requireNonNull(eventSale);

        event.saleItem =
                Objects.requireNonNull(eventItem);

        event.performedBy =
                Objects.requireNonNull(user);

        event.terminal =
                Objects.requireNonNull(terminal);

        event.eventType =
                SaleEventType.SALE_ITEM_MOVED;

        event.details = Map.ofEntries(
                Map.entry(
                        "mode",
                        "PARTIAL"
                ),
                Map.entry(
                        "direction",
                        direction
                ),
                Map.entry(
                        "sourceSaleId",
                        sourceSale.getId().toString()
                ),
                Map.entry(
                        "sourceSaleNumber",
                        sourceSale.getSaleNumber()
                ),
                Map.entry(
                        "targetSaleId",
                        targetSale.getId().toString()
                ),
                Map.entry(
                        "targetSaleNumber",
                        targetSale.getSaleNumber()
                ),
                Map.entry(
                        "sourceItemId",
                        sourceItem.getId().toString()
                ),
                Map.entry(
                        "targetItemId",
                        targetItem.getId().toString()
                ),
                Map.entry(
                        "productName",
                        sourceItem.getProductName()
                ),
                Map.entry(
                        "movedQuantity",
                        movedQuantity
                ),
                Map.entry(
                        "sourceRemainingQuantity",
                        sourceItem.getQuantity()
                )
        );

        event.occurredAt =
                Objects.requireNonNull(now);

        event.createdAt = now;

        return event;
    }

    public static SaleEvent cancelled(
            Sale sale,
            String reason,
            AppUser user,
            Terminal terminal,
            Instant now
    ) {
        SaleEvent event = new SaleEvent();

        event.sale = Objects.requireNonNull(sale);
        event.performedBy = Objects.requireNonNull(user);
        event.terminal = Objects.requireNonNull(terminal);
        event.eventType = SaleEventType.SALE_CANCELLED;

        event.details = Map.of(
                "total",
                sale.getTotal(),
                "reason",
                Objects.requireNonNull(reason).trim()
        );

        event.occurredAt = Objects.requireNonNull(now);
        event.createdAt = now;

        return event;
    }

    public UUID getId() { return id; }
    public Sale getSale() { return sale; }
    public SaleEventType getEventType() { return eventType; }
    public AppUser getPerformedBy() { return performedBy; }
    public Terminal getTerminal() { return terminal; }
    public Instant getOccurredAt() { return occurredAt; }
}
