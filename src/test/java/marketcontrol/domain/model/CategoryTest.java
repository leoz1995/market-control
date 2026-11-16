package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import marketcontrol.exception.data.DataMappingException;

class CategoryTest {
    @Test void createsCategoryWithNormalizedNameAndDescription() {
        Category category = Category.create("  Mercearia  ", "  Alimentos  ", true);

        assertNotNull(category.getId());
        assertEquals("Mercearia", category.getName());
        assertEquals("Alimentos", category.getDescription());
        assertTrue(category.isActive());
    }

    @Test void rejectsMissingOrBlankDescriptionOnCreation() {
        assertThrows(IllegalArgumentException.class, () -> Category.create("Mercearia", null, true));
        assertThrows(IllegalArgumentException.class, () -> Category.create("Higiene", "   ", false));
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

    @Test void keepsExistingDescriptionWhenEditDoesNotProvideANewValue() {
        Category category = Category.create("Mercearia", "Comida", true);

        category.edit("Mercearia atualizada", null, true);

        assertEquals("Comida", category.getDescription());
    }

    @Test void keepsExistingDescriptionWhenEditReceivesBlankText() {
        Category category = Category.create("Mercearia", "Comida", true);

        category.edit("Mercearia atualizada", "  \t ", true);

        assertEquals("Comida", category.getDescription());
    }

    @Test void rejectsInvalidPersistedNameOrDescriptionDuringReconstitution() {
        UUID id = UUID.randomUUID();

        assertAll(
                () -> assertThrows(DataMappingException.class,
                        () -> Category.reconstitute(id, null, "Descrição", true)),
                () -> assertThrows(DataMappingException.class,
                        () -> Category.reconstitute(id, "Nome", null, true)),
                () -> assertThrows(DataMappingException.class,
                        () -> Category.reconstitute(id, "Nome", "  ", true)));
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
        assertThrows(DataMappingException.class,
                () -> Category.reconstitute(null, "Mercearia", null, true));
    }
}
