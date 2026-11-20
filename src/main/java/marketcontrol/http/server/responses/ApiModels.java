package marketcontrol.http.server.responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.domain.model.interfaces.ReadOnlySupplier;
import marketcontrol.domain.repository.ProductRepository.ProductInventorySummary;

/** Explicit HTTP request and response contracts, independent of persistence models. */
public final class ApiModels {
    private ApiModels() {}

    public record ProductRequest(String name, String barCode, String description, UUID categoryId, UnitOfMeasure unit) {}
    public record CategoryRequest(String name, String description, Boolean active) {}
    public record SupplierRequest(String legalName, String tradeName, String document, String email,
                                  String phone, String contactName) {}
    public record LotRequest(UUID productId, UUID supplierId, LocalDate entryDate, LocalDate expirationDate,
                             BigDecimal initialQuantity, BigDecimal unitCost) {}
    public record MovementRequest(BigDecimal quantity, String cause) {}

    public record CategoryResponse(UUID id, String name, String description, boolean active) {
        public static CategoryResponse from(ReadOnlyCategory category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), category.isActive());
        }
    }

    public record ProductResponse(UUID id, String name, String barCode, String description,
                                  UnitOfMeasure unit, CategoryResponse category) {
        public static ProductResponse from(ReadOnlyProduct product) {
            return new ProductResponse(product.getId(), product.getName(), product.getBarCode(), product.getDescription(),
                    product.getUnit(), CategoryResponse.from(product.getCategory()));
        }
    }

    public record ProductInventoryResponse(UUID id, String name, String barCode, String description,
                                           UnitOfMeasure unit, CategoryResponse category,
                                           BigDecimal quantity, long lots) {
        public static ProductInventoryResponse from(ProductInventorySummary summary) {
            ReadOnlyProduct product = summary.product();
            return new ProductInventoryResponse(product.getId(), product.getName(), product.getBarCode(),
                    product.getDescription(), product.getUnit(), CategoryResponse.from(product.getCategory()),
                    summary.availableQuantity(), summary.lotCount());
        }
    }

    public record SupplierResponse(UUID id, String legalName, String tradeName, String document, String email,
                                   String phone, String contactName, boolean active, LocalDateTime createdAt) {
        public static SupplierResponse from(ReadOnlySupplier supplier) {
            return new SupplierResponse(supplier.getId(), supplier.getLegalName(), supplier.getTradeName(),
                    supplier.getDocument(), supplier.getEmail(), supplier.getPhone(), supplier.getContactName(),
                    supplier.isActive(), supplier.getCreatedAt());
        }
    }

    public record LotResponse(UUID id, UUID productId, LocalDate entryDate, LocalDate expirationDate,
                              BigDecimal availableQuantity, boolean expired) {
        public static LotResponse from(ReadOnlyLot lot) {
            return new LotResponse(lot.getId(), lot.getProduct().getId(), lot.getEntryDate(), lot.getExpirationDate(),
                    lot.getAvailableQuantity(), lot.isExpired());
        }
    }

    public record StockResponse(UUID productId, BigDecimal totalAvailableQuantity) {}
    public record CreatedResponse(UUID id) {}
}
