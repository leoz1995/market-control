package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.exception.data.DataMappingException;

class LotTest {
    private final Product product = Product.create("Arroz", "789123", null,
            Category.create("Grãos", "Categoria de grãos", true), UnitOfMeasure.UNIT);

    @Test void createsValidLotWithoutReceivedTotalField() {
        Lot lot = Lot.create(product, LocalDate.now(), LocalDate.now(), new BigDecimal("2.5"));
        assertNotNull(lot.getId());
        assertEquals(new BigDecimal("2.5"), lot.getAvailableQuantity());
    }

    @Test void rejectsNullProductDatesAndInvalidDates() {
        LocalDate today = LocalDate.now();
        assertThrows(IllegalArgumentException.class, () -> Lot.create(null, today, today, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, null, today, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, today, null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, today.plusDays(1), today.plusDays(1), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, today, today.minusDays(1), BigDecimal.ONE));
    }

    @Test void rejectsNullZeroAndNegativeInitialQuantity() {
        LocalDate today = LocalDate.now();
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, today, today, null));
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, today, today, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, today, today, new BigDecimal("-1")));
    }

    @Test void reconstitutesZeroAndRejectsNegativeBalance() {
        UUID id = UUID.randomUUID();
        assertEquals(BigDecimal.ZERO, Lot.reconstitute(id, product, LocalDate.now(), LocalDate.now(), BigDecimal.ZERO).getAvailableQuantity());
        assertThrows(DataMappingException.class, () -> Lot.reconstitute(id, product, LocalDate.now(), LocalDate.now(), new BigDecimal("-0.1")));
    }

    @Test void reducesAndAddsBalanceWithBounds() {
        Lot lot = Lot.create(product, LocalDate.now(), LocalDate.now().plusDays(2), new BigDecimal("10"));
        assertEquals(new BigDecimal("7"), lot.reduceQuantity(new BigDecimal("3")));
        assertEquals(new BigDecimal("9"), lot.addQuantity(new BigDecimal("2")));
        assertThrows(IllegalArgumentException.class, () -> lot.reduceQuantity(new BigDecimal("10")));
        assertThrows(IllegalArgumentException.class, () -> lot.addQuantity(BigDecimal.ZERO));
    }

    @Test void checksExpiryAgainstCurrentDate() {
        assertTrue(Lot.create(product, LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), BigDecimal.ONE).isExpired());
        assertFalse(Lot.create(product, LocalDate.now(), LocalDate.now(), BigDecimal.ONE).isExpired());
    }
}
