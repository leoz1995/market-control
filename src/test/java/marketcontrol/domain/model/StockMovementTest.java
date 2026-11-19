package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.enums.MovementType;
import marketcontrol.exception.data.DataMappingException;

class StockMovementTest {
    private final LocalDateTime now = LocalDateTime.now();
    private final StockMovementItem item = StockMovementItem.create(UUID.randomUUID(), BigDecimal.ONE, BigDecimal.ONE);

    private Supplier supplier(boolean active) {
        Supplier supplier = Supplier.create("Legal", "Trade", "12345678901234", "a@b.com", "123", "Contact");
        supplier.setActive(active);
        return supplier;
    }

    @Test void entryRequiresActiveSupplier() {
        assertEquals(MovementType.ENTRY, StockMovement.create(MovementType.ENTRY, now, "Compra", supplier(true), List.of(item)).getType());
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.ENTRY, now, "Compra", null, List.of(item)));
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.ENTRY, now, "Compra", supplier(false), List.of(item)));
    }

    @Test void exitAndLossRequireNoSupplier() {
        assertNull(StockMovement.create(MovementType.EXIT, now, "Venda", List.of(item)).getSupplier());
        assertNull(StockMovement.create(MovementType.LOSS, now, "Perda", List.of(item)).getSupplier());
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.EXIT, now, "Venda", supplier(true), List.of(item)));
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.LOSS, now, "Perda", supplier(true), List.of(item)));
    }

    @Test void requiresNonEmptyNonNullItemsAndReturnsImmutableCopy() {
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.EXIT, now, "Venda", (List<StockMovementItem>) null));
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.EXIT, now, "Venda", List.of()));
        List<StockMovementItem> mutable = new ArrayList<>(List.of(item));
        StockMovement movement = StockMovement.create(MovementType.EXIT, now, "Venda", mutable);
        mutable.clear();
        assertEquals(1, movement.getItems().size());
        assertThrows(UnsupportedOperationException.class, () -> movement.getItems().clear());
    }

    @Test void reconstitutesHistoricalEntryWithInactiveSupplier() {
        Supplier inactive = supplier(false);
        StockMovement movement = StockMovement.reconstitute(UUID.randomUUID(), MovementType.ENTRY, now, "Histórico", inactive, List.of(item));
        assertFalse(movement.getSupplier().isActive());
        assertThrows(DataMappingException.class, () -> StockMovement.reconstitute(UUID.randomUUID(), MovementType.ENTRY, now, "Histórico", null, List.of(item)));
    }
}
