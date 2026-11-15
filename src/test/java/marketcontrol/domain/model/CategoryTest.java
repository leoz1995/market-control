package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class CategoryTest {
    @Test void createsCategoryWithNormalizedNameAndDescription() {
        Category category = Category.create("  Mercearia  ", "  Alimentos  ", true);

        assertNotNull(category.getId());
        assertEquals("Mercearia", category.getName());
        assertEquals("Alimentos", category.getDescription());
        assertTrue(category.isActive());
    }

    @Test void normalizesNullOrBlankDescriptionToNull() {
        assertNull(Category.create("Mercearia", null, true).getDescription());
        assertNull(Category.create("Higiene", "   ", false).getDescription());
    }

    @Test void rejectsNullOrBlankName() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> Category.create(null, null, true)),
                () -> assertThrows(IllegalArgumentException.class, () -> Category.create("  ", null, true)));
    }

    @Test void editsNameDescriptionAndActiveStateWithoutChangingIdentity() {
        Category category = Category.create("Mercearia", "Comida", true);
        UUID id = category.getId();

        category.edit("  Limpeza ", "  Casa  ", false);

        assertAll(
                () -> assertEquals(id, category.getId()),
                () -> assertEquals("Limpeza", category.getName()),
                () -> assertEquals("Casa", category.getDescription()),
                () -> assertFalse(category.isActive()));
    }

    @Test void rejectsInvalidEditWithoutChangingExistingValues() {
        Category category = Category.create("Mercearia", "Comida", true);

        assertThrows(IllegalArgumentException.class, () -> category.edit(" ", "Alterada", false));

        assertAll(
                () -> assertEquals("Mercearia", category.getName()),
                () -> assertEquals("Comida", category.getDescription()),
                () -> assertTrue(category.isActive()));
    }

    @Test void reconstitutesCategoryWithPersistedIdAndNormalizedValues() {
        UUID id = UUID.randomUUID();
        Category category = Category.reconstitute(id, "  Bebidas ", "  Sucos ", false);

        assertAll(
                () -> assertEquals(id, category.getId()),
                () -> assertEquals("Bebidas", category.getName()),
                () -> assertEquals("Sucos", category.getDescription()),
                () -> assertFalse(category.isActive()));
    }

    @Test void rejectsNullIdDuringReconstitution() {
        assertThrows(IllegalArgumentException.class,
                () -> Category.reconstitute(null, "Mercearia", null, true));
    }
}
