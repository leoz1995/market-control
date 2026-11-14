package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.enums.MovementType;

class StockMovementTest {
    private final StockMovementItem item = StockMovementItem.create(UUID.randomUUID(), BigDecimal.ONE);
    private final LocalDateTime at = LocalDateTime.now();

    @Test void createsMovementWithValidData() {
        StockMovement movement = StockMovement.create(MovementType.ENTRY, at, "Compra recebida", List.of(item));
        assertNotNull(movement.getId());
        assertEquals(MovementType.ENTRY, movement.getType());
        assertEquals(at, movement.getDateTime());
        assertEquals(List.of(item), movement.getItems());
    }

    @Test void movementItemsAreCopiedOnCreation() {
        List<StockMovementItem> items = new ArrayList<>(List.of(item));
        StockMovement movement = StockMovement.create(MovementType.EXIT, at, "Venda", items);
        items.clear();
        assertEquals(1, movement.getItems().size());
        assertThrows(UnsupportedOperationException.class, () -> movement.getItems().clear());
    }

    @Test void reconstitutesMovementWithPersistedId() {
        UUID id = UUID.randomUUID();
        StockMovement movement = StockMovement.reconstitute(id, MovementType.LOSS, at, "Produto vencido", List.of(item));
        assertEquals(id, movement.getId());
        assertEquals(MovementType.LOSS, movement.getType());
    }

    @Test void rejectsNullType() {
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(null, at, "Descrição", List.of(item)));
    }

    @Test void rejectsNullDateTime() {
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.ENTRY, null, "Descrição", List.of(item)));
    }

    @Test void rejectsNullOrBlankDescription() {
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.ENTRY, at, null, List.of(item)));
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.ENTRY, at, "  ", List.of(item)));
    }

    @Test void rejectsNullOrEmptyItemList() {
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.ENTRY, at, "Descrição", null));
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.ENTRY, at, "Descrição", List.of()));
    }

    @Test void rejectsNullItemInList() {
        List<StockMovementItem> items = new ArrayList<>();
        items.add(null);
        assertThrows(IllegalArgumentException.class, () -> StockMovement.create(MovementType.ENTRY, at, "Descrição", items));
    }

    @Test void rejectsNullIdDuringReconstitution() {
        assertThrows(IllegalArgumentException.class, () -> StockMovement.reconstitute(null, MovementType.ENTRY, at, "Descrição", List.of(item)));
    }
}
