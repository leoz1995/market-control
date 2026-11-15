package marketcontrol.domain.model.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MovementTypeTest {
    @Test void resolvesEveryPersistedCode() {
        assertAll(
                () -> assertEquals(MovementType.ENTRY, MovementType.fromCode(1)),
                () -> assertEquals(MovementType.EXIT, MovementType.fromCode(2)),
                () -> assertEquals(MovementType.LOSS, MovementType.fromCode(3)),
                () -> assertEquals(MovementType.ADJUSTMENT, MovementType.fromCode(4)));
    }

    @Test void rejectsUnknownPersistedCode() {
        assertThrows(IllegalArgumentException.class, () -> MovementType.fromCode(99));
    }
}
