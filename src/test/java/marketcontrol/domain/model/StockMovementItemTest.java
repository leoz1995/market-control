package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import marketcontrol.exception.data.DataMappingException;

class StockMovementItemTest {
    private final UUID lotId = UUID.randomUUID();

    @Test void createsItemAndCalculatesRoundedTotalCost() {
        StockMovementItem item = StockMovementItem.create(lotId, new BigDecimal("1.333"), new BigDecimal("2.50"));
        assertNotNull(item.getId());
        assertEquals(lotId, item.getLotId());
        assertEquals(new BigDecimal("3.33"), item.getTotalCost());
        assertEquals(new BigDecimal("1.01"), StockMovementItem.create(lotId, new BigDecimal("1.001"), new BigDecimal("1.005")).getTotalCost());
    }

    @Test void rejectsNullIdentifiersAndNonPositiveValues() {
        assertThrows(IllegalArgumentException.class, () -> StockMovementItem.create(null, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> StockMovementItem.create(lotId, null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> StockMovementItem.create(lotId, BigDecimal.ZERO, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> StockMovementItem.create(lotId, new BigDecimal("-1"), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> StockMovementItem.create(lotId, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> StockMovementItem.create(lotId, BigDecimal.ONE, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> StockMovementItem.create(lotId, BigDecimal.ONE, new BigDecimal("-0.01")));
    }

    @Test void reconstitutesAndValidatesPersistedValues() {
        UUID id = UUID.randomUUID();
        StockMovementItem item = StockMovementItem.reconstitute(id, lotId, new BigDecimal("2"), new BigDecimal("4.25"));
        assertEquals(id, item.getId());
        assertEquals(new BigDecimal("8.50"), item.getTotalCost());
        assertThrows(DataMappingException.class, () -> StockMovementItem.reconstitute(null, lotId, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(DataMappingException.class, () -> StockMovementItem.reconstitute(id, lotId, BigDecimal.ZERO, BigDecimal.ONE));
        assertThrows(DataMappingException.class, () -> StockMovementItem.reconstitute(id, lotId, BigDecimal.ONE, BigDecimal.ZERO));
    }
}
