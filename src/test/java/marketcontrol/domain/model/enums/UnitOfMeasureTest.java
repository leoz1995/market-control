package marketcontrol.domain.model.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UnitOfMeasureTest {
    @Test void resolvesEveryPersistedCode() {
        assertAll(
                () -> assertEquals(UnitOfMeasure.UNIT, UnitOfMeasure.fromCode(1)),
                () -> assertEquals(UnitOfMeasure.KILOGRAM, UnitOfMeasure.fromCode(2)),
                () -> assertEquals(UnitOfMeasure.GRAM, UnitOfMeasure.fromCode(3)),
                () -> assertEquals(UnitOfMeasure.LITER, UnitOfMeasure.fromCode(4)),
                () -> assertEquals(UnitOfMeasure.MILLILITER, UnitOfMeasure.fromCode(5)));
    }

    @Test void rejectsUnknownPersistedCode() {
        assertThrows(IllegalArgumentException.class, () -> UnitOfMeasure.fromCode(99));
    }
}
