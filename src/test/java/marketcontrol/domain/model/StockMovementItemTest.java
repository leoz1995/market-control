package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import marketcontrol.exception.data.DataMappingException;

class StockMovementItemTest {
    @Test void createsItemWithPositiveQuantity() {
        UUID lotId = UUID.randomUUID();
        StockMovementItem item = StockMovementItem.create(lotId, new BigDecimal("2.5"));
        assertNotNull(item.getId());
        assertEquals(lotId, item.getLotId());
        assertEquals(0, new BigDecimal("2.5").compareTo(item.getQuantity()));
    }

    @Test void rejectsNullLotId() {
        assertThrows(IllegalArgumentException.class, () -> StockMovementItem.create(null, BigDecimal.ONE));
    }

    @Test void rejectsNullOrNonPositiveQuantity() {
        UUID lotId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> StockMovementItem.create(lotId, null));
        assertThrows(IllegalArgumentException.class, () -> StockMovementItem.create(lotId, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> StockMovementItem.create(lotId, new BigDecimal("-1")));
    }

    @Test void reconstitutesItemPreservingIdentifiers() {
        UUID itemId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        StockMovementItem item = StockMovementItem.reconstitute(itemId, lotId, BigDecimal.ONE);
        assertEquals(itemId, item.getId());
        assertEquals(lotId, item.getLotId());
    }

    @Test void rejectsNullIdentifiersDuringReconstitution() {
        UUID lotId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        assertThrows(DataMappingException.class, () -> StockMovementItem.reconstitute(null, lotId, BigDecimal.ONE));
        assertThrows(DataMappingException.class, () -> StockMovementItem.reconstitute(itemId, null, BigDecimal.ONE));
    }
}
