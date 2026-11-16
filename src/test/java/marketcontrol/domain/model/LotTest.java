package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import marketcontrol.exception.data.DataMappingException;

import marketcontrol.domain.model.enums.UnitOfMeasure;

class LotTest {
    private final Category category = Category.create("Grãos", "Melhores grãos", true);
    private final Product product = Product.create("Feijão", "12345678900A", "Enlatados", category, UnitOfMeasure.UNIT);

    @Test void createsLotWithValidData() {
        Lot lot = Lot.create(product, LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("100"));
        assertNotNull(lot.getId());
        assertEquals(0, new BigDecimal("100").compareTo(lot.getTotalReceivedQuantity()));
        assertEquals(0, new BigDecimal("100").compareTo(lot.getAvailableQuantity()));
    }

    @Test void rejectsNullProduct() {
        assertThrows(IllegalArgumentException.class, () -> Lot.create(null, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ONE));
    }

    @Test void rejectsNullEntryDate() {
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, null, LocalDate.now().plusDays(1), BigDecimal.ONE));
    }

    @Test void rejectsNullExpirationDate() {
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, LocalDate.now(), null, BigDecimal.ONE));
    }

    @Test void rejectsNullInitialQuantity() {
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, LocalDate.now(), LocalDate.now().plusDays(1), null));
    }

    @Test void rejectsZeroInitialQuantity() {
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ZERO));
    }

    @Test void rejectsNegativeInitialQuantity() {
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, LocalDate.now(), LocalDate.now().plusDays(1), new BigDecimal("-1")));
    }

    @Test void rejectsExpirationBeforeEntry() {
        LocalDate today = LocalDate.now();
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, today, today.minusDays(1), BigDecimal.ONE));
    }

    @Test void rejectsEntryDateInFuture() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        assertThrows(IllegalArgumentException.class, () -> Lot.create(product, tomorrow, tomorrow, BigDecimal.ONE));
    }

    @Test void allowsExpirationOnEntryDate() {
        LocalDate today = LocalDate.now();
        assertNotNull(Lot.create(product, today, today, BigDecimal.ONE));
    }

    @Test void reconstitutesPersistedLotAndAvailableQuantity() {
        UUID id = UUID.randomUUID();
        Lot lot = Lot.reconstitute(id, product, LocalDate.now().minusDays(2), LocalDate.now().plusDays(10),
                new BigDecimal("100"), new BigDecimal("42"));
        assertEquals(id, lot.getId());
        assertEquals(0, new BigDecimal("42").compareTo(lot.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("100").compareTo(lot.getTotalReceivedQuantity()));
    }

    @Test void reconstitutesExhaustedLotWithZeroAvailableQuantity() {
        Lot lot = Lot.reconstitute(UUID.randomUUID(), product, LocalDate.now().minusDays(2),
                LocalDate.now().plusDays(10), new BigDecimal("100"), BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo(lot.getAvailableQuantity()));
        assertEquals(0, new BigDecimal("100").compareTo(lot.getTotalReceivedQuantity()));
    }

    @Test void rejectsNullIdDuringReconstitution() {
        assertThrows(DataMappingException.class, () -> Lot.reconstitute(null, product, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test void rejectsNullProductDuringReconstitution() {
        assertThrows(DataMappingException.class, () -> Lot.reconstitute(UUID.randomUUID(), null, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test void rejectsNullEntryDateDuringReconstitution() {
        assertThrows(DataMappingException.class, () -> Lot.reconstitute(UUID.randomUUID(), product, null, LocalDate.now().plusDays(1), BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test void rejectsNullExpirationDateDuringReconstitution() {
        assertThrows(DataMappingException.class, () -> Lot.reconstitute(UUID.randomUUID(), product, LocalDate.now(), null, BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test void rejectsExpirationBeforeEntryDuringReconstitution() {
        LocalDate today = LocalDate.now();
        assertThrows(DataMappingException.class, () -> Lot.reconstitute(UUID.randomUUID(), product, today, today.minusDays(1), BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test void rejectsNullInitialQuantityDuringReconstitution() {
        assertThrows(DataMappingException.class, () -> Lot.reconstitute(UUID.randomUUID(), product, LocalDate.now(), LocalDate.now().plusDays(1), null, BigDecimal.ZERO));
    }

    @Test void rejectsNullAvailableQuantityDuringReconstitution() {
        assertThrows(DataMappingException.class, () -> Lot.reconstitute(UUID.randomUUID(), product, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ONE, null));
    }

    @Test void rejectsNegativeAvailableQuantityDuringReconstitution() {
        assertThrows(DataMappingException.class, () -> Lot.reconstitute(UUID.randomUUID(), product, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ONE, new BigDecimal("-1")));
    }

    @Test void rejectsAvailableQuantityAboveInitialDuringReconstitution() {
        assertThrows(DataMappingException.class, () -> Lot.reconstitute(UUID.randomUUID(), product, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ONE, new BigDecimal("2")));
    }

    @Test void rejectsNonPositiveInitialQuantityDuringReconstitution() {
        assertThrows(DataMappingException.class, () -> Lot.reconstitute(UUID.randomUUID(), product, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test void reportsExpiredOnlyWhenExpirationIsBeforeToday() {
        Lot expired = Lot.create(product, LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), BigDecimal.ONE);
        Lot validToday = Lot.create(product, LocalDate.now(), LocalDate.now(), BigDecimal.ONE);
        assertTrue(expired.isExpired());
        org.junit.jupiter.api.Assertions.assertFalse(validToday.isExpired());
    }
}
